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

    static {
        Crate miner = new Crate("miner", "Miner Crate", Material.TRIPWIRE_HOOK, "§b§lMiner Key");
        miner.rewards.add(Reward.money("$2,500", 30, 2500));
        miner.rewards.add(Reward.money("$7,500", 20, 7500));
        miner.rewards.add(Reward.tokens("150 Tokens", 25, 150));
        miner.rewards.add(Reward.tokens("400 Tokens", 12, 400));
        miner.rewards.add(Reward.item("Iron Pickaxe", 8, Material.IRON_PICKAXE, 1));
        miner.rewards.add(Reward.item("Golden Apple", 5, Material.GOLDEN_APPLE, 2));
        CRATES.put(miner.id, miner);

        Crate angler = new Crate("angler", "Angler Crate", Material.TRIPWIRE_HOOK, "§a§lAngler Key");
        angler.rewards.add(Reward.money("$4,000", 30, 4000));
        angler.rewards.add(Reward.money("$12,000", 18, 12000));
        angler.rewards.add(Reward.tokens("250 Tokens", 25, 250));
        angler.rewards.add(Reward.tokens("600 Tokens", 12, 600));
        angler.rewards.add(Reward.item("Fishing Rod", 10, Material.FISHING_ROD, 1));
        angler.rewards.add(Reward.item("Cooked Salmon", 5, Material.COOKED_SALMON, 16));
        CRATES.put(angler.id, angler);

        Crate vote = new Crate("vote", "Vote Crate", Material.TRIPWIRE_HOOK, "§e§lVote Key");
        vote.rewards.add(Reward.money("$5,000", 30, 5000));
        vote.rewards.add(Reward.money("$15,000", 18, 15000));
        vote.rewards.add(Reward.tokens("300 Tokens", 26, 300));
        vote.rewards.add(Reward.tokens("800 Tokens", 14, 800));
        vote.rewards.add(Reward.item("Diamond", 8, Material.DIAMOND, 3));
        vote.rewards.add(Reward.item("Golden Apple", 4, Material.GOLDEN_APPLE, 3));
        CRATES.put(vote.id, vote);
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
