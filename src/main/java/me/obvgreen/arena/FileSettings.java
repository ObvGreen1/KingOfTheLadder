package me.obvgreen.arena;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * An immutable read of {@code config.yml}, resolved once at enable.
 *
 * <p>Reading the file once and handing out a record keeps the hot paths — the move listener,
 * the knockoff handler — free of {@code getString} map lookups, and means a mid-tick reload
 * cannot change the knockback window halfway through a fight.</p>
 */
public record FileSettings(
        boolean joinOnRegionEntry,
        GameMode arenaGameMode,
        double arenaMaxHealth,
        List<ItemStack> kitArmour,
        Material kitStickMaterial,
        int kitStickSlot,
        Color kitColour,
        String kitStickName,
        Duration knockbackWindow,
        Duration claimCooldown,
        int kingFireworkCount,
        double kingPushStrength,
        double kingPushVertical,
        long titleFadeInMillis,
        long titleStayMillis,
        long titleFadeOutMillis,
        String noKingText,
        String joinMessage,
        String leaveMessage,
        String knockedByMessage,
        String knockoffMessage,
        String selfKnockoffMessage,
        String ratingMessage,
        String kingTitle,
        String kingSubtitle,
        String kingBroadcast,
        long leaderboardCacheSeconds,
        int leaderboardSize) {

    public static FileSettings from(FileConfiguration config) {
        return new FileSettings(
                config.getBoolean("arena.join-on-region-entry", true),
                parseGameMode(config.getString("arena.game-mode", "ADVENTURE")),
                config.getDouble("arena.max-health", 20.0D),
                readArmour(config),
                parseMaterial(config.getString("kit.stick-material", "STICK"), Material.STICK),
                clamp(config.getInt("kit.stick-slot", 0), 0, 8),
                parseColour(config.getString("kit.colour", "GOLD")),
                config.getString("kit.stick-name", "<gold><bold>Knockback Stick"),
                Duration.ofMillis(config.getLong("combat.knockback-window-seconds", 5L) * 1000L),
                Duration.ofMillis(config.getLong("king.claim-cooldown-seconds", 3L) * 1000L),
                Math.max(0, config.getInt("king.firework-count", 3)),
                config.getDouble("king.push-strength", 0.9D),
                config.getDouble("king.push-vertical", 0.6D),
                config.getLong("king.title.fade-in-millis", 200L),
                config.getLong("king.title.stay-millis", 1400L),
                config.getLong("king.title.fade-out-millis", 600L),
                config.getString("king.no-king-text", "nobody"),
                config.getString("messages.join", "<gray>You entered <white><arena><gray>."),
                config.getString("messages.leave", "<gray>You left <white><arena><gray>."),
                config.getString("messages.knocked-by", "<gray><white><player><gray> knocked you off in <white><arena><gray>."),
                config.getString("messages.knockoff", "<green>You knocked <white><player><green> off the ladder!"),
                config.getString("messages.self-knockoff", "<gray>You fell. No credit to anyone."),
                config.getString("messages.rating", "<gray>Rating <white><rating>"),
                config.getString("king.title.main", "<gold><bold>YOU ARE KING"),
                config.getString("king.title.subtitle", "<yellow>Hold the ladder!"),
                config.getString("king.broadcast", "<gold><player> <yellow>is the new King of <aqua><arena><yellow>!"),
                Math.max(5L, config.getLong("leaderboard.cache-seconds", 60L)),
                Math.max(1, Math.min(100, config.getInt("leaderboard.size", 10))));
    }

    /** Boots, leggings, chestplate, helmet — the order {@code setArmorContents} expects. */
    private static List<ItemStack> readArmour(FileConfiguration config) {
        return List.of(
                parseMaterial(config.getString("kit.armour.boots", "LEATHER_BOOTS"), Material.LEATHER_BOOTS),
                parseMaterial(config.getString("kit.armour.leggings", "LEATHER_LEGGINGS"), Material.LEATHER_LEGGINGS),
                parseMaterial(config.getString("kit.armour.chestplate", "LEATHER_CHESTPLATE"), Material.LEATHER_CHESTPLATE),
                parseMaterial(config.getString("kit.armour.helmet", "LEATHER_HELMET"), Material.LEATHER_HELMET))
                .stream()
                .map(ItemStack::new)
                .toList();
    }

    private static Material parseMaterial(String raw, Material fallback) {
        if (raw == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        return material == null || material.isLegacy() || !material.isItem() ? fallback : material;
    }

    private static GameMode parseGameMode(String raw) {
        if (raw == null) {
            return GameMode.ADVENTURE;
        }
        try {
            return GameMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return GameMode.ADVENTURE;
        }
    }

    private static Color parseColour(String raw) {
        if (raw == null) {
            return Color.fromRGB(0xFF, 0xC0, 0x00);
        }
        try {
            return Color.fromRGB(Integer.parseInt(raw.replace("#", ""), 16));
        } catch (NumberFormatException exception) {
            // Named colours are the friendlier default; unknown names fall back to gold.
            Color named = switch (raw.toUpperCase(Locale.ROOT)) {
                case "AQUA" -> Color.AQUA;
                case "RED" -> Color.RED;
                case "BLUE" -> Color.BLUE;
                case "WHITE" -> Color.WHITE;
                case "BLACK" -> Color.BLACK;
                case "GREEN" -> Color.GREEN;
                case "LIME" -> Color.LIME;
                case "YELLOW" -> Color.YELLOW;
                default -> Color.fromRGB(0xFF, 0xC0, 0x00);
            };
            return named;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }
}
