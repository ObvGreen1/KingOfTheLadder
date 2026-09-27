package me.obvgreen.command;

import java.util.List;
import java.util.Set;

/**
 * One {@code /kotl} subcommand.
 *
 * <p>Every subcommand is a standalone class with no shared mutable state: it reads what it needs
 * out of the {@link CommandContext} and is otherwise independent. That is what lets the setup
 * dialog reuse a subcommand without the player having to type it.</p>
 *
 * <h2>Contract the dispatcher enforces</h2>
 * <p>{@link #permission()} and {@link #requiresPlayer()} are checked <em>before</em>
 * {@link #execute} runs, and each refusal is reported to the sender. A subcommand therefore never
 * has to re-check them, and a silent no-op is not a reachable state.</p>
 */
public interface KotlSubcommand {

    /** The primary name, as typed after {@code /kotl}. */
    String name();

    /** Alternative spellings that resolve to this subcommand. */
    default Set<String> aliases() {
        return Set.of();
    }

    /** The node the sender must hold, or {@code null} when anyone may run it. */
    default String permission() {
        return CommandPermissions.USE;
    }

    /** Whether the sender must be a player. Checked before {@link #execute}. */
    default boolean requiresPlayer() {
        return false;
    }

    /** A one-line description, used by {@code /kotl help} and tab completion. */
    default String description() {
        return "";
    }

    /** The full invocation, e.g. {@code /kotl setplate <name>}. */
    default String usage() {
        return "/" + name();
    }

    /**
     * Runs the subcommand.
     *
     * <p>The dispatcher has already verified the permission and the sender's type, so this method
     * only has to validate its own arguments.</p>
     *
     * @return {@code true} when handled; the dispatcher always reports handled either way
     */
    boolean execute(CommandContext context);

    /**
     * Tab completions for the argument currently being typed.
     *
     * @param context the invocation, with {@code args} as typed so far
     * @return candidate completions, unfiltered
     */
    default List<String> complete(CommandContext context) {
        return List.of();
    }
}
