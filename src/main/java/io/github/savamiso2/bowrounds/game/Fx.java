package io.github.savamiso2.bowrounds.game;

import io.github.savamiso2.bowrounds.BowRounds;
import io.github.savamiso2.bowrounds.card.Card;
import io.github.savamiso2.bowrounds.card.PlayerStats;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DeathProtection;
import java.time.Duration;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** 見た目の演出（パーティクル・タイトル・効果音・ダメージ表示など）をまとめたもの。 */
final class Fx {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Fx() {
    }

    static Component mm(String text) {
        return MM.deserialize(text);
    }

    // ---- 矢 ----

    /** 矢の軌跡。カードの効果ごとに色・粒子を変える。毎tick呼ばれる。 */
    static void trail(AbstractArrow arrow, UUID shooter, PlayerStats stats) {
        Location at = arrow.getLocation();
        World world = at.getWorld();
        if (stats.explosionPower > 0) {
            world.spawnParticle(Particle.FLAME, at, 1, 0, 0, 0, 0.005);
        } else if (stats.homing > 0) {
            world.spawnParticle(Particle.END_ROD, at, 1, 0, 0, 0, 0);
        } else if (stats.poisonTicks > 0) {
            world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(90, 200, 60), 1.1f));
        } else {
            world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(playerColor(shooter), 0.9f));
        }
    }

    /** プレイヤーごとの固定色（矢の軌跡で誰の矢か分かるように）。 */
    static Color playerColor(UUID id) {
        float hue = (id.hashCode() & 0xffff) / 65535f;
        java.awt.Color c = java.awt.Color.getHSBColor(hue, 0.65f, 1f);
        return Color.fromRGB(c.getRed(), c.getGreen(), c.getBlue());
    }

    // ---- 命中 ----

    /** 当たった相手の上にダメージの数字を浮かべ、撃った人に命中音を鳴らす。 */
    static void hit(BowRounds plugin, Player victim, Player shooter, double damage) {
        Location at = victim.getEyeLocation().add(randomOffset(0.4), 0.3, randomOffset(0.4));
        TextDisplay text = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.text(mm("<bold><gradient:#ff5555:#ffaa00>-" + format(damage) + "</gradient></bold>"));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(1.2f, 1.2f, 1.2f), new AxisAngle4f()));
            d.setTeleportDuration(12);
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (text.isValid()) {
                text.teleport(text.getLocation().add(0, 0.8, 0));
            }
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, text::remove, 16L);
        victim.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, victim.getLocation().add(0, 1, 0), 4, 0.3, 0.4, 0.3, 0.1);
        if (shooter != null) {
            shooter.playSound(shooter.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.3f);
        }
    }

    // ---- カード獲得 ----

    /**
     * 不死のトーテムを使ったときの演出で、取ったカードを画面いっぱいに出す。
     * クライアントは手に持っている「死を防ぐ」アイテムを表示するので、少しだけオフハンドにカードを持たせる。
     */
    static void cardReveal(BowRounds plugin, Player player, Card card) {
        ItemStack item = ItemStack.of(card.rarity().icon());
        CardArt.apply(item, card, player);
        item.setData(DataComponentTypes.DEATH_PROTECTION, DeathProtection.deathProtection().build());
        PlayerInventory inventory = player.getInventory();
        ItemStack previous = inventory.getItemInOffHand();
        inventory.setItemInOffHand(item);
        // 持ち物の変更はtickの終わりに送られるので、演出は次のtickに出す（先に出すとトーテムの絵になる）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playEffect(EntityEffect.PROTECTED_FROM_DEATH);
            }
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                inventory.setItemInOffHand(previous);
            }
        }, 3L);
    }

    // ---- 脱落・キル ----

    static void elimination(Player victim, Player killer, String victimName, String killerName) {
        Location at = victim.getLocation().add(0, 1, 0);
        World world = at.getWorld();
        world.spawnParticle(Particle.SOUL, at, 25, 0.4, 0.6, 0.4, 0.05);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, at, 40, 0.3, 0.5, 0.3, 0.4);
        world.playSound(at, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.6f);
        world.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1f);
        victim.showTitle(Title.title(mm("<bold><gradient:#ff3333:#990000>ELIMINATED</gradient></bold>"),
                mm(killerName == null ? "<gray>観戦モード" : "<gray>" + killerName + " <white>に倒された"),
                times(150, 1500, 400)));
        victim.playSound(victim.getLocation(), Sound.ENTITY_WITHER_HURT, 0.6f, 0.7f);
        if (killer != null) {
            killer.showTitle(Title.title(Component.empty(),
                    mm("<bold><gradient:#ffdd55:#ff7700>⚔ KILL</gradient></bold> <white>" + victimName),
                    times(0, 900, 250)));
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            killer.playSound(killer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.6f);
        }
    }

    static Component killFeed(String killer, String victim) {
        return killer == null
                ? mm("<dark_gray>☠ <gray>" + victim + " <dark_gray>が脱落")
                : mm("<gold>" + killer + " <yellow>➶ <gray>" + victim);
    }

    // ---- ラウンド ----

    static Component countdownNumber(int seconds) {
        String color = switch (seconds) {
            case 3 -> "#ff5555";
            case 2 -> "#ffaa00";
            case 1 -> "#ffff55";
            default -> "#ffffff";
        };
        return mm("<bold><color:" + color + ">" + seconds + "</color></bold>");
    }

    static float countdownPitch(int seconds) {
        return switch (seconds) {
            case 3 -> 0.8f;
            case 2 -> 1.0f;
            default -> 1.2f;
        };
    }

    static Component fightTitle() {
        return mm("<bold><gradient:#ff3355:#ffcc00:#ff3355>FIGHT!</gradient></bold>");
    }

    /** 開戦時、各プレイヤーの足元にリングを出す。 */
    static void fightBurst(Player p) {
        Location at = p.getLocation().add(0, 0.2, 0);
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            Vector v = new Vector(Math.cos(a), 0, Math.sin(a));
            at.getWorld().spawnParticle(Particle.FLAME, at.clone().add(v.clone().multiply(0.8)), 0,
                    v.getX(), 0.05, v.getZ(), 0.15);
        }
    }

    static Component roundWinTitle(String winner) {
        return mm("<bold><gradient:#ffe066:#ff9a00>" + winner + "</gradient></bold> <yellow>の勝ち！");
    }

    static Component championTitle(String winner) {
        return mm("<bold><gradient:#fff2a8:#ffcc00:#ff8800>👑 " + winner + " 👑</gradient></bold>");
    }

    /** 花火を打ち上げる（演出用。ダメージは試合外なのでロビー側で無効になる）。 */
    static void firework(Location at, Color main, boolean big) {
        at.getWorld().spawn(at, Firework.class, fw -> {
            var meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(big ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.BURST)
                    .withColor(main, Color.WHITE)
                    .withFade(Color.fromRGB(255, 200, 80))
                    .trail(true).flicker(big)
                    .build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        });
    }

    /** ダメージなしの花火風パーティクル（試合中でも安全）。 */
    static void sparkle(Location at) {
        at.getWorld().spawnParticle(Particle.FIREWORK, at, 40, 0.3, 0.3, 0.3, 0.25);
        at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 30, 0.4, 0.4, 0.4, 0.5);
        at.getWorld().playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1f, 1f);
    }

    static Title.Times times(long fadeIn, long stay, long fadeOut) {
        return Title.Times.times(Duration.ofMillis(fadeIn), Duration.ofMillis(stay), Duration.ofMillis(fadeOut));
    }

    private static double randomOffset(double range) {
        return (Math.random() * 2 - 1) * range;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}
