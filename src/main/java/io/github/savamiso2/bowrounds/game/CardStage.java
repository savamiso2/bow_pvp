package io.github.savamiso2.bowrounds.game;

import io.github.savamiso2.bowrounds.BowRounds;
import io.github.savamiso2.bowrounds.card.Card;
import io.github.savamiso2.bowrounds.card.Rarity;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * 3Dのカード選択。プレイヤーの目の前にカードを浮かび上がらせ、見ているカードを光らせ、クリックで決定する。
 * カードはそのプレイヤーにしか見えない（大人数でも他人のカードが邪魔にならない）。
 */
final class CardStage {

    private static final double DISTANCE = 2.6;
    private static final double SPACING = 1.25;
    private static final double HOVER_COS = Math.cos(Math.toRadians(13));
    private static final float CARD_SCALE = 0.9f;
    /** アイコンは少し上、説明文はその下へ離して置く（説明文は下端から上に伸びるので、下端をアイコンより十分下げる）。 */
    private static final float ICON_Y = 0.2f;
    private static final double TEXT_DROP = 1.55;
    private static final float TEXT_SCALE = 0.55f;

    private final BowRounds plugin;
    private final GamePlayer gamePlayer;
    private final List<Slot> slots = new ArrayList<>();
    private int hovered = -1;
    private boolean closed;

    private record Slot(Card card, Location position, ItemDisplay item, TextDisplay text) {
    }

    CardStage(BowRounds plugin, GamePlayer gamePlayer) {
        this.plugin = plugin;
        this.gamePlayer = gamePlayer;
    }

    /** カードを並べ、1枚ずつ下からせり上がらせる。 */
    void show() {
        Player player = gamePlayer.player();
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().setY(0);
        if (forward.lengthSquared() < 1e-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        List<Card> offer = gamePlayer.offer;
        for (int i = 0; i < offer.size(); i++) {
            Card card = offer.get(i);
            double offset = SPACING * (i - (offer.size() - 1) / 2.0);
            Location position = eye.clone().add(forward.clone().multiply(DISTANCE)).add(right.clone().multiply(offset));
            ItemDisplay item = spawnItem(player, card, position);
            TextDisplay text = spawnText(player, card, position.clone().subtract(0, TEXT_DROP, 0));
            slots.add(new Slot(card, position, item, text));

            int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> reveal(index), 2L + 5L * i);
        }
    }

