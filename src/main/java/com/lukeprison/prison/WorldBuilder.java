package com.lukeprison.prison;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Builds the entire physical server: every mine's shell + ore fill,
 * a simple hub platform, and a sell sign at each mine entrance.
 * Runs once on first server start. Re-running skips mines that
 * already exist (checked via a marker block) so it's safe on restarts.
 */
public class WorldBuilder {

    private final Plugin plugin;
    private final World world;
    private final Map<String, int[]> mineBounds = new LinkedHashMap<>(); // rank -> [x1,y1,z1,x2,y2,z2]
    private final Map<Location, SellSignListener.SellSignData> sellSigns = new HashMap<>();
    private Location hubSpawn;

    public WorldBuilder(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public Map<String, int[]> getMineBounds() { return mineBounds; }
    public Map<Location, SellSignListener.SellSignData> getSellSigns() { return sellSigns; }
    public Location getHubSpawn() { return hubSpawn; }

    public void buildAll() {
        buildHub();
        for (RankMineData.Def def : RankMineData.RANKS.values()) {
            if (def.rank.equals("FREE")) continue;
            buildMine(def);
        }
        connectMineWalkways();
        setupWorldBorder();
        plugin.getLogger().info("World build complete.");
    }

    /** Bridges the gap between each consecutive mine's far door and the next mine's near door. */
    private void connectMineWalkways() {
        List<RankMineData.Def> ordered = new ArrayList<>();
        for (RankMineData.Def def : RankMineData.RANKS.values()) {
            if (!def.rank.equals("FREE")) ordered.add(def);
        }
        for (int i = 0; i < ordered.size() - 1; i++) {
            RankMineData.Def a = ordered.get(i);
            RankMineData.Def b = ordered.get(i + 1);
            int z = a.z1 + ENTRANCE_Z;
            for (int x = a.x2 + 1; x < b.x1; x++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.getBlockAt(x, a.y1, z + dz).setType(Material.SMOOTH_STONE);
                }
            }
        }
    }

