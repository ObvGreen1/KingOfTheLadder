package me.obvgreen;

import me.obvgreen.arena.ArenaManager;
import me.obvgreen.arena.FileSettings;
import me.obvgreen.command.CommandRegistry;
import me.obvgreen.command.KotLCommand;
import me.obvgreen.database.DatabaseManager;
import me.obvgreen.dialog.DialogManager;
import me.obvgreen.dialog.DialogView;
import me.obvgreen.dialog.PlaceholderCache;
import me.obvgreen.dialog.setup.SetupDialogManager;
import me.obvgreen.glicko.GlickoManager;
import me.obvgreen.item.SelectionWand;
import me.obvgreen.listener.ArenaCombatListener;
import me.obvgreen.listener.ArenaDeathListener;
import me.obvgreen.listener.ArenaMoveListener;
import me.obvgreen.listener.ArenaQuitListener;
import me.obvgreen.listener.ArenaRespawnListener;
import me.obvgreen.listener.KingPlateListener;
import me.obvgreen.listener.SelectionWandListener;
import me.obvgreen.placeholder.PlaceholderManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point: builds the manager graph, registers listeners, commands and the
 * PlaceholderAPI expansion, and tears the graph back down on disable.
 *
 * <p>Construction order matters. {@code glicko} reads config, {@code arenas} needs glicko, the
 * cache needs the database, and the dialogs need the cache — so the graph is a straight line and
 * there are no lazy initialisers to trip over.</p>
 */
public final class KingOfTheLadder extends JavaPlugin {

    private FileSettings settings;
    private GlickoManager glicko;
    private DatabaseManager database;
    private ArenaManager arenas;
    private PlaceholderCache cache;
    private DialogView dialogView;
    private DialogManager dialogs;
    private SetupDialogManager setupDialogs;
    private PlaceholderManager placeholders;
    private SelectionWand wand;
    private CommandRegistry commands;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = FileSettings.from(getConfig());

        glicko = new GlickoManager(getConfig());

        database = new DatabaseManager(getLogger(), getDataFolder());
        database.initialize();
        database.trackOnlinePlayers();

        arenas = new ArenaManager(this, database, glicko, settings);
        arenas.loadArenas();
        arenas.reportProblems();
        arenas.startHousekeeping(20L);

        cache = new PlaceholderCache(this, database, settings.leaderboardSize());
        cache.start(settings.leaderboardCacheSeconds());

        dialogView = new DialogView(this);
        dialogs = new DialogManager(this, database, cache);
        setupDialogs = new SetupDialogManager(this, dialogView);

        wand = new SelectionWand(this);
        registerListeners();
        registerCommand();

        placeholders = new PlaceholderManager(this, database, arenas, cache);
        if (registerPlaceholders()) {
            getLogger().info("Registered %kotl_*% placeholders.");
        }

        getLogger().info("King of the Ladder enabled: "
                + arenas.arenas().size() + " arena(s), "
                + database.onlinePlayerCount() + " tracked player(s).");
    }

    /**
     * Registers one listener per concern.
     *
     * <p>Order does not matter between these: the only ordering relationship is between the two
     * {@code PlayerInteractEvent} listeners, and that is pinned by their event priorities rather
     * than by registration order.</p>
     */
    private void registerListeners() {
        register(new ArenaMoveListener(arenas));
        register(new ArenaCombatListener(arenas));
        register(new ArenaDeathListener(arenas));
        register(new ArenaRespawnListener(this, arenas));
        register(new ArenaQuitListener(arenas));
        register(new SelectionWandListener(arenas, wand));
        register(new KingPlateListener(arenas));
    }

    private void register(org.bukkit.event.Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void registerCommand() {
        commands = new CommandRegistry();
        PluginCommand command = getCommand("kotl");
        if (command == null) {
            getLogger().severe("Command 'kotl' is missing from plugin.yml; commands are disabled.");
            return;
        }
        KotLCommand executor = new KotLCommand(this, commands);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            placeholders.unregister();
        }
        if (cache != null) {
            cache.stop();
        }
        if (arenas != null) {
            arenas.shutdown();
        }
        if (database != null) {
            database.shutdown();
        }
        getLogger().info("King of the Ladder disabled.");
    }

    /**
     * Registers the PlaceholderAPI expansion if PlaceholderAPI is installed.
     *
     * @return {@code true} when the expansion went live
     */
    private boolean registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("PlaceholderAPI not found; %kotl_*% placeholders are unavailable.");
            return false;
        }
        try {
            return placeholders.register();
        } catch (RuntimeException exception) {
            getLogger().warning("Failed to register placeholders: " + exception.getMessage());
            return false;
        }
    }

    public FileSettings settings() {
        return settings;
    }

    public ArenaManager arenas() {
        return arenas;
    }

    public DatabaseManager database() {
        return database;
    }

    public GlickoManager glicko() {
        return glicko;
    }

    public DialogManager dialogManager() {
        return dialogs;
    }

    /** The setup wizard behind {@code /kotl setup}. */
    public SetupDialogManager setupDialogs() {
        return setupDialogs;
    }

    public PlaceholderManager placeholders() {
        return placeholders;
    }

    /** The region-selection wand item. */
    public SelectionWand wand() {
        return wand;
    }

    /** The {@code /kotl} subcommand tree. */
    public CommandRegistry commands() {
        return commands;
    }
}
