package com.lukeprison.prison;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;
import java.util.Set;

/**
 * Anti-grief and exploit hardening, sized for a public launch.
 *
 * The core rule: the world is a built map, not a survival world. Players may only break blocks
 * inside mines, and may only place blocks in the small number of places where building is the
 * point (shop plots). Everything else — fire, liquids, explosions, pistons, mob griefing — is
 * simply switched off, because on a public server any of them eventually gets weaponised.
 */
public class ProtectionListener implements Listener {

    private final PrisonPlugin plugin;
    private final Map<String, int[]> mineBounds;
    private int[] loggingBounds; // set post-construction, since the plugin builds it after registering this listener

    /** Items that let players move or destroy terrain, so they're not usable at all. */
    private static final Set<Material> BANNED_INTERACT = Set.of(
            Material.LAVA_BUCKET, Material.WATER_BUCKET, Material.FLINT_AND_STEEL,
            Material.FIRE_CHARGE, Material.TNT, Material.END_CRYSTAL,
            Material.RESPAWN_ANCHOR, Material.BEDROCK);

    public ProtectionListener(PrisonPlugin plugin, Map<String, int[]> mineBounds) {
        this.plugin = plugin;
        this.mineBounds = mineBounds;
    }

    public void setLoggingBounds(int[] bounds) { this.loggingBounds = bounds; }

    private boolean inMine(int x, int y, int z) {
        for (int[] b : mineBounds.values()) {
            if (x > b[0] && x < b[3] && y > b[1] && y < b[4] && z > b[2] && z < b[5]) return true;
        }
        return false;
    }

    private boolean inLoggingYard(int x, int z) {
        if (loggingBounds == null) return false;
        return x >= loggingBounds[0] && x <= loggingBounds[2] && z >= loggingBounds[1] && z <= loggingBounds[3];
    }

    private boolean bypasses(Player p) {
        return p.hasPermission("prison.admin") || p.getGameMode() == GameMode.CREATIVE;
    }

    /** Breaking is confined to mines, plus tree blocks inside the logging yard. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (bypasses(p)) return;
        Material type = e.getBlock().getType();

        // Shop signs and chests are handled by ShopSignListener's ownership check.
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST
                || type.name().endsWith("_SIGN")) return;

        boolean isTree = type == Material.OAK_LOG || type == Material.OAK_LEAVES;
        if (isTree && inLoggingYard(e.getBlock().getX(), e.getBlock().getZ())) return;

        if (!inMine(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ())) {
            e.setCancelled(true);
            p.sendMessage("§cYou can only mine inside a mine.");
        }
    }

    /**
     * Placing is blocked everywhere except shop furniture. Free placement on a built map is how
     * you get obsidian towers in spawn and cobble-walled mine entrances.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (bypasses(p)) return;

        Material type = e.getBlockPlaced().getType();
        boolean shopFurniture = type == Material.CHEST || type == Material.TRAPPED_CHEST
                || type.name().endsWith("_SIGN");

        if (!shopFurniture) {
            e.setCancelled(true);
            p.sendMessage("§cYou can't build here. Chests and signs only, for shops.");
            return;
        }

        // Never allow shop furniture inside a mine — it would survive resets and block ore.
        if (inMine(e.getBlockPlaced().getX(), e.getBlockPlaced().getY(), e.getBlockPlaced().getZ())) {
            e.setCancelled(true);
            p.sendMessage("§cYou can't place that inside a mine.");
        }
    }

    /** Terrain-altering and grief items are unusable outright. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (bypasses(p)) return;
        if (e.getItem() == null) return;
        if (BANNED_INTERACT.contains(e.getItem().getType())) {
            e.setCancelled(true);
            p.sendMessage("§cThat item is disabled on this server.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (bypasses(e.getPlayer())) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage("§cYou can't place liquids here.");
    }

    // ---- Environmental griefing, all switched off -------------------------

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        // No explosion ever modifies the map.
        e.blockList().clear();
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getPlayer() != null && bypasses(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onBurn(BlockBurnEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void onSpread(BlockSpreadEvent e) {
        // Stops fire and vegetation creeping across the built map.
        if (e.getSource().getType() == Material.FIRE) e.setCancelled(true);
    }

    @EventHandler
    public void onFlow(BlockFromToEvent e) {
        Material t = e.getBlock().getType();
        if (t == Material.WATER || t == Material.LAVA) {
            // Let pond water settle inside its own basin, but never flow across the map.
            if (!isPondWater(e.getToBlock().getX(), e.getToBlock().getZ())) e.setCancelled(true);
        }
    }

    private boolean isPondWater(int x, int z) {
        for (FishingData.Pond pond : FishingData.PONDS.values()) {
            if (x >= pond.x1 && x <= pond.x2 && z >= pond.z1 && z <= pond.z2) return true;
        }
        return false;
    }

    @EventHandler
    public void onPiston(BlockPistonExtendEvent e) {
        // Piston machines are a classic route to moving protected blocks.
        e.setCancelled(true);
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        // Endermen picking up blocks, sheep eating grass, zombies breaking doors.
        e.setCancelled(true);
    }

    @EventHandler
    public void onHangingBreak(HangingBreakEvent e) {
        e.setCancelled(true);
    }

    /**
     * Dropping items is blocked inside mines only — it's the usual way players dodge the
     * intended economy by passing stock around mid-grind, and it litters the floor.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (bypasses(p)) return;
        var loc = p.getLocation();
        if (inMine(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
            e.setCancelled(true);
            p.sendMessage("§cYou can't drop items inside a mine.");
        }
    }
}
