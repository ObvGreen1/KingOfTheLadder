package me.obvgreen.command;

import me.obvgreen.arena.Arena;
import me.obvgreen.arena.BlockPos;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * {@code /kotl spawn <name>} — moves an arena's respawn point to where the admin is standing.
 */
public final class SetSpawnCommand implements KotlSubcommand {

    @Override
    public String name() {
        return "spawn";
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
        return "Set an arena's spawn to your current position";
    }

    @Override
    public String usage() {
        return "/kotl spawn <name>";
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

        BlockPos spawn = BlockPos.of(player.getLocation());
        if (!spawn.world().equals(arena.get().world())) {
            context.error("This arena lives in <white>" + arena.get().world()
                    + "<red>, so its spawn has to be set from there.");
            return true;
        }

        context.arenas().saveArena(arena.get().withSpawn(spawn));
        context.success("Spawn for <white>" + arena.get().name() + "<green> set to " + spawn);
        return true;
    }

    @Override
    public List<String> complete(CommandContext context) {
        return context.args().length == 2 ? ArenaLookup.names(context) : List.of();
    }
}
