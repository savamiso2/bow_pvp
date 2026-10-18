package io.github.savamiso2.bowrounds.game;

import io.github.savamiso2.bowrounds.BowRounds;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** 参加待ちリストと、今動いている試合（同時に1つ）を管理する。 */
public final class GameManager {

    private final BowRounds plugin;
    private final Set<UUID> queue = new LinkedHashSet<>();
    private Game game;

    public GameManager(BowRounds plugin) {
        this.plugin = plugin;
    }

    public Optional<Game> game() {
        return Optional.ofNullable(game);
    }

    /** 試合中で、このプレイヤーが参加しているなら Game と GamePlayer を返す。 */
    public Optional<GamePlayer> gamePlayer(Player player) {
        return game == null ? Optional.empty() : game.player(player.getUniqueId());
    }

    public boolean join(Player player) {
        if (!queue.add(player.getUniqueId())) {
            player.sendMessage(Component.text("すでに参加しています", NamedTextColor.GRAY));
            return false;
        }
        broadcastQueue(Component.text(player.getName() + " が参加（" + queueSize() + "人）", NamedTextColor.GREEN));
        if (game != null) {
            player.sendMessage(Component.text("試合中です。次の試合から参加します", NamedTextColor.YELLOW));
        }
        return true;
    }

    public void leave(Player player) {
        boolean wasQueued = queue.remove(player.getUniqueId());
        if (game != null) {
            game.removePlayer(player);
        }
        if (wasQueued) {
            player.sendMessage(Component.text("参加をやめました", NamedTextColor.GRAY));
        }
    }

    public int queueSize() {
        return queuedPlayers().size();
    }

    /** 試合を始める。始められなければ理由を返す。 */
    public Optional<String> start() {
        if (game != null) {
            return Optional.of("すでに試合中です");
        }
        List<Player> players = queuedPlayers();
        if (players.size() < plugin.settings().minPlayers()) {
            return Optional.of("参加者が足りません（" + players.size() + " / " + plugin.settings().minPlayers() + "人）");
        }
        if (plugin.arenas().pick(players.size(), null).isEmpty()) {
            return Optional.of("スポーン地点のあるアリーナがありません");
        }
        game = new Game(plugin, this, players);
        game.start();
        return Optional.empty();
    }

    public void stop() {
        if (game != null) {
            game.finish(false);
        }
    }

    void onFinish(Game finished) {
        if (game == finished) {
            game = null;
        }
    }

    private List<Player> queuedPlayers() {
        return queue.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }

    private void broadcastQueue(Component message) {
        for (Player p : queuedPlayers()) {
            p.sendMessage(message);
        }
    }
}
