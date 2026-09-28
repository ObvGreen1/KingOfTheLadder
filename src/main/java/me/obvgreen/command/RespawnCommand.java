package me.obvgreen.command;

import me.obvgreen.arena.Arena;
import me.obvgreen.arena.BlockPos;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code /kotl respawn <name>} — moves an arena's respawn point to where the admin is standing.
 *
 * <p>The point must be inside the arena's own region. A respawn point outside the box is not a
 * worse-than-default setting, it is an unwinnable one: arriving below the region's floor counts
 * as a knockoff and sends the player straight back, and arriving outside the region drops them
 * out of the arena so the movement listener pulls them straight back in.</p>
 */
public final class RespawnCommand implements KotlSubcommand {

    @Override
    public String name() {
        return "respawn";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("spawn", "setrespawn");
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
        return "Set an arena's respawn point to your current position";
    }

    @Override
    public String usage() {
        return "/kotl respawn <name>";
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

        BlockPos respawn = BlockPos.of(player.getLocation());
        if (!respawn.world().equals(arena.get().world())) {
            context.error("This arena lives in <white>" + arena.get().world()
                    + "<red>, so its respawn point has to be set from there.");
            return true;
        }
        if (!arena.get().contains(respawn)) {
            context.error("Stand inside <white>" + arena.get().name() + "<red> to set its respawn point.");
            context.info("The point has to be inside the arena box "
                    + "(" + arena.get().minX() + "," + arena.get().minY() + "," + arena.get().minZ()
                    + " to " + arena.get().maxX() + "," + arena.get().maxY() + "," + arena.get().maxZ() + ").");
            return true;
        }

        context.arenas().saveArena(arena.get().withRespawn(respawn));
        context.success("Respawn point for <white>" + arena.get().name() + "<green> set to " + respawn);
        return true;
    }

    @Override
    public List<String> complete(CommandContext context) {
        return context.args().length == 2 ? ArenaLookup.names(context) : List.of();
    }
}
