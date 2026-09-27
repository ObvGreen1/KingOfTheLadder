package me.obvgreen.listener;

import me.obvgreen.arena.Arena;
import me.obvgreen.arena.ArenaManager;
import me.obvgreen.arena.BlockPos;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

/**
 * The King pressure plate: standing on it claims the crown.
 *
 * <p>Registered separately from the selection-wand listener and declared
 * {@code ignoreCancelled}, so a wand click — which the wand listener cancels — never also counts
 * as a crown claim. That ordering is what lets both live in their own class without one having to
 * know the other exists.</p>
 */
public final class KingPlateListener implements Listener {

    private final ArenaManager arenas;

    public KingPlateListener(ArenaManager arenas) {
        this.arenas = arenas;
    }

    /**
     * Runs at {@code HIGH} so it is always called after {@link SelectionWandListener}, which sits
     * at {@code LOWEST} and cancels the event when the player is holding the wand. Combined with
     * {@code ignoreCancelled}, a wand click on the King plate selects a corner instead of claiming
     * the crown, without either listener having to know the other exists.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlate(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!arenas.isInArena(player)) {
            return;
        }

        BlockPos pos = BlockPos.of(block);
        Optional<Arena> match = arenas.arenaAtBlockPos(pos).filter(Arena::hasKingPlate);
        if (match.isEmpty() || !match.get().kingPlate().equals(pos)) {
            return;
        }

        // Swallows the press so the plate does not also fire its click sound and event.
        arenas.claimCrown(player, match.get());
    }
}
