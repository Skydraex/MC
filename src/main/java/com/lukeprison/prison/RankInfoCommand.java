package com.lukeprison.prison;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RankInfoCommand implements CommandExecutor {

    private final PrisonPlugin plugin;
    public RankInfoCommand(PrisonPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String current = plugin.ranks().getRank(p);
        int prestige = plugin.ranks().getPrestige(p);
        RankMineData.Def def = RankMineData.RANKS.get(current);
        p.sendMessage("§7--- Your Rank ---");
        p.sendMessage("§fRank: §e" + current + (prestige > 0 ? " §7(Prestige " + prestige + ")" : ""));
        if (!def.next.equals(current)) {
            RankMineData.Def next = RankMineData.RANKS.get(def.next);
            p.sendMessage("§fNext: §e" + next.rank + " §7- costs $" + String.format("%.2f", (double) next.cost));
        } else {
            p.sendMessage("§fYou're Free! Use /prestige to reset for a permanent bonus.");
        }
        return true;
    }
}
