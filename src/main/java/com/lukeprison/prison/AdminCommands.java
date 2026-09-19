package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * /padmin — staff tools, gated by staff rank.
 *
 * Every subcommand declares the rank it needs, and the help only lists what the caller can
 * actually run, so a Helper typing /padmin sees a Helper's toolkit rather than a menu of
 * things that will refuse them.
 *
 * The split is in StaffRank. The part that matters: anything that puts an item or a balance
 * into a player's hands is Super Admin or above. It used to be one permission, which meant
 * anyone trusted to answer questions in chat could also mint themselves a jackpot pickaxe.
 */
public class AdminCommands implements CommandExecutor {

    /** One subcommand: what it is called, what rank it needs, and how to use it. */
    private record Sub(String name, StaffRank need, String args, String help) { }

    private static final List<Sub> SUBS = List.of(
            new Sub("check", StaffRank.HELPER, "<player>", "look up a player's rank, balance and cell"),
            new Sub("warn", StaffRank.HELPER, "<player> <reason>", "send a formal warning"),
            new Sub("staff", StaffRank.HELPER, "", "who is online and what rank they hold"),

            new Sub("mute", StaffRank.MOD, "<player> [minutes]", "stop a player talking (blank = indefinite)"),
            new Sub("unmute", StaffRank.MOD, "<player>", "lift a mute"),
            new Sub("kick", StaffRank.MOD, "<player> [reason]", "remove a player from the server"),
            new Sub("jail", StaffRank.MOD, "<player> <minutes>", "put a player in the hole"),
            new Sub("unjail", StaffRank.MOD, "<player>", "let a player out early"),

            new Sub("setrank", StaffRank.ADMIN, "<player> <A-Z|FREE>", "move a player to any prison rank"),
            new Sub("fishlevel", StaffRank.ADMIN, "<player> <1-60>", "set a fishing level directly"),
            new Sub("resetplayer", StaffRank.ADMIN, "<player>", "wipe progress and send them back to the bus"),
            new Sub("audit", StaffRank.HELPER, "", "check the built world and the plugin for faults"),
            new Sub("validate", StaffRank.ADMIN, "", "run the in-world map connectivity check"),

            new Sub("givekey", StaffRank.SUPERADMIN, "<player> <crate> [n]", "hand out crate keys"),
            new Sub("givegear", StaffRank.SUPERADMIN, "<player> <item>", "hand out a named piece of gear"),
            new Sub("givemoney", StaffRank.SUPERADMIN, "<player> <amount>", "deposit money"),

            new Sub("rebuild", StaffRank.OWNER, "", "clear the build marker so the map regenerates"));

    private final PrisonPlugin plugin;

