package me.obvgreen.database;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite-backed career storage.
 *
 * <h2>Concurrency model</h2>
 * The connection is opened in Write-Ahead Logging mode, which lets readers proceed while a
 * write transaction is in flight. That buys this class two connections:
 * <ul>
 *   <li>{@code writer} â€” used only from the single-threaded {@link #io} executor, so it is
 *       never contended and never needs a lock;</li>
 *   <li>{@code reader} â€” used from any thread, including async leaderboard refreshes.</li>
 * </ul>
 * A second in-memory mirror, {@link #cache}, is the authority for per-player reads. Placeholder
 * lookups and dialog rendering hit that map instead of the disk, and every mutation schedules
 * an asynchronous UPSERT, so gameplay never blocks on I/O.
 */
public final class DatabaseManager {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS player_stats (
                uuid             TEXT    PRIMARY KEY NOT NULL,
                name             TEXT    NOT NULL,
                kills            INTEGER NOT NULL DEFAULT 0,
                deaths           INTEGER NOT NULL DEFAULT 0,
                wins             INTEGER NOT NULL DEFAULT 0,
                rating           REAL    NOT NULL DEFAULT 1500.0,
                rating_deviation REAL    NOT NULL DEFAULT 350.0,
                volatility       REAL    NOT NULL DEFAULT 0.06,
                last_seen        INTEGER NOT NULL DEFAULT 0
            )
            """;

    private static final String UPSERT = """
            INSERT INTO player_stats
                (uuid, name, kills, deaths, wins, rating, rating_deviation, volatility, last_seen)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                name             = excluded.name,
                kills            = excluded.kills,
                deaths           = excluded.deaths,
                wins             = excluded.wins,
                rating           = excluded.rating,
                rating_deviation = excluded.rating_deviation,
                volatility       = excluded.volatility,
                last_seen        = excluded.last_seen
            """;

    private static final String SELECT_ALL = "SELECT * FROM player_stats";

    private final Logger logger;
    private final File databaseFile;
    private final String jdbcUrl;

    private final Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();
    private final ExecutorService io;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private volatile Connection writer;
    private volatile Connection reader;

    public DatabaseManager(Logger logger, File pluginFolder) {
        this.logger = logger;
        this.databaseFile = new File(pluginFolder, "kotl.db");
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        // A single daemon writer thread: SQLite allows exactly one writer at a time anyway,
        // and serialising here turns "database is locked" into a non-event.
        this.io = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "KotL-DB");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Opens both connections, applies the WAL pragmas and creates the schema. */
    public void initialize() {
        try {
            // Force the driver to register before any thread touches the URL.
            Class.forName("org.sqlite.JDBC");

            if (databaseFile.getParentFile() != null && !databaseFile.getParentFile().isDirectory()
                    && !databaseFile.getParentFile().mkdirs()) {
                throw new IOException("could not create plugin data folder for " + databaseFile);
            }

            writer = openTunedConnection();
            reader = openTunedConnection();

            try (Statement statement = writer.createStatement()) {
                statement.executeUpdate(CREATE_TABLE);
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_stats_rating ON player_stats(rating DESC)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_stats_kills ON player_stats(kills DESC)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_stats_deaths ON player_stats(deaths DESC)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_stats_wins ON player_stats(wins DESC)");
            }

            loadIntoCache();
            logger.info("SQLite ready at " + databaseFile.getName() + " (WAL) with " + cache.size() + " known players.");
        } catch (ClassNotFoundException | SQLException | IOException exception) {
            throw new IllegalStateException("Failed to initialise the KotL database", exception);
        }
    }

    /**
     * Opens a connection and applies the Write-Ahead Logging configuration.
     *
     * <p>WAL is what makes concurrent reads cheap: readers see the last committed snapshot
     * instead of blocking on the writer. {@code synchronous=NORMAL} is the standard companion
     * â€” with WAL it is still crash-safe, it just may lose the last transactions on an OS-level
     * power cut rather than on a process crash. {@code wal_autocheckpoint} is raised from
     * SQLite's 1000-page default so that a busy ladder does not fsync a checkpoint on the
     * gameplay thread's back.</p>
     */
    private Connection openTunedConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA temp_store=MEMORY");
            statement.execute("PRAGMA cache_size=-32000");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA wal_autocheckpoint=8000");
        }
        connection.setAutoCommit(true);
        return connection;
    }

    /** Pulls the whole table into the in-memory mirror. Small enough to do once at boot. */
    private void loadIntoCache() throws SQLException {
        cache.clear();
        try (Statement statement = reader.createStatement();
             ResultSet results = statement.executeQuery(SELECT_ALL)) {
            while (results.next()) {
                PlayerStats stats = read(results);
                cache.put(stats.uuid(), stats);
            }
        }
    }

    private static PlayerStats read(ResultSet results) throws SQLException {
        return new PlayerStats(
                UUID.fromString(results.getString("uuid")),
                results.getString("name"),
                results.getInt("kills"),
                results.getInt("deaths"),
                results.getInt("wins"),
                results.getDouble("rating"),
                results.getDouble("rating_deviation"),
                results.getDouble("volatility"));
    }

    // ------------------------------------------------------------------ reads (memory mirror)

    /** @return the cached stats for {@code uuid}, creating a defaults row on first sight. */
    public PlayerStats get(Player player) {
        return get(player.getUniqueId(), player.getName());
    }

    public PlayerStats get(UUID uuid, String name) {
        return cache.compute(uuid, (key, existing) ->
                existing == null ? PlayerStats.defaults(key, name) : existing.withName(name));
    }

    public Optional<PlayerStats> peek(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public int onlinePlayerCount() {
        return cache.size();
    }

    /**
     * Sorts the in-memory mirror into a leaderboard. Ties break on name so the order is
     * stable between refreshes and two players on identical values do not swap places.
     */
    public List<RankEntry> top(StatCategory category, int limit) {
        return cache.values().stream()
                .sorted(Comparator
                        .comparingDouble((PlayerStats stats) -> stats.valueOf(category)).reversed()
                        .thenComparing(stats -> stats.name().toLowerCase(Locale.ROOT)))
                .limit(limit)
                .map(stats -> new RankEntry(0, stats.uuid(), stats.name(), stats.valueOf(category)))
                .toList();
    }

    /**
     * @return the 1-based position of {@code uuid} on {@code category}'s board, or
     *         {@code -1} when the player has never played.
     */
    public int rankOf(UUID uuid, StatCategory category) {
        PlayerStats self = cache.get(uuid);
        if (self == null) {
            return -1;
        }
        double value = self.valueOf(category);
        String name = self.name().toLowerCase(Locale.ROOT);
        int better = 0;
        for (PlayerStats other : cache.values()) {
            double otherValue = other.valueOf(category);
            if (otherValue > value || (otherValue == value
                    && other.name().toLowerCase(Locale.ROOT).compareTo(name) < 0)) {
                better++;
            }
        }
        return better + 1;
    }

    // ------------------------------------------------------------------ writes

    /**
     * Applies {@code mutator} to the cached stats and schedules the matching UPSERT.
     *
     * <p>Runs on the caller's thread for the cache update â€” the map is concurrent and the
     * mutation is idempotent â€” and hands only the write to the I/O thread.</p>
     */
    public PlayerStats mutate(UUID uuid, String name,
                                       java.util.function.UnaryOperator<PlayerStats> mutator) {
        PlayerStats updated = cache.compute(uuid, (key, existing) ->
                mutator.apply(existing == null ? PlayerStats.defaults(key, name) : existing.withName(name)));
        save(uuid);
        return updated;
    }

    /** Schedules an asynchronous UPSERT of {@code uuid}'s current cached row. */
    public void save(UUID uuid) {
        PlayerStats snapshot = cache.get(uuid);
        if (snapshot == null || !running.get()) {
            return;
        }
        io.execute(() -> {
            Connection connection = writer;
            if (connection == null) {
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                statement.setString(1, snapshot.uuid().toString());
                statement.setString(2, snapshot.name());
                statement.setInt(3, snapshot.kills());
                statement.setInt(4, snapshot.deaths());
                statement.setInt(5, snapshot.wins());
                statement.setDouble(6, snapshot.rating());
                statement.setDouble(7, snapshot.ratingDeviation());
                statement.setDouble(8, snapshot.volatility());
                statement.setLong(9, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Failed to persist stats for " + snapshot.name(), exception);
            }
        });
    }

    /** Applies a counter delta (kills / deaths / wins) to a player. */
    public PlayerStats addCounter(Player player, StatCategory category, int delta) {
        if (category == StatCategory.RATING) {
            throw new IllegalArgumentException("rating is not a counter; use applyRating instead");
        }
        return mutate(player.getUniqueId(), player.getName(), stats -> switch (category) {
            case KILLS -> stats.withCounters(stats.kills() + delta, stats.deaths(), stats.wins());
            case DEATHS -> stats.withCounters(stats.kills(), stats.deaths() + delta, stats.wins());
            case WINS -> stats.withCounters(stats.kills(), stats.deaths(), stats.wins() + delta);
            case RATING -> throw new IllegalStateException("unreachable");
        });
    }

    /** Writes a new Glicko rating triple onto a player. */
    public PlayerStats applyRating(Player player, double rating, double deviation, double volatility) {
        return mutate(player.getUniqueId(), player.getName(),
                stats -> stats.withRating(rating, deviation, volatility));
    }

    /** Ensures every online player has a row, so their name is never blank on a leaderboard. */
    public void trackOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            save(get(player.getUniqueId(), player.getName()).uuid());
        }
    }

    /** @return a snapshot of the mirror, used by the admin dialog. */
    public List<PlayerStats> snapshot() {
        return new ArrayList<>(cache.values());
    }

    /**
     * Flushes every cached row and closes both connections. Called on plugin disable; the
     * writer queue is drained first so a shutdown cannot drop the last few knockoffs.
     */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        io.execute(() -> {
            for (UUID uuid : new ArrayList<>(cache.keySet())) {
                PlayerStats snapshot = cache.get(uuid);
                Connection connection = writer;
                if (snapshot == null || connection == null) {
                    continue;
                }
                try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                    statement.setString(1, snapshot.uuid().toString());
                    statement.setString(2, snapshot.name());
                    statement.setInt(3, snapshot.kills());
                    statement.setInt(4, snapshot.deaths());
                    statement.setInt(5, snapshot.wins());
                    statement.setDouble(6, snapshot.rating());
                    statement.setDouble(7, snapshot.ratingDeviation());
                    statement.setDouble(8, snapshot.volatility());
                    statement.setLong(9, System.currentTimeMillis());
                    statement.executeUpdate();
                } catch (SQLException exception) {
                    logger.log(Level.WARNING, "Final flush failed for " + snapshot.name(), exception);
                }
            }
        });

        try {
            io.shutdown();
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("SQLite writer did not drain within 5s; forcing shutdown.");
                io.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            io.shutdownNow();
        }

        closeQuietly(writer);
        closeQuietly(reader);
        writer = null;
        reader = null;
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            logger.log(Level.FINE, "Failed to close a SQLite connection", exception);
        }
    }

    /** Exposed for diagnostics and the admin dialog. */
    public File databaseFile() {
        return databaseFile;
    }

    /** @return an offline player's last known name, or their UUID string when unknown. */
    public String nameOf(OfflinePlayer player) {
        PlayerStats stats = cache.get(player.getUniqueId());
        return stats != null ? stats.name() : player.getName();
    }
}
