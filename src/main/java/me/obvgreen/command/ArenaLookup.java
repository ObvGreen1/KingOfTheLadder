package me.obvgreen.command;

import me.obvgreen.arena.Arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared arena-name resolution for the subcommands that operate on an existing arena.
 *
 * <p>Four commands take {@code <name>} and all of them need the same three outcomes — missing
 * argument, unknown name, found — reported the same way. Centralising it keeps those messages
 * consistent and means a tab-completion list only has to be written once.</p>
 */
final class ArenaLookup {

    private ArenaLookup() {
    }

    /**
     * Resolves the arena named at {@code index}.
     *
     * <p>Reports the failure to the sender as a side effect, so callers can simply return when
     * the result is empty.</p>
     *
     * @param usage the subcommand's usage line, shown when the argument is missing
     * @return the arena, or empty when the argument was absent or the name is unknown
     */
    static Optional<Arena> from(CommandContext context, int index, String usage) {
        String name = context.rawArg(index);
        if (name == null || name.isBlank()) {
            context.error("Usage: <white>" + usage);
            return Optional.empty();
        }
        Optional<Arena> arena = context.arenas().byName(name);
        if (arena.isEmpty()) {
            context.error("No arena called <white>" + name + "<red>.");
            context.info("Run <white>/kotl list<gray> to see the configured arenas.");
        }
        return arena;
    }

    /** The names of every configured arena, for tab completion. */
    static List<String> names(CommandContext context) {
        List<String> names = new ArrayList<>();
        context.arenas().arenas().forEach(arena -> names.add(arena.name()));
        return names;
    }
}
