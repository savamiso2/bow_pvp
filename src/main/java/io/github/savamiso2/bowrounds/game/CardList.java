package io.github.savamiso2.bowrounds.game;

import io.github.savamiso2.bowrounds.card.Card;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** 試合中の全員が持っているカード（能力）の一覧。カード名にカーソルを合わせると効果が出る。 */
public final class CardList {

    private CardList() {
    }

    public static List<Component> lines(Game game) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("―― みんなの能力（カード名にカーソルで効果） ――", NamedTextColor.GOLD));
        game.players().stream()
                .sorted(Comparator.comparingInt(GamePlayer::points).reversed())
                .forEach(gp -> lines.add(line(gp)));
        return lines;
    }

    /** チャットに出す「/br cards で確認」の案内（クリックで実行）。 */
    public static Component hint() {
        return Component.text("[みんなの能力を見る]", NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand("/br cards"))
                .hoverEvent(HoverEvent.showText(Component.text("Fキー・本の右クリックでも開けます")));
    }

    private static Component line(GamePlayer gp) {
        Component line = Component.text(gp.name(), gp.alive() ? NamedTextColor.WHITE : NamedTextColor.GRAY)
                .append(Component.text(" " + gp.points() + "pt: ", NamedTextColor.YELLOW));
        Map<Card, Integer> counts = new LinkedHashMap<>();
        for (Card card : gp.cards()) {
            counts.merge(card, 1, Integer::sum);
        }
        if (counts.isEmpty()) {
            return line.append(Component.text("なし", NamedTextColor.DARK_GRAY));
        }
        boolean first = true;
        for (Map.Entry<Card, Integer> entry : counts.entrySet()) {
            Card card = entry.getKey();
            if (!first) {
                line = line.append(Component.text(" ", NamedTextColor.GRAY));
            }
            first = false;
            String label = "[" + card.displayName() + (entry.getValue() > 1 ? " x" + entry.getValue() : "") + "]";
            Component hover = Component.text(card.displayName(), card.rarity().color())
                    .append(Component.newline())
                    .append(Component.text(card.effect(), NamedTextColor.GREEN));
            if (card.drawback() != null) {
                hover = hover.append(Component.newline()).append(Component.text(card.drawback(), NamedTextColor.RED));
            }
            line = line.append(Component.text(label, card.rarity().color()).hoverEvent(HoverEvent.showText(hover)));
        }
        return line;
    }
}