    private void buildHub() {
        // Enclosed room, not just an open platform: floor, 4 walls, ceiling, one opening toward the mines,
        // plus ceiling lighting so nothing spawns on it at night.
        int hx1 = -40, hx2 = -5, hz1 = -20, hz2 = 20, hy = 94;
        int wallTop = hy + 6;
        for (int x = hx1; x <= hx2; x++) {
            for (int z = hz1; z <= hz2; z++) {
                world.getBlockAt(x, hy, z).setType(Material.SMOOTH_STONE);
                for (int y = hy + 1; y < wallTop; y++) {
                    boolean edge = x == hx1 || x == hx2 || z == hz1 || z == hz2;
                    world.getBlockAt(x, y, z).setType(edge ? Material.GRAY_CONCRETE : Material.AIR);
                }
                world.getBlockAt(x, wallTop, z).setType(Material.GRAY_CONCRETE);
            }
        }
        // Ceiling light strip.
        for (int x = hx1 + 2; x <= hx2 - 2; x += 6) {
            for (int z = hz1 + 2; z <= hz2 - 2; z += 6) {
                world.getBlockAt(x, wallTop - 1, z).setType(Material.SEA_LANTERN);
            }
        }
        // Opening toward the mines (positive X side) — aligned with every mine's entrance (see buildMine).
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = 1; dy <= 3; dy++) {
                world.getBlockAt(hx2, hy + dy, ENTRANCE_Z + dz).setType(Material.AIR);
            }
        }
        // Bridge the gap between the hub exit and Mine A's wall so there's guaranteed floor underfoot.
        RankMineData.Def mineA = RankMineData.RANKS.get("A");
        if (mineA != null) {
            for (int x = hx2 + 1; x < mineA.x1; x++) {
                for (int z = ENTRANCE_Z - 2; z <= ENTRANCE_Z + 2; z++) {
                    world.getBlockAt(x, mineA.y1, z).setType(Material.SMOOTH_STONE);
                }
            }
        }
        hubSpawn = new Location(world, hx1 + 5.5, hy + 1, 0.5);
        world.setSpawnLocation(hx1 + 5, hy + 1, 0);
    }

    private static final int ENTRANCE_Z = 3; // shared Z-alignment for the hub exit and every mine's entrance

    private void buildMine(RankMineData.Def d) {
        // Skip if already built (marker block check at a fixed corner).
        Block marker = world.getBlockAt(d.x1, d.y1, d.z1);
        if (marker.getType() == Material.BEDROCK) return;

        Material filler = safeMaterial(d.filler);
        Material common = safeMaterial(d.common);
        Material rare = safeMaterial(d.rare);

        // Shell: bedrock walls/floor/ceiling so nothing leaks out.
        for (int x = d.x1; x <= d.x2; x++) {
            for (int z = d.z1; z <= d.z2; z++) {
                for (int y = d.y1; y <= d.y2; y++) {
                    boolean edge = x == d.x1 || x == d.x2 || z == d.z1 || z == d.z2 || y == d.y1 || y == d.y2;
                    Block b = world.getBlockAt(x, y, z);
                    if (edge) {
                        b.setType(Material.BEDROCK);
                    } else {
                        b.setType(pickOre(filler, common, rare));
                    }
                }
            }
        }

        // Carve a 3-wide, 3-tall entrance in the x1 wall, aligned with the hub's exit (ENTRANCE_Z)
        // so every mine's door lines up on the same walkway instead of drifting with mine size.
        int entranceZ = d.z1 + ENTRANCE_Z;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = 1; dy <= 3; dy++) {
                world.getBlockAt(d.x1, d.y1 + dy, entranceZ + dz).setType(Material.AIR);
                world.getBlockAt(d.x2, d.y1 + dy, entranceZ + dz).setType(Material.AIR); // matching door on the far wall, toward the next mine
            }
        }

        placeMineLights(d);

        mineBounds.put(d.rank, new int[]{d.x1, d.y1, d.z1, d.x2, d.y2, d.z2});

        // Sell sign right beside the entrance (same Z alignment), facing in.
        int signX = d.x1 - 1;
        int signY = d.y1 + 1;
        int signZ = entranceZ;
        Block signBlock = world.getBlockAt(signX, signY, signZ);
        signBlock.setType(Material.OAK_SIGN);
        if (signBlock.getState() instanceof Sign sign) {
            sign.getSide(Side.FRONT).setLine(0, "[Sell]");
            sign.getSide(Side.FRONT).setLine(1, "Mine " + d.rank + " ores");
            sign.getSide(Side.FRONT).setLine(2, "Right-click");
            sign.getSide(Side.FRONT).setLine(3, "to sell held");
            sign.update(true);
        }
        Map<Material, Double> prices = new HashMap<>();
        prices.put(filler, d.fillerPrice);
        prices.put(common, d.commonPrice);
        prices.put(rare, d.rarePrice);
        sellSigns.put(signBlock.getLocation(), new SellSignListener.SellSignData(d.rank, prices));
    }

    /** Weighted pick: 70% filler, 25% common, 5% rare — deterministic-ish via simple RNG. */
    private Material pickOre(Material filler, Material common, Material rare) {
        double r = Math.random() * 100.0;
        if (r < 70) return filler;
        if (r < 95) return common;
        return rare;
    }

    /** Embeds glowstone in the ceiling every 8 blocks so mines are lit and don't spawn mobs. */
    private void placeMineLights(RankMineData.Def d) {
        for (int x = d.x1 + 4; x < d.x2; x += 8) {
            for (int z = d.z1 + 4; z < d.z2; z += 8) {
                world.getBlockAt(x, d.y2 - 1, z).setType(Material.GLOWSTONE);
            }
        }
    }

    /** Confines the whole server to the built area so players can't wander into raw unbuilt terrain. */
    private void setupWorldBorder() {
        int minX = -45, maxX = -45, minZ = -25, maxZ = 25; // hub extents with a little buffer
        for (int[] b : mineBounds.values()) {
            minX = Math.min(minX, b[0]);
            maxX = Math.max(maxX, b[3]);
            minZ = Math.min(minZ, b[2]);
            maxZ = Math.max(maxZ, b[5]);
        }
        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double size = Math.max(maxX - minX, maxZ - minZ) + 40;
        world.getWorldBorder().setCenter(centerX, centerZ);
        world.getWorldBorder().setSize(size);
    }

    private Material safeMaterial(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Material.STONE;
        }
    }

    /** Called by MineResetTask to refill a mine's interior once it's mostly emptied out. */
    public void resetMine(String rank) {
        int[] b = mineBounds.get(rank);
        if (b == null) return;
        RankMineData.Def d = RankMineData.RANKS.get(rank);
        Material filler = safeMaterial(d.filler);
        Material common = safeMaterial(d.common);
        Material rare = safeMaterial(d.rare);
        for (int x = b[0] + 1; x < b[3]; x++) {
            for (int z = b[2] + 1; z < b[5]; z++) {
                for (int y = b[1] + 1; y < b[4]; y++) {
                    world.getBlockAt(x, y, z).setType(pickOre(filler, common, rare));
                }
            }
        }
        placeMineLights(d);
    }

    /** Fraction of non-air blocks remaining inside a mine (sampled, not exhaustive, to stay cheap). */
    public double percentRemaining(String rank) {
        int[] b = mineBounds.get(rank);
        if (b == null) return 100;
        int total = 0, filled = 0;
        int step = Math.max(1, (b[3] - b[0]) / 20);
        for (int x = b[0] + 1; x < b[3]; x += step) {
            for (int z = b[2] + 1; z < b[5]; z += step) {
                for (int y = b[1] + 1; y < b[4]; y += 2) {
                    total++;
                    if (world.getBlockAt(x, y, z).getType() != Material.AIR) filled++;
                }
            }
        }
        return total == 0 ? 100 : (100.0 * filled / total);
    }
}
