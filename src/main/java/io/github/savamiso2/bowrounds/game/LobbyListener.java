package io.github.savamiso2.bowrounds.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** 専用サーバー向け：入ったらロビーへ。試合に出ていない人はロビーで無敵・満腹、ブロックも触れない。 */
public final class LobbyListener implements Listener {

    private final GameManager games;

    public LobbyListener(GameManager games) {
        this.games = games;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        games.sendToLobby(player);
        if (games.game().isPresent()) {
            player.sendMessage(Component.text("試合中です。次の試合から参加できます", NamedTextColor.YELLOW));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (games.gamePlayer(event.getPlayer()).isEmpty()) {
            games.sendToLobby(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && games.gamePlayer(p).isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && games.gamePlayer(p).isEmpty()) {
            event.setCancelled(true);
        }
    }

    /** ロビーから落ちたら戻す。 */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getTo().getY() < event.getTo().getWorld().getMinHeight() + 10
                && games.gamePlayer(player).isEmpty()) {
            games.sendToLobby(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!event.getPlayer().hasPermission("bowrounds.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!event.getPlayer().hasPermission("bowrounds.admin")) {
            event.setCancelled(true);
        }
    }
}
