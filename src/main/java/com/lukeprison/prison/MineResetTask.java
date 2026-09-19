package com.lukeprison.prison;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Auto-resets mines, safely.
 *
 * A reset refills every block in the mine — including the ones a player is standing inside. So
 * before any refill, everyone in that mine gets a 10-second countdown, then is moved to the mine
 * entrance. Silent resets that entomb people are the classic prison-server rage-quit.
 */
public class MineResetTask extends BukkitRunnable {

    private final PrisonPlugin plugin;
    private final WorldBuilder builder;
    private int tickCount = 0;


    public MineResetTask(PrisonPlugin plugin, WorldBuilder builder) {
        this.plugin = plugin;
        this.builder = builder;
    }

    @Override
    public void run() {
        if (!builder.alreadyBuilt()) return; // never touch an unbuilt world
        tickCount++;
        for (String rank : builder.getMineBounds().keySet()) {
            double remaining = builder.percentRemaining(rank);
            if (remaining < 35 || tickCount % 6 == 0) {
                scheduleReset(rank);
            }
        }
    }

    private void scheduleReset(String rank) {
        List<Player> inside = playersIn(rank);
        if (inside.isEmpty()) {
            builder.resetMine(rank);
            return;
        }

        // Ten-second warning to everyone inside, then a safe teleport and the refill.
        for (Player p : inside) {
            p.showTitle(Title.title(
                    Component.text("MINE RESET", NamedTextColor.RED),
                    Component.text("Mine " + rank + " resets in 10 seconds", NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(1))));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.7f);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!builder.getMineBounds().containsKey(rank)) return;
            // The mine's own cage landing. The old spot was hardcoded to the mine's WEST side,
            // which is only where the entrance is for a minority of mines — everywhere else it
            // dropped players into the shell or the void mid-reset.
            Location safe = builder.safeMineSpot(rank);
            for (Player p : playersIn(rank)) {
                // Forced: the teleport guard must never leave someone standing in a mine
                // that is about to be rewritten, whatever else is true of them.
                plugin.teleports().forced(p, () -> p.teleport(safe));
                p.sendMessage("§eMine " + rank + " is resetting — you've been moved to the entrance.");
            }
            builder.resetMine(rank);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.ranks().getRank(p).equals(rank)) {
                    p.sendMessage("§aMine " + rank + " has been reset.");
                }
            }
        }, 20L * 10);
    }

    private List<Player> playersIn(String rank) {
        int[] b = builder.getMineBounds().get(rank);
        List<Player> out = new ArrayList<>();
        if (b == null) return out;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            if (!l.getWorld().equals(builder.getWorld())) continue;
            int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] + 2 && z >= b[2] && z <= b[5]) out.add(p);
        }
        return out;
    }
}
