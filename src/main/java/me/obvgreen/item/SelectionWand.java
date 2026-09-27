package me.obvgreen.item;

import me.obvgreen.text.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The administrator's region-selection wand: a feather that remembers it was issued by this
 * plugin.
 *
 * <p>Identity lives in a persistent-data key rather than a display name, so renaming the item in
 * an anvil does not make it a wand and no other plugin's feather is mistaken for one.</p>
 */
public final class SelectionWand {

    private static final String KEY_NAME = "selection_wand";

    private final NamespacedKey key;

    public SelectionWand(Plugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    /** The namespaced key the wand is tagged with, for listeners that need to re-check it. */
    public NamespacedKey key() {
        return key;
    }

    /** @return whether {@code stack} is a selection wand this plugin issued */
    public boolean isWand(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Builds a fresh selection wand. */
    public ItemStack create() {
        ItemStack wand = new ItemStack(Material.FEATHER);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.of("<gold><bold>KotL Selection Wand"));
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.setUnbreakable(true);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    /**
     * Gives {@code player} a wand.
     *
     * <p>Anything that did not fit in the inventory is dropped at their feet rather than silently
     * destroyed, so an admin working with a full inventory does not lose the item unnoticed.</p>
     */
    public void give(Player player) {
        player.getInventory().addItem(create()).forEach((index, leftover) ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.4f);
    }
}
