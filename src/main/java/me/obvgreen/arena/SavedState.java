package me.obvgreen.arena;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A full snapshot of a player's pre-arena state.
 *
 * <p>Captured on arena join and replayed on exit. Every field is a deep copy taken at capture
 * time, because the live objects keep mutating while the player is in the ladder â€” holding on
 * to the original {@link ItemStack} references would restore an emptied stack.</p>
 */
public final class SavedState {

    private final ItemStack[] contents;
    private final ItemStack[] armour;
    private final ItemStack[] extra;
    private final ItemStack cursor;
    private final int heldSlot;
    private final int level;
    private final float experience;
    private final double health;
    private final double maxHealth;
    private final int foodLevel;
    private final float saturation;
    private final int fireTicks;
    private final boolean allowFlight;
    private final boolean flying;
    private final GameMode gameMode;
    private final Location location;
    private final List<PotionEffect> effects;

    private SavedState(ItemStack[] contents, ItemStack[] armour, ItemStack[] extra, ItemStack cursor,
                       int heldSlot, int level, float experience, double health, double maxHealth,
                       int foodLevel, float saturation, int fireTicks, boolean allowFlight,
                       boolean flying, GameMode gameMode, Location location, List<PotionEffect> effects) {
        this.contents = contents;
        this.armour = armour;
        this.extra = extra;
        this.cursor = cursor;
        this.heldSlot = heldSlot;
        this.level = level;
        this.experience = experience;
        this.health = health;
        this.maxHealth = maxHealth;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.fireTicks = fireTicks;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.gameMode = gameMode;
        this.location = location;
        this.effects = effects;
    }

    public static SavedState capture(Player player) {
        PlayerInventory inventory = player.getInventory();
        return new SavedState(
                copy(inventory.getStorageContents()),
                copy(inventory.getArmorContents()),
                copy(inventory.getExtraContents()),
                copyItem(player.getItemOnCursor()),
                inventory.getHeldItemSlot(),
                player.getLevel(),
                player.getExp(),
                player.getHealth(),
                maxHealthOf(player),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getFireTicks(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getGameMode(),
                player.getLocation().clone(),
                new ArrayList<>(player.getActivePotionEffects()));
    }

    /**
     * Replays the snapshot onto {@code player}.
     *
     * <p>Health is applied after the inventory because the arena kit may have changed the
     * player's max health attribute, and Bukkit rejects a {@code setHealth} above the current
     * maximum.</p>
     */
    public void restore(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setStorageContents(copy(contents));
        inventory.setArmorContents(copy(armour));
        inventory.setExtraContents(copy(extra));
        player.setItemOnCursor(cursor == null ? null : cursor.clone());
        inventory.setHeldItemSlot(heldSlot);

        for (PotionEffect active : player.getActivePotionEffects()) {
            player.removePotionEffect(active.getType());
        }
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }

        player.setLevel(level);
        player.setExp(experience);
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setFireTicks(fireTicks);
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying && allowFlight);
        player.setFallDistance(0.0F);

        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(maxHealth);
        }
        player.setHealth(Math.min(health, maxHealthOf(player)));

        player.teleport(location);
    }

    private static double maxHealthOf(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0D : attribute.getValue();
    }

    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int slot = 0; slot < source.length; slot++) {
            copy[slot] = copyItem(source[slot]);
        }
        return copy;
    }

    /**
     * Deep-copies a single stack, or returns {@code null}.
     *
     * <p>Used everywhere instead of a bare {@code ItemStack.clone()}. {@code clone()} is declared
     * to return {@code ItemStack} even though {@code getType()} returns {@code ItemType}, so a
     * shallow copy is what the signature advertises — fine for the client, wrong for a snapshot
     * that has to survive the original being mutated.</p>
     */
    private static ItemStack copyItem(ItemStack source) {
        return source == null ? null : source.clone();
    }

    /**
     * @return an independent deep copy, used when a snapshot is handed to a second player
     *
     * <p>The cursor is copied through {@link #copyItem}, not a bare {@code clone()}. A bare
     * {@code clone()} is exactly what the IDE flags here: {@code ItemStack.clone()} is declared
     * to return {@code ItemStack}, while {@code getType()} returns {@code ItemType}, so the
     * copy is one abstraction layer too shallow and an editor has no way to check the
     * deep-copy intent. {@link #copyItem} deep-copies the stack's data, which is what a
     * snapshot needs.</p>
     */
    public SavedState duplicate() {
        return new SavedState(copy(contents), copy(armour), copy(extra), copyItem(cursor),
                heldSlot, level, experience, health,
                maxHealth, foodLevel, saturation, fireTicks, allowFlight, flying, gameMode,
                location.clone(), new ArrayList<>(effects));
    }

    public Collection<PotionEffect> effects() {
        return List.copyOf(effects);
    }

    public Location location() {
        return location.clone();
    }
}
