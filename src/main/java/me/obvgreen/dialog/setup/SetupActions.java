package me.obvgreen.dialog.setup;

import me.obvgreen.KingOfTheLadder;
import me.obvgreen.command.CommandContext;
import me.obvgreen.command.KotlSubcommand;
import org.bukkit.entity.Player;

/**
 * Runs a subcommand on a subcommand's behalf, on behalf of a dialog button.
 *
 * <p>The setup wizard never re-implements an arena rule. A button that sets the King plate builds
 * an argument list equivalent to {@code /kotl setplate tower} and hands it to the very same
 * {@link KotlSubcommand} class, so the two routes cannot drift apart: the same validation, the same
 * messages, the same persistence call.</p>
 */
final class SetupActions {

    private SetupActions() {
    }

    /**
     * Executes the named subcommand as {@code player}.
     *
     * <p>Permission is not re-checked — the setup dialog is only reachable through
     * {@code /kotl setup}, which already required {@code kotl.admin}. The subcommand's own
     * argument validation still runs and still reports to the player's chat.</p>
     *
     * @param subcommand the subcommand's primary name
     * @param arguments  the arguments that would follow it on the command line
     */
    static void run(KingOfTheLadder plugin, Player player, String subcommand, String... arguments) {
        KotlSubcommand target = plugin.commands().find(subcommand);
        if (target == null) {
            plugin.getLogger().warning("Setup dialog asked for unknown subcommand: " + subcommand);
            return;
        }

        String[] args = new String[arguments.length + 1];
        args[0] = subcommand;
        System.arraycopy(arguments, 0, args, 1, arguments.length);
        target.execute(new CommandContext(plugin, player, "kotl", args));
    }
}
