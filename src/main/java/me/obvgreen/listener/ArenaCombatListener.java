package me.obvgreen.listener;

import me.obvgreen.arena.ArenaManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Combat rules inside a region.
 *
 * <p>Two jobs that belong together because they are the same question — is this hit an arena hit?
 * — asked twice: once to keep PvP from leaking across the boundary, once to record the hitter and
 * drop fall damage.</p>
 */
public final class ArenaCombatListener implements Listener {

    private final ArenaManager arenas;

    public ArenaCombatListener(ArenaManager arenas) {
        this.arenas = arenas;
    }

    /**
     * PvP is allowed inside a region but must not leak out of it: an arena player cannot hit
     * somebody standing outside, and an outside player cannot hit somebody inside.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        boolean attackerInArena = attacker != null && arenas.isInArena(attacker);
        boolean victimInArena = arenas.isInArena(victim);
        if (attackerInArena == victimInArena) {
            return;
        }

        event.setCancelled(true);
        if (attacker != null) {
            attacker.sendMessage(ArenaManager.mini(
                    "<gray>You cannot attack players outside an arena."));
        }
    }

    /**
     * Records the last hitter so a knockoff can be attributed, and drops fall damage inside a
     * region — the ladder is a knockback game, not a platforming one.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = resolveAttacker(byEntity);
            if (attacker != null && arenas.isInArena(player) && arenas.isInArena(attacker)) {
                arenas.registerHit(player, attacker);
            }
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && arenas.isInArena(player)) {
            event.setCancelled(true);
        }
    }

    /** @return the player behind {@code event}, resolving projectiles to their shooter. */
    private static Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) {
            return direct;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
