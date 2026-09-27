package me.obvgreen.command;

import me.obvgreen.arena.Arena;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /kotl list} — prints every configured arena as chat lines.
 *
 * <p>Chat rather than a dialog on purpose: this is the one setup command an admin may want to run
 * from a console, where a dialog cannot be shown at all.</p>
 */
public final class ListArenasCommand implements KotlSubcommand {

    @Override
    public String name() {
        return "list";
    }

    @Override
    public String description() {
        return "List the configured arenas";
    }

    @Override
    public String usage() {
        return "/kotl list";
    }

    @Override
    public boolean execute(CommandContext context) {
        List<Arena> all = new ArrayList<>(context.arenas().arenas());
        if (all.isEmpty()) {
            context.reply("<yellow>No arenas configured.");
            context.info("Use <white>/kotl setup<gray> for the guided setup dialog.");
            return true;
        }

        context.reply("<gold><bold>Arenas <reset><dark_gray>(<gray>" + all.size() + "<reset>)");
        for (Arena arena : all) {
            context.reply(" <dark_gray>• <white>" + arena.name()
                    + " <dark_gray>(" + (arena.active() ? "<green>active" : "<red>disabled")
                    + "<dark_gray>) <gray>" + arena.describe());
        }
        context.info("Edit any of them with <white>/kotl setup<gray>.");
        return true;
    }
}
