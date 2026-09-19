package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * /prisonadmin — tools for testing and moderation. Requires prison.admin (op by default).
 *
 *   setrank <player> <rank>       jump a player to any rank (A–Z, FREE)
 *   givetokens <player> <amount>
 *   givekey <player> <crate> [n]  miner | angler | vote
 *   fishlevel <player> <level>    set fishing level directly
 *   resetplayer <player>          wipe rank/tokens/kit so they replay the intro
 *   rebuild                       delete the world-built marker; next boot rebuilds the map
 */
public class AdminCommands implements CommandExecutor {

    private final PrisonPlugin plugin;

    public AdminCommands(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("prison.admin")) {
            s.sendMessage("§cYou don't have permission.");
            return true;
        }
        if (a.length == 0) {
            usage(s);
            return true;
        }

        switch (a[0].toLowerCase()) {
            case "setrank" -> {
                Player t = target(s, a, 1);
                if (t == null || a.length < 3) { usage(s); return true; }
                String rank = a[2].toUpperCase();
                if (!RankMineData.RANKS.containsKey(rank)) {
                    s.sendMessage("§cUnknown rank. Use A–Z or FREE.");
                    return true;
                }
                plugin.ranks().setRank(t, rank);
                plugin.scoreboard().update(t);
                plugin.chat().updateTab(t);
                s.sendMessage("§aSet " + t.getName() + " to rank " + rank + ".");
                t.sendMessage("§eAn admin set your rank to §f" + rank + "§e.");
            }
            case "givetokens" -> {
                Player t = target(s, a, 1);
                if (t == null || a.length < 3) { usage(s); return true; }
                long amount = parseLong(a[2]);
                if (amount <= 0) { s.sendMessage("§cAmount must be positive."); return true; }
                plugin.ranks().addTokens(t, amount);
                plugin.scoreboard().update(t);
                s.sendMessage("§aGave " + t.getName() + " " + amount + " tokens.");
            }
            case "givekey" -> {
                Player t = target(s, a, 1);
                if (t == null || a.length < 3) { usage(s); return true; }
                String crate = a[2].toLowerCase();
                if (!CrateData.CRATES.containsKey(crate)) {
                    s.sendMessage("§cUnknown crate. Options: " + String.join(", ", CrateData.CRATES.keySet()));
                    return true;
                }
                int n = a.length >= 4 ? (int) Math.max(1, parseLong(a[3])) : 1;
                for (int i = 0; i < n; i++) plugin.crates().giveKey(t, crate);
                s.sendMessage("§aGave " + t.getName() + " " + n + "x " + crate + " key.");
            }
            case "givegear" -> {
                Player t = target(s, a, 1);
                if (t == null || a.length < 3) {
                    s.sendMessage("\u00a7cUsage: /padmin givegear <player> <item>");
                    s.sendMessage("\u00a77Items: \u00a7f" + String.join(", ", CrateData.GEAR_IDS));
                    return true;
                }
                String id = a[2].toLowerCase();
                org.bukkit.inventory.ItemStack gear = CrateData.buildNamedGear(id);
                if (gear == null) {
                    s.sendMessage("\u00a7cUnknown item. Options: " + String.join(", ", CrateData.GEAR_IDS));
                    return true;
                }
                t.getInventory().addItem(gear);
                s.sendMessage("\u00a7aGave " + t.getName() + " a " + id + ".");
                if (!t.equals(s)) t.sendMessage("\u00a77An admin handed you a piece of gear.");
            }
            case "fishlevel" -> {
                Player t = target(s, a, 1);
                if (t == null || a.length < 3) { usage(s); return true; }
                int level = (int) parseLong(a[2]);
                if (level < 1 || level > 60) { s.sendMessage("§cLevel must be 1–60."); return true; }
                long targetXp = FishingData.xpForLevel(level);
                long current = plugin.fishing().getXp(t);
                plugin.fishing().addXp(t, targetXp - current);
                s.sendMessage("§aSet " + t.getName() + "'s fishing level to " + level + ".");
            }
            case "resetplayer" -> {
                Player t = target(s, a, 1);
                if (t == null) { usage(s); return true; }
                plugin.ranks().resetPlayer(t);
                plugin.scoreboard().update(t);
                t.teleport(plugin.builder().getStarterSpawn());
                s.sendMessage("§aReset " + t.getName() + " to a brand-new player and sent them to the bus.");
            }
            case "validate" -> {
                s.sendMessage("§eRunning map connectivity check...");
                new MapValidator(plugin.builder().getWorld(), WorldBuilder.Y)
                        .run(s, plugin.builder().getHubSpawn());
            }
            case "rebuild" -> {
                File marker = new File(plugin.getDataFolder(), "world-built.marker");
                if (marker.exists() && marker.delete()) {
                    s.sendMessage("§eMarker removed. Delete the §fprison§e world folder and restart to rebuild.");
                } else {
                    s.sendMessage("§cNo marker to remove.");
                }
            }
            default -> usage(s);
        }
        return true;
    }

    private Player target(CommandSender s, String[] a, int idx) {
        if (a.length <= idx) return null;
        Player p = Bukkit.getPlayerExact(a[idx]);
        if (p == null) s.sendMessage("§cPlayer not online: " + a[idx]);
        return p;
    }

    private long parseLong(String v) {
        try { return Long.parseLong(v.replace(",", "")); } catch (NumberFormatException e) { return -1; }
    }

    private void usage(CommandSender s) {
        s.sendMessage("§6/prisonadmin §7<setrank|givetokens|givekey|givegear|fishlevel|resetplayer|rebuild>");
        s.sendMessage("§7  setrank <player> <A-Z|FREE>");
        s.sendMessage("§7  givetokens <player> <amount>");
        s.sendMessage("§7  givekey <player> <miner|angler|vote> [count]");
        s.sendMessage("§7  givegear <player> <item> §8— " + String.join(", ", CrateData.GEAR_IDS));
        s.sendMessage("§7  fishlevel <player> <1-60>");
        s.sendMessage("§7  resetplayer <player>");
        s.sendMessage("§7  rebuild");
        s.sendMessage("§7  validate §8- scan the map for void gaps in the floor");
    }
}
