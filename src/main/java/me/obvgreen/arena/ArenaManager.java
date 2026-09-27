package me.obvgreen.arena;

import me.obvgreen.KingOfTheLadder;
import me.obvgreen.database.DatabaseManager;
import me.obvgreen.database.PlayerStats;
import me.obvgreen.database.StatCategory;
import me.obvgreen.glicko.GlickoManager;
import me.obvgreen.glicko.GlickoRating;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Owns every piece of mutable arena state: the configured arenas, who is currently standing in
 * one, the snapshots needed to undo the arena, who currently wears the crown, and the
 * knockback-attribution window.
 *
 * <p>Everything here touches Bukkit state, so it is main-thread only. The one exception is the
 * stat writes it delegates to {@link DatabaseManager}, which schedule their own I/O.</p>
 */
public final class ArenaManager {

    private static final String ARENA_ROOT = "arenas";

    private final KingOfTheLadder plugin;
    private final DatabaseManager database;
    private final GlickoManager glicko;
    private final FileSettings settings;

    private final Map<String, Arena> arenas = new HashMap<>();
    private final Map<UUID, String> membership = new ConcurrentHashMap<>();
    private final Map<UUID, SavedState> savedStates = new HashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<String, UUID> kings = new ConcurrentHashMap<>();
    private final Map<UUID, Long> claimCooldown = new HashMap<>();
    private final Map<UUID, PendingHit> pendingHits = new HashMap<>();

    private BukkitTask housekeepingTask;

    public ArenaManager(KingOfTheLadder plugin, DatabaseManager database,
                        GlickoManager glicko, FileSettings settings) {
        this.plugin = plugin;
        this.database = database;
        this.glicko = glicko;
        this.settings = settings;
    }

    // ------------------------------------------------------------------ persistence

    public void loadArenas() {
        arenas.clear();
        var section = plugin.getConfig().getConfigurationSection(ARENA_ROOT);
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            try {
                arenas.put(name.toLowerCase(Locale.ROOT), readArena(name));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping malformed arena '" + name + "': " + exception.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arena(s).");
    }

    private Arena readArena(String name) {
        String path = ARENA_ROOT + "." + name + ".";
        String world = plugin.getConfig().getString(path + "world");
        if (world == null) {
            throw new IllegalArgumentException("missing world");
        }
        BlockPos min = parsePos(plugin.getConfig().getString(path + "bounds.min"), world);
        BlockPos max = parsePos(plugin.getConfig().getString(path + "bounds.max"), world);
        BlockPos spawn = parsePos(plugin.getConfig().getString(path + "spawn"), world);
        String plate = plugin.getConfig().getString(path + "king-plate");
        if (min == null || max == null) {
            throw new IllegalArgumentException("missing bounds");
        }
        return Arena.of(name, world, min, max, spawn == null ? min : spawn,
                plate == null || plate.isBlank() ? null : parsePos(plate, world),
                plugin.getConfig().getBoolean(path + "active", true));
    }

