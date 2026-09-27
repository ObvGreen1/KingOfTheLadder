package me.obvgreen.placeholder;

import me.obvgreen.KingOfTheLadder;
import me.obvgreen.arena.Arena;
import me.obvgreen.arena.ArenaManager;
import me.obvgreen.database.DatabaseManager;
import me.obvgreen.dialog.PlaceholderCache;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Owns the lifetime of the {@code %kotl_*%} PlaceholderAPI expansion.
 *
 * <p>Deliberately does <em>not</em> extend {@code PlaceholderExpansion}. A plugin class that
 * extends a class from an optional dependency is resolved the moment it is loaded, so with
 * PlaceholderAPI missing the server throws {@code NoClassDefFoundError} during
 * {@code onEnable} — before any "is PlaceholderAPI installed?" check could run. Keeping the
 * PAPI subclass in {@link KotLExpansion} means that class is only resolved when
 * {@link #register()} actually finds the plugin present.</p>
 */
public final class PlaceholderManager {

    private final KingOfTheLadder plugin;
    private final DatabaseManager database;
    private final ArenaManager arenas;
    private final PlaceholderCache cache;

    private KotLExpansion expansion;

    public PlaceholderManager(KingOfTheLadder plugin, DatabaseManager database,
                              ArenaManager arenas, PlaceholderCache cache) {
        this.plugin = plugin;
        this.database = database;
        this.arenas = arenas;
        this.cache = cache;
    }

    /**
     * Registers the expansion when PlaceholderAPI is installed.
     *
     * <p>The reference to {@link KotLExpansion} lives inside this method, so the JVM only
     * resolves that class at the point of execution — after the presence check has passed.</p>
     *
     * @return {@code true} when the expansion went live
     */
    public boolean register() {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().warning("PlaceholderAPI not found; %kotl_*% placeholders are unavailable.");
            return false;
        }
        KotLExpansion candidate = new KotLExpansion(plugin, database, arenas, cache);
        if (!candidate.register()) {
            return false;
        }
        expansion = candidate;
        return true;
    }

    /** Unregisters the expansion if it was ever registered. Safe to call unconditionally. */
    public void unregister() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }

    /** @return {@code true} when the expansion is currently live. */
    public boolean active() {
        return expansion != null;
    }

    /** @return the arena {@code player} is standing in, if any. */
    public Optional<Arena> arenaOf(Player player) {
        return arenas.arenaOf(player);
    }
}
