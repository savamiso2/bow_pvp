package io.github.savamiso2.bowrounds.game;

import io.github.savamiso2.bowrounds.BowRounds;
import io.github.savamiso2.bowrounds.card.PlayerStats;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
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
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** カード効果が乗った矢：散弾・跳弾・追尾・重力無視・爆発・毒・吸血とダメージ計算。 */
public final class ArrowListener implements Listener {

    private static final int MAX_ARROW_TICKS = 200;
    private static final double HOMING_RANGE_SQUARED = 16 * 16;
    private static final int SPLIT_AFTER_TICKS = 8;
    /** バニラの判定の余裕が 0.3 まで広がりきる頃まで（2tick + 0.05/tick × 6）。 */
    private static final int CLASSIC_MARGIN_TICKS = 10;
    private static final double SPLIT_DEGREES = 12;

    private final BowRounds plugin;
    private final GameManager games;
    private final NamespacedKey shooterKey;
    private final NamespacedKey forceKey;
    private final NamespacedKey bouncesKey;
    private final NamespacedKey splitKey;
    private final Set<AbstractArrow> active = new HashSet<>();

    public ArrowListener(BowRounds plugin, GameManager games) {
        this.plugin = plugin;
        this.games = games;
        this.shooterKey = new NamespacedKey(plugin, "shooter");
        this.forceKey = new NamespacedKey(plugin, "force");
        this.bouncesKey = new NamespacedKey(plugin, "bounces");
        this.splitKey = new NamespacedKey(plugin, "split");
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
            setup(arrow, shooter.getUniqueId(), force, stats, stats.bounces, stats.split, velocity);

            // 散弾：左右交互に広げて追加発射（弾は消費しない）
            for (int i = 1; i <= stats.extraArrows; i++) {
                double side = (i % 2 == 1) ? 1 : -1;
                double angle = Math.toRadians(stats.spreadDegrees * ((i + 1) / 2) * side);
                Vector extraVelocity = velocity.clone().rotateAroundY(angle);
                Arrow extra = shooter.launchProjectile(Arrow.class, extraVelocity);
                setup(extra, shooter.getUniqueId(), force, stats, stats.bounces, stats.split, extraVelocity);
            }
            Bukkit.getScheduler().runTask(plugin, () -> game.afterShot(gp));
        });
    }

    private void setup(AbstractArrow arrow, UUID shooter, double force, PlayerStats stats, int bounces, boolean split,
                       Vector velocity) {
        arrow.setVelocity(velocity);
        arrow.setGravity(!stats.noGravity);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setCritical(force >= 1.0);
        arrow.setPierceLevel(Math.min(127, stats.pierce));
        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        pdc.set(shooterKey, PersistentDataType.STRING, shooter.toString());
        pdc.set(forceKey, PersistentDataType.DOUBLE, force);
        pdc.set(bouncesKey, PersistentDataType.INTEGER, bounces);
        pdc.set(splitKey, PersistentDataType.BOOLEAN, split);
        active.add(arrow);
    }

    /** shooter の矢として新しく1本飛ばす（跳弾・分裂・反射用）。 */
    private Arrow spawnFor(GamePlayer shooter, Location from, Vector velocity, double force, int bounces) {
        Arrow next = from.getWorld().spawnArrow(from, velocity, (float) velocity.length(), 0f);
        Player shooterPlayer = shooter.player();
        if (shooterPlayer != null) {
            next.setShooter(shooterPlayer);
        }
        setup(next, shooter.id(), force, shooter.stats(), bounces, false, velocity);
        return next;
    }

    /** 追尾の舵取りと、飛びっぱなしの矢（重力無視など）の掃除。 */
    private void tickArrows() {
        for (AbstractArrow arrow : List.copyOf(active)) {
            if (!arrow.isValid() || arrow.isInBlock()) {
                active.remove(arrow);
                continue;
            }
            if (arrow.getTicksLived() > MAX_ARROW_TICKS) {
                arrow.remove();
                active.remove(arrow);
                continue;
            }
            Optional<GamePlayer> shooter = shooter(arrow);
            if (shooter.isEmpty()) {
                continue;
            }
            Fx.trail(arrow, shooter.get().id(), shooter.get().stats());
            double homing = shooter.get().stats().homing;
            if (homing > 0) {
                steer(arrow, shooter.get(), homing);
            }
            if (arrow.getTicksLived() < CLASSIC_MARGIN_TICKS) {
                classicHitMargin(arrow, shooter.get());
            }
            if (arrow.getTicksLived() == SPLIT_AFTER_TICKS
                    && arrow.getPersistentDataContainer().getOrDefault(splitKey, PersistentDataType.BOOLEAN, false)) {
                split(arrow, shooter.get());
            }
        }
    }

    /**
     * 昔の当たり判定。今のバニラは撃った直後の判定の余裕が0で、飛ぶほど 0.3 ブロックまで広がる。
     * 昔は最初から一定（0.3）だったので、広がりきるまでの間は「この tick で余裕込みなら当たる」矢を
     * 相手の体へ向け直して、バニラの判定でそのまま当たるようにする。
     */
    private void classicHitMargin(AbstractArrow arrow, GamePlayer shooter) {
        double margin = plugin.settings().arrowHitMargin();
        Vector velocity = arrow.getVelocity();
        double speed = velocity.length();
        if (margin <= 0 || speed < 1e-3) {
            return;
        }
        Location from = arrow.getLocation();
        RayTraceResult hit = from.getWorld().rayTraceEntities(from, velocity, speed, margin, e ->
                e instanceof Player p && !p.getUniqueId().equals(shooter.id())
                        && games.gamePlayer(p).filter(g -> g.alive).isPresent());
        if (hit == null || hit.getHitEntity() == null) {
            return;
        }
        BoundingBox body = hit.getHitEntity().getBoundingBox().clone().expand(-0.05);
        Vector point = hit.getHitPosition();
        Vector inside = new Vector(
                Math.clamp(point.getX(), body.getMinX(), body.getMaxX()),
                Math.clamp(point.getY(), body.getMinY(), body.getMaxY()),
                Math.clamp(point.getZ(), body.getMinZ(), body.getMaxZ()));
        Vector aim = inside.subtract(from.toVector());
        if (aim.lengthSquared() > 1e-6) {
            arrow.setVelocity(aim.normalize().multiply(speed));
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

    private void split(AbstractArrow arrow, GamePlayer shooter) {
        arrow.getPersistentDataContainer().set(splitKey, PersistentDataType.BOOLEAN, false);
        Vector velocity = arrow.getVelocity();
        double force = arrow.getPersistentDataContainer().getOrDefault(forceKey, PersistentDataType.DOUBLE, 1.0);
        int bounces = arrow.getPersistentDataContainer().getOrDefault(bouncesKey, PersistentDataType.INTEGER, 0);
        Location from = arrow.getLocation();
        for (double side : new double[] {-1, 1}) {
            spawnFor(shooter, from, velocity.clone().rotateAroundY(Math.toRadians(SPLIT_DEGREES * side)), force, bounces);
        }
        from.getWorld().spawnParticle(Particle.FIREWORK, from, 6, 0.1, 0.1, 0.1, 0.02);
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

        if (event.getHitEntity() instanceof Player victim && !victim.getUniqueId().equals(shooter.id())) {
            Optional<GamePlayer> guard = games.gamePlayer(victim).filter(GamePlayer::guarding);
            if (guard.isPresent()) {
                event.setCancelled(true);
                blocked(arrow, shooter, guard.get(), victim);
                return;
            }
        }

        if (stats.explosionPower > 0) {
            explode(location, shooter, stats);
        }

        if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
            int bounces = arrow.getPersistentDataContainer().getOrDefault(bouncesKey, PersistentDataType.INTEGER, 0);
            if (bounces > 0) {
                bounce(arrow, shooter, event.getHitBlockFace().getDirection(), bounces - 1);
            }
            active.remove(arrow);
            Bukkit.getScheduler().runTask(plugin, arrow::remove);
        }
        // プレイヤーに当たった矢は消さない（貫通のため。拾えない設定なのでそのうち消える）
    }

    /** ガード成功：矢を消し、反射カードがあれば撃った相手へ跳ね返す。 */
    private void blocked(AbstractArrow arrow, GamePlayer attacker, GamePlayer defender, Player victim) {
        Vector incoming = arrow.getVelocity();
        active.remove(arrow);
        arrow.remove();
        Location at = victim.getEyeLocation();
        victim.getWorld().playSound(at, Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
        victim.getWorld().spawnParticle(Particle.CRIT, at, 15, 0.3, 0.3, 0.3, 0.2);
        if (!defender.stats().guardReflect) {
            return;
        }
        double speed = Math.max(1.5, incoming.length());
        Player target = attacker.player();
        Vector direction = target != null && target.getWorld().equals(at.getWorld())
                ? target.getLocation().add(0, 1.0, 0).toVector().subtract(at.toVector()).normalize()
                : incoming.multiply(-1).normalize();
        Location from = at.clone().add(direction.clone().multiply(0.8));
        spawnFor(defender, from, direction.multiply(speed), 1.0, defender.stats().bounces);
    }

    private void bounce(AbstractArrow arrow, GamePlayer shooter, Vector normal, int remaining) {
        Vector velocity = arrow.getVelocity();
        // 反射ベクトル v - 2(v·n)n
        Vector reflected = velocity.clone().subtract(normal.clone().multiply(2 * velocity.dot(normal)));
        Location from = arrow.getLocation().add(normal.clone().multiply(0.3));
        double force = arrow.getPersistentDataContainer().getOrDefault(forceKey, PersistentDataType.DOUBLE, 1.0);
        spawnFor(shooter, from, reflected, force, remaining);
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
        if (target.guarding()) {
            event.setCancelled(true); // 念のため（通常は onHit で止まっている）
            return;
        }
        PlayerStats stats = shooter.stats();
        // チャージ量に関係なく一定（設定値 × カード倍率）
        double damage = plugin.settings().baseDamage() * stats.damageMultiplier;
        event.setDamage(damage);
        target.lastDamager = shooter.id();
        Fx.hit(plugin, victim, shooter.player(), damage);

        if (stats.poisonTicks > 0) {
            Game.poison(victim, stats.poisonTicks);
        }
        Player shooterPlayer = shooter.player();
        if (stats.lifesteal > 0 && shooterPlayer != null && shooter.alive) {
            double max = stats.maxHealth;
            shooterPlayer.setHealth(Math.min(max, shooterPlayer.getHealth() + damage * stats.lifesteal));
        }
        Vector push = arrow.getVelocity().setY(0);
        Bukkit.getScheduler().runTask(plugin, () -> {
            victim.setArrowsInBody(0);
            if (stats.knockback > 0 && push.lengthSquared() > 1e-4) {
                victim.setVelocity(push.normalize().multiply(0.7 * stats.knockback).setY(0.4));
            }
        });
    }

    /**
     * 爆裂矢。ブロックは壊さず、範囲内の相手に距離に関係なく一定ダメージを与えて外へ吹き飛ばす。
     * 直撃した矢のダメージと重なっても無敵時間で消えないよう、1tick後に与える。
     */
    private void explode(Location at, GamePlayer shooter, PlayerStats stats) {
        World world = at.getWorld();
        world.spawnParticle(Particle.EXPLOSION, at, 1);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.1f);
        double radius = stats.explosionPower * 2.0;
        double damage = plugin.settings().explosionDamage() * stats.damageMultiplier;
        Player source = shooter.player();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player victim : world.getNearbyPlayers(at, radius)) {
                if (victim.getUniqueId().equals(shooter.id())) {
                    continue; // 自爆なし
                }
                Optional<GamePlayer> target = games.gamePlayer(victim);
                if (target.isEmpty() || !target.get().alive || target.get().guarding()) {
                    continue;
                }
                target.get().lastDamager = shooter.id();
                victim.setNoDamageTicks(0);
                DamageSource.Builder cause = DamageSource.builder(DamageType.EXPLOSION).withDamageLocation(at);
                if (source != null) {
                    cause.withCausingEntity(source);
                }
                victim.damage(damage, cause.build());
                Fx.hit(plugin, victim, source, damage);
                Vector away = victim.getLocation().toVector().subtract(at.toVector()).setY(0);
                if (away.lengthSquared() < 1e-4) {
                    away = new Vector(0, 0, 0);
                } else {
                    away.normalize().multiply(0.6);
                }
                victim.setVelocity(victim.getVelocity().add(away.setY(0.45)));
            }
        });
    }

    private Optional<GamePlayer> shooter(AbstractArrow arrow) {
        String id = arrow.getPersistentDataContainer().get(shooterKey, PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        return games.game().flatMap(g -> g.player(UUID.fromString(id)));
    }
}
