package me.obvgreen.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.Optional;

/**
 * A configured KotL arena: one cuboid region plus the spawn point and the King pressure plate.
 *
 * <p>All coordinates are stored normalised (min &lt;= max on every axis) by {@link #of} so the
 * containment test never has to care which corner the administrator selected first.</p>
 */
public record Arena(
        String name,
        String world,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        BlockPos spawn,
        BlockPos kingPlate,
        boolean active) {

    public Arena {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(world, "world");
    }

    /**
     * Builds an arena, normalising the two corners.
     *
     * @param kingPlate may be {@code null} until {@code /kotl setplate} is run
     */
    public static Arena of(String name, String world, BlockPos a, BlockPos b,
                           BlockPos spawn, BlockPos kingPlate, boolean active) {
        if (!a.world().equals(world) || !b.world().equals(world)) {
            throw new IllegalArgumentException("arena corners must be in the same world as the arena");
        }
        return new Arena(name, world,
                Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()),
                spawn, kingPlate, active);
    }

    /** @return the arena containing {@code location}, ignoring the active flag. */
    public boolean contains(Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(world)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(BlockPos pos) {
        return pos.world().equals(world)
                && pos.x() >= minX && pos.x() <= maxX
                && pos.y() >= minY && pos.y() <= maxY
                && pos.z() >= minZ && pos.z() <= maxZ;
    }

    /**
     * The vertical level at or below which a player counts as knocked off the ladder.
     *
     * <p>Region membership and knockoff are deliberately different tests. Falling past the
     * arena's minimum Y respawns the player at the arena spawn and keeps them in the game;
     * leaving the region in any other way (a portal, a command, flight) restores their
     * real inventory and drops them out of the arena entirely.</p>
     */
    public boolean isKnockedOff(double y) {
        return y < minY;
    }

    /** @return the world this arena lives in, or empty when it is not loaded. */
    public Optional<World> resolveWorld() {
        return Optional.ofNullable(org.bukkit.Bukkit.getWorld(world));
    }

    /** @return the arena's spawn point, or the region centre when no spawn was configured. */
    public BlockPos spawnOrCentre() {
        if (spawn != null) {
            return spawn;
        }
        return new BlockPos(world, (minX + maxX) / 2, minY, (minZ + maxZ) / 2);
    }

    public Arena withActive(boolean newActive) {
        return new Arena(name, world, minX, minY, minZ, maxX, maxY, maxZ, spawn, kingPlate, newActive);
    }

    public Arena withKingPlate(BlockPos plate) {
        return new Arena(name, world, minX, minY, minZ, maxX, maxY, maxZ, spawn, plate, active);
    }

    public Arena withSpawn(BlockPos newSpawn) {
        return new Arena(name, world, minX, minY, minZ, maxX, maxY, maxZ, newSpawn, kingPlate, active);
    }

    public boolean hasKingPlate() {
        return kingPlate != null;
    }

    /** @return a one-line summary for command output and the admin dialog. */
    public String describe() {
        return name
                + " [" + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ + "]"
                + (active ? " active" : " disabled")
                + (hasKingPlate() ? " plate@" + kingPlate : " plate=<unset>");
    }
}
