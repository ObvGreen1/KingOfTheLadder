package me.obvgreen.dialog;

import me.obvgreen.database.DatabaseManager;
import me.obvgreen.database.RankEntry;
import me.obvgreen.database.StatCategory;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The leaderboard snapshot both the dialogs and the placeholders read from.
 *
 * <p>Refreshed on an async timer (default every 60 s) into immutable per-category lists. The
 * renderers then do pure list indexing on the main thread, which is what keeps a
 * PlaceholderAPI expansion cheap enough to be called from a scoreboard twenty times a second.</p>
 *
 * <p>The refresh reads the database's in-memory mirror, not the disk, so it is cheap; the point
 * of the cache is to stop every placeholder call from re-sorting the whole player table.</p>
 */
public final class PlaceholderCache {

    private final org.bukkit.plugin.Plugin plugin;
    private final DatabaseManager database;
    private final Map<StatCategory, List<RankEntry>> snapshots = new EnumMap<>(StatCategory.class);
    private final int size;
    private BukkitTask task;

    public PlaceholderCache(org.bukkit.plugin.Plugin plugin, DatabaseManager database, int size) {
        this.plugin = plugin;
        this.database = database;
        this.size = size;
        for (StatCategory category : StatCategory.values()) {
            snapshots.put(category, List.of());
        }
    }

    /** Kicks off the periodic refresh. Must be called from the main thread at enable. */
    public void start(long periodSeconds) {
        refreshNow();
        long periodTicks = Math.max(20L, periodSeconds * 20L);
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::refreshNow, periodTicks, periodTicks);
    }

    /** Recomputes every category's top slice from the database mirror. */
    public void refreshNow() {
        for (StatCategory category : StatCategory.values()) {
            List<RankEntry> sorted = database.top(category, size);
            List<RankEntry> positioned = new ArrayList<>(sorted.size());
            for (int index = 0; index < sorted.size(); index++) {
                RankEntry entry = sorted.get(index);
                positioned.add(new RankEntry(index + 1, entry.uuid(), entry.name(), entry.value()));
            }
            snapshots.put(category, List.copyOf(positioned));
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** @return the cached top slice, already 1-indexed by position. */
    public List<RankEntry> top(StatCategory category) {
        return snapshots.getOrDefault(category, List.of());
    }

    /**
     * @return the cached entry at {@code position} (1-based), or {@code null} when the board
     *         is shorter than that
     */
    public RankEntry at(StatCategory category, int position) {
        List<RankEntry> entries = top(category);
        if (position < 1 || position > entries.size()) {
            return null;
        }
        return entries.get(position - 1);
    }

    public int size() {
        return size;
    }
}
