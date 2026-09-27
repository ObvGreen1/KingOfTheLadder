package me.obvgreen.command;

import me.obvgreen.arena.Arena;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * {@code /kotl toggle <name>} — enables or disables an arena.
 *
 * <p>Works from the console as well as in-game: unlike the other setup commands it needs no
 * position, only a name.</p>
 */
public final class ToggleCommand implements KotlSubcommand {

    @Override
    public String name() {
        return "toggle";
    }

    @Override
    public String permission() {
        return CommandPermissions.ADMIN;
    }

    @Override
    public String description() {
        return "Enable or disable an arena";
    }

    @Override
    public String usage() {
        return "/kotl toggle <name>";
    }

    @Override
    public boolean execute(CommandContext context) {
        Optional<Arena> arena = ArenaLookup.from(context, 1, usage());
        if (arena.isEmpty()) {
            return true;
        }

        Arena toggled = arena.get().withActive(!arena.get().active());
        context.arenas().saveArena(toggled);

        if (toggled.active()) {
            context.success(toggled.name() + " is now <white>active<green>.");
        } else {
            context.info(toggled.name() + " is now <white>disabled<gray> — players will not be "
                    + "pulled into it.");
        }

        // Anyone standing inside a region that just went inactive has to be let out, otherwise
        // they keep the kit and the arena-only rules while the arena is off.
        if (!toggled.active()) {
            List<Player> inside = context.arenas().occupants(toggled);
            inside.forEach(player -> context.arenas().leave(player, false));
            if (!inside.isEmpty()) {
                context.info("Released " + inside.size() + " player(s) from the arena.");
            }
        }
        return true;
    }

    @Override
    public List<String> complete(CommandContext context) {
        return context.args().length == 2 ? ArenaLookup.names(context) : List.of();
    }
}
