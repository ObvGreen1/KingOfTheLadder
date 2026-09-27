package me.obvgreen.command.sub;

import me.obvgreen.command.CommandContext;
import me.obvgreen.command.CommandPermissions;
import me.obvgreen.command.KotlSubcommand;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * {@code /kotl wand} — hands the administrator a region-selection wand.
 */
public final class WandCommand implements KotlSubcommand {

    @Override
    public String name() {
        return "wand";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("selectionwand");
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
        return "Get the region-selection wand";
    }

    @Override
    public String usage() {
        return "/kotl wand";
    }

    @Override
    public boolean execute(CommandContext context) {
        Player player = context.player();
        if (player == null) {
            return true;
        }
        context.plugin().wand().give(player);
        context.success("Selection wand given. <gray>Left-click = position 1, right-click = position 2.");
        return true;
    }
}
