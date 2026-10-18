package io.github.savamiso2.bowrounds.game;

import io.github.savamiso2.bowrounds.card.Card;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

/** 試合中プレイヤーの被ダメージ・脱落・移動・インベントリまわり。 */
public final class GameListener implements Listener {

    private final GameManager games;

    public GameListener(GameManager games) {
        this.games = games;
    }

    // ---- ダメージと脱落 ----

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || games.gamePlayer(victim).isEmpty()) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true); // 近接は無効
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        games.gamePlayer(victim).ifPresent(gp -> {
            Game game = games.game().orElseThrow();
            if (game.state() != GameState.PLAYING || !gp.alive) {
                event.setCancelled(true);
                return;
            }
            if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player attacker
                    && !attacker.equals(victim)) {
                gp.lastDamager = attacker.getUniqueId(); // 爆発など
            }
            if (event.getFinalDamage() >= victim.getHealth()) {
                event.setCancelled(true);
                GamePlayer killer = gp.lastDamager == null ? null : game.player(gp.lastDamager).orElse(null);
                game.eliminate(gp, killer);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player p && games.gamePlayer(p).isPresent()) {
            EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
            if (reason == EntityRegainHealthEvent.RegainReason.SATIATED
                    || reason == EntityRegainHealthEvent.RegainReason.REGEN) {
                event.setCancelled(true); // 自然回復なし（吸血カードだけが回復手段）
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && games.gamePlayer(p).isPresent()) {
            event.setCancelled(true);
        }
    }

    // ---- 移動 ----

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        games.gamePlayer(player).ifPresent(gp -> {
            Game game = games.game().orElseThrow();
            Location from = event.getFrom();
            Location to = event.getTo();
            if (game.state() == GameState.COUNTDOWN && from.distanceSquared(to) > 0.0001) {
                Location frozen = from.clone();
                frozen.setYaw(to.getYaw());
                frozen.setPitch(to.getPitch());
                event.setTo(frozen);
                return;
            }
            if (game.state() != GameState.PLAYING || !gp.alive) {
                return;
            }
            if (to.getY() < to.getWorld().getMinHeight() - 5) {
                GamePlayer killer = gp.lastDamager == null ? null : game.player(gp.lastDamager).orElse(null);
                game.eliminate(gp, killer);
                return;
            }
            int maxJumps = gp.stats().airJumps;
            if (maxJumps > 0 && gp.airJumpsLeft < maxJumps && ((Entity) player).isOnGround()) {
                gp.airJumpsLeft = maxJumps;
                player.setAllowFlight(true);
            }
        });
    }

    /** 空中でジャンプキー2回押し → 空中ジャンプ（ジェットカード）。 */
    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        games.gamePlayer(player).ifPresent(gp -> {
            event.setCancelled(true);
            player.setFlying(false);
            Game game = games.game().orElseThrow();
            if (game.state() != GameState.PLAYING || !gp.alive || gp.airJumpsLeft <= 0) {
                return;
            }
            gp.airJumpsLeft--;
            Vector boost = player.getLocation().getDirection().setY(0).normalize().multiply(0.5).setY(0.75);
            player.setVelocity(boost);
            if (gp.airJumpsLeft <= 0) {
                player.setAllowFlight(false);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        games.leave(event.getPlayer());
    }

    // ---- インベントリ ----

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof CardMenu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            Card card = menu.cardAt(event.getSlot());
            if (card != null) {
                games.gamePlayer(player).ifPresent(gp -> games.game().orElseThrow().pick(gp, card));
            }
            return;
        }
        if (games.gamePlayer(player).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player p
                && (event.getInventory().getHolder() instanceof CardMenu || games.gamePlayer(p).isPresent())) {
            event.setCancelled(true);
        }
    }

    /** 選ばずに閉じたら、選択時間中は開き直す。 */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CardMenu) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        games.gamePlayer(player).ifPresent(gp -> {
            Game game = games.game().orElseThrow();
            if (!gp.picked && game.state() == GameState.CARD_PICK) {
                Bukkit.getScheduler().runTask(game.plugin(), () -> game.openMenu(gp));
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (games.gamePlayer(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (games.gamePlayer(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && games.gamePlayer(p).isPresent()) {
            event.setCancelled(true);
        }
    }
}
