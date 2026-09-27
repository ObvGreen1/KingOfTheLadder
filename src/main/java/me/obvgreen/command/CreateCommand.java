package me.obvgreen.command;

import me.obvgreen.arena.Arena;
import me.obvgreen.arena.ArenaManager;
import me.obvgreen.arena.BlockPos;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * {@code /kotl create <name>} — turns the wand selection into a persistent arena.
 *
 * <p>The selection is consumed on success, so re-running the command with the same corners
 * reports a missing selection rather than silently creating a second arena.</p>
 */
public final class CreateCommand implements KotlSubcommand {

    /** Longest arena name that still fits a chat line and a dialog button. */
    public static final int MAX_NAME_LENGTH = 24;

    @Override
    public String name() {
        return "create";
    }

    @Override
    public String permission() {
        return CommandPermissions.ADMIN;
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }

    @Override
    public String description() {
        return "Create an arena from your wand selection";
    }

    @Override
    public String usage() {
        return "/kotl create <name>";
    }

    @Override
    public boolean execute(CommandContext context) {
        Player player = context.player();
        if (player == null) {
            return true;
        }

        String raw = context.rawArg(1);
        if (raw == null || raw.isBlank()) {
            context.error("Usage: <white>/kotl create <name>");
            context.info("Pick two corners with the wand first: <white>/kotl wand");
            return true;
        }

        String name = raw.trim();
        String problem = validate(context, name);
        if (problem != null) {
            context.error(problem);
            return true;
        }

        ArenaManager arenas = context.arenas();
        ArenaManager.Selection selection = arenas.selection(player);
        if (!selection.complete()) {
            context.error("Select both corners with the wand first.");
            context.info("Left-click a block for position 1, right-click for position 2.");
            return true;
        }
        if (!selection.first().world().equals(selection.second().world())) {
            context.error("Both corners must be in the same world — position 1 is in <white>"
                    + selection.first().world() + "<red> and position 2 is in <white>"
                    + selection.second().world() + "<red>.");
            return true;
        }

        BlockPos spawn = BlockPos.of(player.getLocation());
        Arena arena = Arena.of(name, selection.first().world(),
                selection.first(), selection.second(), spawn, null, true);
        arenas.saveArena(arena);
        arenas.clearSelection(player);

        context.success("Created arena <white>" + name + "<green>.");
        context.info("Next: look at the King pressure plate and run <white>/kotl setplate " + name);
        return true;
    }

    /**
     * Checks an arena name against everything that can go wrong with it.
     *
     * @return an error message, or {@code null} when the name is acceptable
     */
    private String validate(CommandContext context, String name) {
        if (name.length() > MAX_NAME_LENGTH) {
            return "Arena names are limited to " + MAX_NAME_LENGTH + " characters (“" + name + "” is "
                    + name.length() + ").";
        }
        if (!name.matches("[A-Za-z0-9_-]+")) {
            return "Arena names may only use letters, digits, <white>-<red> and <white>_<red>.";
        }
        if (context.arenas().byName(name.toLowerCase(Locale.ROOT)).isPresent()) {
            return "An arena called <white>" + name + " <red>already exists.";
        }
        return null;
    }
}
