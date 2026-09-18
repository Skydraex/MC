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

    public WorldBuilder(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public Map<String, int[]> getMineBounds() { return mineBounds; }
    public Map<Location, SellSignListener.SellSignData> getSellSigns() { return sellSigns; }

    public void buildAll() {
        buildHub();
        for (RankMineData.Def def : RankMineData.RANKS.values()) {
            if (def.rank.equals("FREE")) continue;
            buildMine(def);
        }
        plugin.getLogger().info("World build complete.");
    }

    private void buildHub() {
        // Simple flat spawn platform at origin, well clear of the mine row (mines start at x=0 going +x,
        // so the hub sits at negative X).
        int hx1 = -40, hx2 = -5, hz1 = -20, hz2 = 20, hy = 94;
        for (int x = hx1; x <= hx2; x++) {
            for (int z = hz1; z <= hz2; z++) {
                world.getBlockAt(x, hy, z).setType(Material.SMOOTH_STONE);
                world.getBlockAt(x, hy + 1, z).setType(Material.AIR);
                world.getBlockAt(x, hy + 2, z).setType(Material.AIR);
            }
        }
        world.setSpawnLocation(hx1 + 5, hy + 1, 0);
    }

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

        // Carve a 3-wide, 3-tall entrance in the x1 wall so players can actually walk in.
        int midZ = (d.z1 + d.z2) / 2;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = 1; dy <= 3; dy++) {
                world.getBlockAt(d.x1, d.y1 + dy, midZ + dz).setType(Material.AIR);
            }
        }

        mineBounds.put(d.rank, new int[]{d.x1, d.y1, d.z1, d.x2, d.y2, d.z2});

        // Sell sign at the mine entrance (just outside the wall, facing in).
        int signX = d.x1 - 1;
        int signY = d.y1 + 1;
        int signZ = (d.z1 + d.z2) / 2;
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
