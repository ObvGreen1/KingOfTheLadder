package me.obvgreen.dialog.setup;

import me.obvgreen.KingOfTheLadder;
import me.obvgreen.arena.Arena;
import me.obvgreen.arena.ArenaManager;
import me.obvgreen.dialog.DialogView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The first screen of {@code /kotl setup}: what exists, what is broken, and the two buttons that
 * start something new.
 */
final class SetupDialogs {

    private final KingOfTheLadder plugin;
    private final DialogView view;

    SetupDialogs(KingOfTheLadder plugin, DialogView view) {
        this.plugin = plugin;
        this.view = view;
    }

    /** Opens the setup menu. */
    void open(Player player) {
        ArenaManager arenas = plugin.arenas();
        List<Arena> all = new ArrayList<>(arenas.arenas());
        List<DialogBody> body = new ArrayList<>();
        List<ActionButton> buttons = new ArrayList<>();

        body.add(DialogView.line("<gray>" + all.size() + " arena(s) configured. <gray>Online: <white>"
                + Bukkit.getOnlinePlayers().size()));
        body.add(DialogView.gap());

        List<String> problems = arenas.problems();
        if (!problems.isEmpty()) {
            body.add(DialogView.line("<red><bold>Needs attention"));
            problems.forEach(problem -> body.add(DialogView.line("<red>• " + problem)));
            body.add(DialogView.gap());
        }

        if (all.isEmpty()) {
            body.add(DialogView.line("<yellow>No arenas yet."));
            body.add(DialogView.line("<gray>1. Get the wand, then click two opposite corners of "
                    + "your tower."));
            body.add(DialogView.line("<gray>2. Come back here and create the arena from that "
                    + "selection."));
        }

        for (Arena arena : all) {
            String label = (arena.active() ? "<green>● " : "<red>○ ") + "<white>" + arena.name();
            body.add(DialogView.line(label
                    + " <dark_gray>(" + arenas.occupants(arena).size() + " inside)"
                    + (arena.hasKingPlate() ? "" : " <yellow>[no King plate]")));
            buttons.add(view.button(
                    "<white>" + arena.name() + " <dark_gray>[" + (arena.active() ? "on" : "off") + "]",
                    "<gray>Open setup for " + arena.name(),
                    180,
                    player2 -> new ArenaSetupPage(plugin, view).open(player2, arena.name())));
        }

        buttons.add(view.button("<gold>New arena",
                "<gray>Create an arena from the corners you picked with the wand",
                180,
                player2 -> new CreateArenaPage(plugin, view).open(player2)));

        buttons.add(view.button("<gold>Give me the wand",
                "<gray>Left-click = position 1, right-click = position 2",
                180,
                player2 -> {
                    SetupActions.run(plugin, player2, "wand");
                    open(player2);
                }));

        buttons.add(view.closeButton());

        view.show(player, view.build(
                DialogView.text("<gold><bold>KotL Setup"),
                body,
                view.actions(buttons, 2)));
    }
}
