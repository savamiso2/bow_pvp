package io.github.savamiso2.bowrounds.arena;

import io.github.savamiso2.bowrounds.BowRounds;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** arenas.yml の読み書きと、人数に合ったアリーナの選択。 */
public final class ArenaManager {

    private final BowRounds plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private Location lobby;

    public ArenaManager(BowRounds plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
    }

    public void load() {
        arenas.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        lobby = yaml.getLocation("lobby");
        ConfigurationSection section = yaml.getConfigurationSection("arenas");
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection a = section.getConfigurationSection(name);
            Location center = a.getLocation("center");
            if (center == null) {
                plugin.getLogger().warning("Arena " + name + " has no center (world missing?), skipped");
                continue;
            }
            Arena arena = new Arena(name, center, a.getDouble("border-size", 60),
                    a.getInt("min-players", 2), a.getInt("max-players", 16));
            for (Object o : a.getList("spawns", List.of())) {
                if (o instanceof Location loc) {
                    arena.spawns().add(loc);
                }
            }
            arenas.put(name, arena);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("lobby", lobby);
        for (Arena arena : arenas.values()) {
            String path = "arenas." + arena.name();
            yaml.set(path + ".center", arena.center());
            yaml.set(path + ".border-size", arena.borderSize());
            yaml.set(path + ".min-players", arena.minPlayers());
            yaml.set(path + ".max-players", arena.maxPlayers());
            yaml.set(path + ".spawns", new ArrayList<>(arena.spawns()));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save arenas.yml: " + e.getMessage());
        }
    }

    public Optional<Arena> get(String name) {
        return Optional.ofNullable(arenas.get(name));
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    public Arena create(String name, Location center, int maxPlayers) {
        Arena arena = new Arena(name, center, 60, 2, maxPlayers);
        arenas.put(name, arena);
        save();
        return arena;
    }

    public boolean remove(String name) {
        boolean removed = arenas.remove(name) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Location lobby() {
        return lobby == null ? null : lobby.clone();
    }

    public void lobby(Location lobby) {
        this.lobby = lobby;
        save();
    }

    /** 人数に合うアリーナからランダムに選ぶ。合うものがなければ最大人数が一番大きいもの。 */
    public Optional<Arena> pick(int players, Arena previous) {
        List<Arena> fitting = arenas.values().stream().filter(a -> a.fits(players)).toList();
        if (fitting.size() > 1 && previous != null) {
            fitting = fitting.stream().filter(a -> a != previous).toList();
        }
        if (!fitting.isEmpty()) {
            return Optional.of(fitting.get(ThreadLocalRandom.current().nextInt(fitting.size())));
        }
        return arenas.values().stream()
                .filter(a -> !a.spawns().isEmpty())
                .max(Comparator.comparingInt(Arena::maxPlayers));
    }
}
