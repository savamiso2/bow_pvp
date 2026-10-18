package io.github.savamiso2.bowrounds.game;

import io.github.savamiso2.bowrounds.Settings;
import io.github.savamiso2.bowrounds.card.Card;
import io.github.savamiso2.bowrounds.card.PlayerStats;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** 1マッチ中のプレイヤーの状態。 */
public final class GamePlayer {

    private final UUID id;
    private final String name;
    private final List<Card> cards = new ArrayList<>();
    private PlayerStats stats;

    boolean alive;
    int points;
    int kills;
    int airJumpsLeft;
    boolean reloading;
    UUID lastDamager;
    int guardUntilTick;
    int guardReadyTick;
    List<Card> offer = List.of();
    boolean picked;

    GamePlayer(Player player, Settings settings) {
        this.id = player.getUniqueId();
        this.name = player.getName();
        this.stats = PlayerStats.compute(settings, cards);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Player player() {
        return Bukkit.getPlayer(id);
    }

    public List<Card> cards() {
        return cards;
    }

    public PlayerStats stats() {
        return stats;
    }

    /** 今この瞬間ガード中か。 */
    public boolean guarding() {
        return alive && Bukkit.getCurrentTick() <= guardUntilTick;
    }

    public boolean alive() {
        return alive;
    }

    public int points() {
        return points;
    }

    void addCard(Card card, Settings settings) {
        cards.add(card);
        stats = PlayerStats.compute(settings, cards);
    }

    long count(Card card) {
        return cards.stream().filter(c -> c == card).count();
    }
}
