package com.lukeprison.prison;

import org.bukkit.scheduler.BukkitRunnable;

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
        tickCount++;
        for (String rank : builder.getMineBounds().keySet()) {
            double remaining = builder.percentRemaining(rank);
            // Reset if less than 35% ore blocks remain (i.e. 65%+ mined out),
            // or force a reset every 6th check (~30 min) regardless.
            if (remaining < 35 || tickCount % 6 == 0) {
                builder.resetMine(rank);
            }
        }
    }
}
