package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Audits the PLUGIN, the way MapAuditor audits the world.
 *
 * A command declared in plugin.yml but never given an executor does not error on startup and
 * does not error when typed — it just prints the usage line and does nothing, forever, until
 * somebody happens to try it and mentions it. A help menu advertising a command that was
 * renamed behaves the same way. A rank whose "next" points at a rank that does not exist only
 * breaks for the one player who gets that far.
 *
 * All of that is mechanical, and all of it is checkable in milliseconds at startup rather
 * than by a person typing every command in turn.
 */
public class SystemAudit {

    public record Finding(String category, String detail) {
        @Override public String toString() { return detail; }
    }

    private final PrisonPlugin plugin;
    private final List<Finding> findings = new ArrayList<>();
    private final Map<String, Integer> checked = new LinkedHashMap<>();

    public SystemAudit(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public List<Finding> getFindings() { return findings; }

    private void fail(String category, String detail) {
        findings.add(new Finding(category, detail));
    }

    public void runAll() {
        findings.clear();
        checked.clear();
        auditCommands();
        auditHelpMenu();
        auditDependencies();
        auditListeners();
        auditRankLadder();
        auditCrates();
        auditEnchants();
        auditPermissions();
    }

    /** Every command in plugin.yml has something behind it. */
    private void auditCommands() {
        Map<String, Map<String, Object>> declared = plugin.getDescription().getCommands();
        int n = 0;
        for (String name : declared.keySet()) {
            PluginCommand cmd = plugin.getCommand(name);
            if (cmd == null) {
                fail("command", "/" + name + " is in plugin.yml but the server did not register it");
            } else if (cmd.getExecutor() == null || cmd.getExecutor() == plugin) {
                // Bukkit falls back to the plugin itself when nothing is set, and that
                // fallback silently does nothing.
                fail("command", "/" + name + " has no executor — it will do nothing when typed");
            }
            n++;
        }
        checked.put("declared commands", n);
    }

    /** Everything /help advertises is a real, registered command. */
    private void auditHelpMenu() {
        HelpGUI help = plugin.help();
        if (help == null) {
            fail("help", "the help menu was never created");
            return;
        }
        Set<String> declared = new LinkedHashSet<>(plugin.getDescription().getCommands().keySet());
        // Aliases count as real commands for this purpose.
        for (Map<String, Object> spec : plugin.getDescription().getCommands().values()) {
            Object aliases = spec.get("aliases");
            if (aliases instanceof List<?> list) {
                for (Object a : list) declared.add(String.valueOf(a).toLowerCase());
            }
        }

        int n = 0;
        for (String advertised : help.advertisedCommands()) {
            if (!declared.contains(advertised)) {
                fail("help", "/help offers /" + advertised + ", which is not a registered command");
            }
            n++;
        }
        checked.put("help entries", n);
    }

    /** Vault, and an actual economy behind it. */
    private void auditDependencies() {
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            fail("dependency", "Vault is missing or disabled — nothing economic will work");
        }
        if (plugin.economy() == null) {
            fail("dependency", "no economy provider is registered with Vault "
                    + "(install EssentialsX or similar)");
        } else {
            try {
                plugin.economy().getName();
            } catch (Exception e) {
                fail("dependency", "the economy provider threw on a basic call: " + e.getMessage());
            }
        }
        checked.put("dependencies", 2);
    }

    /** The managers that have to exist for the server to function at all. */
    private void auditListeners() {
        Map<String, Object> required = new LinkedHashMap<>();
        required.put("world builder", plugin.builder());
        required.put("rank manager", plugin.ranks());
        required.put("crate listener", plugin.crates());
        required.put("jail manager", plugin.jail());
        required.put("combat tagging", plugin.combat());
        required.put("teleport guard", plugin.teleports());
        required.put("scoreboard", plugin.scoreboard());

        for (Map.Entry<String, Object> e : required.entrySet()) {
            if (e.getValue() == null) {
                fail("wiring", e.getKey() + " was never created — anything using it will throw");
            }
        }
        checked.put("subsystems", required.size());
    }

