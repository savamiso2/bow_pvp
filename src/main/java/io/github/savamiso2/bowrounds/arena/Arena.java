package io.github.savamiso2.bowrounds.arena;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;

/** 戦闘マップ。中心・ボーダーサイズ・スポーン地点と、遊べる人数の範囲を持つ。 */
public final class Arena {

    private final String name;
    private Location center;
    private double borderSize;
    private int minPlayers;
    private int maxPlayers;
    private final List<Location> spawns = new ArrayList<>();

    public Arena(String name, Location center, double borderSize, int minPlayers, int maxPlayers) {
        this.name = name;
        this.center = center;
        this.borderSize = borderSize;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
    }

    public String name() {
        return name;
    }

    public Location center() {
        return center.clone();
    }

    public void center(Location center) {
        this.center = center;
    }

    public double borderSize() {
        return borderSize;
    }

    public void borderSize(double borderSize) {
        this.borderSize = borderSize;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public void playerRange(int min, int max) {
        this.minPlayers = min;
        this.maxPlayers = max;
    }

    public List<Location> spawns() {
        return spawns;
    }

    public boolean fits(int players) {
        return players >= minPlayers && players <= maxPlayers && !spawns.isEmpty();
    }
}
