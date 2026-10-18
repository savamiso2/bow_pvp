package io.github.savamiso2.bowrounds;

import io.github.savamiso2.bowrounds.arena.ArenaManager;
import io.github.savamiso2.bowrounds.command.BowRoundsCommand;
import io.github.savamiso2.bowrounds.game.ArrowListener;
import io.github.savamiso2.bowrounds.game.GameListener;
import io.github.savamiso2.bowrounds.game.GameManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class BowRounds extends JavaPlugin {

    private Settings settings;
    private ArenaManager arenas;
    private GameManager games;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig());
        arenas = new ArenaManager(this);
        arenas.load();
        games = new GameManager(this);

        getServer().getPluginManager().registerEvents(new GameListener(games), this);
        getServer().getPluginManager().registerEvents(new ArrowListener(this, games), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new BowRoundsCommand(this).register(event.registrar()));
    }

    @Override
    public void onDisable() {
        if (games != null) {
            games.stop();
        }
    }

    public Settings settings() {
        return settings;
    }

    public ArenaManager arenas() {
        return arenas;
    }

    public GameManager games() {
        return games;
    }
}
