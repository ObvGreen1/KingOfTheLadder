package me.obvgreen.command;

import me.obvgreen.arena.Arena;
import me.obvgreen.arena.BlockPos;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * {@code /kotl setplate <name>} — marks which pressure plate makes a player the King.
 *
 * <p>Targets the block the admin is looking at, within {@link #REACH} blocks, rather than the
 * block they are standing on: the plate is at the top of a tower, and looking up at it is the
 * natural gesture.</p>
 */
public final class SetPlateCommand implements KotlSubcommand {

    /** How far the admin may be looking from the plate. */
    public static final int REACH = 6;

    @Override
    public String name() {
        return "setplate";
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
        return "Set an arena's King pressure plate to the block you are looking at";
    }

    @Override
    public String usage() {
        return "/kotl setplate <name>";
    }

    @Override
    public boolean execute(CommandContext context) {
        Player player = context.player();
        if (player == null) {
            return true;
        }
        Optional<Arena> arena = ArenaLookup.from(context, 1, usage());
        if (arena.isEmpty()) {
            return true;
        }

        Block target = player.getTargetBlockExact(REACH);
        if (target == null) {
            context.error("Look at a pressure plate within " + REACH + " blocks.");
            return true;
        }
        if (!target.getType().name().contains("PRESSURE_PLATE")) {
            context.error("That block is <white>" + target.getType()
                    + "<red>, not a pressure plate.");
            context.info("Aim at a heavy or light weighted pressure plate.");
            return true;
        }

        BlockPos plate = BlockPos.of(target);
        context.arenas().saveArena(arena.get().withKingPlate(plate));
        context.success("King plate for <white>" + arena.get().name() + "<green> set to " + plate);
        return true;
    }

    @Override
    public List<String> complete(CommandContext context) {
        return context.args().length == 2 ? ArenaLookup.names(context) : List.of();
    }
}
