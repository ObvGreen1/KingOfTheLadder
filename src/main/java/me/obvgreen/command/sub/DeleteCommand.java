package me.obvgreen.command.sub;

import me.obvgreen.arena.Arena;
import me.obvgreen.command.CommandContext;
import me.obvgreen.command.CommandPermissions;
import me.obvgreen.command.KotlSubcommand;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * {@code /kotl delete <name> confirm} — removes an arena and its saved file entry.
 *
 * <p>The literal {@code confirm} argument is required. Deleting an arena is irreversible from
 * inside the game — the bounds are gone and the players inside lose their saved state — so a
 * mistyped name must not be enough.</p>
 */
public final class DeleteCommand implements KotlSubcommand {

    /** The word that has to follow the arena name. */
    public static final String CONFIRM_WORD = "confirm";

    @Override
    public String name() {
        return "delete";
    }

    @Override
    public String permission() {
        return CommandPermissions.ADMIN;
    }

    @Override
    public String description() {
        return "Delete an arena (irreversible)";
    }

    @Override
    public String usage() {
        return "/kotl delete <name> " + CONFIRM_WORD;
    }

    @Override
    public boolean execute(CommandContext context) {
        Optional<Arena> arena = ArenaLookup.from(context, 1, usage());
        if (arena.isEmpty()) {
            return true;
        }

        String second = context.arg(2);
        if (!CONFIRM_WORD.equals(second)) {
            context.error("This deletes <white>" + arena.get().name() + "<red> and cannot be undone.");
            context.reply("<yellow>Type <white>/kotl delete " + arena.get().name() + " " + CONFIRM_WORD
                    + "<yellow> to confirm.");
            return true;
        }

        // Release anyone inside first: their saved state has to be restored before the arena
        // disappears, otherwise they keep the kit and lose their way back to it.
        List<Player> inside = context.arenas().occupants(arena.get());
        inside.forEach(player -> context.arenas().leave(player, false));

        context.arenas().deleteArena(arena.get().name());
        context.success("Deleted arena <white>" + arena.get().name() + "<green>.");
        if (!inside.isEmpty()) {
            context.info("Released " + inside.size() + " player(s) first.");
        }
        return true;
    }

    @Override
    public List<String> complete(CommandContext context) {
        return switch (context.args().length) {
            case 2 -> ArenaLookup.names(context);
            case 3 -> List.of(CONFIRM_WORD);
            default -> List.of();
        };
    }
}
