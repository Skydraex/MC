package com.lukeprison.prison;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Crate definitions and loot tables.
 *
 * Design rule: standard rewards are useful but never game-breaking. The only genuinely powerful
 * items sit behind a 0.1% jackpot roll, so they read as a rare prize rather than an expectation.
 */
public class CrateData {

    private static final Random RANDOM = new Random();

    /** A single possible reward. */
    public static class Reward {
        public final String display;
        public final int weight;
        public final RewardKind kind;
        public final Material material;
        public final int amount;
        public final long money;
        public final long tokens;

        private Reward(String display, int weight, RewardKind kind, Material material,
                       int amount, long money, long tokens) {
            this.display = display;
            this.weight = weight;
            this.kind = kind;
            this.material = material;
            this.amount = amount;
            this.money = money;
            this.tokens = tokens;
        }

        public static Reward money(String display, int weight, long amount) {
            return new Reward(display, weight, RewardKind.MONEY, null, 0, amount, 0);
        }

        public static Reward tokens(String display, int weight, long amount) {
            return new Reward(display, weight, RewardKind.TOKENS, null, 0, 0, amount);
        }

        public static Reward item(String display, int weight, Material mat, int amount) {
            return new Reward(display, weight, RewardKind.ITEM, mat, amount, 0, 0);
        }

        /**
         * A named, enchanted piece of gear.
         *
         * Item rewards used to be nothing but a Material and a count, so every "Fishing Rod" or
         * "Iron Pickaxe" a crate gave out was a plain vanilla one. The only enchanted item in the
         * whole system was the 0.1% jackpot, which left a cliff: common junk, then nothing at all
         * until a legendary. These fill the middle.
         */
        public static Reward gear(String display, int weight, Material mat, String name,
                                  Map<Enchantment, Integer> enchants, String rarity) {
            Reward r = new Reward(display, weight, RewardKind.ITEM, mat, 1, 0, 0);
            r.gearName = name;
            r.enchants = enchants;
            r.rarity = rarity;
            return r;
        }

        /** Set only for gear rewards. */
        public String gearName, rarity;
        public Map<Enchantment, Integer> enchants;

        public boolean isGear() { return enchants != null && !enchants.isEmpty(); }
    }

    public enum RewardKind { MONEY, TOKENS, ITEM, JACKPOT }

    public static class Crate {
        public final String id, display;
        public final Material keyMaterial;
        public final String keyName;
        public final List<Reward> rewards = new ArrayList<>();

        public Crate(String id, String display, Material keyMaterial, String keyName) {
            this.id = id;
            this.display = display;
            this.keyMaterial = keyMaterial;
            this.keyName = keyName;
        }
    }

    /** Chance (in tenths of a percent) that a crate opening rolls a jackpot instead. */
    public static final int JACKPOT_CHANCE_PER_THOUSAND = 1; // 0.1%

    public static final Map<String, Crate> CRATES = new LinkedHashMap<>();

    // Weights are out of 1000 per crate, so a weight reads directly as a tenth of a percent.
    //
    // Every crate runs the same ladder, and each rung is clearly worse than the one above it:
    //
    //   ~7%    a modest enchanted tool, better than vanilla but nothing special
    //   ~2%    a good one, roughly what a player could buy with tokens
    //   ~0.5%  a very good one, the best thing you can get without a jackpot
    //   0.1%   the jackpot, and it beats the tier below it on every single stat
    //
    // Nothing here is game-breaking on purpose: the strongest non-jackpot pickaxe tops out at
    // Efficiency 5 / Fortune 2, which is around what the token shop sells, and it has no Mending.
    // Only the jackpot goes above that, and only modestly.