    private ItemDisplay spawnItem(Player viewer, Card card, Location at) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setVisibleByDefault(false);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setItemStack(icon(card));
            // せり上がる前の位置（下に沈めて小さく）
            d.setTransformation(transform(-1.2f, 0.01f));
            viewer.showEntity(plugin, d);
        });
    }

    private TextDisplay spawnText(Player viewer, Card card, Location at) {
        return at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setVisibleByDefault(false);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(130);
            d.setShadowed(true);
            d.setBackgroundColor(background(card.rarity(), false));
            d.text(label(card));
            d.setTransformation(transform(-1.2f, 0.01f));
            viewer.showEntity(plugin, d);
        });
    }

    private void reveal(int index) {
        if (closed || index >= slots.size()) {
            return;
        }
        Slot slot = slots.get(index);
        animate(slot.item(), transform(ICON_Y, CARD_SCALE), 10);
        animate(slot.text(), transform(0f, TEXT_SCALE), 10);
        Player player = gamePlayer.player();
        if (player == null) {
            return;
        }
        float pitch = 0.8f + 0.2f * index;
        player.playSound(slot.position(), Sound.ITEM_BOOK_PAGE_TURN, 1f, pitch);
        if (slot.card().rarity() == Rarity.EPIC) {
            player.playSound(slot.position(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            player.spawnParticle(Particle.END_ROD, slot.position(), 20, 0.3, 0.4, 0.3, 0.02);
        } else if (slot.card().rarity() == Rarity.RARE) {
            player.spawnParticle(Particle.ENCHANT, slot.position(), 30, 0.3, 0.4, 0.3, 0.5);
        }
    }

    /** 視線の先のカードを光らせる。毎tick数回呼ばれる。 */
    void updateHover() {
        Player player = gamePlayer.player();
        if (closed || player == null) {
            return;
        }
        Location eye = player.getEyeLocation();
        // 縦方向は甘めに判定する（アイコンを見ても下の説明文を読んでいても、そのカードが選ばれるように）
        Vector look = squash(eye.getDirection());
        int best = -1;
        double bestDot = HOVER_COS;
        for (int i = 0; i < slots.size(); i++) {
            Vector toCard = squash(slots.get(i).position().clone().subtract(0, 0.6, 0).toVector().subtract(eye.toVector()));
            double dot = toCard.dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = i;
            }
        }
        if (best == hovered) {
            return;
        }
        if (hovered >= 0) {
            highlight(slots.get(hovered), false);
        }
        hovered = best;
        if (best >= 0) {
            Slot slot = slots.get(best);
            highlight(slot, true);
            player.playSound(eye, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.6f);
            player.sendActionBar(Component.text("クリックで「" + slot.card().displayName() + "」に決定", NamedTextColor.YELLOW));
        }
    }

    private void highlight(Slot slot, boolean on) {
        Color glow = switch (slot.card().rarity()) {
            case COMMON -> Color.WHITE;
            case RARE -> Color.AQUA;
            case EPIC -> Color.FUCHSIA;
        };
        slot.item().setGlowColorOverride(glow);
        slot.item().setGlowing(on);
        animate(slot.item(), transform(on ? ICON_Y + 0.15f : ICON_Y, on ? CARD_SCALE * 1.25f : CARD_SCALE), 3);
        slot.text().setBackgroundColor(background(slot.card().rarity(), on));
    }

    /** 見ているカード。なければ null。 */
    Card hoveredCard() {
        return hovered >= 0 && hovered < slots.size() ? slots.get(hovered).card() : null;
    }

    /** 決定演出：選んだカードは手元へ吸い込まれ、残りは沈む。少ししたら全部消す。 */
    void select(Card card) {
        if (closed) {
            return;
        }
        closed = true;
        Player player = gamePlayer.player();
        for (Slot slot : slots) {
            if (slot.card() == card) {
                slot.item().setGlowing(true);
                animate(slot.item(), transform(-0.6f, 0.05f), 12);
                animate(slot.text(), transform(-0.6f, 0.01f), 8);
                if (player != null) {
                    player.spawnParticle(Particle.TOTEM_OF_UNDYING, slot.position(), 40, 0.2, 0.3, 0.2, 0.3);
                }
            } else {
                animate(slot.item(), transform(-1.5f, 0.01f), 10);
                animate(slot.text(), transform(-1.5f, 0.01f), 10);
            }
        }
        if (player != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::remove, 14L);
    }

    void remove() {
        closed = true;
        for (Slot slot : slots) {
            slot.item().remove();
            slot.text().remove();
        }
        slots.clear();
    }

    // ---- 見た目 ----

    private static void animate(Display display, Transformation to, int ticks) {
        if (!display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(to);
    }

    private static Vector squash(Vector v) {
        return v.clone().setY(v.getY() * 0.4).normalize();
    }

    private static Transformation transform(float y, float scale) {
        return new Transformation(new Vector3f(0, y, 0), new AxisAngle4f(),
                new Vector3f(scale, scale, scale), new AxisAngle4f());
    }

    private static Color background(Rarity rarity, boolean bright) {
        int alpha = bright ? 220 : 150;
        return switch (rarity) {
            case COMMON -> Color.fromARGB(alpha, 40, 40, 40);
            case RARE -> Color.fromARGB(alpha, 20, 60, 110);
            case EPIC -> Color.fromARGB(alpha, 90, 20, 110);
        };
    }

    private static Component label(Card card) {
        Component text = Component.text(card.displayName(), card.rarity().color()).decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(Component.text("[" + card.rarity().label() + "]", card.rarity().color()))
                .append(Component.newline())
                .append(Component.text(card.effect(), NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false));
        if (card.drawback() != null) {
            text = text.append(Component.newline())
                    .append(Component.text(card.drawback(), NamedTextColor.RED).decoration(TextDecoration.BOLD, false));
        }
        return text;
    }

    private static ItemStack icon(Card card) {
        Material material = switch (card) {
            case SPREAD -> Material.SPECTRAL_ARROW;
            case BOUNCE -> Material.SLIME_BALL;
            case QUICK_RELOAD -> Material.CLOCK;
            case BIG_MAGAZINE -> Material.BUNDLE;
            case TOUGH -> Material.IRON_CHESTPLATE;
            case POISON -> Material.SPIDER_EYE;
            case STRAIGHT -> Material.FEATHER;
            case HOMING -> Material.ENDER_EYE;
            case VAMPIRE -> Material.REDSTONE;
            case JET -> Material.FIREWORK_ROCKET;
            case POWER -> Material.BOW;
            case EXPLOSIVE -> Material.TNT;
            case PIERCE -> Material.TRIDENT;
            case SPLIT -> Material.PRISMARINE_SHARD;
            case KNOCKBACK -> Material.PISTON;
            case DASH -> Material.RABBIT_FOOT;
            case REFLECT -> Material.SHIELD;
            case SHOCKWAVE -> Material.WIND_CHARGE;
            case GUARD_MASTER -> Material.IRON_INGOT;
            case BLINK_GUARD -> Material.ENDER_PEARL;
        };
        ItemStack item = ItemStack.of(material);
        if (card.rarity() == Rarity.EPIC) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }
}
