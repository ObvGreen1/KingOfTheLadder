package me.obvgreen.command;

import me.obvgreen.KingOfTheLadder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

/**
 * {@code /kotl} — the dispatcher.
 *
 * <p>It resolves the subcommand, enforces its permission and the player's state, and hands over.
 * All of the actual work lives in a {@link KotlSubcommand} class of its own.</p>
 *
 * <h2>Why the guards are here</h2>
 * <p>Permission and sender-type checks happen in exactly one place, before {@code execute} is
 * called, and every refusal prints a message. An earlier version of this class inlined the check
 * in each {@code case} and got the condition backwards — {@code if (requireAdmin(sender)) return;} —
 * so {@code /kotl gui} and {@code /kotl create} silently did nothing for exactly the players who
 * were allowed to use them. Centralising the guard is what makes that class of bug unrepresentable.</p>
 */
public final class KotLCommand implements CommandExecutor, TabCompleter {

    private final KingOfTheLadder plugin;
    private final CommandRegistry registry;

    public KotLCommand(KingOfTheLadder plugin, CommandRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CommandContext context = new CommandContext(plugin, sender, label, args);

        if (args.length == 0) {
            HelpCommand.sendUsage(context, registry);
            return true;
        }

        KotlSubcommand subcommand = registry.find(args[0]);
        if (subcommand == null) {
            context.error("Unknown subcommand <white>" + args[0] + "<red>.");
            context.info("Run <white>/kotl help<gray> to see what is available.");
            return true;
        }

        String permission = subcommand.permission();
        if (permission != null && !context.requirePermission(permission)) {
            return true;
        }
        if (subcommand.requiresPlayer() && !context.isPlayer()) {
            context.requirePlayer(subcommand.usage());
            return true;
        }

        subcommand.execute(context);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        CommandContext context = new CommandContext(plugin, sender, alias, args);

        if (args.length <= 1) {
            return filter(registry.visibleTo(context), args.length == 1 ? args[0] : "");
        }

        KotlSubcommand subcommand = registry.find(args[0]);
        if (subcommand == null) {
            return List.of();
        }
        String permission = subcommand.permission();
        if (permission != null && !context.has(permission)) {
            return List.of();
        }
        return filter(subcommand.complete(context), args[args.length - 1]);
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .toList();
    }
}
