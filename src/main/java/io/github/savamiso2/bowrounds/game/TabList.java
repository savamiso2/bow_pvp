package io.github.savamiso2.bowrounds.game;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/** 試合中の情報をすべて Tab リストにまとめて表示する（ヘッダー・名前・ポイント・フッター）。 */
final class TabList {

    private static final Component TITLE = Fx.mm("<bold><gradient:#ffcc33:#ff6633>➶ BOW ROUNDS</gradient></bold>");

    private final Game game;
    private final Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    private final Objective points;

    TabList(Game game) {
        this.game = game;
        this.points = scoreboard.registerNewObjective("br_points", Criteria.DUMMY, TITLE);
        points.setDisplaySlot(DisplaySlot.PLAYER_LIST);
    }

    void show(Player player) {
        player.setScoreboard(scoreboard);
    }

    /** 全員分の表示を作り直す。毎秒と、ポイントや生死が変わったときに呼ぶ。 */
    void refresh() {
        List<GamePlayer> ranking = game.players().stream()
                .sorted(Comparator.comparingInt(GamePlayer::points).reversed()).toList();
        Component header = header();
        for (int i = 0; i < ranking.size(); i++) {
            GamePlayer gp = ranking.get(i);
            int rank = rankOf(ranking, gp);
            points.getScore(gp.name()).setScore(gp.points);
            points.getScore(gp.name()).numberFormat(NumberFormat.fixed(Fx.mm("<gold>" + gp.points + "<yellow>pt")));
            Player p = gp.player();
            if (p == null) {
                continue;
            }
            p.playerListName(entry(gp, rank));
            // ポイントが高い人ほど上に並ぶ
            p.setPlayerListOrder(Math.max(0, gp.points));
            p.sendPlayerListHeaderAndFooter(header, footer(gp, rank));
        }
    }

    /** 試合から抜けた人の Tab 表示を元に戻し、ロビー用の表示にする。 */
    static void reset(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        player.playerListName(null);
        player.setPlayerListOrder(0);
    }

    static void lobby(Player player) {
        player.sendPlayerListHeaderAndFooter(
                Component.newline().append(TITLE).append(Component.newline())
                        .append(Fx.mm("<gray>ロビーで待機中  <dark_gray>|  <white>" + Bukkit.getOnlinePlayers().size() + "<gray>人がオンライン")),
                Fx.mm("<dark_gray>次の試合が始まるまでお待ちください"));
    }

    private Component header() {
        String status = switch (game.state()) {
            case COUNTDOWN -> "<yellow>まもなく開始";
            case PLAYING -> "<green>戦闘中  <dark_gray>|  <white>生存 <bold>" + game.alivePlayers().size()
                    + "</bold> <gray>/ " + game.players().size();
            case ROUND_END -> "<gold>ラウンド終了";
            case CARD_PICK -> "<light_purple>カード選択中  <dark_gray>|  <white>あと <bold>"
                    + game.players().stream().filter(g -> !g.picked && g.player() != null).count() + "</bold><gray>人";
            default -> "<gray>試合終了";
        };
        String arena = game.arena() == null ? "" : "  <dark_gray>·  <aqua>" + game.arena().name();
        return Component.newline().append(TITLE).append(Component.newline())
                .append(Fx.mm("<gray>ROUND <white><bold>" + game.round() + "</bold> <dark_gray>/ "
                        + game.settings().rounds() + arena))
                .append(Component.newline())
                .append(Fx.mm(status))
                .append(Component.newline());
    }

    private static Component entry(GamePlayer gp, int rank) {
        String medal = switch (rank) {
            case 1 -> "<#ffcc33>";
            case 2 -> "<#d0d8e0>";
            case 3 -> "<#d08a4a>";
            default -> "<gray>";
        };
        String life = gp.alive ? "<green>❤ <white>" + gp.name() : "<dark_gray>☠ <gray><st>" + gp.name() + "</st>";
        String cards = gp.cards().isEmpty() ? "" : "  <light_purple>✦" + gp.cards().size();
        return Fx.mm(medal + "<bold>#" + rank + "</bold> " + life + cards + " ");
    }

    private static Component footer(GamePlayer gp, int rank) {
        return Component.newline()
                .append(Fx.mm("<gray>あなた  <white><bold>" + rank + "</bold>位  <dark_gray>·  <gold>" + gp.points
                        + "pt  <dark_gray>·  <red>" + gp.kills + "キル  <dark_gray>·  <light_purple>カード "
                        + gp.cards().size() + "枚"))
                .append(Component.newline())
                .append(Fx.mm("<dark_gray>Fキー / 本でみんなの能力を見る"))
                .append(Component.newline());
    }

    /** 同点は同じ順位にする。 */
    private static int rankOf(List<GamePlayer> ranking, GamePlayer gp) {
        int better = 0;
        for (GamePlayer other : ranking) {
            if (other.points > gp.points) {
                better++;
            }
        }
        return better + 1;
    }
}
