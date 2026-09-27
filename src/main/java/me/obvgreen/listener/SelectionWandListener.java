package me.obvgreen.listener;

import me.obvgreen.arena.ArenaManager;
import me.obvgreen.arena.BlockPos;
import me.obvgreen.item.SelectionWand;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * The administrator's region-selection wand: left-click sets corner 1, right-click sets corner 2.
 *
 * <p>Cancels the click so using the wand never places a block, breaks one, or eats food. That
 * cancellation is also what tells {@link KingPlateListener} to stand down.</p>
 */
public final class SelectionWandListener implements Listener {

    private final ArenaManager arenas;
    private final SelectionWand wand;

    public SelectionWandListener(ArenaManager arenas, SelectionWand wand) {
        this.arenas = arenas;
        this.wand = wand;
    }

    /**
     * Runs at {@code LOWEST} and cancels the event, which is what makes the wand win over the
     * King plate regardless of the order the two listeners were registered in. The plate listener
     * is {@code ignoreCancelled} and sits at {@code HIGH}, so it is always called second and always
     * sees the cancellation.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onWandClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!wand.isWand(player.getInventory().getItemInMainHand())) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        event.setCancelled(true);
        BlockPos pos = BlockPos.of(block);
        if (action == Action.LEFT_CLICK_BLOCK) {
            arenas.setSelectionFirst(player, pos);
            player.sendMessage(ArenaManager.mini("<gray>Position 1 set to <white>" + pos));
            return;
        }

        arenas.setSelectionSecond(player, pos);
        player.sendMessage(ArenaManager.mini("<gray>Position 2 set to <white>" + pos));
        if (arenas.selection(player).complete()) {
            player.sendMessage(ArenaManager.mini(
                    "<green>Selection complete. <gray>Run <white>/kotl create <name>"
                            + "<gray>, or use <white>/kotl setup<gray>."));
        }
    }
}
