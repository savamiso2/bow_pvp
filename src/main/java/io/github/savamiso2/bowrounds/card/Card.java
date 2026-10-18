package io.github.savamiso2.bowrounds.card;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** 強化カード。重ね取り可能で、効果は PlayerStats に加算される。 */
public enum Card {
    SPREAD("散弾", Rarity.COMMON, "1回で矢を+2本発射", "ダメージ -40%", s -> {
        s.extraArrows += 2;
        s.damageMultiplier *= 0.6;
    }),
    BOUNCE("跳弾", Rarity.COMMON, "矢が壁で1回跳ね返る", "矢の速度 -10%", s -> {
        s.bounces += 1;
        s.arrowSpeedMultiplier *= 0.9;
    }),
    QUICK_RELOAD("速装填", Rarity.COMMON, "リロード時間 -40%", "ダメージ -10%", s -> {
        s.reloadMultiplier *= 0.6;
        s.damageMultiplier *= 0.9;
    }),
    BIG_MAGAZINE("大容量", Rarity.COMMON, "装填数 +3", "リロード時間 +30%", s -> {
        s.magazine += 3;
        s.reloadMultiplier *= 1.3;
    }),
    TOUGH("頑丈", Rarity.COMMON, "最大体力 +2ハート", "移動速度 -10%", s -> {
        s.maxHealth += 4;
        s.moveSpeedBonus -= 0.1;
    }),
    POISON("毒矢", Rarity.COMMON, "命中した相手に毒2秒", null, s -> s.poisonTicks += 40),
    STRAIGHT("重力無視", Rarity.RARE, "矢がまっすぐ飛ぶ", "ダメージ -15%", s -> {
        s.noGravity = true;
        s.damageMultiplier *= 0.85;
    }),
    HOMING("追尾", Rarity.RARE, "矢が近くの敵へ曲がる", "リロード時間 +20%", s -> {
        s.homing += 0.08;
        s.reloadMultiplier *= 1.2;
    }),
    VAMPIRE("吸血", Rarity.RARE, "与ダメージの30%を回復", "最大体力 -1ハート", s -> {
        s.lifesteal += 0.3;
        s.maxHealth -= 2;
    }),
    JET("ジェット", Rarity.RARE, "空中ジャンプ +1", null, s -> s.airJumps += 1),
    POWER("剛弓", Rarity.RARE, "ダメージ +40%、矢の速度 +30%", "装填数 -1", s -> {
        s.damageMultiplier *= 1.4;
        s.arrowSpeedMultiplier *= 1.3;
        s.magazine -= 1;
    }),
    EXPLOSIVE("爆裂矢", Rarity.EPIC, "着弾地点で小爆発", "装填数 -1", s -> {
        s.explosionPower += 1.2f;
        s.magazine -= 1;
    });

    private final String displayName;
    private final Rarity rarity;
    private final String effect;
    private final String drawback;
    private final Consumer<PlayerStats> modifier;

    Card(String displayName, Rarity rarity, String effect, String drawback, Consumer<PlayerStats> modifier) {
        this.displayName = displayName;
        this.rarity = rarity;
        this.effect = effect;
        this.drawback = drawback;
        this.modifier = modifier;
    }

    public String displayName() {
        return displayName;
    }

    public Rarity rarity() {
        return rarity;
    }

    public String effect() {
        return effect;
    }

    /** デメリットの説明。無ければ null。 */
    public String drawback() {
        return drawback;
    }

    void apply(PlayerStats stats) {
        modifier.accept(stats);
    }

    /** 重複なしで count 枚を、レア度の重み付きで抽選する。 */
    public static List<Card> draw(int count, boolean boosted) {
        Set<Card> pool = EnumSet.allOf(Card.class);
        List<Card> result = new ArrayList<>(count);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (result.size() < count && !pool.isEmpty()) {
            int total = pool.stream().mapToInt(c -> c.rarity.weight(boosted)).sum();
            int roll = random.nextInt(total);
            for (Card card : pool) {
                roll -= card.rarity.weight(boosted);
                if (roll < 0) {
                    result.add(card);
                    pool.remove(card);
                    break;
                }
            }
        }
        return result;
    }
}
