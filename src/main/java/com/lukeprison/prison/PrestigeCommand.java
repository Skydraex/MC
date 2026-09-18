package com.lukeprison.prison;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PrestigeCommand implements CommandExecutor {

    private final PrisonPlugin plugin;
    public PrestigeCommand(PrisonPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!plugin.ranks().getRank(p).equals("FREE")) {
            p.sendMessage("§cYou need to reach Free before you can prestige.");
            return true;
        }
        boolean ok = plugin.ranks().prestige(p);
        if (ok) {
            int level = plugin.ranks().getPrestige(p);
            p.sendMessage("§dPrestiged! You are now Prestige " + level + ". Back to rank A with a permanent bonus.");
            plugin.chat().updateTab(p);
        } else {
            double cost = RankMineData.RANKS.get("FREE").cost;
            p.sendMessage("§cYou need $" + String.format("%.2f", cost) + " to prestige.");
        }
        return true;
    }
}
