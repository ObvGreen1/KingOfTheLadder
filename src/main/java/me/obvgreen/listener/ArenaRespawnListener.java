package me.obvgreen.listener;

import me.obvgreen.KingOfTheLadder;
import me.obvgreen.arena.Arena;
import me.obvgreen.arena.ArenaManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Optional;

/**
 * Respawn inside an arena: back on the arena's spawn point, at full health.
 */
public final class ArenaRespawnListener implements Listener {

    private final KingOfTheLadder plugin;
    private final ArenaManager arenas;

    public ArenaRespawnListener(KingOfTheLadder plugin, ArenaManager arenas) {
        this.plugin = plugin;
        this.arenas = arenas;
    }

    /** Puts the victim back on the arena spawn rather than the world spawn. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Optional<Arena> arena = arenas.arenaOf(player);
        if (arena.isEmpty()) {
            return;
        }
        arena.get().resolveWorld().ifPresent(world ->
                event.setRespawnLocation(arena.get().spawnOrCentre().toLocation(world)));
        // Applied on the next tick: the attribute is reset by the respawn itself.
        Bukkit.getScheduler().runTask(plugin, () -> arenas.healFully(player));
    }
}