    /** @return a deserialised {@code x,y,z} triple, or {@code null} for a blank value. */
    private static BlockPos parsePos(String raw, String world) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("'" + raw + "' is not x,y,z");
        }
        return new BlockPos(world,
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim()));
    }

    public void saveArena(Arena arena) {
        arenas.put(arena.name().toLowerCase(Locale.ROOT), arena);
        String path = ARENA_ROOT + "." + arena.name() + ".";
        plugin.getConfig().set(path + "world", arena.world());
        plugin.getConfig().set(path + "bounds.min",
                arena.minX() + "," + arena.minY() + "," + arena.minZ());
        plugin.getConfig().set(path + "bounds.max",
                arena.maxX() + "," + arena.maxY() + "," + arena.maxZ());
        BlockPos spawn = arena.spawnOrCentre();
        plugin.getConfig().set(path + "spawn",
                spawn.x() + "," + spawn.y() + "," + spawn.z());
        if (arena.kingPlate() == null) {
            plugin.getConfig().set(path + "king-plate", null);
        } else {
            plugin.getConfig().set(path + "king-plate", arena.kingPlate().x() + ","
                    + arena.kingPlate().y() + "," + arena.kingPlate().z());
        }
        plugin.getConfig().set(path + "active", arena.active());
        plugin.saveConfig();
    }

    public void deleteArena(String name) {
        arenas.remove(name.toLowerCase(Locale.ROOT));
        kings.remove(name.toLowerCase(Locale.ROOT));
        plugin.getConfig().set(ARENA_ROOT + "." + name, null);
        plugin.saveConfig();
    }

    public Collection<Arena> arenas() {
        return List.copyOf(arenas.values());
    }

    public Optional<Arena> byName(String name) {
        return Optional.ofNullable(arenas.get(name.toLowerCase(Locale.ROOT)));
    }

    /**
     * @return the arena whose region contains {@code pos}, ignoring the active flag. The plate
     *         handler needs this: a disabled arena's plate must still resolve so a crown
     *         attempt reports why it failed instead of silently doing nothing.
     */
    public Optional<Arena> arenaAtBlockPos(BlockPos pos) {
        for (Arena arena : arenas.values()) {
            if (arena.contains(pos)) {
                return Optional.of(arena);
            }
        }
        return Optional.empty();
    }

    /** @return the active arena containing {@code location}, if any. */
    public Optional<Arena> activeAt(Location location) {
        for (Arena arena : arenas.values()) {
            if (arena.active() && arena.contains(location)) {
                return Optional.of(arena);
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ membership

    public boolean isInArena(Player player) {
        return membership.containsKey(player.getUniqueId());
    }

    public Optional<Arena> arenaOf(Player player) {
        String name = membership.get(player.getUniqueId());
        return name == null ? Optional.empty() : byName(name);
    }

    /**
     * Puts {@code player} into {@code arena}: snapshot, strip, kit, teleport.
     *
     * <p>Idempotent per arena, and a player already inside a different arena is moved across
     * rather than double-snapshotted â€” a re-entrant region event must never leak the first
     * inventory.</p>
     */
    public void join(Player player, Arena arena) {
        UUID uuid = player.getUniqueId();
        String previous = membership.get(uuid);
        if (previous != null && previous.equalsIgnoreCase(arena.name())) {
            return;
        }
        if (previous != null) {
            leave(player, true);
        }

        savedStates.put(uuid, SavedState.capture(player));
        membership.put(uuid, arena.name());

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setItemOnCursor(null);
        player.setLevel(0);
        player.setExp(0.0F);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setGameMode(settings.arenaGameMode());

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(settings.arenaMaxHealth());
        }
        player.setHealth(settings.arenaMaxHealth());

        giveKit(player);
        teleportToSpawn(player, arena);
        player.sendMessage(mini(settings.joinMessage().replace("<arena>", arena.name())));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.6F);
    }

    /**
     * Removes {@code player} from whatever arena they are in and replays their snapshot.
     *
     * @param notify whether to send the leave message; {@code false} on shutdown and on kick
     */
    public void leave(Player player, boolean notify) {
        UUID uuid = player.getUniqueId();
        Arena arena = arenaOf(player).orElse(null);
        membership.remove(uuid);
        if (arena != null) {
            kings.remove(arena.name().toLowerCase(Locale.ROOT), uuid);
        }
        SavedState state = savedStates.remove(uuid);
        if (state != null) {
            state.restore(player);
            if (notify) {
                player.sendMessage(mini(settings.leaveMessage()
                        .replace("<arena>", arena == null ? "the arena" : arena.name())));
            }
        }
        pendingHits.remove(uuid);
    }

    /** Restores and forgets everyone; used on plugin disable. */
    public void shutdown() {
        if (housekeepingTask != null) {
            housekeepingTask.cancel();
            housekeepingTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            leave(player, false);
        }
        arenas.clear();
        savedStates.clear();
        selections.clear();
        kings.clear();
        claimCooldown.clear();
        pendingHits.clear();
    }

    public void startHousekeeping(long periodTicks) {
        housekeepingTask = Bukkit.getScheduler()
                .runTaskTimer(plugin, this::expirePendingHits, periodTicks, periodTicks);
    }

    // ------------------------------------------------------------------ the kit

    /**
     * Equips the KotL kit: a full set of leather armour plus a Knockback I stick.
     *
     * <p>Armour goes through {@code setArmorContents}, whose array is in
     * boots-leggings-chestplate-helmet order; the stick goes into the hotbar slot the
     * configuration names.</p>
     */
    public void giveKit(Player player) {
        List<ItemStack> configured = settings.kitArmour();
        ItemStack[] armour = new ItemStack[4];
        for (int slot = 0; slot < 4; slot++) {
            if (slot >= configured.size()) {
                continue;
            }
            ItemStack piece = configured.get(slot).clone();
            if (piece.getItemMeta() instanceof LeatherArmorMeta leather) {
                leather.setColor(settings.kitColour());
                piece.setItemMeta(leather);
            }
            armour[slot] = piece;
        }
        player.getInventory().setArmorContents(armour);

        ItemStack stick = new ItemStack(settings.kitStickMaterial());
        ItemMeta meta = stick.getItemMeta();
        if (meta != null) {
            meta.displayName(mini(settings.kitStickName()));
            meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
            meta.setUnbreakable(true);
            stick.setItemMeta(meta);
        }
        player.getInventory().setItem(settings.kitStickSlot(), stick);
    }

    // ------------------------------------------------------------------ movement / knockoff

    /**
     * Called from the move listener. Handles the three transitions a player can make:
     * knocked off the bottom (respawn in-arena), left the region (restore), or entered one
     * (join).
     */
    public void handleMove(Player player, Location to) {
        String current = membership.get(player.getUniqueId());
        if (current == null) {
            if (settings.joinOnRegionEntry()) {
                activeAt(to).ifPresent(arena -> join(player, arena));
            }
            return;
        }

        Arena arena = byName(current).orElse(null);
        if (arena == null) {
            leave(player, true);
            return;
        }
        if (arena.isKnockedOff(to.getY())) {
            handleKnockoff(player, arena);
            return;
        }
        if (!arena.contains(to)) {
            leave(player, true);
        }
    }

    /**
     * A knockoff: attribute it to the last player who hit the victim, respawn them at the
     * arena spawn, and re-rate both sides.
     */
    public void handleKnockoff(Player victim, Arena arena) {
        Player attacker = consumeAttacker(victim).orElse(null);

        database.addCounter(victim, StatCategory.DEATHS, 1);
        healFully(victim);
        teleportToSpawn(victim, arena);

        if (attacker == null) {
            victim.sendMessage(mini(settings.selfKnockoffMessage()));
            return;
        }
        database.addCounter(attacker, StatCategory.KILLS, 1);
        applyCombatRating(attacker, victim);

        victim.sendMessage(mini(settings.knockedByMessage()
                .replace("<player>", attacker.getName())
                .replace("<arena>", arena.name())));
        attacker.sendMessage(mini(settings.knockoffMessage()
                .replace("<player>", victim.getName())
                .replace("<arena>", arena.name())));
    }

    /** A player's death inside the arena: attribute it, then heal and respawn them. */
    public void handleDeath(Player victim, Arena arena) {
        handleKnockoff(victim, arena);
    }

    /**
     * Runs a Glicko-2 update for a completed knockoff.
     *
     * <p>Both sides are re-solved from their own point of view against the same single
     * opponent, which is exactly what {@code GlickoManager.rateMatch} does.</p>
     */
    private void applyCombatRating(Player winner, Player loser) {
        PlayerStats winnerStats = database.get(winner);
        PlayerStats loserStats = database.get(loser);
        GlickoRating[] updated = glicko.rateMatch(
                GlickoRating.of(winnerStats), GlickoRating.of(loserStats));

        GlickoRating newWinner = updated[0];
        GlickoRating newLoser = updated[1];
        database.applyRating(winner, newWinner.rating(), newWinner.deviation(), newWinner.volatility());
        database.applyRating(loser, newLoser.rating(), newLoser.deviation(), newLoser.volatility());

        winner.sendMessage(mini(settings.ratingMessage()
                .replace("<rating>", formatSigned(newWinner.rating() - winnerStats.rating()))));
        loser.sendMessage(mini(settings.ratingMessage()
                .replace("<rating>", formatSigned(newLoser.rating() - loserStats.rating()))));
    }

    private static String formatSigned(double delta) {
        return String.format(Locale.ROOT, "%+.1f", delta);
    }

    /** Restores the player to full health at their current maximum. */
    public void healFully(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0D : maxHealth.getValue());
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
    }

    // ------------------------------------------------------------------ knockback attribution

    /** Records that {@code attacker} last damaged {@code victim}. */
    public void registerHit(Player victim, Player attacker) {
        if (victim.getUniqueId().equals(attacker.getUniqueId())) {
            return;
        }
        pendingHits.put(victim.getUniqueId(),
                new PendingHit(attacker.getUniqueId(), Instant.now().toEpochMilli()));
    }

    /**
     * Consumes the attribution window for {@code victim}.
     *
     * @return the attacker when they hit {@code victim} within the configured window and are
     *         still online, otherwise empty
     */
    public Optional<Player> consumeAttacker(Player victim) {
        PendingHit hit = pendingHits.remove(victim.getUniqueId());
        if (hit == null) {
            return Optional.empty();
        }
        if (Instant.now().toEpochMilli() - hit.atMillis() > settings.knockbackWindow().toMillis()) {
            return Optional.empty();
        }
        Player attacker = Bukkit.getPlayer(hit.attacker());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return Optional.empty();
        }
        return Optional.of(attacker);
    }

    private void expirePendingHits() {
        long cutoff = Instant.now().toEpochMilli() - settings.knockbackWindow().toMillis();
        pendingHits.values().removeIf(hit -> hit.atMillis() < cutoff);
        long cooldownCutoff = System.currentTimeMillis() - settings.claimCooldown().toMillis();
        claimCooldown.values().removeIf(at -> at < cooldownCutoff);
    }

    // ------------------------------------------------------------------ the King

    /** @return the current King of {@code arena}, if any is online. */
    public Optional<Player> king(Arena arena) {
        UUID uuid = kings.get(arena.name().toLowerCase(Locale.ROOT));
        return uuid == null ? Optional.empty() : Optional.ofNullable(Bukkit.getPlayer(uuid));
    }

    public String kingName(Arena arena) {
        return king(arena).map(Player::getName).orElse(settings.noKingText());
    }

    /**
     * Handles a player stepping onto the King plate.
     *
     * @return {@code true} when the claim went through
     */
    public boolean claimCrown(Player player, Arena arena) {
        if (!arena.hasKingPlate() || !isInArena(player)) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long until = claimCooldown.get(player.getUniqueId());
        if (until != null && now < until) {
            return false;
        }

        kings.put(arena.name().toLowerCase(Locale.ROOT), player.getUniqueId());
        claimCooldown.put(player.getUniqueId(), now + settings.claimCooldown().toMillis());
        database.addCounter(player, StatCategory.WINS, 1);

        player.showTitle(Title.title(
                mini(settings.kingTitle()),
                mini(settings.kingSubtitle()),
                Title.Times.times(
                        Duration.ofMillis(settings.titleFadeInMillis()),
                        Duration.ofMillis(settings.titleStayMillis()),
                        Duration.ofMillis(settings.titleFadeOutMillis()))));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.2F);

        Location plate = arena.kingPlate().toLocation(player.getWorld());
        Bukkit.broadcast(mini(settings.kingBroadcast()
                .replace("<player>", player.getName())
                .replace("<arena>", arena.name())));
        spawnFireworks(plate, settings.kingFireworkCount());
        pushBystanders(player, arena, plate);
        return true;
    }

    /** Shoves every other player in the arena away from the freshly crowned King. */
    private void pushBystanders(Player king, Arena arena, Location plate) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(king.getUniqueId()) || !isInArena(other)) {
                continue;
            }
            boolean sameArena = arenaOf(other).map(a -> a.name().equals(arena.name())).orElse(false);
            if (!sameArena) {
                continue;
            }
            Vector away = other.getLocation().toVector().subtract(plate.toVector());
            if (away.lengthSquared() < 0.01D) {
                away = new Vector(0, 1, 0);
            }
            away = away.normalize();
            double horizontal = settings.kingPushStrength();
            away.setX(away.getX() * horizontal)
                    .setZ(away.getZ() * horizontal)
                    .setY(away.getY() + settings.kingPushVertical());
            other.setVelocity(away);
        }
    }

    private void spawnFireworks(Location location, int count) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        FireworkEffect effect = FireworkEffect.builder()
                .with(FireworkEffect.Type.BURST)
                .withColor(Color.AQUA, Color.LIME, Color.WHITE)
                .withFade(Color.AQUA)
                .withTrail()
                .build();

        for (int index = 0; index < count; index++) {
            world.spawn(location, Firework.class, firework -> {
                firework.setVelocity(new Vector(
                        (ThreadLocalRandom.current().nextDouble() - 0.5D) * 0.2D,
                        0.25D,
                        (ThreadLocalRandom.current().nextDouble() - 0.5D) * 0.2D));
                FireworkMeta meta = firework.getFireworkMeta();
                meta.addEffect(effect);
                meta.setPower(1);
                firework.setFireworkMeta(meta);
            });
            world.spawnParticle(Particle.FLAME, location, 40, 0.4D, 0.2D, 0.4D, 0.05D);
        }
    }

    // ------------------------------------------------------------------ teleport helpers

    public void teleportToSpawn(Player player, Arena arena) {
        Optional<World> world = arena.resolveWorld();
        if (world.isEmpty()) {
            plugin.getLogger().warning("Cannot teleport " + player.getName()
                    + " into '" + arena.name() + "': world '" + arena.world() + "' is not loaded.");
            return;
        }
        player.teleport(arena.spawnOrCentre().toLocation(world.get()));
        player.setFallDistance(0.0F);
    }

    // ------------------------------------------------------------------ selection (wand)

    /** The two corners an administrator has picked with the wand. Session-scoped, not persisted. */
    public record Selection(BlockPos first, BlockPos second) {

        public Selection withFirst(BlockPos pos) {
            return new Selection(pos, second);
        }

        public Selection withSecond(BlockPos pos) {
            return new Selection(first, pos);
        }

        public boolean complete() {
            return first != null && second != null;
        }
    }

    public Selection selection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), key -> new Selection(null, null));
    }

    public void setSelectionFirst(Player player, BlockPos pos) {
        selections.put(player.getUniqueId(), selection(player).withFirst(pos));
    }

    public void setSelectionSecond(Player player, BlockPos pos) {
        selections.put(player.getUniqueId(), selection(player).withSecond(pos));
    }

    public void clearSelection(Player player) {
        selections.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------ diagnostics

    /** @return every player currently standing in {@code arena}, for the admin dialog. */
    public List<Player> occupants(Arena arena) {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean sameArena = arenaOf(player).map(a -> a.name().equals(arena.name())).orElse(false);
            if (sameArena) {
                players.add(player);
            }
        }
        return players;
    }

    /** @return human-readable configuration problems, empty when everything checks out. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (!arena.hasKingPlate()) {
                problems.add(arena.name() + " has no King plate (/kotl setplate " + arena.name() + ")");
            }
            if (arena.resolveWorld().isEmpty()) {
                problems.add(arena.name() + " references unloaded world '" + arena.world() + "'");
            }
        }
        return problems;
    }

    /** Logs any configuration problems at WARNING; never throws, never blocks startup. */
    public void reportProblems() {
        for (String problem : problems()) {
            plugin.getLogger().log(Level.WARNING, problem);
        }
    }

    // ------------------------------------------------------------------ small helpers

    public static Component mini(String raw) {
        return MiniMessage.miniMessage().deserialize(raw);
    }


    private record PendingHit(UUID attacker, long atMillis) {
    }
}
