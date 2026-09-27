package me.obvgreen.listener;

import me.obvgreen.arena.ArenaManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Disconnect while in an arena: restore the player and drop them out.
 *
 * <p>Without this, a player who quits mid-game keeps their membership entry until they log back
 * in, and comes out wearing the KotL kit.</p>
 */
public final class ArenaQuitListener implements Listener {

    private final ArenaManager arenas;

    public ArenaQuitListener(ArenaManager arenas) {
        this.arenas = arenas;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        arenas.leave(event.getPlayer(), false);
    }
}
