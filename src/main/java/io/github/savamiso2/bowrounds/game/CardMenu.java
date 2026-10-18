package io.github.savamiso2.bowrounds.game;

import io.github.savamiso2.bowrounds.card.Card;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** 勝者以外に出すカード選択画面。スロット番号からカードを引く。 */
public final class CardMenu implements InventoryHolder {

    private static final int[][] LAYOUTS = {
            {13},
            {11, 15},
            {11, 13, 15},
            {10, 12, 14, 16},
            {9, 11, 13, 15, 17},
    };

    private final Inventory inventory;
    private final List<Card> offer;
    private final int[] slots;

    CardMenu(GamePlayer gamePlayer) {
        this.offer = gamePlayer.offer;
        this.slots = LAYOUTS[Math.min(offer.size(), LAYOUTS.length) - 1];
        this.inventory = Bukkit.createInventory(this, 27,
                Component.text("強化カードを1枚選ぶ", NamedTextColor.DARK_PURPLE));

        ItemStack filler = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        for (int i = 0; i < slots.length; i++) {
            inventory.setItem(slots[i], icon(offer.get(i), gamePlayer.count(offer.get(i)), gamePlayer.player()));
        }
    }

    private static ItemStack icon(Card card, long owned, Player viewer) {
        ItemStack item = ItemStack.of(card.rarity().icon());
        CardArt.apply(item, card, viewer);
        item.editMeta(meta -> {
            meta.displayName(plain(card.displayName(), card.rarity().color()).decoration(TextDecoration.BOLD, true));
            List<Component> lore = new ArrayList<>();
            lore.add(plain("[" + card.rarity().label() + "]", card.rarity().color()));
            lore.add(plain("+ " + card.effect(), NamedTextColor.GREEN));
            if (card.drawback() != null) {
                lore.add(plain("- " + card.drawback(), NamedTextColor.RED));
            }
            if (owned > 0) {
                lore.add(plain("所持: " + owned + "枚", NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            lore.add(plain("クリックで選ぶ", NamedTextColor.YELLOW));
            meta.lore(lore);
        });
        return item;
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /** クリックされたスロットのカード。カード以外なら null。 */
    Card cardAt(int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return offer.get(i);
            }
        }
        return null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
