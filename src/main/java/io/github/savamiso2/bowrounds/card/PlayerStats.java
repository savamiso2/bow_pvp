package io.github.savamiso2.bowrounds.card;

import io.github.savamiso2.bowrounds.Settings;
import java.util.List;

/** 所持カードから合成したプレイヤーの能力値。カードを取るたびに作り直す。 */
public final class PlayerStats {

    public double maxHealth;
    public double moveSpeedBonus;  // ADD_SCALAR（0.1 = +10%）
    public int magazine;
    public double reloadMultiplier = 1.0;
    public double damageMultiplier = 1.0;
    public double arrowSpeedMultiplier = 1.0;
    public int extraArrows;
    public double spreadDegrees = 8.0;
    public int bounces;
    public double homing;          // 1tickあたりの曲がる強さ
    public float explosionPower;
    public boolean noGravity;
    public int poisonTicks;
    public double lifesteal;
    public int airJumps;
    public int pierce;
    public boolean split;
    public double knockback;

    // ガード（左クリック）
    public int guardDurationTicks;
    public double guardCooldownMultiplier = 1.0;
    public int guardCooldownBonusTicks;
    public boolean guardReflect;
    public double guardShockwaveRadius;
    public double guardBlinkDistance;
    public double guardDashPower;

    public static PlayerStats compute(Settings settings, List<Card> cards) {
        PlayerStats stats = new PlayerStats();
        stats.maxHealth = settings.maxHealth();
        stats.magazine = settings.magazine();
        stats.arrowSpeedMultiplier = settings.arrowSpeed();
        stats.guardDurationTicks = settings.guardDurationTicks();
        for (Card card : cards) {
            card.apply(stats);
        }
        stats.maxHealth = Math.max(10.0, stats.maxHealth);
        stats.magazine = Math.max(1, stats.magazine);
        stats.extraArrows = Math.min(stats.extraArrows, settings.maxExtraArrows());
        stats.bounces = Math.min(stats.bounces, settings.maxBounces());
        stats.moveSpeedBonus = Math.max(-0.5, stats.moveSpeedBonus);
        return stats;
    }

    public int reloadTicks(Settings settings) {
        return Math.max(5, (int) Math.round(settings.reloadTicks() * reloadMultiplier));
    }

    public int guardCooldownTicks(Settings settings) {
        return Math.max(10, (int) Math.round(settings.guardCooldownTicks() * guardCooldownMultiplier) + guardCooldownBonusTicks);
    }
}
