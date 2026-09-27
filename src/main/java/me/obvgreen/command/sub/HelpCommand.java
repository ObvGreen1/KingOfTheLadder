package me.obvgreen.command.sub;

import me.obvgreen.command.CommandContext;
import me.obvgreen.command.CommandRegistry;
import me.obvgreen.command.KotlSubcommand;

import java.util.List;

/**
 * {@code /kotl help} — lists the subcommands the sender may actually use.
 *
 * <p>Prints chat rather than opening a dialog, because a console or a command block can run it and
 * neither can be shown a dialog.</p>
 */
public final class HelpCommand implements KotlSubcommand {

    private final CommandRegistry registry;

    public HelpCommand(CommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String permission() {
        return null;
    }

    @Override
    public String description() {
        return "Show this list";
    }

    @Override
    public String usage() {
        return "/kotl help";
    }

    @Override
    public boolean execute(CommandContext context) {
        sendUsage(context, registry);
        return true;
    }

    /** The shared usage listing, also used when {@code /kotl} is run with no arguments. */
    public static void sendUsage(CommandContext context, CommandRegistry registry) {
        context.reply("<gold><bold>King of the Ladder");
        for (KotlSubcommand subcommand : registry.all()) {
            String permission = subcommand.permission();
            if (permission != null && !context.has(permission)) {
                continue;
            }
            context.reply(" <dark_gray>• <white>" + subcommand.usage()
                    + " <dark_gray>- <gray>" + subcommand.description());
        }
        context.info("Everything can also be set up through <white>/kotl setup<gray>.");
    }

    @Override
    public List<String> complete(CommandContext context) {
        return List.of();
    }
}
