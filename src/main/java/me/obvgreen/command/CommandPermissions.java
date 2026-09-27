package me.obvgreen.command;

/**
 * The permission nodes this plugin checks, in one place.
 *
 * <p>Declared in {@code plugin.yml} as {@code kotl.admin} (op, child {@code kotl.use}) and
 * {@code kotl.use} (true). Every subcommand names the node it needs through
 * {@link KotlSubcommand#permission()}, so the set is visible from the command tree rather than
 * only from YAML.</p>
 */
public final class CommandPermissions {

    /** Required by every subcommand a normal player is allowed to use. */
    public static final String USE = "kotl.use";

    /** Required by anything that creates, edits or deletes arenas. */
    public static final String ADMIN = "kotl.admin";

    private CommandPermissions() {
    }
}
