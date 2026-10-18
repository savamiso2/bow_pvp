package io.github.savamiso2.bowrounds.card;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum Rarity {
    COMMON("コモン", NamedTextColor.WHITE, Material.PAPER, 70),
    RARE("レア", NamedTextColor.AQUA, Material.MAP, 25),
    EPIC("エピック", NamedTextColor.LIGHT_PURPLE, Material.ENCHANTED_BOOK, 5);

    private final String label;
    private final NamedTextColor color;
    private final Material icon;
    private final int weight;

    Rarity(String label, NamedTextColor color, Material icon, int weight) {
        this.label = label;
        this.color = color;
        this.icon = icon;
        this.weight = weight;
    }

    public String label() {
        return label;
    }

    public NamedTextColor color() {
        return color;
    }

    public Material icon() {
        return icon;
    }

    /** 抽選の重み。boosted（下位プレイヤー）ならレア以上が出やすくなる。 */
    public int weight(boolean boosted) {
        if (!boosted) {
            return weight;
        }
        return switch (this) {
            case COMMON -> 45;
            case RARE -> 40;
            case EPIC -> 15;
        };
    }
}
