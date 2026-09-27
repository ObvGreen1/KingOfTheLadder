package me.obvgreen.dialog.setup;

import me.obvgreen.KingOfTheLadder;
import me.obvgreen.arena.Arena;
import me.obvgreen.arena.ArenaManager;
import me.obvgreen.command.DeleteCommand;
import me.obvgreen.command.SetPlateCommand;
import me.obvgreen.dialog.DialogView;
import me.obvgreen.text.Text;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The per-arena setup screen.
 *
 * <p>Every button here reads something from the world at the moment it is clicked — the block the
 * admin is looking at, the block they are standing on — and hands it to the matching subcommand.
 * The dialog cannot know those things itself, so it asks the player to aim first and states the
 * requirement on the button's tooltip.</p>
 */
final class ArenaSetupPage {

    private final KingOfTheLadder plugin;
    private final DialogView view;

    ArenaSetupPage(KingOfTheLadder plugin, DialogView view) {
        this.plugin = plugin;
        this.view = view;
    }

    void open(Player player, String arenaName) {
        ArenaManager arenas = plugin.arenas();
        Optional<Arena> found = arenas.byName(arenaName);
        if (found.isEmpty()) {
            player.sendMessage(Text.of(
                    "<red>No arena called <white>" + arenaName + "<red> any more."));
            new SetupDialogs(plugin, view).open(player);
            return;
        }

        Arena arena = found.get();
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogView.line((arena.active() ? "<green>● active" : "<red>○ disabled")
                + " <dark_gray>— <gray>" + arena.describe()));
        body.add(DialogView.line("<gray>World: <white>" + arena.world()
                + "  <gray>Size: <white>" + size(arena) + " blocks"));
        body.add(DialogView.gap());
        body.add(DialogView.line(arena.hasKingPlate()
                ? "<gray>King plate: <white>" + arena.kingPlate()
                : "<yellow>King plate: <red>not set <dark_gray>(nobody can claim the crown)"));
        body.add(DialogView.line("<gray>Spawn: <white>"
                + (arena.spawn() == null ? "<red>unset" : arena.spawn().toString())));
        body.add(DialogView.line("<gray>Players inside: <white>"
                + arenas.occupants(arena).size()));
        body.add(DialogView.gap());

        List<ActionButton> buttons = new ArrayList<>();

        buttons.add(view.button(
                arena.hasKingPlate() ? "<yellow>Change King plate" : "<yellow>Set King plate",
                "<gray>Look at the pressure plate within " + SetPlateCommand.REACH
                        + " blocks, then click",
                200,
                clicker -> {
                    SetupActions.run(plugin, clicker, "setplate", arena.name());
                    open(clicker, arena.name());
                }));

        buttons.add(view.button(
                "<yellow>Set spawn here",
                "<gray>Uses the block you are standing on",
                200,
                clicker -> {
                    SetupActions.run(plugin, clicker, "spawn", arena.name());
                    open(clicker, arena.name());
                }));

        buttons.add(view.button(
                arena.active() ? "<red>Disable arena" : "<green>Enable arena",
                arena.active()
                        ? "<gray>Stops new players being pulled in and releases anyone inside"
                        : "<gray>Players can enter this region again",
                200,
                clicker -> {
                    SetupActions.run(plugin, clicker, "toggle", arena.name());
                    open(clicker, arena.name());
                }));

        buttons.add(view.button(
                "<red>Delete arena",
                "<gray>Permanently removes " + arena.name(),
                200,
                clicker -> confirmDelete(clicker, arena.name())));

        buttons.add(view.button(
                "<gray>Back",
                "<gray>Back to the setup menu",
                200,
                clicker -> new SetupDialogs(plugin, view).open(clicker)));

        view.show(player, view.build(
                DialogView.text("<gold><bold>" + arena.name()),
                body,
                view.actions(buttons, 2)));
    }

    /**
     * A yes/no confirmation before an irreversible delete.
     *
     * <p>Uses Paper's {@code confirmation} dialog type rather than a self-built pair of buttons,
     * so it gets the client's standard red/green treatment.</p>
     */
    private void confirmDelete(Player player, String arenaName) {
        List<DialogBody> body = List.of(
                DialogView.line("<red>This permanently removes <white>" + arenaName + "<red>."),
                DialogView.line("<gray>Its bounds, spawn and King plate are gone. Players inside are "
                        + "released first."),
                DialogView.gap(),
                DialogView.line("<gray>The same as typing <white>/kotl delete " + arenaName
                        + " " + DeleteCommand.CONFIRM_WORD));

        ActionButton yes = view.button(
                DialogView.text("<red>Yes, delete it"),
                DialogView.text("<red>This cannot be undone"),
                200,
                clicker -> {
                    SetupActions.run(plugin, clicker, "delete", arenaName, DeleteCommand.CONFIRM_WORD);
                    new SetupDialogs(plugin, view).open(clicker);
                });

        ActionButton no = view.button(
                DialogView.text("<green>No, keep it"),
                DialogView.text("<gray>Go back"),
                200,
                clicker -> open(clicker, arenaName));

        DialogType confirmation = DialogView.buttons().confirmation(yes, no);
        view.show(player, view.build(
                DialogView.text("<red><bold>Delete " + arenaName + "?"),
                body,
                confirmation));
    }

    private static String size(Arena arena) {
        return (arena.maxX() - arena.minX() + 1)
                + " × " + (arena.maxY() - arena.minY() + 1)
                + " × " + (arena.maxZ() - arena.minZ() + 1);
    }
}
