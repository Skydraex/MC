package com.lukeprison.prison;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RankUpCommand implements CommandExecutor {

    private final PrisonPlugin plugin;
    public RankUpCommand(PrisonPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String current = plugin.ranks().getRank(p);
        RankMineData.Def def = RankMineData.RANKS.get(current);
        if (def.next.equals(current)) {
            p.sendMessage("§eYou're at Free — use /prestige to go again.");
            return true;
        }
        RankMineData.Def next = RankMineData.RANKS.get(def.next);
        boolean ok = plugin.ranks().rankUp(p);
        if (ok) {
            p.sendMessage("§aRanked up to §f" + next.rank + "§a! Mine " + next.rank + " is now unlocked.");
        } else {
            p.sendMessage("§cYou need $" + String.format("%.2f", (double) next.cost) + " to rank up to " + next.rank + ".");
        }
        return true;
    }
}
