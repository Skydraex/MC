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

/** Custom pickaxe enchants, bought with tokens and stored on the item itself. */
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

        /** Cost climbs steeply per level so max levels are a long-term goal, not a quick buy. */
        public long costFor(int nextLevel) {
            return Math.round(baseCost * Math.pow(nextLevel, 1.8));
        }
    }

    public static final Map<String, EnchantDef> ENCHANTS = new LinkedHashMap<>();

    static {
        // Deliberately conservative. Nothing here should trivialise early play: the strong
        // enchants are gated behind a minimum rank so they arrive as a reward for progress,
        // not as a shortcut past it.
        ENCHANTS.put("efficiency", new EnchantDef("efficiency", "Efficiency",
                "Mine blocks faster", 5, 80, Material.GOLDEN_PICKAXE, "A"));
        ENCHANTS.put("fortune", new EnchantDef("fortune", "Fortune",
                "Small chance of bonus drops", 4, 220, Material.DIAMOND, "C"));
        ENCHANTS.put("haste", new EnchantDef("haste", "Haste",
                "Mining speed boost while held", 3, 400, Material.BEACON, "F"));
        ENCHANTS.put("autosmelt", new EnchantDef("autosmelt", "Auto-Smelt",
                "Ores smelt into ingots automatically", 1, 900, Material.FURNACE, "H"));
        ENCHANTS.put("tokenator", new EnchantDef("tokenator", "Tokenator",
                "Small chance of bonus tokens", 5, 600, Material.SUNFLOWER, "J"));
        ENCHANTS.put("explosive", new EnchantDef("explosive", "Explosive",
                "Chance to blast a small area", 5, 1800, Material.TNT, "M"));
        ENCHANTS.put("jackhammer", new EnchantDef("jackhammer", "Jackhammer",
                "Rare chance to clear a patch", 3, 5000, Material.NETHERITE_PICKAXE, "R"));
        ENCHANTS.put("fly", new EnchantDef("fly", "Flight",
                "Fly inside mines only", 1, 12000, Material.FEATHER, "V"));
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
        return getLevel(hand, "haste");
    }
}
