package me.obvgreen.dialog.setup;

import me.obvgreen.KingOfTheLadder;
import me.obvgreen.arena.ArenaManager;
import me.obvgreen.arena.BlockPos;
import me.obvgreen.command.sub.CreateCommand;
import me.obvgreen.dialog.DialogView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The "new arena" screen: a name field and a create button.
 *
 * <p>This is the one setup step that needs a value the player has to invent, so it is the one step
 * that uses a dialog input rather than reading a position from the world. Submitting it runs
 * {@code /kotl create <name>} with whatever was typed, and the subcommand does the validating.</p>
 */
final class CreateArenaPage {

    private static final String NAME_KEY = "arena_name";

    private final KingOfTheLadder plugin;
    private final DialogView view;

    CreateArenaPage(KingOfTheLadder plugin, DialogView view) {
        this.plugin = plugin;
        this.view = view;
    }

    void open(Player player) {
        ArenaManager arenas = plugin.arenas();
        ArenaManager.Selection selection = arenas.selection(player);

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogView.line("<gray>An arena is the box between two opposite corners. The "
                + "floor is the bottom of the box: falling below it puts you back on the spawn "
                + "point and keeps you in the game."));
        body.add(DialogView.gap());
        body.add(DialogView.line(selection.first() == null
                ? "<yellow>Position 1: <red>not set"
                : "<green>Position 1: <white>" + selection.first()));
        body.add(DialogView.line(selection.second() == null
                ? "<yellow>Position 2: <red>not set"
                : "<green>Position 2: <white>" + selection.second()));
        body.add(DialogView.gap());

        boolean ready = selection.complete();
        if (!ready) {
            body.add(DialogView.line("<yellow>Both corners are needed before an arena can be made."));
            body.add(DialogView.line("<gray>Get the wand, then left-click one corner and right-click "
                    + "the opposite one."));
        } else {
            body.add(DialogView.line("<green>Ready. The arena will span "
                    + describe(selection) + "."));
            body.add(DialogView.line("<gray>You will be teleported nowhere — players are pulled in "
                    + "when they walk into the region."));
        }

        List<DialogInput> inputs = List.of(
                DialogInput.text(NAME_KEY, DialogView.text("<white>Arena name"))
                        .width(220)
                        .maxLength(CreateCommand.MAX_NAME_LENGTH)
                        .initial("")
                        .build());

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(view.button(
                DialogView.text("<green>Create arena"),
                DialogView.text("<gray>Creates the arena from your selection"),
                200,
                view.onSubmit((clicker, response) -> {
                    String name = response.getText(NAME_KEY);
                    if (name == null || name.isBlank()) {
                        clicker.sendMessage(me.obvgreen.arena.ArenaManager.mini(
                                "<red>Type a name for the arena first."));
                        open(clicker);
                        return;
                    }
                    SetupActions.run(plugin, clicker, "create", name);
                    new SetupDialogs(plugin, view).open(clicker);
                })));

        buttons.add(view.button(
                DialogView.text("<gold>Give me the wand"),
                DialogView.text("<gray>Left-click = position 1, right-click = position 2"),
                200,
                clicker -> {
                    SetupActions.run(plugin, clicker, "wand");
                    open(clicker);
                }));

        buttons.add(view.button(
                DialogView.text("<gray>Back"),
                DialogView.text("<gray>Back to the setup menu"),
                140,
                clicker -> new SetupDialogs(plugin, view).open(clicker)));

        view.show(player, view.build(
                DialogView.text("<gold><bold>New Arena"),
                body,
                inputs,
                view.actions(buttons, 2)));
    }

    /** A short human-readable span of the selection, for the confirmation line. */
    private static String describe(ArenaManager.Selection selection) {
        BlockPos first = selection.first();
        BlockPos second = selection.second();
        int width = Math.abs(first.x() - second.x()) + 1;
        int height = Math.abs(first.y() - second.y()) + 1;
        int depth = Math.abs(first.z() - second.z()) + 1;
        return width + " × " + height + " × " + depth + " blocks in " + first.world();
    }
}
