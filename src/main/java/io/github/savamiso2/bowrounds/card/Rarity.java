package io.github.savamiso2.bowrounds.card;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

public enum Rarity {
    COMMON("コモン", TextColor.color(0x2fe0a8), Material.PAPER, 70),
    RARE("レア", TextColor.color(0x3d8bff), Material.MAP, 25),
    EPIC("エピック", TextColor.color(0xff2e7e), Material.ENCHANTED_BOOK, 5);

    private final String label;
    private final TextColor color;
    private final Material icon;
    private final int weight;

    Rarity(String label, TextColor color, Material icon, int weight) {
        this.label = label;
        this.color = color;
        this.icon = icon;
        this.weight = weight;
    }

    public String label() {
        return label;
    }

    public TextColor color() {
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
