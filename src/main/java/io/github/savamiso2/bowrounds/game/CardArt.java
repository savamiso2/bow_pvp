package io.github.savamiso2.bowrounds.game;

import io.github.savamiso2.bowrounds.BowRounds;
import io.github.savamiso2.bowrounds.card.Card;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;

/**
 * カードの絵柄（リソースパック）。参加時にパックを配り、読み込めた人にだけカードの見た目を差し替える。
 * パックを断った人・読み込みに失敗した人には、これまでどおりバニラのアイテムで表示する。
 */
public final class CardArt implements Listener {

    private static final Set<UUID> LOADED = ConcurrentHashMap.newKeySet();
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("bowrounds:cards".getBytes(StandardCharsets.UTF_8));

    private final BowRounds plugin;
    private final boolean required;
    private volatile ResourcePackInfo pack;

    public CardArt(BowRounds plugin) {
        this.plugin = plugin;
        this.required = plugin.getConfig().getBoolean("resource-pack.required", false);
        if (plugin.getConfig().getBoolean("resource-pack.enabled", true)) {
            String url = plugin.getConfig().getString("resource-pack.url", "");
            if (!url.isBlank()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> prepare(url));
            }
        }
    }

    /** そのプレイヤーがカードの絵柄を見られるか。 */
    public static boolean hasPack(Player viewer) {
        return viewer != null && LOADED.contains(viewer.getUniqueId());
    }

    /** 見る人がパックを読み込めていれば、アイテムの見た目をカードの絵柄にする。 */
    static void apply(ItemStack item, Card card, Player viewer) {
        if (hasPack(viewer)) {
            item.setData(DataComponentTypes.ITEM_MODEL, Key.key("bowrounds", "card/" + card.name().toLowerCase(Locale.ROOT)));
        }
    }

    /** パックをダウンロードして SHA-1 を求める（キャッシュの更新判定に使われる）。 */
    private void prepare(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                plugin.getLogger().warning("リソースパックを取得できませんでした（HTTP " + response.statusCode() + "）: " + url);
                return;
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(response.body()));
            pack = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url), hash);
            plugin.getLogger().info("カードのリソースパックを用意しました（sha1 " + hash + "）");
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::send));
        } catch (Exception e) {
            plugin.getLogger().warning("リソースパックを取得できませんでした: " + e.getMessage());
        }
    }

    private void send(Player player) {
        ResourcePackInfo info = pack;
        if (info == null || LOADED.contains(player.getUniqueId())) {
            return;
        }
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(required)
                .prompt(Fx.mm("<gold>カードの絵柄を表示するリソースパックです")));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> LOADED.add(id);
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> LOADED.remove(id);
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        LOADED.remove(event.getPlayer().getUniqueId());
    }
}
