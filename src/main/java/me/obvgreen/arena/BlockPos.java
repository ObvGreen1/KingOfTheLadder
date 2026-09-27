package me.obvgreen.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;

/**
 * An immutable integer block coordinate together with its world name.
 *
 * <p>Deliberately not a Bukkit {@link Location}: arenas are persisted, and a Location carries a
 * live {@link World} reference plus doubles that would silently round-trip badly through
 * YAML. Block positions are what region maths and the King plate actually need.</p>
 */
public record BlockPos(String world, int x, int y, int z) {

    public BlockPos {
        Objects.requireNonNull(world, "world");
    }

    public static BlockPos of(Location location) {
        return new BlockPos(location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static BlockPos of(Block block) {
        return new BlockPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /** @return the live location, or {@code null} when the arena's world is not loaded. */
    public Location toLocation(World world) {
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    /** Serialised form, e.g. {@code world:12,64,-40}. */
    @Override
    public String toString() {
        return world + ":" + x + "," + y + "," + z;
    }
}
