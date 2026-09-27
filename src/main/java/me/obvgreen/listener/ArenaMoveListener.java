package me.obvgreen.listener;

import me.obvgreen.arena.ArenaManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Region membership: entering an arena, leaving it, and being knocked off the bottom.
 *
 * <p>All three are decided from a move, so they share one handler rather than three that would all
 * have to re-derive the same block-coordinate guard.</p>
 */
public final class ArenaMoveListener implements Listener {

    private final ArenaManager arenas;

    public ArenaMoveListener(ArenaManager arenas) {
        this.arenas = arenas;
    }

    /**
     * Region entry, region exit and knockoff detection.
     *
     * <p>Guarded on a block-coordinate change. {@code PlayerMoveEvent} fires on every head
     * movement, so without this the containment test would run several times per tick per
     * player for no benefit.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        arenas.handleMove(event.getPlayer(), to);
    }
}
