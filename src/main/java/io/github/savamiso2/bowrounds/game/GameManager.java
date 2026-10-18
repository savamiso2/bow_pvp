package io.github.savamiso2.bowrounds.game;

import io.github.savamiso2.bowrounds.BowRounds;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/** 今動いている試合（同時に1つ）を管理する。サーバーにいる全員が参加する。 */
public final class GameManager {

    private final BowRounds plugin;
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

    /** サーバーを抜けた人を試合から外す。 */
    public void quit(Player player) {
        if (game != null) {
            game.removePlayer(player);
        }
    }

    /** ロビーへ送り、待機用の状態にする（アドベンチャー・全回復・持ち物なし）。 */
    public void sendToLobby(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            player.setHealth(health.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setFallDistance(0f);
        Location lobby = plugin.arenas().lobby();
        player.teleport(lobby != null ? lobby : player.getWorld().getSpawnLocation());
    }

    /** 試合を始める。始められなければ理由を返す。 */
    public Optional<String> start() {
        if (game != null) {
            return Optional.of("すでに試合中です");
        }
        // サーバーにいる全員で遊ぶ（観戦モードにしている人だけ除く）
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .map(p -> (Player) p)
                .toList();
        if (players.size() < plugin.settings().minPlayers()) {
            return Optional.of("人数が足りません（" + players.size() + " / " + plugin.settings().minPlayers() + "人）");
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
}
