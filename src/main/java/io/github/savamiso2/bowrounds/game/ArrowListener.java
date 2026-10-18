package io.github.savamiso2.bowrounds.game;

import io.github.savamiso2.bowrounds.BowRounds;
import io.github.savamiso2.bowrounds.card.PlayerStats;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/** カード効果が乗った矢：散弾・跳弾・追尾・重力無視・爆発・毒・吸血とダメージ計算。 */
public final class ArrowListener implements Listener {

    private static final int MAX_ARROW_TICKS = 200;
    private static final double HOMING_RANGE_SQUARED = 16 * 16;

    private final BowRounds plugin;
    private final GameManager games;
    private final NamespacedKey shooterKey;
    private final NamespacedKey forceKey;
    private final NamespacedKey bouncesKey;
    private final Set<AbstractArrow> active = new HashSet<>();

    public ArrowListener(BowRounds plugin, GameManager games) {
        this.plugin = plugin;
        this.games = games;
        this.shooterKey = new NamespacedKey(plugin, "shooter");
        this.forceKey = new NamespacedKey(plugin, "force");
        this.bouncesKey = new NamespacedKey(plugin, "bounces");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickArrows, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) {
            return;
        }
        games.gamePlayer(shooter).ifPresent(gp -> {
            Game game = games.game().orElseThrow();
            if (game.state() != GameState.PLAYING || !gp.alive
                    || !(event.getProjectile() instanceof AbstractArrow arrow)) {
                event.setCancelled(true);
                return;
            }
            PlayerStats stats = gp.stats();
            double force = Math.max(0.1, Math.min(1.0, event.getForce()));
            Vector velocity = arrow.getVelocity().multiply(stats.arrowSpeedMultiplier);
            setup(arrow, shooter.getUniqueId(), force, stats.bounces, stats.noGravity, velocity);

            // 散弾：左右交互に広げて追加発射（弾は消費しない）
            for (int i = 1; i <= stats.extraArrows; i++) {
                double side = (i % 2 == 1) ? 1 : -1;
                double angle = Math.toRadians(stats.spreadDegrees * ((i + 1) / 2) * side);
                Vector extraVelocity = velocity.clone().rotateAroundY(angle);
                Arrow extra = shooter.launchProjectile(Arrow.class, extraVelocity);
                setup(extra, shooter.getUniqueId(), force, stats.bounces, stats.noGravity, extraVelocity);
            }
            Bukkit.getScheduler().runTask(plugin, () -> game.afterShot(gp));
        });
    }

    private void setup(AbstractArrow arrow, UUID shooter, double force, int bounces, boolean noGravity, Vector velocity) {
        arrow.setVelocity(velocity);
        arrow.setGravity(!noGravity);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setCritical(force >= 1.0);
        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        pdc.set(shooterKey, PersistentDataType.STRING, shooter.toString());
        pdc.set(forceKey, PersistentDataType.DOUBLE, force);
        pdc.set(bouncesKey, PersistentDataType.INTEGER, bounces);
        active.add(arrow);
    }

    /** 追尾の舵取りと、飛びっぱなしの矢（重力無視など）の掃除。 */
    private void tickArrows() {
        Iterator<AbstractArrow> it = active.iterator();
        while (it.hasNext()) {
            AbstractArrow arrow = it.next();
            if (!arrow.isValid() || arrow.isInBlock()) {
                it.remove();
                continue;
            }
            if (arrow.getTicksLived() > MAX_ARROW_TICKS) {
                arrow.remove();
                it.remove();
                continue;
            }
            Optional<GamePlayer> shooter = shooter(arrow);
            if (shooter.isEmpty()) {
                continue;
            }
            double homing = shooter.get().stats().homing;
            if (homing > 0) {
                steer(arrow, shooter.get(), homing);
            }
        }
    }

    private void steer(AbstractArrow arrow, GamePlayer shooter, double homing) {
        Game game = games.game().orElse(null);
        if (game == null) {
            return;
        }
        Location here = arrow.getLocation();
        Player target = null;
        double best = HOMING_RANGE_SQUARED;
        for (GamePlayer other : game.alivePlayers()) {
            if (other == shooter) {
                continue;
            }
            Player p = other.player();
            if (!p.getWorld().equals(here.getWorld())) {
                continue;
            }
            double d = p.getLocation().distanceSquared(here);
            if (d < best) {
                best = d;
                target = p;
            }
        }
        if (target == null) {
            return;
        }
        Vector velocity = arrow.getVelocity();
        double speed = velocity.length();
        if (speed < 0.05) {
            return;
        }
        Vector toTarget = target.getLocation().add(0, 1.0, 0).toVector().subtract(here.toVector()).normalize();
        Vector steered = velocity.normalize().multiply(1 - homing).add(toTarget.multiply(homing)).normalize().multiply(speed);
        arrow.setVelocity(steered);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        Optional<GamePlayer> shooterOpt = shooter(arrow);
        if (shooterOpt.isEmpty()) {
            return;
        }
        GamePlayer shooter = shooterOpt.get();
        PlayerStats stats = shooter.stats();
        Location location = arrow.getLocation();

        if (stats.explosionPower > 0) {
            location.getWorld().createExplosion(location, stats.explosionPower, false, false, shooter.player());
        }

        if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
            int bounces = arrow.getPersistentDataContainer().getOrDefault(bouncesKey, PersistentDataType.INTEGER, 0);
            if (bounces > 0) {
                bounce(arrow, shooter, event.getHitBlockFace().getDirection(), bounces - 1);
            }
        }
        active.remove(arrow);
        Bukkit.getScheduler().runTask(plugin, arrow::remove);
    }

    private void bounce(AbstractArrow arrow, GamePlayer shooter, Vector normal, int remaining) {
        Vector velocity = arrow.getVelocity();
        // 反射ベクトル v - 2(v·n)n
        Vector reflected = velocity.clone().subtract(normal.clone().multiply(2 * velocity.dot(normal)));
        Location from = arrow.getLocation().add(normal.clone().multiply(0.3));
        double force = arrow.getPersistentDataContainer().getOrDefault(forceKey, PersistentDataType.DOUBLE, 1.0);
        Arrow next = from.getWorld().spawnArrow(from, reflected, (float) reflected.length(), 0f);
        Player shooterPlayer = shooter.player();
        if (shooterPlayer != null) {
            next.setShooter(shooterPlayer);
        }
        setup(next, shooter.id(), force, remaining, !arrow.hasGravity(), reflected);
        from.getWorld().spawnParticle(Particle.CRIT, from, 6, 0.1, 0.1, 0.1, 0.05);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Optional<GamePlayer> shooterOpt = shooter(arrow);
        if (shooterOpt.isEmpty()) {
            return;
        }
        GamePlayer shooter = shooterOpt.get();
        Optional<GamePlayer> victimOpt = games.gamePlayer(victim);
        if (victimOpt.isEmpty() || shooter.id().equals(victim.getUniqueId())) {
            event.setCancelled(true); // 自分の矢（跳弾）では傷つかない
            return;
        }
        GamePlayer target = victimOpt.get();
        PlayerStats stats = shooter.stats();
        double force = arrow.getPersistentDataContainer().getOrDefault(forceKey, PersistentDataType.DOUBLE, 1.0);
        double damage = plugin.settings().baseDamage() * force * stats.damageMultiplier;
        event.setDamage(damage);
        target.lastDamager = shooter.id();

        if (stats.poisonTicks > 0) {
            Game.poison(victim, stats.poisonTicks);
        }
        Player shooterPlayer = shooter.player();
        if (stats.lifesteal > 0 && shooterPlayer != null && shooter.alive) {
            double max = stats.maxHealth;
            shooterPlayer.setHealth(Math.min(max, shooterPlayer.getHealth() + damage * stats.lifesteal));
        }
        Bukkit.getScheduler().runTask(plugin, () -> victim.setArrowsInBody(0));
    }

    private Optional<GamePlayer> shooter(AbstractArrow arrow) {
        String id = arrow.getPersistentDataContainer().get(shooterKey, PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        return games.game().flatMap(g -> g.player(UUID.fromString(id)));
    }
}
