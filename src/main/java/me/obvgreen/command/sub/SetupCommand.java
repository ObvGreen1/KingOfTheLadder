package me.obvgreen.command.sub;

import me.obvgreen.command.CommandContext;
import me.obvgreen.command.CommandPermissions;
import me.obvgreen.command.KotlSubcommand;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * {@code /kotl setup} — opens the guided setup dialog.
 *
 * <p>The dialog is the whole point of this command: it drives every arena-setting operation from
 * one screen. The individual commands ({@code wand}, {@code create}, {@code setplate},
 * {@code spawn}, {@code toggle}, {@code delete}) still exist and still work, for scripting and
 * for anyone who prefers chat.</p>
 *
 * <p>Both routes run the same code. The dialog does not re-implement the setup rules; it builds a
 * {@link CommandContext} with the arena name filled in and calls the matching subcommand, so a
 * button and the equivalent typed command can never disagree.</p>
 */
public final class SetupCommand implements KotlSubcommand {

    @Override
    public String name() {
        return "setup";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("gui", "admin", "config");
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
        return "Open the setup dialog";
    }

    @Override
    public String usage() {
        return "/kotl setup";
    }

    @Override
    public boolean execute(CommandContext context) {
        Player player = context.player();
        if (player == null) {
            return true;
        }
        context.plugin().setupDialogs().open(player);
        return true;
    }
}
