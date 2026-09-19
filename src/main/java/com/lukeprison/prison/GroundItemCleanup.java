package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Clears dropped items on a cycle, with warnings first.
 *
 * A prison server generates enormous quantities of dropped blocks — that is the entire
 * gameplay loop — and a mine reset with a hundred players in it can leave thousands of
 * item entities lying around. Left alone they cost more than the players do.
 *
 * Deliberately conservative. It removes dropped ITEMS and nothing else: no armour stands
 * (the holograms are armour stands), no villagers (the Warden and Quartermaster are
 * villagers), no players, no projectiles. It also spares anything that looks deliberate —
 * a renamed or enchanted item, or a crate key — because those are things a player has
 * earned or been given, and losing one to a timer is not recoverable.
 */
public class GroundItemCleanup implements Runnable {

    /** How long a cycle is, and when the two warnings land inside it. */
    private static final int CYCLE_SECONDS = 300;
    private static final int FIRST_WARNING = 30;
    private static final int FINAL_WARNING = 10;

    /** Items younger than this are left alone, so nothing vanishes under a player's feet. */
    private static final int MIN_AGE_TICKS = 20 * 30;

    private final PrisonPlugin plugin;

    public GroundItemCleanup(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    /** Starts the cycle: two warnings, then the sweep, repeating. */
    public void start() {
        long cycle = 20L * CYCLE_SECONDS;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> warn(FIRST_WARNING),
                20L * (CYCLE_SECONDS - FIRST_WARNING), cycle);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> warn(FINAL_WARNING),
                20L * (CYCLE_SECONDS - FINAL_WARNING), cycle);
        Bukkit.getScheduler().runTaskTimer(plugin, this, cycle, cycle);
    }

    private void warn(int seconds) {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        if (countSweepable() == 0) return;          // nothing to clear; don't cry wolf
        Bukkit.broadcastMessage("§e[Server] §7Ground items will be cleared in §f"
                + seconds + " seconds§7!");
    }

    @Override
    public void run() {
        int cleared = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (!(e instanceof Item item) || !sweepable(item)) continue;
                item.remove();
                cleared++;
            }
        }
        if (cleared > 0) {
            Bukkit.broadcastMessage("§e[Server] §7Cleared §f" + cleared
                    + "§7 ground items.");
            plugin.getLogger().info("Ground item cleanup removed " + cleared + " items.");
        }
    }

    private int countSweepable() {
        int n = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e instanceof Item item && sweepable(item)) n++;
            }
        }
        return n;
    }

    /** Ordinary mining spoil, and nothing a player would be upset to lose. */
    private boolean sweepable(Item item) {
        if (item.getTicksLived() < MIN_AGE_TICKS) return false;
        ItemStack stack = item.getItemStack();
        if (stack == null) return false;
        if (!stack.getEnchantments().isEmpty()) return false;
        if (CrateData.keyTypeOf(stack) != null) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && (meta.hasDisplayName() || meta.hasLore())) return false;
        return true;
    }
}
