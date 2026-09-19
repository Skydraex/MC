package com.lukeprison.prison;

import org.bukkit.Bukkit;

import java.util.List;

/**
 * Scheduled server messages, on rotation.
 *
 * A quiet server feels dead, and a new player who is not told that /vote, /rules or /help
 * exist will never find them. Deliberately plain text on a slow rotation — this era of
 * server had a line of chat every few minutes, not a floating UI.
 */
public class ServerAnnouncer implements Runnable {

    private static final int EVERY_MINUTES = 7;

    private static final List<String[]> MESSAGES = List.of(
            new String[]{"§7Type §f/help §7for a menu of everything you can do."},
            new String[]{"§7Vote for the server with §f/vote §7— you get a Vote Key for each one."},
            new String[]{"§7Mine, sell at the shop, then §f/rankup§7. That is the whole game."},
            new String[]{"§7Rent a cell with §f/cell claim§7. Ground floor is cheapest and closest."},
            new String[]{"§7Red floor means PvP is live. Grey paths are safe."},
            new String[]{"§7Read the rules with §f/rules§7. Breaking them gets you jailed."},
            new String[]{"§7Upgrade your pickaxe at the enchanter, in the middle of the hub."});

    private final PrisonPlugin plugin;
    private int next = 0;

    public ServerAnnouncer(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long period = 20L * 60 * EVERY_MINUTES;
        Bukkit.getScheduler().runTaskTimer(plugin, this, period, period);
    }

    @Override
    public void run() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;      // nobody to tell
        for (String line : MESSAGES.get(next)) {
            Bukkit.broadcastMessage(line);
        }
        next = (next + 1) % MESSAGES.size();
    }
}
