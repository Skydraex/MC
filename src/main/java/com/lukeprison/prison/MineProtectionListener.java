package com.lukeprison.prison;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Map;

public class MineProtectionListener implements Listener {

    private final PrisonPlugin plugin;
    private final Map<String, int[]> mineBounds;

    public MineProtectionListener(PrisonPlugin plugin, Map<String, int[]> mineBounds) {
        this.plugin = plugin;
        this.mineBounds = mineBounds;
    }

    private String mineAt(int x, int y, int z) {
        for (Map.Entry<String, int[]> e : mineBounds.entrySet()) {
            int[] b = e.getValue();
            if (x > b[0] && x < b[3] && y > b[1] && y < b[4] && z > b[2] && z < b[5]) return e.getKey();
        }
        return null;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        String mine = mineAt(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ());
        if (mine == null) return;
        Player p = e.getPlayer();
        if (!plugin.ranks().canAccessMine(p, mine)) {
            e.setCancelled(true);
            p.sendMessage("§cYou need to rank up to mine here.");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        // No building inside mines — keeps the layout intact.
        String mine = mineAt(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ());
        if (mine != null) e.setCancelled(true);
    }
}
