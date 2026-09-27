package me.obvgreen.dialog;

import me.obvgreen.KingOfTheLadder;
import me.obvgreen.text.Text;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogInstancesProvider;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Everything needed to build and show one native Paper dialog, and nothing about what any
 * particular dialog says.
 *
 * <h2>How the 26.2 dialog API fits together</h2>
 * <p>Paper 26.2 exposes dialogs through the registry system, not the older
 * {@code Dialog#builder} shape. A dialog is a {@link DialogRegistryEntry} — a
 * {@link DialogBase} (title, body, inputs) plus a {@link DialogType} (notice, confirmation,
 * multiAction, dialogList). Passing a builder to {@link Dialog#create(Consumer)} registers it
 * and returns the live handle, which is then shown with {@code Audience#showDialog}.</p>
 *
 * <p>Interaction is a custom-click action whose callback is registered up front through
 * {@link DialogInstancesProvider#register}.</p>
 *
 * <p>This class is shared by the leaderboard dialog and the setup wizard so that both build
 * buttons, register callbacks and close dialogs the same way.</p>
 */
public final class DialogView {

    /** Width of a body line, in dialog pixels. */
    public static final int BODY_WIDTH = 320;

    private static final int LIFETIME_MINUTES = 10;

    private final KingOfTheLadder plugin;

    public DialogView(KingOfTheLadder plugin) {
        this.plugin = plugin;
    }

    // ================================================================== assembly

    /**
     * Assembles a {@link Dialog} from its parts.
     *
     * <p>{@link Dialog#create} takes a consumer of a {@code RegistryBuilderFactory}: the
     * consumer obtains an entry builder, fills in the base and type, and {@code create}
     * registers it and hands back the live dialog.</p>
     */
    public Dialog build(Component title, List<DialogBody> body, List<DialogInput> inputs, DialogType type) {
        DialogBase base = DialogBase.builder(title)
                .body(body)
                .inputs(inputs)
                .canCloseWithEscape(true)
                // MUST be false: Paper throws when a pausing dialog uses after_action NONE
                .pause(false)
                // NONE, not CLOSE: closing the screen makes the client call grabMouse, which
                // re-centres the OS cursor and zeroes the camera delta, snapping the crosshair.
                // Menus navigate by showing the next dialog, and the exit button calls closeDialog.
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();
        return Dialog.create(factory -> {
            DialogRegistryEntry.Builder entry = factory.empty();
            entry.base(base);
            entry.type(type);
        });
    }

    /** A dialog with no inputs. */
    public Dialog build(Component title, List<DialogBody> body, DialogType type) {
        return build(title, body, List.of(), type);
    }

    public void show(Player player, Dialog dialog) {
        player.showDialog(dialog);
    }

    /** The shared instance of Paper's dialog factory. */
    public static DialogInstancesProvider buttons() {
        return DialogInstancesProvider.instance();
    }

    // ================================================================== content

    /** A component for a title, button label or tooltip. */
    public static Component text(String miniMessage) {
        return Text.of(miniMessage);
    }

    public static DialogBody line(String miniMessage) {
        return buttons().plainMessageDialogBody(Text.of(miniMessage), BODY_WIDTH);
    }

    public static DialogBody line(Component component) {
        return buttons().plainMessageDialogBody(component, BODY_WIDTH);
    }

    /** An empty body line, used as vertical spacing. */
    public static DialogBody gap() {
        return line(Component.empty());
    }

    /** A one-line-button dialog: a title, some body, and a row of actions. */
    public DialogType actions(List<ActionButton> buttons, int columns) {
        return buttons().multiAction(buttons).exitAction(closeButton()).columns(columns).build();
    }

    /**
     * The base button builder.
     *
     * <p>Deliberately an instance method even though it touches no instance state: as a static
     * overload it competed with the {@code Consumer<Player>} overloads for the same argument
     * shapes, and {@code javac} resolved the ambiguity by picking this one.</p>
     */
    public ActionButton button(Component label, Component tooltip, int width, DialogAction action) {
        return buttons().actionButtonBuilder(label)
                .tooltip(tooltip)
                .width(width)
                .action(action)
                .build();
    }

    public ActionButton button(String label, String tooltip, int width, Consumer<Player> onClick) {
        return button(text(label), text(tooltip), width, onClick(onClick));
    }

    /** The same, for labels that were already built as components. */
    public ActionButton button(Component label, Component tooltip, int width, Consumer<Player> onClick) {
        return button(label, tooltip, width, onClick(onClick));
    }

    public ActionButton closeButton() {
        return button(text("<red>Close"), text("<gray>Close this dialog"), 120, closeAction());
    }

    // ================================================================== actions

    /**
     * The close button's action.
     *
     * <p>Adventure 5.2 has no {@code ClickEvent.closeDialog()} constant, so closing rides the
     * same custom-click mechanism as every other button: the callback receives the audience
     * and calls {@code closeDialog()} on it.</p>
     */
    public DialogAction closeAction() {
        return new RegisteredAction(buttons().register(
                (response, audience) -> audience.closeDialog(), clickOptions())).handle();
    }

    /**
     * A custom click action that runs {@code onClick} against the player who clicked.
     *
     * <p>Paper already invokes dialog callbacks on the main thread; the hop through
     * {@code runTask} keeps a re-opened dialog off the click handler's own stack so a button
     * that rebuilds its dialog cannot be re-entered mid-render.</p>
     */
    public DialogAction onClick(Consumer<Player> onClick) {
        return new RegisteredAction(buttons().register((response, audience) -> {
            if (audience instanceof Player player && player.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> onClick.accept(player));
            }
        }, clickOptions())).handle();
    }

    /**
     * Like {@link #onClick}, but the handler also receives the dialog's input values.
     *
     * <p>Used by the setup wizard's name field: {@code response.getText("name")} is what the
     * player typed.</p>
     */
    public DialogAction onSubmit(BiConsumer<Player, DialogResponseView> onSubmit) {
        return new RegisteredAction(buttons().register((response, audience) -> {
            if (audience instanceof Player player && player.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> onSubmit.accept(player, response));
            }
        }, clickOptions())).handle();
    }

    /**
     * The bounded options every registered callback gets. Without a lifetime, a callback
     * registered today stays reachable from a dialog a player left open all week.
     */
    private static ClickCallback.Options clickOptions() {
        return ClickCallback.Options.builder()
                .uses(ClickCallback.UNLIMITED_USES)
                .lifetime(Duration.ofMinutes(LIFETIME_MINUTES))
                .build();
    }

    /** A registered custom-click action; {@link #handle()} unwraps it for {@code ActionButton}. */
    public record RegisteredAction(DialogAction.CustomClickAction handle) {
    }
}
