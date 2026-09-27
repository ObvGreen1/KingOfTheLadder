package me.obvgreen.listener;

import me.obvgreen.arena.Arena;
import me.obvgreen.arena.ArenaManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Optional;

/**
 * Death inside an arena: attribute the kill, drop nothing, and skip the death screen.
 */
public final class ArenaDeathListener implements Listener {

    private final ArenaManager arenas;

    public ArenaDeathListener(ArenaManager arenas) {
        this.arenas = arenas;
    }

    /**
     * A death inside the arena attributes the kill to whoever last hit the victim, then hands
     * over to the respawn logic.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Optional<Arena> arena = arenas.arenaOf(victim);
        if (arena.isEmpty()) {
            return;
        }

        // Nothing should drop on a ladder: the kit is a snapshot, not loot.
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);
        arenas.handleDeath(victim, arena.get());
    }
}
