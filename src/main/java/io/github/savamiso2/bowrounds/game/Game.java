package io.github.savamiso2.bowrounds.game;

import io.github.savamiso2.bowrounds.BowRounds;
import io.github.savamiso2.bowrounds.Settings;
import io.github.savamiso2.bowrounds.arena.Arena;
import io.github.savamiso2.bowrounds.arena.MapBuilder;
import io.github.savamiso2.bowrounds.card.Card;
import io.github.savamiso2.bowrounds.card.PlayerStats;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * 1マッチ分の進行。ラウンド開始 → 戦闘 → 順位とポイント → 勝者以外がカード選択、を規定ラウンド数くり返す。
 */
public final class Game {

    private static final int ARROW_SLOT = 8;

    private final BowRounds plugin;
    private final Settings settings;
    private final GameManager manager;
    private final NamespacedKey speedKey;
    private final Map<UUID, GamePlayer> players = new LinkedHashMap<>();
    private final List<GamePlayer> eliminated = new ArrayList<>();
    private final Map<UUID, CardStage> stages = new LinkedHashMap<>();
    private final List<Block> pickFloor = new ArrayList<>();
    private final BossBar bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private final Scoreboard scoreboard;
    private final Objective sidebar;

    private GameState state = GameState.COUNTDOWN;
    private int round;
    private Arena arena;
    private BukkitTask task;
    private BukkitTask hoverTask;
    private boolean finishing;
    private int phaseSeconds;
    private double originalBorderSize;
    private Location originalBorderCenter;

