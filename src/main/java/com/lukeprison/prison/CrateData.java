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

    // The gear ladder.
    //
    // Each rung has ONE weight, used by every crate that contains it. A player reads the
    // percentage and expects it to mean something; "Miner's Pickaxe, 0.50%" in one crate
    // and 1.80% in another is not a rarity, it is a coincidence.
    //
    // Weights are out of 1000, so a weight reads directly as a tenth of a percent:
    //
    //   120   12.0%   Uncommon    a real upgrade on a plain tool
    //    35    3.5%   Rare        better than an average enchanting table roll
    //     5    0.5%   Very rare   beats ANYTHING vanilla enchanting can produce
    //     1    0.1%   Jackpot     beats the rung below it on every stat
    //
    // The 0.5% rung is the one that has to justify itself, and the test is concrete.
    // Vanilla tops out at Efficiency 5 / Fortune 3 / Unbreaking 3, and a table roll cannot
    // carry Mending, so anything at 0.5% has to be past that line on some axis. Efficiency 3
    // / Fortune 1 / Unbreaking 3 is ten minutes at an enchanting table — handing it out once
    // every two hundred keys was worse than handing out nothing.
    //
    // Everything still uses ordinary Minecraft enchantments. Only the 0.1% jackpot goes
    // above vanilla levels, and only modestly.

    private static Map<Enchantment, Integer> ench(Object... pairs) {
        Map<Enchantment, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((Enchantment) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    private static final int W_UNCOMMON = 120, W_RARE = 35, W_VERY_RARE = 5;

    private static final String R_UNCOMMON = "\u00a7aUncommon";
    private static final String R_RARE = "\u00a79Rare";
    private static final String R_VERY_RARE = "\u00a75Very rare";

    // -- pickaxes ---------------------------------------------------------------------
    private static final Reward STURDY_PICK = Reward.gear("Sturdy Pickaxe", W_UNCOMMON,
            Material.IRON_PICKAXE, "\u00a7fSturdy Pickaxe",
            ench(Enchantment.EFFICIENCY, 3, Enchantment.UNBREAKING, 2), R_UNCOMMON);

    private static final Reward MINERS_PICK = Reward.gear("Miner's Pickaxe", W_RARE,
            Material.DIAMOND_PICKAXE, "\u00a7bMiner's Pickaxe",
            ench(Enchantment.EFFICIENCY, 4, Enchantment.FORTUNE, 2, Enchantment.UNBREAKING, 3),
            R_RARE);

    private static final Reward FOREMANS_PICK = Reward.gear("Foreman's Pickaxe", W_VERY_RARE,
            Material.DIAMOND_PICKAXE, "\u00a7dForeman's Pickaxe",
            ench(Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3, Enchantment.UNBREAKING, 4,
                 Enchantment.MENDING, 1), R_VERY_RARE);

    // -- rods -------------------------------------------------------------------------
    private static final Reward REINFORCED_ROD = Reward.gear("Reinforced Rod", W_UNCOMMON,
            Material.FISHING_ROD, "\u00a7fReinforced Rod",
            ench(Enchantment.LURE, 2, Enchantment.UNBREAKING, 2), R_UNCOMMON);

    private static final Reward ANGLERS_ROD = Reward.gear("Angler's Rod", W_RARE,
            Material.FISHING_ROD, "\u00a7bAngler's Rod",
            ench(Enchantment.LURE, 3, Enchantment.LUCK_OF_THE_SEA, 2, Enchantment.UNBREAKING, 3),
            R_RARE);

    private static final Reward DEEPWATER_ROD = Reward.gear("Deepwater Rod", W_VERY_RARE,
            Material.FISHING_ROD, "\u00a7dDeepwater Rod",
            ench(Enchantment.LURE, 4, Enchantment.LUCK_OF_THE_SEA, 4, Enchantment.UNBREAKING, 4,
                 Enchantment.MENDING, 1), R_VERY_RARE);

    static {
        Crate miner = new Crate("miner", "Miner Crate", Material.TRIPWIRE_HOOK, "\u00a7b\u00a7lMiner Key");
        miner.rewards.add(Reward.money("$2,500", 300, 2500));
        miner.rewards.add(Reward.tokens("150 Tokens", 250, 150));
        miner.rewards.add(Reward.money("$7,500", 180, 7500));
        miner.rewards.add(Reward.tokens("400 Tokens", 60, 400));
        miner.rewards.add(Reward.item("Golden Apple x2", 50, Material.GOLDEN_APPLE, 2));
        miner.rewards.add(STURDY_PICK);
        miner.rewards.add(MINERS_PICK);
        miner.rewards.add(FOREMANS_PICK);
        CRATES.put(miner.id, miner);

        Crate angler = new Crate("angler", "Angler Crate", Material.TRIPWIRE_HOOK, "\u00a7a\u00a7lAngler Key");
        angler.rewards.add(Reward.money("$4,000", 300, 4000));
        angler.rewards.add(Reward.tokens("250 Tokens", 250, 250));
        angler.rewards.add(Reward.money("$12,000", 180, 12000));
        angler.rewards.add(Reward.tokens("600 Tokens", 60, 600));
        angler.rewards.add(Reward.item("Cooked Salmon x16", 50, Material.COOKED_SALMON, 16));
        angler.rewards.add(REINFORCED_ROD);
        angler.rewards.add(ANGLERS_ROD);
        angler.rewards.add(DEEPWATER_ROD);
        CRATES.put(angler.id, angler);

        Crate vote = new Crate("vote", "Vote Crate", Material.TRIPWIRE_HOOK, "\u00a7e\u00a7lVote Key");
        vote.rewards.add(Reward.money("$5,000", 260, 5000));
        vote.rewards.add(Reward.tokens("300 Tokens", 200, 300));
        vote.rewards.add(Reward.money("$15,000", 140, 15000));
        vote.rewards.add(Reward.tokens("800 Tokens", 50, 800));
        vote.rewards.add(Reward.item("Diamond x3", 40, Material.DIAMOND, 3));
        vote.rewards.add(STURDY_PICK);
        vote.rewards.add(REINFORCED_ROD);
        vote.rewards.add(MINERS_PICK);
        vote.rewards.add(ANGLERS_ROD);
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

    /**
     * Checks the loot tables hold together, and is called on startup so a mistake shows up in
     * the console rather than in someone's inventory two hundred keys later.
     *
     * Three rules, each one written because it was broken at least once:
     *   1. Weights total 1000 per crate, so a displayed percentage is exact.
     *   2. An item has the same weight in every crate, so its rarity means one thing.
     *   3. Anything rarer than 1% carries Mending. A sub-1% tool that wears out and is gone
     *      is not a reward, and a player will not get another one.
     */
    public static List<String> audit() {
        List<String> problems = new ArrayList<>();
        Map<String, Integer> weightOf = new LinkedHashMap<>();

        for (Crate crate : CRATES.values()) {
            int total = 0;
            for (Reward r : crate.rewards) total += r.weight;
            if (total != 1000) {
                problems.add(crate.display + " weights total " + total
                        + ", not 1000 — its displayed odds are wrong");
            }
            for (Reward r : crate.rewards) {
                Integer seen = weightOf.putIfAbsent(r.display, r.weight);
                if (seen != null && seen != r.weight) {
                    problems.add("\"" + r.display + "\" is weight " + seen + " in one crate and "
                            + r.weight + " in " + crate.display + " — pick one rarity");
                }
                if (r.weight < 10 && r.isGear() && !r.enchants.containsKey(Enchantment.MENDING)) {
                    problems.add("\"" + r.display + "\" drops at "
                            + String.format("%.2f%%", r.weight / 10.0)
                            + " but has no Mending — it would break and be unreplaceable");
                }
            }
        }
        return problems;
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
        return RANDOM.nextBoolean() ? buildNamedGear("wardens_pickaxe") : buildNamedGear("leviathan_rod");
    }

    /**
     * Every named piece of gear by id, including the two jackpots, so admins can hand one
     * out for testing without opening crates until a 0.1% comes up.
     */
    public static final List<String> GEAR_IDS = List.of(
            "sturdy_pickaxe", "miners_pickaxe", "foremans_pickaxe", "wardens_pickaxe",
            "reinforced_rod", "anglers_rod", "deepwater_rod", "leviathan_rod");

    public static ItemStack buildNamedGear(String id) {
        switch (id) {
            case "sturdy_pickaxe" -> { return buildItem(STURDY_PICK); }
            case "miners_pickaxe" -> { return buildItem(MINERS_PICK); }
            case "foremans_pickaxe" -> { return buildItem(FOREMANS_PICK); }
            case "reinforced_rod" -> { return buildItem(REINFORCED_ROD); }
            case "anglers_rod" -> { return buildItem(ANGLERS_ROD); }
            case "deepwater_rod" -> { return buildItem(DEEPWATER_ROD); }
            case "wardens_pickaxe" -> {
                return jackpot(Material.DIAMOND_PICKAXE, "\u00a76\u00a7lWarden's Pickaxe",
                        ench(Enchantment.EFFICIENCY, 7, Enchantment.FORTUNE, 4,
                             Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1));
            }
            case "leviathan_rod" -> {
                return jackpot(Material.FISHING_ROD, "\u00a76\u00a7lLeviathan Rod",
                        ench(Enchantment.LURE, 5, Enchantment.LUCK_OF_THE_SEA, 5,
                             Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1));
            }
            default -> { return null; }
        }
    }

    /**
     * The 0.1% prizes. Strong, but bounded: levels sit modestly above vanilla rather than at
     * the absurd numbers OP servers use, so a winner is powerful, not untouchable. Both carry
     * Mending, because a prize this rare must not be consumable.
     */
    private static ItemStack jackpot(Material mat, String name, Map<Enchantment, Integer> enchants) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            meta.addEnchant(e.getKey(), e.getValue(), true);
        }
        List<String> lore = new ArrayList<>();
        lore.add("\u00a76Legendary");
        lore.add("\u00a78One in a thousand crates.");
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
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
