package io.github.savamiso2.bowrounds.game;

import io.github.savamiso2.bowrounds.BowRounds;
import io.github.savamiso2.bowrounds.card.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * 全員の基本操作の左クリックガードと、ダッシュなどの能力。
 * ガードで矢を防ぐ・跳ね返す処理は ArrowListener 側。
 */
public final class AbilityListener implements Listener {

    private static final int DOUBLE_SNEAK_TICKS = 8;

    private final BowRounds plugin;
    private final GameManager games;

    public AbilityListener(BowRounds plugin, GameManager games) {
        this.plugin = plugin;
        this.games = games;
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateCooldownBars, 2L, 2L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLeftClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            tryGuard(event.getPlayer());
        }
    }

    /** プレイヤーを殴ったとき（近接は無効）もガード操作として扱う。 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPunch(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            tryGuard(attacker);
        }
    }

    private void tryGuard(Player player) {
        games.gamePlayer(player).ifPresent(gp -> {
            Game game = games.game().orElseThrow();
            int now = Bukkit.getCurrentTick();
            if (game.state() != GameState.PLAYING || !gp.alive || now < gp.guardReadyTick) {
                return;
            }
            PlayerStats stats = gp.stats();
            gp.guardUntilTick = now + stats.guardDurationTicks;
            gp.guardReadyTick = now + stats.guardCooldownTicks(plugin.settings());

            Location loc = player.getLocation();
            player.getWorld().playSound(loc, Sound.ITEM_SHIELD_BLOCK, 1f, 1.4f);
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0.05);

            if (stats.guardShockwaveRadius > 0) {
                shockwave(game, gp, player, stats.guardShockwaveRadius);
            }
            if (stats.guardBlinkDistance > 0) {
                blink(player, stats.guardBlinkDistance);
            }
        });
    }

    private void shockwave(Game game, GamePlayer self, Player player, double radius) {
        Location center = player.getLocation();
        double radiusSquared = radius * radius;
        for (GamePlayer other : game.alivePlayers()) {
            if (other == self) {
                continue;
            }
            Player target = other.player();
            if (!target.getWorld().equals(center.getWorld())) {
                continue;
            }
            Vector away = target.getLocation().toVector().subtract(center.toVector());
            double distanceSquared = away.lengthSquared();
            if (distanceSquared > radiusSquared) {
                continue;
            }
            if (distanceSquared < 0.01) {
                away = new Vector(0, 0, 1);
            }
            target.setVelocity(away.setY(0).normalize().multiply(1.3).setY(0.5));
            other.lastDamager = self.id();
        }
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
    }

    /** 向いている水平方向へ、壁の手前まで瞬間移動。 */
    private void blink(Player player, double distance) {
        Location from = player.getLocation();
        Vector direction = from.getDirection().setY(0);
        if (direction.lengthSquared() < 1e-4) {
            return;
        }
        direction.normalize();
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, direction, distance, FluidCollisionMode.NEVER, true);
        double travel = hit == null ? distance : Math.max(0, hit.getHitPosition().distance(eye.toVector()) - 0.6);
        // 足元や頭がブロックに埋まる位置なら少しずつ手前に戻す
        for (; travel > 0.5; travel -= 0.5) {
            Location target = from.clone().add(direction.clone().multiply(travel));
            Block feet = target.getBlock();
            Block head = feet.getRelative(0, 1, 0);
            if (feet.isPassable() && head.isPassable()) {
                player.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
                player.teleport(target);
                player.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f);
                return;
            }
        }
    }

    /** スニーク2回でダッシュ（ダッシュカード）。 */
    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        games.gamePlayer(player).ifPresent(gp -> {
            Game game = games.game().orElseThrow();
            int now = Bukkit.getCurrentTick();
            boolean doubleTap = now - gp.lastSneakTick <= DOUBLE_SNEAK_TICKS;
            gp.lastSneakTick = now;
            PlayerStats stats = gp.stats();
            if (!doubleTap || stats.dashLevel <= 0 || game.state() != GameState.PLAYING
                    || !gp.alive || now < gp.dashReadyTick) {
                return;
            }
            gp.dashReadyTick = now + stats.dashCooldownTicks(plugin.settings());
            gp.lastSneakTick = -100;
            Vector direction = player.getLocation().getDirection().setY(0);
            if (direction.lengthSquared() < 1e-4) {
                return;
            }
            player.setVelocity(direction.normalize().multiply(1.4).setY(0.2));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 1.2f);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 8, 0.2, 0.1, 0.2, 0.02);
        });
    }

    /** XPバーにガードのクールダウンを表示（満タン＝使える）。 */
    private void updateCooldownBars() {
        Game game = games.game().orElse(null);
        if (game == null || game.state() != GameState.PLAYING) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        for (GamePlayer gp : game.alivePlayers()) {
            int total = gp.stats().guardCooldownTicks(plugin.settings());
            int remaining = Math.max(0, gp.guardReadyTick - now);
            float progress = 1f - Math.min(1f, (float) remaining / total);
            gp.player().setExp(Math.min(0.999f, progress));
        }
    }
}
