package io.github.savamiso2.bowrounds.game;

import io.github.savamiso2.bowrounds.card.Card;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * 能力一覧のGUI。一覧ページでは全員の頭が並び（説明に持っているカード）、頭をクリックするとその人のカードが大きく並ぶ。
 * 大人数でも見られるよう、一覧は45人ずつページ分けする。
 */
final class AbilityMenu implements InventoryHolder {

    private static final int PAGE_SIZE = 45;
    private static final int PREV = 45;
    private static final int BACK = 49;
    private static final int NEXT = 53;

    private final Game game;
    private final Inventory inventory;
    private final List<UUID> heads = new ArrayList<>();
    private final int page;
    private final boolean detail;

    /** 全員の一覧（page は 0 始まり）。 */
    static AbilityMenu overview(Game game, int page) {
        return new AbilityMenu(game, page, null);
    }

    /** 1人分のカード一覧。 */
    static AbilityMenu detail(Game game, GamePlayer target, int fromPage) {
        return new AbilityMenu(game, fromPage, target);
    }

    private AbilityMenu(Game game, int page, GamePlayer target) {
        this.game = game;
        this.detail = target != null;
        List<GamePlayer> players = game.players().stream()
                .sorted(Comparator.comparingInt(GamePlayer::points).reversed())
                .toList();
        int pages = Math.max(1, (players.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.clamp(page, 0, pages - 1);
        Component title = detail
                ? Component.text(target.name() + " の能力", NamedTextColor.DARK_PURPLE)
                : Component.text("みんなの能力" + (pages > 1 ? "（" + (this.page + 1) + "/" + pages + "）" : ""),
                        NamedTextColor.DARK_PURPLE);
        this.inventory = Bukkit.createInventory(this, 54, title);

        if (detail) {
            int slot = 0;
            for (Map.Entry<Card, Integer> entry : grouped(target.cards()).entrySet()) {
                if (slot >= PAGE_SIZE) {
                    break;
                }
                inventory.setItem(slot++, cardIcon(entry.getKey(), entry.getValue()));
            }
            if (target.cards().isEmpty()) {
                inventory.setItem(22, named(Material.BARRIER, "まだカードを持っていません", NamedTextColor.GRAY));
            }
            inventory.setItem(BACK, named(Material.ARROW, "一覧に戻る", NamedTextColor.YELLOW));
            return;
        }
        List<GamePlayer> shown = players.subList(this.page * PAGE_SIZE,
                Math.min(players.size(), (this.page + 1) * PAGE_SIZE));
        for (int i = 0; i < shown.size(); i++) {
            GamePlayer gp = shown.get(i);
            heads.add(gp.id());
            inventory.setItem(i, head(gp));
        }
        if (this.page > 0) {
            inventory.setItem(PREV, named(Material.ARROW, "前のページ", NamedTextColor.YELLOW));
        }
        if (this.page < pages - 1) {
            inventory.setItem(NEXT, named(Material.ARROW, "次のページ", NamedTextColor.YELLOW));
        }
    }

    /** クリックされたスロットに応じて、次に開くメニューを返す。何もしないなら null。 */
    AbilityMenu click(int slot) {
        if (detail) {
            return slot == BACK ? overview(game, page) : null;
        }
        if (slot == PREV) {
            return overview(game, page - 1);
        }
        if (slot == NEXT) {
            return overview(game, page + 1);
        }
        if (slot >= 0 && slot < heads.size()) {
            return game.player(heads.get(slot)).map(gp -> detail(game, gp, page)).orElse(null);
        }
        return null;
    }

    private static ItemStack head(GamePlayer gp) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        item.editMeta(SkullMeta.class, meta -> {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(gp.id()));
            meta.displayName(plain(gp.name(), gp.alive() ? NamedTextColor.WHITE : NamedTextColor.GRAY)
                    .decoration(TextDecoration.BOLD, true));
            List<Component> lore = new ArrayList<>();
            lore.add(plain(gp.points() + " pt" + (gp.alive() ? "" : "（脱落中）"), NamedTextColor.YELLOW));
            Map<Card, Integer> cards = grouped(gp.cards());
            if (cards.isEmpty()) {
                lore.add(plain("カードなし", NamedTextColor.DARK_GRAY));
            }
            cards.forEach((card, count) -> lore.add(
                    plain(card.displayName() + (count > 1 ? " x" + count : ""), card.rarity().color())));
            lore.add(Component.empty());
            lore.add(plain("クリックで詳しく見る", NamedTextColor.AQUA));
            meta.lore(lore);
        });
        return item;
    }

    private static ItemStack cardIcon(Card card, int count) {
        ItemStack item = ItemStack.of(card.rarity().icon(), Math.min(count, 64));
        item.editMeta(meta -> {
            meta.displayName(plain(card.displayName(), card.rarity().color()).decoration(TextDecoration.BOLD, true));
            List<Component> lore = new ArrayList<>();
            lore.add(plain("[" + card.rarity().label() + "]" + (count > 1 ? " x" + count : ""), card.rarity().color()));
            lore.add(plain("+ " + card.effect(), NamedTextColor.GREEN));
            if (card.drawback() != null) {
                lore.add(plain("- " + card.drawback(), NamedTextColor.RED));
            }
            meta.lore(lore);
        });
        return item;
    }

    private static Map<Card, Integer> grouped(List<Card> cards) {
        Map<Card, Integer> counts = new LinkedHashMap<>();
        for (Card card : cards) {
            counts.merge(card, 1, Integer::sum);
        }
        return counts;
    }

    private static ItemStack named(Material material, String name, NamedTextColor color) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> meta.displayName(plain(name, color)));
        return item;
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
