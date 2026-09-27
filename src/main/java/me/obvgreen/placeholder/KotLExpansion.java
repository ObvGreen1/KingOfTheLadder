package me.obvgreen.placeholder;

import me.obvgreen.KingOfTheLadder;
import me.obvgreen.arena.ArenaManager;
import me.obvgreen.database.DatabaseManager;
import me.obvgreen.database.PlayerStats;
import me.obvgreen.database.RankEntry;
import me.obvgreen.database.StatCategory;
import me.obvgreen.dialog.PlaceholderCache;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.Optional;

/**
 * The actual {@code %kotl_*%} PlaceholderAPI expansion.
 *
 * <p>Only ever loaded from inside {@link PlaceholderManager#register()}, after that method has
 * confirmed PlaceholderAPI is installed — see the note on that class for why it cannot live in
 * the same file as the presence check.</p>
 *
 * <h2>Placeholders</h2>
 * <ul>
 *   <li>{@code %kotl_top_<category>_<1-N>_name%} and {@code %kotl_top_<category>_<1-N>_value%}</li>
 *   <li>{@code %kotl_player_<category>%} — the viewing player's own total</li>
 *   <li>{@code %kotl_player_rank_<category>%} — the viewing player's 1-based position</li>
 *   <li>{@code %kotl_king_<arena>%} — the current King of a named arena</li>
 * </ul>
 *
 * <p>Leaderboard reads go through {@link PlaceholderCache} (rebuilt async every 60 s); self-stat
 * reads go through {@link DatabaseManager}'s in-memory mirror. Nothing here touches the disk, so
 * the expansion is cheap enough for a scoreboard to poll on every tick.</p>
 */
public final class KotLExpansion extends PlaceholderExpansion {

    private final KingOfTheLadder plugin;
    private final DatabaseManager database;
    private final ArenaManager arenas;
    private final PlaceholderCache cache;

    public KotLExpansion(KingOfTheLadder plugin, DatabaseManager database,
                         ArenaManager arenas, PlaceholderCache cache) {
        this.plugin = plugin;
        this.database = database;
        this.arenas = arenas;
        this.cache = cache;
    }

    @Override
    public String getIdentifier() {
        return "kotl";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** Keeps the expansion alive across a {@code /papi reload}. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer viewer, String params) {
        String[] parts = params.toLowerCase(Locale.ROOT).split("_");

        if (parts.length >= 4 && "top".equals(parts[0])) {
            return top(parts[1], parts[2], parts[3]);
        }
        if (parts.length >= 3 && "player".equals(parts[0])) {
            return playerStat(viewer, parts);
        }
        if (parts.length >= 2 && "king".equals(parts[0])) {
            return king(params);
        }
        // Returning null lets PlaceholderAPI fall through to the next expansion, which is the
        // documented "I do not know this placeholder" signal.
        return null;
    }

    /**
     * Resolves {@code top_<category>_<position>_<name|value>}.
     *
     * @param category the category token, e.g. {@code rating}
     * @param position the position token, e.g. {@code 1}
     * @param field    {@code name} or {@code value}
     */
    private String top(String category, String position, String field) {
        StatCategory stat = StatCategory.byKey(category);
        if (stat == null) {
            return "";
        }
        int rank;
        try {
            rank = Integer.parseInt(position);
        } catch (NumberFormatException exception) {
            return "";
        }
        RankEntry entry = rank < 1 ? null : cache.at(stat, rank);
        if (entry == null) {
            return "";
        }
        return switch (field) {
            case "name" -> entry.name();
            case "value" -> stat.format(entry.value());
            default -> null;
        };
    }

    /** Resolves {@code player_<category>} and {@code player_rank_<category>}. */
    private String playerStat(OfflinePlayer viewer, String[] parts) {
        if (viewer == null) {
            return "";
        }
        boolean wantsRank = "rank".equals(parts[1]);
        StatCategory stat = StatCategory.byKey(parts[wantsRank ? 2 : 1]);
        if (stat == null) {
            return "";
        }
        Optional<PlayerStats> stats = database.peek(viewer.getUniqueId());
        if (stats.isEmpty()) {
            return wantsRank ? "-" : "0";
        }
        if (wantsRank) {
            int rank = database.rankOf(viewer.getUniqueId(), stat);
            return rank < 0 ? "-" : Integer.toString(rank);
        }
        return stat.format(stats.get().valueOf(stat));
    }

    /** Resolves {@code king_<arena>}. Arena names may contain underscores, so only the first is split. */
    private String king(String params) {
        String arenaName = params.substring(params.indexOf('_') + 1);
        return arenas.byName(arenaName).map(arenas::kingName).orElse("-");
    }
}