    public AdminCommands(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        StaffRank rank = StaffRank.of(s);
        if (rank == StaffRank.NONE) {
            s.sendMessage("§cYou don't have permission.");
            return true;
        }
        if (a.length == 0) {
            usage(s, rank);
            return true;
        }

        String sub = a[0].toLowerCase();
        Sub def = SUBS.stream().filter(x -> x.name().equals(sub)).findFirst().orElse(null);
        if (def == null) {
            s.sendMessage("§cNo such command: §f" + sub);
            usage(s, rank);
            return true;
        }
        if (!rank.atLeast(def.need())) {
            // Named explicitly rather than "no permission", so staff know who to ask.
            s.sendMessage("§c/padmin " + def.name() + " §7needs §f" + def.need().display
                    + "§7. You are §f" + rank.display + "§7.");
            return true;
        }

        switch (sub) {
            case "check" -> {
                Player t = target(s, a, 1);
                if (t == null) return true;
                s.sendMessage("§8§m                        ");
                s.sendMessage("§7Player: §f" + t.getName() + " §8(" + StaffRank.of(t).display + ")");
                s.sendMessage("§7Rank: §f" + plugin.ranks().getRank(t)
                        + "   §7Prestige: §f" + plugin.ranks().getPrestige(t));
                s.sendMessage("§7Balance: §6$" + String.format("%,.0f", plugin.economy().getBalance(t)));
                s.sendMessage("§7Blocks mined: §f" + String.format("%,d", plugin.ranks().getBlocksMined(t)));
                Integer cell = plugin.ranks().cellOf(t);
                s.sendMessage("§7Cell: §f" + (cell == null ? "none" : "#" + cell));
                long muted = StaffRank.muteRemaining(t.getUniqueId());
                if (muted != 0) {
                    s.sendMessage("§cMuted: §f" + (muted < 0 ? "indefinitely" : muted + " min left"));
                }
            }
            case "warn" -> {
                Player t = target(s, a, 1);
                if (t == null || a.length < 3) { s.sendMessage("§c/padmin warn <player> <reason>"); return true; }
                String reason = join(a, 2);
                t.sendMessage("§c§lWARNING §7from " + s.getName() + ": §f" + reason);
                s.sendMessage("§aWarned " + t.getName() + ".");
                plugin.getLogger().info("[staff] " + s.getName() + " warned " + t.getName() + ": " + reason);
            }
            case "staff" -> {
                s.sendMessage("§6Staff online:");
                boolean any = false;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    StaffRank r = StaffRank.of(p);
                    if (r == StaffRank.NONE) continue;
                    s.sendMessage("  §f" + p.getName() + " §8— §7" + r.display);
                    any = true;
                }
                if (!any) s.sendMessage("  §8(nobody)");
            }
            case "mute" -> {
                Player t = target(s, a, 1);
                if (t == null) return true;
                long minutes = a.length >= 3 ? parseLong(a[2]) : 0;
                StaffRank.mute(t.getUniqueId(), minutes);
                String span = minutes > 0 ? minutes + " minutes" : "indefinitely";
                t.sendMessage("§cYou have been muted " + span + ".");
                s.sendMessage("§aMuted " + t.getName() + " " + span + ".");
                plugin.getLogger().info("[staff] " + s.getName() + " muted " + t.getName() + " " + span);
            }
            case "unmute" -> {
                Player t = target(s, a, 1);
                if (t == null) return true;
                StaffRank.unmute(t.getUniqueId());
                t.sendMessage("§aYou can talk again.");
                s.sendMessage("§aUnmuted " + t.getName() + ".");
            }
            case "kick" -> {
                Player t = target(s, a, 1);
                if (t == null) return true;
                String reason = a.length >= 3 ? join(a, 2) : "Kicked by staff";
                plugin.getLogger().info("[staff] " + s.getName() + " kicked " + t.getName() + ": " + reason);
                t.kickPlayer("§c" + reason);
                s.sendMessage("§aKicked " + t.getName() + ".");
            }
            case "jail" -> {
                Player t = target(s, a, 1);
                if (t == null || a.length < 3) { s.sendMessage("§c/padmin jail <player> <minutes>"); return true; }
                long minutes = parseLong(a[2]);
                if (minutes <= 0) { s.sendMessage("§cMinutes must be positive."); return true; }
                plugin.jail().jail(t, minutes, s.getName());
                s.sendMessage("§aJailed " + t.getName() + " for " + minutes + " minutes.");
            }
            case "unjail" -> {
                Player t = target(s, a, 1);
                if (t == null) return true;
                plugin.jail().release(t);
                s.sendMessage("§aReleased " + t.getName() + ".");
            }
            case "setrank" -> {
                Player t = target(s, a, 1);
                if (t == null || a.length < 3) { s.sendMessage("§c/padmin setrank <player> <A-Z|FREE>"); return true; }
                String newRank = a[2].toUpperCase();
                if (!RankMineData.RANKS.containsKey(newRank)) {
                    s.sendMessage("§cUnknown rank. Use A–Z or FREE.");
                    return true;
                }
                plugin.ranks().setRank(t, newRank);
                plugin.scoreboard().update(t);
                plugin.chat().updateTab(t);
                s.sendMessage("§aSet " + t.getName() + " to rank " + newRank + ".");
                t.sendMessage("§eAn admin set your rank to §f" + newRank + "§e.");
                plugin.getLogger().info("[staff] " + s.getName() + " set " + t.getName() + " to " + newRank);
            }
            case "fishlevel" -> {
                Player t = target(s, a, 1);
                if (t == null || a.length < 3) { s.sendMessage("§c/padmin fishlevel <player> <1-60>"); return true; }
                int level = (int) parseLong(a[2]);
                if (level < 1 || level > 60) { s.sendMessage("§cLevel must be 1–60."); return true; }
                plugin.fishing().addXp(t, FishingData.xpForLevel(level) - plugin.fishing().getXp(t));
                s.sendMessage("§aSet " + t.getName() + "'s fishing level to " + level + ".");
            }
            case "resetplayer" -> {
                Player t = target(s, a, 1);
                if (t == null) return true;
                plugin.ranks().resetPlayer(t);
                plugin.scoreboard().update(t);
                t.teleport(plugin.builder().getStarterSpawn());
                s.sendMessage("§aReset " + t.getName() + " and sent them to the bus.");
                plugin.getLogger().info("[staff] " + s.getName() + " reset " + t.getName());
            }
            case "audit" -> {
                s.sendMessage("\u00a7eAuditing the world and the plugin...");
                MapAuditor world = new MapAuditor(plugin.builder());
                world.runAll();
                world.report(s);
                SystemAudit system = new SystemAudit(plugin);
                system.runAll();
                system.report(s);
                int total = world.getFindings().size() + system.getFindings().size();
                for (MapAuditor.Finding f : world.getFindings()) {
                    plugin.getLogger().warning("[audit] " + f.category() + ": " + f);
                }
                for (SystemAudit.Finding f : system.getFindings()) {
                    plugin.getLogger().warning("[audit] " + f.category() + ": " + f.detail());
                }
                s.sendMessage(total == 0
                        ? "\u00a7a\u00a7lEverything checks out."
                        : "\u00a7c" + total + " problem(s) — full detail in the console.");
            }
            case "validate" -> {
                s.sendMessage("§eRunning map connectivity check...");
                new MapValidator(plugin.builder().getWorld(), WorldBuilder.Y)
                        .run(s, plugin.builder().getHubSpawn());
            }
            case "givekey" -> {
                Player t = target(s, a, 1);
                if (t == null || a.length < 3) {
                    s.sendMessage("§c/padmin givekey <player> <"
                            + String.join("|", CrateData.CRATES.keySet()) + "> [count]");
                    return true;
                }
                String crate = a[2].toLowerCase();
                if (!CrateData.CRATES.containsKey(crate)) {
                    s.sendMessage("§cUnknown crate: " + String.join(", ", CrateData.CRATES.keySet()));
                    return true;
                }
                int n = a.length >= 4 ? (int) Math.max(1, parseLong(a[3])) : 1;
                for (int i = 0; i < n; i++) plugin.crates().giveKey(t, crate);
                s.sendMessage("§aGave " + t.getName() + " " + n + "x " + crate + " key.");
                plugin.getLogger().info("[staff] " + s.getName() + " gave " + t.getName()
                        + " " + n + "x " + crate + " key");
            }
            case "givegear" -> {
                Player t = target(s, a, 1);
                if (t == null || a.length < 3) {
                    s.sendMessage("§c/padmin givegear <player> <item>");
                    s.sendMessage("§7Items: §f" + String.join(", ", CrateData.GEAR_IDS));
                    return true;
                }
                String id = a[2].toLowerCase();
                org.bukkit.inventory.ItemStack gear = CrateData.buildNamedGear(id);
                if (gear == null) {
                    s.sendMessage("§cUnknown item: " + String.join(", ", CrateData.GEAR_IDS));
                    return true;
                }
                t.getInventory().addItem(gear);
                s.sendMessage("§aGave " + t.getName() + " a " + id + ".");
                plugin.getLogger().info("[staff] " + s.getName() + " gave " + t.getName() + " " + id);
            }
            case "givemoney" -> {
                Player t = target(s, a, 1);
                if (t == null || a.length < 3) { s.sendMessage("§c/padmin givemoney <player> <amount>"); return true; }
                long amount = parseLong(a[2]);
                if (amount <= 0) { s.sendMessage("§cAmount must be positive."); return true; }
                plugin.economy().depositPlayer(t, amount);
                plugin.scoreboard().update(t);
                s.sendMessage("§aGave " + t.getName() + " $" + String.format("%,d", amount) + ".");
                plugin.getLogger().info("[staff] " + s.getName() + " gave " + t.getName() + " $" + amount);
            }
            case "rebuild" -> {
                File marker = new File(plugin.getDataFolder(), "world-built.marker");
                if (marker.exists() && marker.delete()) {
                    s.sendMessage("§eMarker removed. Delete the §fprison§e world folder and restart to rebuild.");
                } else {
                    s.sendMessage("§cNo marker to remove.");
                }
            }
            default -> usage(s, rank);
        }
        return true;
    }

    /** Only what this caller can actually run. */
    private void usage(CommandSender s, StaffRank rank) {
        s.sendMessage("§8§m                                        ");
        s.sendMessage("§6/padmin §8— you are §f" + rank.display);
        StaffRank heading = null;
        List<String> lines = new ArrayList<>();
        for (Sub sub : SUBS) {
            if (!rank.atLeast(sub.need())) continue;
            if (sub.need() != heading) {
                heading = sub.need();
                lines.add("§8  " + heading.display + ":");
            }
            lines.add("  §e" + sub.name() + " §7" + sub.args() + " §8— " + sub.help());
        }
        lines.forEach(s::sendMessage);

        List<String> locked = SUBS.stream().filter(x -> !rank.atLeast(x.need()))
                .map(Sub::name).toList();
        if (!locked.isEmpty()) {
            s.sendMessage("§8  Not available to you: " + String.join(", ", locked));
        }
    }

    private Player target(CommandSender s, String[] a, int idx) {
        if (a.length <= idx) {
            s.sendMessage("§cWhich player?");
            return null;
        }
        Player p = Bukkit.getPlayerExact(a[idx]);
        if (p == null) s.sendMessage("§cPlayer not online: " + a[idx]);
        return p;
    }

    private static String join(String[] a, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(a, from, a.length));
    }

    private long parseLong(String v) {
        try { return Long.parseLong(v.replace(",", "")); } catch (NumberFormatException e) { return -1; }
    }
}
