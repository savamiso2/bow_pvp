package io.github.savamiso2.bowrounds.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.savamiso2.bowrounds.BowRounds;
import io.github.savamiso2.bowrounds.arena.Arena;
import io.github.savamiso2.bowrounds.arena.MapBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /bowrounds（/br）
 * <pre>
 * join | leave | status
 * start | stop | setlobby                         （管理者）
 * arena setup                                    （専用ワールドに既製マップ3種とロビーを建てる）
 * arena create &lt;名前&gt; &lt;最大人数&gt;              （今いる場所が中心）
 * arena addspawn &lt;名前&gt;                         （今いる場所をスポーンに追加）
 * arena border &lt;名前&gt; &lt;サイズ&gt;
 * arena players &lt;名前&gt; &lt;最小&gt; &lt;最大&gt;
 * arena delete &lt;名前&gt; | arena list
 * </pre>
 */
public final class BowRoundsCommand {

    private static final String ADMIN = "bowrounds.admin";

    private final BowRounds plugin;

    public BowRoundsCommand(BowRounds plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bowrounds")
                .then(Commands.literal("join")
                        .requires(s -> s.getSender().hasPermission("bowrounds.play"))
                        .executes(playerOnly((p, ctx) -> plugin.games().join(p))))
                .then(Commands.literal("leave")
                        .executes(playerOnly((p, ctx) -> plugin.games().leave(p))))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("start").requires(this::admin).executes(ctx -> {
                    Optional<String> error = plugin.games().start();
                    error.ifPresentOrElse(
                            msg -> ctx.getSource().getSender().sendMessage(Component.text(msg, NamedTextColor.RED)),
                            () -> ctx.getSource().getSender().sendMessage(Component.text("試合を開始しました", NamedTextColor.GREEN)));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("stop").requires(this::admin).executes(ctx -> {
                    plugin.games().stop();
                    ctx.getSource().getSender().sendMessage(Component.text("試合を停止しました", NamedTextColor.YELLOW));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("setlobby").requires(this::admin)
                        .executes(playerOnly((p, ctx) -> {
                            plugin.arenas().lobby(p.getLocation());
                            p.sendMessage(Component.text("ロビーを設定しました", NamedTextColor.GREEN));
                        })))
                .then(arena());

        commands.register(root.build(), "ROUNDS風ローグライク弓PvP", List.of("br"));
    }

    private LiteralArgumentBuilder<CommandSourceStack> arena() {
        return Commands.literal("arena").requires(this::admin)
                .then(Commands.literal("list").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (plugin.arenas().all().isEmpty()) {
                        sender.sendMessage(Component.text("アリーナはまだありません", NamedTextColor.GRAY));
                    }
                    for (Arena a : plugin.arenas().all()) {
                        sender.sendMessage(Component.text(a.name() + "  " + a.minPlayers() + "〜" + a.maxPlayers()
                                + "人  スポーン" + a.spawns().size() + "  ボーダー" + (int) a.borderSize(), NamedTextColor.WHITE));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("setup").executes(ctx -> {
                    List<String> built = new MapBuilder(plugin).buildAll();
                    ctx.getSource().getSender().sendMessage(Component.text(
                            "マップを作成しました: " + String.join(", ", built) + "（ワールド " + MapBuilder.WORLD_NAME + "）",
                            NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("max", IntegerArgumentType.integer(2, 1000))
                                        .executes(playerOnly((p, ctx) -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            int max = IntegerArgumentType.getInteger(ctx, "max");
                                            plugin.arenas().create(name, p.getLocation(), max);
                                            p.sendMessage(Component.text("アリーナ " + name + " を作成（中心＝現在地）。"
                                                    + "/br arena addspawn " + name + " でスポーンを追加してください", NamedTextColor.GREEN));
                                        })))))
                .then(Commands.literal("addspawn")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(playerOnly((p, ctx) -> withArena(p, ctx, a -> {
                                    a.spawns().add(p.getLocation());
                                    plugin.arenas().save();
                                    p.sendMessage(Component.text("スポーンを追加（" + a.spawns().size() + "個目）", NamedTextColor.GREEN));
                                })))))
                .then(Commands.literal("border")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("size", DoubleArgumentType.doubleArg(4, 10000))
                                        .executes(playerOnly((p, ctx) -> withArena(p, ctx, a -> {
                                            a.borderSize(DoubleArgumentType.getDouble(ctx, "size"));
                                            plugin.arenas().save();
                                            p.sendMessage(Component.text("ボーダーサイズを変更しました", NamedTextColor.GREEN));
                                        }))))))
                .then(Commands.literal("players")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("min", IntegerArgumentType.integer(2, 1000))
                                        .then(Commands.argument("max", IntegerArgumentType.integer(2, 1000))
                                                .executes(playerOnly((p, ctx) -> withArena(p, ctx, a -> {
                                                    a.playerRange(IntegerArgumentType.getInteger(ctx, "min"),
                                                            IntegerArgumentType.getInteger(ctx, "max"));
                                                    plugin.arenas().save();
                                                    p.sendMessage(Component.text("人数範囲を変更しました", NamedTextColor.GREEN));
                                                })))))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    boolean removed = plugin.arenas().remove(name);
                                    ctx.getSource().getSender().sendMessage(removed
                                            ? Component.text("削除しました", NamedTextColor.YELLOW)
                                            : Component.text("アリーナが見つかりません", NamedTextColor.RED));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(Component.text("参加待ち: " + plugin.games().queueSize() + "人", NamedTextColor.WHITE));
        plugin.games().game().ifPresentOrElse(
                g -> sender.sendMessage(Component.text("試合中（" + g.state() + "）参加者 " + g.players().size() + "人", NamedTextColor.YELLOW)),
                () -> sender.sendMessage(Component.text("試合は行われていません", NamedTextColor.GRAY)));
        return Command.SINGLE_SUCCESS;
    }

    private boolean admin(CommandSourceStack source) {
        return source.getSender().hasPermission(ADMIN);
    }

    private void withArena(Player p, CommandContext<CommandSourceStack> ctx, Consumer<Arena> action) {
        String name = StringArgumentType.getString(ctx, "name");
        plugin.arenas().get(name).ifPresentOrElse(action,
                () -> p.sendMessage(Component.text("アリーナ " + name + " が見つかりません", NamedTextColor.RED)));
    }

    private static Command<CommandSourceStack> playerOnly(BiConsumer<Player, CommandContext<CommandSourceStack>> action) {
        return ctx -> {
            if (ctx.getSource().getExecutor() instanceof Player player) {
                action.accept(player, ctx);
            } else {
                ctx.getSource().getSender().sendMessage(Component.text("プレイヤーのみ実行できます", NamedTextColor.RED));
            }
            return Command.SINGLE_SUCCESS;
        };
    }
}
