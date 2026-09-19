package com.lukeprison.prison;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Small command handlers for the Phase 2 features. */
public class SimpleCommands {

    public static class PrisonMenu implements CommandExecutor {
        private final MenuGUI menu;
        public PrisonMenu(MenuGUI menu) { this.menu = menu; }
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (s instanceof Player p) menu.openMenu(p);
            return true;
        }
    }

    public static class Warps implements CommandExecutor {
        private final MenuGUI menu;
        public Warps(MenuGUI menu) { this.menu = menu; }
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (s instanceof Player p) menu.openWarps(p);
            return true;
        }
    }

    /** "/mine <rank>" — warps straight to a mine without opening the GUI, if it's unlocked
     *  (rank's own mine or anything below it). "/mine" with no argument just opens the GUI,
     *  same as "/warps". */
    public static class Mine implements CommandExecutor {
        private final MenuGUI menu;
        public Mine(MenuGUI menu) { this.menu = menu; }
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (!(s instanceof Player p)) return true;
            if (a.length == 0) { menu.openWarps(p); return true; }
            menu.warpToMine(p, a[0]);
            return true;
        }
    }

    public static class Enchant implements CommandExecutor {
        private final EnchantGUI gui;
        public Enchant(EnchantGUI gui) { this.gui = gui; }
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (s instanceof Player p) gui.open(p);
            return true;
        }
    }

    }

    public static class AutoSell implements CommandExecutor {
        private final PrisonPlugin plugin;
        public AutoSell(PrisonPlugin plugin) { this.plugin = plugin; }
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (s instanceof Player p) {
                boolean on = plugin.ranks().toggleAutoSell(p);
                p.sendMessage(on
                        ? "§aAuto-sell enabled — blocks now sell instantly as you mine."
                        : "§cAuto-sell disabled — blocks go to your inventory.");
            }
            return true;
        }
    }
}
