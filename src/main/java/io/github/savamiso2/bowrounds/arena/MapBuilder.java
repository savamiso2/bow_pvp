package io.github.savamiso2.bowrounds.arena;

import io.github.savamiso2.bowrounds.BowRounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * 専用の空ワールドに、すぐ遊べるシンプルなマップ3種とロビーを建ててアリーナ登録する。
 * <ul>
 *   <li>pillars（2〜8人）：小さな石の床と柱。外は奈落</li>
 *   <li>islands（6〜24人）：浮島7つを細い橋でつないだマップ</li>
 *   <li>colosseum（16〜200人）：壁に囲まれた広い砂岩の闘技場</li>
 * </ul>
 */
public final class MapBuilder {

    public static final String WORLD_NAME = "bowrounds_arenas";
    private static final int Y = 64;

    private final BowRounds plugin;

    public MapBuilder(BowRounds plugin) {
        this.plugin = plugin;
    }

    /** ワールドを用意（なければ作成）する。 */
    public static World world() {
        World world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) {
            world = new WorldCreator(WORLD_NAME).generator(new VoidGenerator()).createWorld();
        }
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        return world;
    }

    /** 全マップとロビーを建てて登録する。建てたマップ名を返す。 */
    public List<String> buildAll() {
        World world = world();
        List<String> built = new ArrayList<>();
        built.add(register(buildPillars(new Location(world, 0.5, Y, 0.5))));
        built.add(register(buildIslands(new Location(world, 1000.5, Y, 0.5))));
        built.add(register(buildColosseum(new Location(world, 2000.5, Y, 0.5))));
        Location lobby = buildLobby(new Location(world, -1000.5, Y, 0.5));
        if (plugin.arenas().lobby() == null) {
            plugin.arenas().lobby(lobby);
        }
        return built;
    }

    private String register(Arena arena) {
        // 遮蔽物の中に埋まったスポーンは除く
        arena.spawns().removeIf(s -> !s.getBlock().isPassable() || !s.clone().add(0, 1, 0).getBlock().isPassable());
        plugin.arenas().put(arena);
        return arena.name();
    }

    // ---- pillars ----

    private Arena buildPillars(Location center) {
        World w = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        fill(w, cx - 20, Y - 2, cz - 20, cx + 20, Y + 12, cz + 20, Material.AIR);
        disc(w, cx, Y - 1, cz, 15, Material.SMOOTH_STONE);
        ring(w, cx, Y - 1, cz, 15, Material.STONE_BRICKS);
        // 柱（遮蔽物）
        int[][] pillars = {{-8, -8}, {8, -8}, {-8, 8}, {8, 8}, {0, -11}, {0, 11}, {-11, 0}, {11, 0}};
        for (int[] p : pillars) {
            fill(w, cx + p[0] - 1, Y, cz + p[1] - 1, cx + p[0] + 1, Y + 3, cz + p[1] + 1, Material.STONE_BRICKS);
        }
        // 中央のピラミッド（高台）
        fill(w, cx - 3, Y, cz - 3, cx + 3, Y, cz + 3, Material.POLISHED_ANDESITE);
        fill(w, cx - 2, Y + 1, cz - 2, cx + 2, Y + 1, cz + 2, Material.POLISHED_ANDESITE);
        fill(w, cx - 1, Y + 2, cz - 1, cx + 1, Y + 2, cz + 1, Material.CHISELED_STONE_BRICKS);

        Arena arena = new Arena("pillars", center.clone(), 40, 2, 8);
        circleSpawns(arena, center, 13.5, 12);
        return arena;
    }

    // ---- islands ----

    private Arena buildIslands(Location center) {
        World w = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        fill(w, cx - 40, Y - 6, cz - 40, cx + 40, Y + 14, cz + 40, Material.AIR);
        Arena arena = new Arena("islands", center.clone(), 70, 6, 24);

        island(w, cx, Y, cz, 7);
        spawnsOnIsland(arena, center, cx, cz, 4.5, 4);
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI * 2 * i / 6;
            int ix = cx + (int) Math.round(Math.cos(angle) * 22);
            int iz = cz + (int) Math.round(Math.sin(angle) * 22);
            int iy = Y + (i % 2 == 0 ? 0 : 3);
            island(w, ix, iy, iz, 5);
            bridge(w, cx, Y - 1, cz, ix, iy - 1, iz);
            spawnsOnIsland(arena, center, ix, iz, 3, 4, iy);
        }
        return arena;
    }

    private void island(World w, int cx, int y, int cz, int radius) {
        disc(w, cx, y - 1, cz, radius, Material.GRASS_BLOCK);
        disc(w, cx, y - 2, cz, radius - 1, Material.DIRT);
        disc(w, cx, y - 3, cz, radius - 2, Material.STONE);
        disc(w, cx, y - 4, cz, Math.max(1, radius - 4), Material.STONE);
        // 小さな木の代わりの遮蔽物
        fill(w, cx + 1, y, cz - 1, cx + 2, y + 1, cz - 1, Material.OAK_LEAVES);
    }

    /** 中心島から外の島への幅1の橋（ハーフブロック）。 */
    private void bridge(World w, int x1, int y1, int z1, int x2, int y2, int z2) {
        int steps = (int) Math.ceil(Math.hypot(x2 - x1, z2 - z1));
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            int x = (int) Math.round(x1 + (x2 - x1) * t);
            int z = (int) Math.round(z1 + (z2 - z1) * t);
            int y = (int) Math.round(y1 + (y2 - y1) * t);
            if (w.getBlockAt(x, y, z).getType().isAir()) {
                w.getBlockAt(x, y, z).setType(Material.SPRUCE_PLANKS, false);
            }
        }
    }

    private void spawnsOnIsland(Arena arena, Location mapCenter, int cx, int cz, double radius, int count) {
        spawnsOnIsland(arena, mapCenter, cx, cz, radius, count, Y);
    }

    private void spawnsOnIsland(Arena arena, Location mapCenter, int cx, int cz, double radius, int count, int y) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count + Math.PI / 4;
            Location spawn = new Location(mapCenter.getWorld(),
                    cx + 0.5 + Math.cos(angle) * radius, y, cz + 0.5 + Math.sin(angle) * radius);
            arena.spawns().add(faceTowards(spawn, mapCenter));
        }
    }

    // ---- colosseum ----

    private Arena buildColosseum(Location center) {
        World w = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int r = 40;
        fill(w, cx - r - 2, Y - 2, cz - r - 2, cx + r + 2, Y + 12, cz + r + 2, Material.AIR);
        fill(w, cx - r, Y - 1, cz - r, cx + r, Y - 1, cz + r, Material.SMOOTH_SANDSTONE);
        // 外壁
        for (int y = Y; y < Y + 6; y++) {
            Material wall = y == Y + 5 ? Material.CHISELED_SANDSTONE : Material.CUT_SANDSTONE;
            fill(w, cx - r, y, cz - r, cx + r, y, cz - r, wall);
            fill(w, cx - r, y, cz + r, cx + r, y, cz + r, wall);
            fill(w, cx - r, y, cz - r, cx - r, y, cz + r, wall);
            fill(w, cx + r, y, cz - r, cx + r, y, cz + r, wall);
        }
        // 遮蔽物：毎回同じ形になるよう固定シードで配置
        Random random = new Random(20261018L);
        for (int i = 0; i < 60; i++) {
            int x = cx + random.nextInt(2 * r - 8) - (r - 4);
            int z = cz + random.nextInt(2 * r - 8) - (r - 4);
            if (Math.abs(x - cx) < 5 && Math.abs(z - cz) < 5) {
                continue;
            }
            if (random.nextBoolean()) {
                boolean alongX = random.nextBoolean();
                fill(w, x, Y, z, x + (alongX ? 3 : 0), Y + 1, z + (alongX ? 0 : 3), Material.SANDSTONE_WALL);
            } else {
                fill(w, x, Y, z, x + 1, Y + 1, z + 1, Material.SANDSTONE);
            }
        }
        // 四隅の高台
        int[][] corners = {{-28, -28}, {28, -28}, {-28, 28}, {28, 28}};
        for (int[] c : corners) {
            fill(w, cx + c[0] - 3, Y, cz + c[1] - 3, cx + c[0] + 3, Y + 1, cz + c[1] + 3, Material.SANDSTONE);
            fill(w, cx + c[0] - 3, Y + 2, cz + c[1] - 3, cx + c[0] + 3, Y + 2, cz + c[1] - 3, Material.SANDSTONE_WALL);
            // 登り段（ハーフ → 1段＋ハーフ → 高台）
            w.getBlockAt(cx + c[0], Y, cz + c[1] + 5).setType(Material.SANDSTONE_SLAB, false);
            w.getBlockAt(cx + c[0], Y, cz + c[1] + 4).setType(Material.SANDSTONE, false);
            w.getBlockAt(cx + c[0], Y + 1, cz + c[1] + 4).setType(Material.SANDSTONE_SLAB, false);
        }
        // 中央の塔
        fill(w, cx - 2, Y, cz - 2, cx + 2, Y + 4, cz + 2, Material.CUT_SANDSTONE);
        fill(w, cx - 1, Y + 5, cz - 1, cx + 1, Y + 5, cz + 1, Material.GOLD_BLOCK);

        Arena arena = new Arena("colosseum", center.clone(), 90, 16, 200);
        circleSpawns(arena, center, 16, 12);
        circleSpawns(arena, center, 26, 20);
        circleSpawns(arena, center, 35, 28);
        return arena;
    }

    // ---- ロビー ----

    private Location buildLobby(Location center) {
        World w = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        fill(w, cx - 7, Y - 1, cz - 7, cx + 7, Y - 1, cz + 7, Material.WHITE_STAINED_GLASS);
        ring(w, cx, Y - 1, cz, 7, Material.SEA_LANTERN);
        for (int y = Y; y < Y + 2; y++) {
            fill(w, cx - 7, y, cz - 7, cx + 7, y, cz - 7, Material.GLASS);
            fill(w, cx - 7, y, cz + 7, cx + 7, y, cz + 7, Material.GLASS);
            fill(w, cx - 7, y, cz - 7, cx - 7, y, cz + 7, Material.GLASS);
            fill(w, cx + 7, y, cz - 7, cx + 7, y, cz + 7, Material.GLASS);
        }
        return center.clone();
    }

    // ---- 道具 ----

    private static void fill(World w, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    w.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    private static void disc(World w, int cx, int y, int cz, int radius, Material material) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius + radius) {
                    w.getBlockAt(cx + x, y, cz + z).setType(material, false);
                }
            }
        }
    }

    private static void ring(World w, int cx, int y, int cz, int radius, Material material) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int d = x * x + z * z;
                if (d <= radius * radius + radius && d > (radius - 1) * (radius - 1) + (radius - 1)) {
                    w.getBlockAt(cx + x, y, cz + z).setType(material, false);
                }
            }
        }
    }

    private static void circleSpawns(Arena arena, Location center, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            Location spawn = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            arena.spawns().add(faceTowards(spawn, center));
        }
    }

    private static Location faceTowards(Location from, Location target) {
        Location result = from.clone();
        result.setDirection(target.toVector().subtract(from.toVector()).setY(0));
        return result;
    }
}