    /**
     * The rank ladder joins up end to end, and gets more expensive as it goes.
     *
     * A broken "next" only breaks for the player who reaches that rank, which on an 8-week
     * ladder means finding out about it eight weeks in.
     */
    private void auditRankLadder() {
        int n = 0;
        long previousCost = -1;
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (d.next != null && !d.next.isEmpty() && !RankMineData.RANKS.containsKey(d.next)) {
                fail("ranks", "rank " + d.rank + " advances to \"" + d.next + "\", which does not exist");
            }
            if (d.cost < previousCost) {
                fail("ranks", "rank " + d.rank + " costs less than the rank before it");
            }
            previousCost = d.cost;

            if (d.hasMine()) {
                if (d.fillerPrice <= 0 && d.commonPrice <= 0) {
                    fail("ranks", "mine " + d.rank + " sells nothing for anything");
                }
                if (org.bukkit.Material.matchMaterial(d.filler) == null) {
                    fail("ranks", "mine " + d.rank + " filler \"" + d.filler + "\" is not a Material");
                }
                if (org.bukkit.Material.matchMaterial(d.common) == null) {
                    fail("ranks", "mine " + d.rank + " common ore \"" + d.common + "\" is not a Material");
                }
                if (org.bukkit.Material.matchMaterial(d.rare) == null) {
                    fail("ranks", "mine " + d.rank + " rare ore \"" + d.rare + "\" is not a Material");
                }
            }
            n++;
        }
        checked.put("ranks", n);
    }

    private void auditCrates() {
        for (String problem : CrateData.audit()) fail("crates", problem);
        checked.put("crates", CrateData.CRATES.size());
    }

    /** Enchants are buyable, in order, and gated behind ranks that exist. */
    private void auditEnchants() {
        int n = 0;
        for (PickaxeEnchants.EnchantDef def : PickaxeEnchants.ENCHANTS.values()) {
            if (!RankMineData.RANKS.containsKey(def.requiredRank)) {
                fail("enchants", def.display + " requires rank " + def.requiredRank
                        + ", which does not exist");
            }
            if (def.maxLevel < 1) {
                fail("enchants", def.display + " has a max level below 1 — it can never be bought");
            }
            long last = -1;
            for (int lvl = 1; lvl <= def.maxLevel; lvl++) {
                long cost = def.costFor(lvl);
                if (cost <= last) {
                    fail("enchants", def.display + " level " + lvl + " is not dearer than the one below");
                }
                last = cost;
            }
            n++;
        }
        checked.put("enchants", n);
    }

    /** The staff ladder is declared, so the ranks can actually be granted. */
    private void auditPermissions() {
        int n = 0;
        for (StaffRank rank : StaffRank.values()) {
            if (rank.permission == null) continue;
            if (Bukkit.getPluginManager().getPermission(rank.permission) == null) {
                fail("permissions", rank.permission + " is not declared in plugin.yml — "
                        + rank.display + " cannot be granted");
            }
            n++;
        }
        checked.put("staff ranks", n);
    }

    public void report(CommandSender to) {
        to.sendMessage("§8§m                                        ");
        to.sendMessage("§6§lSYSTEM AUDIT");
        for (Map.Entry<String, Integer> e : checked.entrySet()) {
            to.sendMessage("§8  checked §7" + e.getValue() + " " + e.getKey());
        }
        if (findings.isEmpty()) {
            to.sendMessage("§a§lPASS §7— every command wired, every help entry real,");
            to.sendMessage("§7the rank ladder joins up, dependencies present.");
            return;
        }
        to.sendMessage("§c§lFAIL §7— " + findings.size() + " problem(s):");
        for (Finding f : findings) to.sendMessage("§7  §c[" + f.category() + "] §7" + f.detail());
    }
}