    private static Map<Enchantment, Integer> ench(Object... pairs) {
        Map<Enchantment, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((Enchantment) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    static {
        Crate miner = new Crate("miner", "Miner Crate", Material.TRIPWIRE_HOOK, "§b§lMiner Key");
        miner.rewards.add(Reward.money("$2,500", 300, 2500));
        miner.rewards.add(Reward.tokens("150 Tokens", 250, 150));
        miner.rewards.add(Reward.money("$7,500", 200, 7500));
        miner.rewards.add(Reward.tokens("400 Tokens", 120, 400));
        miner.rewards.add(Reward.item("Golden Apple x2", 50, Material.GOLDEN_APPLE, 2));
        miner.rewards.add(Reward.gear("Sturdy Pickaxe", 60, Material.IRON_PICKAXE,
                "§f§lSturdy Pickaxe",
                ench(Enchantment.EFFICIENCY, 2, Enchantment.UNBREAKING, 2), "§7Uncommon"));
        miner.rewards.add(Reward.gear("Miner's Pickaxe", 18, Material.DIAMOND_PICKAXE,
                "§b§lMiner's Pickaxe",
                ench(Enchantment.EFFICIENCY, 3, Enchantment.FORTUNE, 1,
                        Enchantment.UNBREAKING, 3), "§bRare"));
        miner.rewards.add(Reward.gear("Foreman's Pickaxe", 2, Material.DIAMOND_PICKAXE,
                "§5§lForeman's Pickaxe",
                ench(Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 2,
                        Enchantment.UNBREAKING, 4), "§5Very rare"));
        CRATES.put(miner.id, miner);

        Crate angler = new Crate("angler", "Angler Crate", Material.TRIPWIRE_HOOK, "§a§lAngler Key");
        angler.rewards.add(Reward.money("$4,000", 300, 4000));
        angler.rewards.add(Reward.tokens("250 Tokens", 250, 250));
        angler.rewards.add(Reward.money("$12,000", 180, 12000));
        angler.rewards.add(Reward.tokens("600 Tokens", 120, 600));
        angler.rewards.add(Reward.item("Cooked Salmon x16", 50, Material.COOKED_SALMON, 16));
        angler.rewards.add(Reward.gear("Reinforced Rod", 70, Material.FISHING_ROD,
                "§f§lReinforced Rod",
                ench(Enchantment.LURE, 2, Enchantment.UNBREAKING, 2), "§7Uncommon"));
        angler.rewards.add(Reward.gear("Angler's Rod", 25, Material.FISHING_ROD,
                "§b§lAngler's Rod",
                ench(Enchantment.LURE, 3, Enchantment.LUCK_OF_THE_SEA, 2,
                        Enchantment.UNBREAKING, 3), "§bRare"));
        angler.rewards.add(Reward.gear("Deepwater Rod", 5, Material.FISHING_ROD,
                "§5§lDeepwater Rod",
                ench(Enchantment.LURE, 4, Enchantment.LUCK_OF_THE_SEA, 3,
                        Enchantment.UNBREAKING, 4), "§5Very rare"));
        CRATES.put(angler.id, angler);

        Crate vote = new Crate("vote", "Vote Crate", Material.TRIPWIRE_HOOK, "§e§lVote Key");
        vote.rewards.add(Reward.money("$5,000", 300, 5000));
        vote.rewards.add(Reward.tokens("300 Tokens", 260, 300));
        vote.rewards.add(Reward.money("$15,000", 180, 15000));
        vote.rewards.add(Reward.tokens("800 Tokens", 140, 800));
        vote.rewards.add(Reward.item("Diamond x3", 70, Material.DIAMOND, 3));
        vote.rewards.add(Reward.gear("Sturdy Pickaxe", 25, Material.IRON_PICKAXE,
                "§f§lSturdy Pickaxe",
                ench(Enchantment.EFFICIENCY, 2, Enchantment.UNBREAKING, 2), "§7Uncommon"));
        vote.rewards.add(Reward.gear("Reinforced Rod", 20, Material.FISHING_ROD,
                "§f§lReinforced Rod",
                ench(Enchantment.LURE, 2, Enchantment.UNBREAKING, 2), "§7Uncommon"));
        vote.rewards.add(Reward.gear("Miner's Pickaxe", 5, Material.DIAMOND_PICKAXE,
                "§b§lMiner's Pickaxe",
                ench(Enchantment.EFFICIENCY, 3, Enchantment.FORTUNE, 1,
                        Enchantment.UNBREAKING, 3), "§bRare"));
        CRATES.put(vote.id, vote);
    }

    /**
     * Turns a reward into the actual item handed over, enchantments and all. The granting code
     * used to build a bare ItemStack from the Material, which silently dropped everything that
     * made a piece of gear worth winning.
     */
    public static ItemStack buildItem(Reward r) {
        ItemStack it = new ItemStack(r.material, Math.max(1, r.amount));
        if (!r.isGear()) return it;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setDisplayName(r.gearName);
        for (Map.Entry<Enchantment, Integer> e : r.enchants.entrySet()) {
            meta.addEnchant(e.getKey(), e.getValue(), true);
        }
        List<String> lore = new ArrayList<>();
        lore.add(r.rarity == null ? "§7Crate reward" : r.rarity);
        lore.add("§8Won from a crate.");
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    /** Weighted pick from a crate's standard table. */
    public static Reward roll(Crate crate) {
        int total = 0;
        for (Reward r : crate.rewards) total += r.weight;
        int roll = RANDOM.nextInt(total);
        int acc = 0;
        for (Reward r : crate.rewards) {
            acc += r.weight;
            if (roll < acc) return r;
        }
        return crate.rewards.get(0);
    }

    public static boolean rollJackpot() {
        return RANDOM.nextInt(1000) < JACKPOT_CHANCE_PER_THOUSAND;
    }

    /**
     * The 0.1% prizes. Strong, but bounded: enchant levels sit modestly above vanilla rather
     * than at the absurd numbers OP servers use, so a winner is powerful, not untouchable.
     */
    public static ItemStack buildJackpot() {
        boolean pickaxe = RANDOM.nextBoolean();
        if (pickaxe) {
            ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
            ItemMeta meta = pick.getItemMeta();
            meta.setDisplayName("§6§lWarden's Pickaxe");
            meta.addEnchant(Enchantment.EFFICIENCY, 7, true);
            meta.addEnchant(Enchantment.FORTUNE, 5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 5, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            List<String> lore = new ArrayList<>();
            lore.add("§7A legendary find.");
            lore.add("§8Won from a crate — 0.1% chance.");
            meta.setLore(lore);
            pick.setItemMeta(meta);
            return pick;
        }
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        meta.setDisplayName("§6§lLeviathan Rod");
        meta.addEnchant(Enchantment.LURE, 5, true);
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 5, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        List<String> lore = new ArrayList<>();
        lore.add("§7A legendary find.");
        lore.add("§8Won from a crate — 0.1% chance.");
        meta.setLore(lore);
        rod.setItemMeta(meta);
        return rod;
    }

    /** Builds a physical key item for a crate. */
    public static ItemStack buildKey(Crate crate, int amount) {
        ItemStack key = new ItemStack(crate.keyMaterial, amount);
        ItemMeta meta = key.getItemMeta();
        meta.setDisplayName(crate.keyName);
        List<String> lore = new ArrayList<>();
        lore.add("§7Opens the §f" + crate.display);
        lore.add("§8Right-click the crate at spawn.");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(PrisonPlugin.get(), "crate_key"),
                org.bukkit.persistence.PersistentDataType.STRING, crate.id);
        key.setItemMeta(meta);
        return key;
    }

    /** Reads which crate a key belongs to, or null if the item isn't a key. */
    public static String keyTypeOf(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(PrisonPlugin.get(), "crate_key"),
                org.bukkit.persistence.PersistentDataType.STRING);
    }
}
