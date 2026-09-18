package com.lukeprison.prison;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The core mining loop: awards tokens, counts blocks, applies custom enchant effects
 * (Explosive, Jackhammer, Fortune, Auto-Smelt), and handles auto-sell.
 */
public class MiningListener implements Listener {

    private final PrisonPlugin plugin;
    private final Map<String, int[]> mineBounds;
    private final Random random = new Random();

    public MiningListener(PrisonPlugin plugin, Map<String, int[]> mineBounds) {
        this.plugin = plugin;
        this.mineBounds = mineBounds;
    }

    private String mineAt(int x, int y, int z) {
        for (Map.Entry<String, int[]> e : mineBounds.entrySet()) {
            int[] b = e.getValue();
            if (x > b[0] && x < b[3] && y > b[1] && y < b[4] && z > b[2] && z < b[5]) return e.getKey();
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();
        String mine = mineAt(block.getX(), block.getY(), block.getZ());
        if (mine == null) return;

        ItemStack pick = p.getInventory().getItemInMainHand();
        RankMineData.Def def = RankMineData.RANKS.get(mine);
        if (def == null) return;

        // Base: this block.
        int blocksBroken = 1;
        Map<Material, Integer> haul = new HashMap<>();
        addHaul(haul, block.getType(), 1);

        // Explosive: blast a 3x3x3 around the broken block.
        int explosive = PickaxeEnchants.getLevel(pick, "explosive");
        if (explosive > 0 && random.nextInt(100) < explosive * 5) {
            blocksBroken += blastArea(block.getLocation(), 1, mine, haul);
        }

        // Jackhammer: clear an entire Y-layer of the mine.
        int jack = PickaxeEnchants.getLevel(pick, "jackhammer");
        if (jack > 0 && random.nextInt(1000) < jack * 5) {
            blocksBroken += clearLayer(block.getY(), mine, haul);
            p.sendMessage("§6Jackhammer! §7Cleared a full layer.");
        }

        // Fortune: multiply the haul.
        int fortune = PickaxeEnchants.getLevel(pick, "fortune");
        if (fortune > 0) {
            int multiplier = 1 + random.nextInt(fortune + 1);
            if (multiplier > 1) {
                for (Map.Entry<Material, Integer> entry : haul.entrySet()) {
                    entry.setValue(entry.getValue() * multiplier);
                }
            }
        }

        // Auto-smelt: convert ores to their smelted form.
        if (PickaxeEnchants.getLevel(pick, "autosmelt") > 0) {
            Map<Material, Integer> smelted = new HashMap<>();
            for (Map.Entry<Material, Integer> entry : haul.entrySet()) {
                smelted.merge(smelt(entry.getKey()), entry.getValue(), Integer::sum);
            }
            haul.clear();
            haul.putAll(smelted);
        }

        // Block counter + tokens.
        for (int i = 0; i < blocksBroken; i++) plugin.ranks().addBlockMined(p);

        long tokensEarned = blocksBroken / 10; // baseline drip
        int tokenator = PickaxeEnchants.getLevel(pick, "tokenator");
        if (tokenator > 0 && random.nextInt(100) < tokenator * 4) {
            tokensEarned += 1 + random.nextInt(tokenator);
        }
        if (tokensEarned > 0) plugin.ranks().addTokens(p, tokensEarned);

        // Payout: auto-sell straight to cash, or drop items into the inventory.
        if (plugin.ranks().isAutoSell(p)) {
            double total = 0;
            for (Map.Entry<Material, Integer> entry : haul.entrySet()) {
                total += priceOf(def, entry.getKey()) * entry.getValue();
            }
            if (total > 0) plugin.economy().depositPlayer(p, total);
        } else {
            for (Map.Entry<Material, Integer> entry : haul.entrySet()) {
                if (entry.getKey() == Material.AIR) continue;
                p.getInventory().addItem(new ItemStack(entry.getKey(), entry.getValue()));
            }
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
    }

    private void addHaul(Map<Material, Integer> haul, Material mat, int amount) {
        if (mat == Material.AIR || mat == Material.BEDROCK) return;
        haul.merge(mat, amount, Integer::sum);
    }

    /** Breaks a cube of the given radius, staying inside the mine's bounds. */
    private int blastArea(Location center, int radius, String mine, Map<Material, Integer> haul) {
        int broken = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block b = center.getWorld().getBlockAt(
                            center.getBlockX() + dx, center.getBlockY() + dy, center.getBlockZ() + dz);
                    if (b.getType() == Material.AIR || b.getType() == Material.BEDROCK) continue;
                    if (!mine.equals(mineAt(b.getX(), b.getY(), b.getZ()))) continue;
                    addHaul(haul, b.getType(), 1);
                    b.setType(Material.AIR);
                    broken++;
                }
            }
        }
        return broken;
    }

    /** Clears one full Y-layer of the given mine. */
    private int clearLayer(int y, String mine, Map<Material, Integer> haul) {
        int[] b = mineBounds.get(mine);
        if (b == null) return 0;
        int broken = 0;
        for (int x = b[0] + 1; x < b[3]; x++) {
            for (int z = b[2] + 1; z < b[5]; z++) {
                Block blk = plugin.getServer().getWorlds().get(0).getBlockAt(x, y, z);
                if (blk.getType() == Material.AIR || blk.getType() == Material.BEDROCK) continue;
                addHaul(haul, blk.getType(), 1);
                blk.setType(Material.AIR);
                broken++;
            }
        }
        return broken;
    }

    private double priceOf(RankMineData.Def def, Material mat) {
        if (mat.name().equals(def.filler)) return def.fillerPrice;
        if (mat.name().equals(def.common)) return def.commonPrice;
        if (mat.name().equals(def.rare)) return def.rarePrice;
        // Smelted forms sell at the ore's rate.
        if (mat == Material.IRON_INGOT) return def.commonPrice;
        if (mat == Material.GOLD_INGOT) return def.commonPrice;
        return def.fillerPrice; // fallback so nothing is worthless
    }

    private Material smelt(Material ore) {
        return switch (ore) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.GOLD_INGOT;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            default -> ore;
        };
    }

    /** Keeps Haste applied while a player holds an enchanted pickaxe. */
    public void applyHaste(Player p) {
        int lvl = PickaxeEnchants.hasteLevel(p);
        if (lvl > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 200, lvl - 1, true, false, false));
        }
    }
}
