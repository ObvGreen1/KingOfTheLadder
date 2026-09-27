package me.obvgreen.dialog.setup;

import me.obvgreen.KingOfTheLadder;
import me.obvgreen.dialog.DialogView;
import org.bukkit.entity.Player;

/**
 * The entry point to the setup wizard behind {@code /kotl setup}.
 *
 * <p>Thin on purpose: the screens live in {@link SetupDialogs}, {@link ArenaSetupPage} and
 * {@link CreateArenaPage}, and they navigate between themselves. This class exists so the plugin
 * can hand out a single object without exposing which page is which.</p>
 */
public final class SetupDialogManager {

    private final KingOfTheLadder plugin;
    private final DialogView view;
    private final SetupDialogs menu;

    public SetupDialogManager(KingOfTheLadder plugin, DialogView view) {
        this.plugin = plugin;
        this.view = view;
        this.menu = new SetupDialogs(plugin, view);
    }

    /** Opens the first setup screen. */
    public void open(Player player) {
        menu.open(player);
    }
}
