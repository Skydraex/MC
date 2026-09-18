package com.lukeprison.prison;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RankUpCommand implements CommandExecutor {

    private final PrisonPlugin plugin;
    private final RanksGUI gui;

    public RankUpCommand(PrisonPlugin plugin, RanksGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        gui.open(p);
        return true;
    }
}