    Game(BowRounds plugin, GameManager manager, Collection<Player> participants) {
        this.plugin = plugin;
        this.settings = plugin.settings();
        this.manager = manager;
        this.speedKey = new NamespacedKey(plugin, "card_speed");
        for (Player player : participants) {
            players.put(player.getUniqueId(), new GamePlayer(player, settings));
        }
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.sidebar = scoreboard.registerNewObjective("bowrounds", Criteria.DUMMY,
                Component.text("BowRounds", NamedTextColor.GOLD));
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public GameState state() {
        return state;
    }

    public Optional<GamePlayer> player(UUID id) {
        return Optional.ofNullable(players.get(id));
    }

    public Collection<GamePlayer> players() {
        return Collections.unmodifiableCollection(players.values());
    }

    BowRounds plugin() {
        return plugin;
    }

    void start() {
        for (GamePlayer gp : players.values()) {
            Player p = gp.player();
            p.setScoreboard(scoreboard);
            p.showBossBar(bossBar);
            sidebar.getScore(gp.name()).setScore(0);
        }
        nextRound();
    }

    // ---- ラウンド ----

    private void nextRound() {
        round++;
        if (round > settings.rounds()) {
            finish(true);
            return;
        }
        List<GamePlayer> online = online();
        if (online.size() < 2) {
            finish(true);
            return;
        }
        Arena next = plugin.arenas().pick(online.size(), arena).orElse(null);
        if (next == null) {
            broadcast(Component.text("使えるアリーナがありません。試合を終了します。", NamedTextColor.RED));
            finish(false);
            return;
        }
        restoreBorder();
        arena = next;
        eliminated.clear();
        if (arena.center().getWorld().getName().equals(MapBuilder.WORLD_NAME)) {
            arena.center().getWorld().setTime(6000); // 専用ワールドは常に昼
        }
        setupBorder();

        List<Location> spawns = new ArrayList<>(arena.spawns());
        Collections.shuffle(spawns);
        for (int i = 0; i < online.size(); i++) {
            GamePlayer gp = online.get(i);
            Location spawn = spawns.get(i % spawns.size()).clone();
            if (i >= spawns.size()) {
                // スポーン地点より人数が多いときは少しずらして重ならないようにする
                ThreadLocalRandom r = ThreadLocalRandom.current();
                spawn.add(r.nextDouble(-1.5, 1.5), 0, r.nextDouble(-1.5, 1.5));
            }
            preparePlayer(gp);
            gp.player().teleport(spawn);
        }

        state = GameState.COUNTDOWN;
        phaseSeconds = settings.countdownSeconds();
        bossBar.name(Component.text("ラウンド " + round + " / " + settings.rounds() + "  " + arena.name(), NamedTextColor.YELLOW));
        bossBar.progress(1f);
        startTimer(this::tickCountdown);
    }

    private void tickCountdown() {
        if (phaseSeconds <= 0) {
            state = GameState.PLAYING;
            phaseSeconds = 0;
            showTitle(Component.text("FIGHT!", NamedTextColor.RED), Component.empty());
            playSound(Sound.ENTITY_ENDER_DRAGON_GROWL);
            startTimer(this::tickPlaying);
            return;
        }
        showTitle(Component.text(phaseSeconds, NamedTextColor.GOLD),
                Component.text("ラウンド " + round, NamedTextColor.YELLOW));
        playSound(Sound.BLOCK_NOTE_BLOCK_HAT);
        phaseSeconds--;
    }

    private void tickPlaying() {
        phaseSeconds++;
        int limit = settings.roundTimeSeconds();
        bossBar.progress((float) Math.max(0, 1.0 - (double) phaseSeconds / limit));
        if (phaseSeconds == settings.borderShrinkAfterSeconds()) {
            arena.center().getWorld().getWorldBorder()
                    .setSize(settings.borderMinSize(), settings.borderShrinkDurationSeconds());
            broadcast(Component.text("ボーダーが縮み始めた！", NamedTextColor.RED));
        }
        if (phaseSeconds == limit) {
            for (GamePlayer gp : alivePlayers()) {
                gp.player().setGlowing(true);
            }
            bossBar.color(BossBar.Color.RED);
            broadcast(Component.text("サドンデス！全員が発光します", NamedTextColor.RED));
        }
    }

    void eliminate(GamePlayer victim, GamePlayer killer) {
        if (state != GameState.PLAYING || !victim.alive) {
            return;
        }
        victim.alive = false;
        eliminated.add(victim);
        Player p = victim.player();
        if (p != null) {
            p.setGlowing(false);
            p.setGameMode(GameMode.SPECTATOR);
            p.showTitle(Title.title(Component.text("脱落", NamedTextColor.RED), Component.text("観戦モード")));
        }
        if (killer != null && killer != victim) {
            killer.kills++;
            addPoints(killer, settings.killPoints());
            broadcast(Component.text(killer.name() + " が " + victim.name() + " を倒した", NamedTextColor.GRAY));
            Player kp = killer.player();
            if (kp != null) {
                kp.playSound(kp.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            }
        } else {
            broadcast(Component.text(victim.name() + " が脱落した", NamedTextColor.GRAY));
        }
        if (alivePlayers().size() <= 1) {
            endRound();
        }
    }

    private void endRound() {
        state = GameState.ROUND_END;
        cancelTimer();

        // 順位：生き残り → 後に脱落した順
        List<GamePlayer> ranking = new ArrayList<>(alivePlayers());
        List<GamePlayer> reversed = new ArrayList<>(eliminated);
        Collections.reverse(reversed);
        ranking.addAll(reversed);
        for (int i = 0; i < ranking.size(); i++) {
            addPoints(ranking.get(i), settings.placementPoints(i + 1));
        }

        GamePlayer winner = ranking.isEmpty() ? null : ranking.getFirst();
        Component name = Component.text(winner == null ? "なし" : winner.name(), NamedTextColor.GOLD);
        showTitle(name.append(Component.text(" の勝ち！", NamedTextColor.YELLOW)), Component.text("ラウンド " + round, NamedTextColor.GRAY));
        playSound(Sound.UI_TOAST_CHALLENGE_COMPLETE);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == GameState.ROUND_END) {
                startCardPick(ranking);
            }
        }, 60L);
    }

    // ---- カード選択 ----

    private void startCardPick(List<GamePlayer> ranking) {
        if (round >= settings.rounds()) {
            nextRound(); // 最終ラウンドの後はカード不要
            return;
        }
        state = GameState.CARD_PICK;
        // 観戦モードだとGUIを操作できないので、全員アリーナ上空の足場へ集める（カードが壁や柱に埋まらないように）。
        // カードに集中できるよう他の人は隠す
        Location spot = buildPickFloor();
        List<GamePlayer> online = online();
        for (GamePlayer gp : online) {
            Player p = gp.player();
            p.setGameMode(GameMode.ADVENTURE);
            p.setGlowing(false);
            p.setFallDistance(0f);
            Location at = spot.clone();
            at.setYaw(p.getLocation().getYaw());
            at.setPitch(0f);
            p.teleport(at);
            for (GamePlayer other : online) {
                if (other != gp) {
                    p.hidePlayer(plugin, other.player());
                }
            }
        }
        int size = ranking.size();
        int bottomFrom = (int) Math.ceil(size * (1.0 - settings.bottomBonusRatio()));
        for (int i = 0; i < size; i++) {
            GamePlayer gp = ranking.get(i);
            gp.picked = i == 0; // 勝者はカードなし
            gp.offer = List.of();
            if (i == 0) {
                continue;
            }
            boolean bottom = i >= bottomFrom;
            gp.offer = Card.draw(settings.offerSize() + (bottom ? 1 : 0), bottom);
            if (settings.cardStage()) {
                CardStage stage = new CardStage(plugin, gp);
                stages.put(gp.id(), stage);
                stage.show();
            } else {
                openMenu(gp);
            }
        }
        if (!stages.isEmpty()) {
            hoverTask = Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> stages.values().forEach(CardStage::updateHover), 1L, 2L);
        }
        phaseSeconds = settings.cardPickSeconds();
        bossBar.name(Component.text("カード選択中…", NamedTextColor.LIGHT_PURPLE));
        bossBar.color(BossBar.Color.PURPLE);
        startTimer(this::tickCardPick);
    }

    /** 3Dカード選択中のプレイヤーがクリックした：見ているカードに決定。 */
    void stageClick(GamePlayer gp) {
        CardStage stage = stages.get(gp.id());
        if (stage != null && stage.hoveredCard() != null) {
            pick(gp, stage.hoveredCard());
        }
    }

    boolean choosingOnStage(GamePlayer gp) {
        return state == GameState.CARD_PICK && !gp.picked && stages.containsKey(gp.id());
    }

    void openMenu(GamePlayer gp) {
        Player p = gp.player();
        if (p != null && !gp.picked && state == GameState.CARD_PICK && !stages.containsKey(gp.id())) {
            p.openInventory(new CardMenu(gp).getInventory());
        }
    }

    void pick(GamePlayer gp, Card card) {
        if (state != GameState.CARD_PICK || gp.picked || !gp.offer.contains(card)) {
            return;
        }
        gp.picked = true;
        gp.addCard(card, settings);
        Player p = gp.player();
        CardStage stage = stages.get(gp.id());
        if (stage != null) {
            stage.select(card);
        }
        if (p != null) {
            p.closeInventory();
            p.sendMessage(Component.text("「" + card.displayName() + "」を手に入れた", card.rarity().color()));
            if (stage == null) {
                p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
            }
        }
        if (!finishing && players.values().stream().allMatch(g -> g.picked || g.player() == null)) {
            // 決定演出を見せてから次のラウンドへ
            cancelTimer();
            Bukkit.getScheduler().runTaskLater(plugin, this::finishCardPick, 20L);
        }
    }

    private void tickCardPick() {
        if (settings.cardPickSeconds() <= 0) {
            // 制限時間なし：全員が選ぶまで待つ。ボスバーは残り人数
            long total = players.values().stream().filter(g -> !g.offer.isEmpty() && g.player() != null).count();
            long waiting = players.values().stream().filter(g -> !g.picked && g.player() != null).count();
            bossBar.name(Component.text("カード選択中… あと" + waiting + "人", NamedTextColor.LIGHT_PURPLE));
            bossBar.progress(total == 0 ? 1f : (float) (total - waiting) / total);
            return;
        }
        bossBar.progress((float) Math.max(0, (double) phaseSeconds / settings.cardPickSeconds()));
        if (phaseSeconds <= 0) {
            finishCardPick();
            return;
        }
        phaseSeconds--;
    }

    /** 管理者用：選んでいない人の分は自動で決めて、カード選択を終える。 */
    public boolean skipCardPick() {
        if (state != GameState.CARD_PICK || finishing) {
            return false;
        }
        cancelTimer();
        finishCardPick();
        return true;
    }

    private void finishCardPick() {
        if (state != GameState.CARD_PICK) {
            return;
        }
        finishing = true;
        for (GamePlayer gp : players.values()) {
            if (!gp.picked && !gp.offer.isEmpty()) {
                // 時間切れは自動で選ぶ
                pick(gp, gp.offer.get(ThreadLocalRandom.current().nextInt(gp.offer.size())));
            }
        }
        finishing = false;
        closeStages();
        bossBar.color(BossBar.Color.YELLOW);
        cancelTimer();
        nextRound();
    }

    // ---- 終了 ----

    void finish(boolean announce) {
        if (state == GameState.FINISHED) {
            return;
        }
        state = GameState.FINISHED;
        cancelTimer();
        closeStages();
        restoreBorder();
        if (announce) {
            List<GamePlayer> ranking = players.values().stream()
                    .sorted(Comparator.comparingInt(GamePlayer::points).reversed()).toList();
            broadcast(Component.text("===== 最終結果 =====", NamedTextColor.GOLD));
            for (int i = 0; i < Math.min(5, ranking.size()); i++) {
                GamePlayer gp = ranking.get(i);
                broadcast(Component.text((i + 1) + "位 " + gp.name() + "  " + gp.points + "pt（" + gp.kills + "キル）",
                        i == 0 ? NamedTextColor.YELLOW : NamedTextColor.WHITE));
            }
            if (!ranking.isEmpty()) {
                showTitle(Component.text(ranking.getFirst().name() + " の優勝！", NamedTextColor.GOLD), Component.empty());
            }
        }
        for (GamePlayer gp : players.values()) {
            Player p = gp.player();
            if (p != null) {
                resetPlayer(p);
            }
        }
        manager.onFinish(this);
    }

    void removePlayer(Player player) {
        GamePlayer gp = players.get(player.getUniqueId());
        if (gp == null) {
            return;
        }
        if (state == GameState.PLAYING && gp.alive) {
            eliminate(gp, null);
        }
        gp.alive = false;
        gp.picked = true;
        CardStage stage = stages.remove(gp.id());
        if (stage != null) {
            stage.remove();
        }
        resetPlayer(player);
        if (state == GameState.FINISHED) {
            return;
        }
        if (online().size() < 2) {
            finish(true);
        } else if (state == GameState.CARD_PICK
                && players.values().stream().allMatch(g -> g.picked || g.player() == null)) {
            finishCardPick();
        }
    }

    // ---- プレイヤーの準備 ----

    private void preparePlayer(GamePlayer gp) {
        Player p = gp.player();
        PlayerStats stats = gp.stats();
        gp.alive = true;
        gp.lastDamager = null;
        gp.reloading = false;
        gp.airJumpsLeft = stats.airJumps;
        gp.guardUntilTick = 0;
        gp.guardReadyTick = 0;

        p.closeInventory();
        p.setGameMode(GameMode.ADVENTURE);
        p.getInventory().clear();
        p.clearActivePotionEffects();
        p.setGlowing(false);
        p.setFireTicks(0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setArrowsInBody(0);
        p.setLevel(0);
        p.setExp(1f);
        p.setAllowFlight(stats.airJumps > 0);
        p.setFlying(false);

        AttributeInstance health = p.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(stats.maxHealth);
        }
        p.setHealth(stats.maxHealth);
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(speedKey);
            if (stats.moveSpeedBonus != 0) {
                speed.addModifier(new AttributeModifier(speedKey, stats.moveSpeedBonus,
                        AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
            }
        }

        ItemStack bow = ItemStack.of(Material.BOW);
        bow.editMeta(meta -> {
            meta.setUnbreakable(true);
            meta.displayName(Component.text("弓", NamedTextColor.GOLD));
        });
        p.getInventory().setItem(0, bow);
        giveArrows(gp);
    }

    void giveArrows(GamePlayer gp) {
        Player p = gp.player();
        if (p != null) {
            p.getInventory().setItem(ARROW_SLOT, ItemStack.of(Material.ARROW, gp.stats().magazine));
        }
    }

    /** 矢を撃った直後に呼ぶ。残弾表示と、空ならリロード。 */
    void afterShot(GamePlayer gp) {
        Player p = gp.player();
        if (p == null || !gp.alive) {
            return;
        }
        int ammo = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.ARROW) {
                ammo += item.getAmount();
            }
        }
        if (ammo > 0) {
            p.sendActionBar(Component.text("残弾 " + ammo + " / " + gp.stats().magazine, NamedTextColor.WHITE));
            return;
        }
        if (gp.reloading) {
            return;
        }
        gp.reloading = true;
        int reloadTicks = gp.stats().reloadTicks(settings);
        int roundAtStart = round;
        new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                Player player = gp.player();
                if (player == null || !gp.alive || round != roundAtStart || state != GameState.PLAYING) {
                    gp.reloading = false;
                    cancel();
                    return;
                }
                elapsed += 2;
                if (elapsed >= reloadTicks) {
                    gp.reloading = false;
                    giveArrows(gp);
                    player.sendActionBar(Component.text("リロード完了", NamedTextColor.GREEN));
                    player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1f, 1f);
                    cancel();
                    return;
                }
                int filled = (int) Math.round(10.0 * elapsed / reloadTicks);
                player.sendActionBar(Component.text("リロード中 ", NamedTextColor.YELLOW)
                        .append(Component.text("■".repeat(filled), NamedTextColor.GREEN))
                        .append(Component.text("■".repeat(10 - filled), NamedTextColor.DARK_GRAY)));
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private void resetPlayer(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            p.showPlayer(plugin, other);
            other.showPlayer(plugin, p);
        }
        p.closeInventory();
        p.getInventory().clear();
        p.clearActivePotionEffects();
        p.setGlowing(false);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setExp(0f);
        p.setGameMode(GameMode.SURVIVAL);
        AttributeInstance health = p.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getDefaultValue());
            p.setHealth(health.getValue());
        }
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(speedKey);
        }
        p.hideBossBar(bossBar);
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        Location lobby = plugin.arenas().lobby();
        p.teleport(lobby != null ? lobby : p.getWorld().getSpawnLocation());
    }

    // ---- ボーダー ----

    private void setupBorder() {
        WorldBorder border = arena.center().getWorld().getWorldBorder();
        originalBorderSize = border.getSize();
        originalBorderCenter = border.getCenter();
        border.setCenter(arena.center());
        border.setSize(arena.borderSize());
        border.setDamageBuffer(0);
        border.setDamageAmount(1.0);
    }

    private void restoreBorder() {
        if (arena == null || originalBorderCenter == null) {
            return;
        }
        WorldBorder border = arena.center().getWorld().getWorldBorder();
        border.setCenter(originalBorderCenter);
        border.setSize(originalBorderSize);
        originalBorderCenter = null;
    }

    // ---- 小物 ----

    private void addPoints(GamePlayer gp, int amount) {
        if (amount == 0) {
            return;
        }
        gp.points += amount;
        sidebar.getScore(gp.name()).setScore(gp.points);
    }

    List<GamePlayer> alivePlayers() {
        return players.values().stream().filter(g -> g.alive && g.player() != null).toList();
    }

    private List<GamePlayer> online() {
        return players.values().stream().filter(g -> g.player() != null).toList();
    }

    /** カード選択用に、アリーナ上空の何もない所へバリアの足場を置き、立つ位置を返す。 */
    private Location buildPickFloor() {
        clearPickFloor();
        Location center = arena.center();
        World world = center.getWorld();
        int x = center.getBlockX();
        int z = center.getBlockZ();
        int highest = center.getBlockY();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                highest = Math.max(highest, world.getHighestBlockYAt(x + dx, z + dz));
            }
        }
        int y = Math.min(highest + 20, world.getMaxHeight() - 6);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = world.getBlockAt(x + dx, y, z + dz);
                if (block.getType().isAir()) {
                    block.setType(Material.BARRIER, false);
                    pickFloor.add(block);
                }
            }
        }
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    private void clearPickFloor() {
        pickFloor.forEach(b -> b.setType(Material.AIR, false));
        pickFloor.clear();
    }

    private void closeStages() {
        clearPickFloor();
        if (hoverTask != null) {
            hoverTask.cancel();
            hoverTask = null;
        }
        stages.values().forEach(CardStage::remove);
        stages.clear();
        List<GamePlayer> online = online();
        for (GamePlayer gp : online) {
            for (GamePlayer other : online) {
                if (other != gp) {
                    gp.player().showPlayer(plugin, other.player());
                }
            }
        }
    }

    private void startTimer(Runnable tick) {
        cancelTimer();
        task = Bukkit.getScheduler().runTaskTimer(plugin, tick, 0L, 20L);
    }

    private void cancelTimer() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void broadcast(Component message) {
        for (GamePlayer gp : players.values()) {
            Player p = gp.player();
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }

    private void showTitle(Component title, Component subtitle) {
        Title t = Title.title(title, subtitle,
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300)));
        for (GamePlayer gp : players.values()) {
            Player p = gp.player();
            if (p != null) {
                p.showTitle(t);
            }
        }
    }

    private void playSound(Sound sound) {
        for (GamePlayer gp : players.values()) {
            Player p = gp.player();
            if (p != null) {
                p.playSound(p.getLocation(), sound, 1f, 1f);
            }
        }
    }

    static void poison(Player victim, int ticks) {
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, ticks, 0));
    }
}
