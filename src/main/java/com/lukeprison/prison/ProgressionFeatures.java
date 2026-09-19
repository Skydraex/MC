package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Daily rewards, block milestones, leaderboards and /sell — the small loops that keep people logging in. */
public class ProgressionFeatures {

    private final PrisonPlugin plugin;

    public ProgressionFeatures(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- Daily rewards: streak-based, resets if you miss a day -----------------

    private static final long DAY_MS = 20L * 60 * 60 * 1000;    // 20h window so time zones don't punish
    private static final long STREAK_BREAK_MS = 48L * 60 * 60 * 1000;

    public class Daily implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (!(s instanceof Player p)) return true;
            long last = plugin.ranks().getLastDaily(p);
            long now = System.currentTimeMillis();
            if (now - last < DAY_MS) {
                long left = DAY_MS - (now - last);
                p.sendMessage("§eDaily already claimed. Next in §f" + (left / 3600000) + "h " + ((left / 60000) % 60) + "m§e.");
                return true;
            }
            int streak = (now - last > STREAK_BREAK_MS) ? 1 : plugin.ranks().getDailyStreak(p) + 1;
            plugin.ranks().recordDaily(p, streak);

            // Escalating rewards, with a key every 7th day.
            long money = 2500L * Math.min(streak, 14);
            plugin.economy().depositPlayer(p, money);
            p.sendMessage("§a§lDAILY REWARD §7(streak " + streak + ")");
            p.sendMessage("§7  +§6$" + String.format("%,d", money));
            if (streak % 7 == 0) {
                plugin.crates().giveKey(p, "vote");
                p.sendMessage("§7  +§eVote Key §7(7-day streak bonus)");
            }
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
            plugin.scoreboard().update(p);
            return true;
        }
    }

    // ---- Milestones: called from the mining loop ------------------------------

    private static final long[] MILESTONES = {1_000, 10_000, 50_000, 100_000, 250_000, 500_000, 1_000_000};

    public void checkMilestones(Player p) {
        long blocks = plugin.ranks().getBlocksMined(p);
        for (long m : MILESTONES) {
            if (blocks < m) break;
            if (!plugin.ranks().claimMilestone(p, m)) continue;
            long money = m * 4;
            plugin.economy().depositPlayer(p, money);
            p.sendMessage("");
            p.sendMessage("§6§lMILESTONE §f" + String.format("%,d", m) + " blocks mined!");
            p.sendMessage("§7  +§6$" + String.format("%,d", money));
            if (m >= 50_000) {
                plugin.crates().giveKey(p, "miner");
                p.sendMessage("§7  +§bMiner Key");
            }
            p.sendMessage("");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            if (m >= 100_000) {
                Bukkit.broadcastMessage("§6★ §f" + p.getName() + " §7has mined §f" + String.format("%,d", m) + " §7blocks!");
            }
        }
    }

    // ---- Leaderboards: /top money|blocks|prestige|fishing ----------------------

    public class Top implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            String type = a.length > 0 ? a[0].toLowerCase() : "money";
            List<Map.Entry<String, Double>> rows = new ArrayList<>();

            switch (type) {
                case "blocks" -> plugin.ranks().allBlocksMined().forEach((id, v) -> rows.add(Map.entry(name(id), (double) v)));
                case "prestige" -> plugin.ranks().allPrestige().forEach((id, v) -> rows.add(Map.entry(name(id), (double) v)));
                case "fishing" -> plugin.fishing().allXp().forEach((id, v) -> rows.add(Map.entry(name(id), (double) FishingData.levelForXp(v))));
                default -> {
                    type = "money";
                    for (UUID id : plugin.ranks().knownPlayers()) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                        rows.add(Map.entry(name(id), plugin.economy().getBalance(op)));
                    }
                }
            }
            rows.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));

            s.sendMessage("§8§m--------§r §b§lTOP " + type.toUpperCase() + " §8§m--------");
            int i = 1;
            for (Map.Entry<String, Double> row : rows) {
                if (i > 10) break;
                String val = type.equals("money") ? "$" + String.format("%,.0f", row.getValue()) : String.format("%,.0f", row.getValue());
                String medal = i == 1 ? "§6" : i == 2 ? "§7" : i == 3 ? "§c" : "§8";
                s.sendMessage(medal + "#" + i + " §f" + row.getKey() + " §7— §a" + val);
                i++;
            }
            if (rows.isEmpty()) s.sendMessage("§7No data yet.");
            s.sendMessage("§7Types: money, blocks, prestige, fishing");
            return true;
        }

        private String name(UUID id) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            return n == null ? "Unknown" : n;
        }
    }

    // ---- /sell: sell every mine block in your inventory, with prestige multiplier ----

    public class Sell implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (!(s instanceof Player p)) return true;
            double total = 0;
            int count = 0;
            double mult = plugin.ranks().getSellMultiplier(p);
            ItemStack[] contents = p.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack it = contents[i];
                if (it == null || it.getType().isAir()) continue;
                double price = bestPrice(it.getType());
                if (price <= 0) continue;
                total += price * it.getAmount() * mult;
                count += it.getAmount();
                p.getInventory().setItem(i, null);
            }
            if (count == 0) {
                p.sendMessage("§cNothing sellable in your inventory.");
                return true;
            }
            plugin.economy().depositPlayer(p, total);
            p.sendMessage("§aSold §f" + count + " §ablocks for §6$" + String.format("%,.2f", total)
                    + (mult > 1 ? " §7(x" + String.format("%.2f", mult) + " prestige)" : ""));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
            plugin.scoreboard().update(p);
            return true;
        }

        /** Highest price any mine pays for this block — so /sell never undercuts the sign. */
        private double bestPrice(Material mat) {
            double best = 0;
            for (RankMineData.Def d : RankMineData.RANKS.values()) {
                if (mat.name().equals(d.filler)) best = Math.max(best, d.fillerPrice);
                if (mat.name().equals(d.common)) best = Math.max(best, d.commonPrice);
                if (mat.name().equals(d.rare)) best = Math.max(best, d.rarePrice);
            }
            if (mat == Material.IRON_INGOT) best = Math.max(best, priceOfOre("IRON_ORE"));
            if (mat == Material.GOLD_INGOT) best = Math.max(best, priceOfOre("GOLD_ORE"));
            return best;
        }

        private double priceOfOre(String ore) {
            double best = 0;
            for (RankMineData.Def d : RankMineData.RANKS.values()) {
                if (ore.equals(d.common)) best = Math.max(best, d.commonPrice);
                if (ore.equals(d.rare)) best = Math.max(best, d.rarePrice);
            }
            return best;
        }
    }
}
