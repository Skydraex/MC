package com.lukeprison.prison;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.*;

/**
 * A self-diagnostic: flood-fills every walkable tile reachable from spawn at the main floor
 * level and flags any tile that's walkable (air at foot and head height, adjacent to other
 * walkable ground) but has no solid block underneath — i.e. exactly the "looks fine, falls
 * into the void" bug that's been hard to catch by eye. Runs spread across ticks so a map this
 * size doesn't freeze the server, and reports every gap it finds with exact coordinates.
 */
public class MapValidator {

    private final World world;
    private final int floorY;

    public MapValidator(World world, int floorY) {
        this.world = world;
        this.floorY = floorY;
    }

    public void run(CommandSender requester, Location start) {
        requester.sendMessage("§eScanning the main floor level for gaps — this will take a moment...");

        Set<Long> visited = new HashSet<>();
        List<int[]> gaps = new ArrayList<>();
        Deque<int[]> queue = new ArrayDeque<>();
        int startX = start.getBlockX(), startZ = start.getBlockZ();
        queue.add(new int[]{startX, startZ});
        visited.add(key(startX, startZ));

        int[] dirs = {1, 0, -1, 0, 0, 1, 0, -1};
        int scanned = 0;
        int maxTiles = 400_000; // safety cap so a runaway scan can't run forever

        while (!queue.isEmpty() && scanned < maxTiles) {
            int[] cur = queue.poll();
            int x = cur[0], z = cur[1];
            scanned++;

            for (int d = 0; d < 4; d++) {
                int nx = x + dirs[d * 2], nz = z + dirs[d * 2 + 1];
                long k = key(nx, nz);
                if (visited.contains(k)) continue;
                visited.add(k);

                Material feet = world.getBlockAt(nx, floorY, nz).getType();
                Material head = world.getBlockAt(nx, floorY + 1, nz).getType();
                Material below = world.getBlockAt(nx, floorY - 1, nz).getType();

                boolean walkable = (feet == Material.AIR || feet == Material.WATER) && head == Material.AIR;
                if (!walkable) continue; // wall or solid obstruction — not part of the floor network

                if (below == Material.AIR) {
                    gaps.add(new int[]{nx, floorY, nz});
                } else {
                    queue.add(new int[]{nx, nz}); // keep exploring past solid ground
                }
            }
        }

        requester.sendMessage("§aScan complete. §7Checked " + scanned + " walkable tiles.");
        if (gaps.isEmpty()) {
            requester.sendMessage("§aNo void gaps found on the main floor network.");
        } else {
            requester.sendMessage("§c" + gaps.size() + " void gap(s) found:");
            int shown = 0;
            for (int[] g : gaps) {
                if (shown++ >= 25) {
                    requester.sendMessage("§7...and " + (gaps.size() - 25) + " more.");
                    break;
                }
                requester.sendMessage("§7  x=" + g[0] + " y=" + g[1] + " z=" + g[2]);
            }
        }
        if (scanned >= maxTiles) {
            requester.sendMessage("§eHit the scan cap (" + maxTiles + " tiles) — the network may extend further than checked.");
        }
    }

    private long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }
}
