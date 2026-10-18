package io.github.savamiso2.bowrounds;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

public record Settings(
        int rounds,
        int minPlayers,
        int countdownSeconds,
        int roundTimeSeconds,
        int borderShrinkAfterSeconds,
        int borderShrinkDurationSeconds,
        double borderMinSize,
        int cardPickSeconds,
        int killPoints,
        List<Integer> placementPoints,
        double maxHealth,
        int magazine,
        int reloadTicks,
        double baseDamage,
        double arrowSpeed,
        int guardDurationTicks,
        int guardCooldownTicks,
        int dashCooldownTicks,
        int offerSize,
        double bottomBonusRatio,
        int maxExtraArrows,
        int maxBounces) {

    public static Settings load(FileConfiguration c) {
        return new Settings(
                c.getInt("match.rounds", 8),
                Math.max(2, c.getInt("match.min-players", 2)),
                c.getInt("match.countdown-seconds", 3),
                c.getInt("match.round-time-seconds", 90),
                c.getInt("match.border-shrink-after-seconds", 45),
                c.getInt("match.border-shrink-duration-seconds", 40),
                c.getDouble("match.border-min-size", 8),
                c.getInt("match.card-pick-seconds", 15),
                c.getInt("points.kill", 1),
                c.getIntegerList("points.placement"),
                c.getDouble("player.max-health", 10.0),
                c.getInt("player.magazine", 3),
                c.getInt("player.reload-ticks", 40),
                c.getDouble("player.base-damage", 4.0),
                c.getDouble("player.arrow-speed", 1.0),
                c.getInt("player.guard-duration-ticks", 6),
                c.getInt("player.guard-cooldown-ticks", 80),
                c.getInt("player.dash-cooldown-ticks", 60),
                c.getInt("cards.offer-size", 3),
                c.getDouble("cards.bottom-bonus-ratio", 0.25),
                c.getInt("cards.max-extra-arrows", 6),
                c.getInt("cards.max-bounces", 3));
    }

    public int placementPoints(int place) {
        int index = place - 1;
        return index >= 0 && index < placementPoints.size() ? placementPoints.get(index) : 0;
    }
}
