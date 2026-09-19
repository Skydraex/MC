package com.lukeprison.prison;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom pickaxe enchants, applied at the prison's enchanter and stored on the item itself.
 *
 * Bought with MONEY. There used to be a second currency, tokens, earned by a drip from
 * mining and spent only here; with one currency the same purchase is legible against
 * everything else a player could spend on, and there is no separate balance to explain.
 */
public class PickaxeEnchants {

    public static class EnchantDef {
        public final String id, display, description;
        public final int maxLevel;
        public final long baseCost;
        public final Material icon;
        /** Minimum prison rank before this enchant can be bought at all. */
        public final String requiredRank;

        public EnchantDef(String id, String display, String description, int maxLevel,
                          long baseCost, Material icon, String requiredRank) {
            this.id = id;
            this.display = display;
            this.description = description;
            this.maxLevel = maxLevel;
            this.baseCost = baseCost;
            this.icon = icon;
            this.requiredRank = requiredRank;
        }

        /**
         * Cost climbs steeply per level so max levels are a long-term goal, not a quick buy.
         * baseCost is in dollars; the old token prices were scaled by MONEY_PER_TOKEN when
         * the second currency was removed, so the relative cost of each enchant is unchanged.
         */
        public long costFor(int nextLevel) {
            return Math.round(baseCost * MONEY_PER_TOKEN * Math.pow(nextLevel, 1.8));
        }
    }

    /** What one of the old tokens was worth, used to convert the prices across. */
    private static final long MONEY_PER_TOKEN = 150;

    public static final Map<String, EnchantDef> ENCHANTS = new LinkedHashMap<>();

    static {
        // Deliberately conservative, and ordinary. Every one of these is a vanilla idea:
        // swing faster, drop a bit more, hold a haste effect. Nothing multiplies a swing
        // into a 3x3x3 and nothing smelts for you, because those two are what turn a prison
        // server into an OP prison server - they delete the carry-it-back-and-sell-it half
        // of the loop that the whole game is built on.
        //
        // Strong enchants are gated behind a minimum rank so they arrive as a reward for
        // progress rather than a shortcut past it.
        ENCHANTS.put("efficiency", new EnchantDef("efficiency", "Efficiency",
                "Mine blocks faster", 5, 80, Material.GOLDEN_PICKAXE, "A"));
        ENCHANTS.put("fortune", new EnchantDef("fortune", "Fortune",
                "Small chance of bonus drops", 4, 220, Material.DIAMOND, "C"));
        ENCHANTS.put("haste", new EnchantDef("haste", "Haste",
                "Mining speed boost while held", 3, 400, Material.BEACON, "F"));
        ENCHANTS.put("efficiency2", new EnchantDef("efficiency2", "Deep Efficiency",
                "Further mining speed, late game only", 3, 4000, Material.NETHERITE_PICKAXE, "R"));
    }

    private static NamespacedKey key(String enchantId) {
        return new NamespacedKey(PrisonPlugin.get(), "ench_" + enchantId);
    }

    public static boolean isPickaxe(ItemStack item) {
        if (item == null) return false;
        String n = item.getType().name();
        return n.endsWith("_PICKAXE");
    }

    public static int getLevel(ItemStack pick, String enchantId) {
        if (!isPickaxe(pick) || pick.getItemMeta() == null) return 0;
        PersistentDataContainer pdc = pick.getItemMeta().getPersistentDataContainer();
        Integer lvl = pdc.get(key(enchantId), PersistentDataType.INTEGER);
        return lvl == null ? 0 : lvl;
    }

    public static void setLevel(ItemStack pick, String enchantId, int level) {
        if (!isPickaxe(pick)) return;
        ItemMeta meta = pick.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key(enchantId), PersistentDataType.INTEGER, level);
        pick.setItemMeta(meta);
        refreshLore(pick);
    }

    /** Rewrites the pickaxe's lore so enchants and block count are visible in-game. */
    public static void refreshLore(ItemStack pick) {
        if (!isPickaxe(pick)) return;
        ItemMeta meta = pick.getItemMeta();
        if (meta == null) return;

        List<String> lore = new ArrayList<>();
        lore.add("§7Prison Pickaxe");
        lore.add("");
        boolean any = false;
        for (EnchantDef def : ENCHANTS.values()) {
            int lvl = getLevel(pick, def.id);
            if (lvl > 0) {
                lore.add("§b" + def.display + " §f" + lvl);
                any = true;
            }
        }
        if (!any) lore.add("§8No enchants yet — use /enchant");

        meta.setLore(lore);
        pick.setItemMeta(meta);
    }

    /** Applies the Haste effect level if the held pickaxe has that enchant. */
    public static int hasteLevel(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        // Deep Efficiency contributes a further Haste tier on top of the base enchant.
        return getLevel(hand, "haste") + getLevel(hand, "efficiency2");
    }
}
