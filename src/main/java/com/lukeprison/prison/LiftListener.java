package com.lukeprison.prison;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;

/**
 * The mine cages.
 *
 * Every mine's ward holds a barred cage with a lodestone plate in the floor; the matching cage
 * on the mine's rim has one too. Using a plate rides the cage between the two.
 *
 * This is what lets the hub stay small. Mines live on their own level underground, so their
 * footprints never have to fit along the hub wall — which is the constraint that forced the old
 * hub out to 462 blocks across. The lift is the link, and thematically it is what a mine would
 * actually have.
 */
public class LiftListener implements Listener {

    private final PrisonPlugin plugin;

    public LiftListener(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.PHYSICAL) return;
        if (e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != Material.LODESTONE) return;

        Location key = e.getClickedBlock().getLocation();
        Map<Location, String> pads = plugin.builder().getLiftPads();
        String dest = pads.get(key);
        if (dest == null) {
            // Location equality includes the world and exact coordinates; fall back to a
            // block-coordinate match so a pad still works if the Location was built differently.
            for (Map.Entry<Location, String> entry : pads.entrySet()) {
                Location l = entry.getKey();
                if (l.getBlockX() == key.getBlockX() && l.getBlockY() == key.getBlockY()
                        && l.getBlockZ() == key.getBlockZ()) {
                    dest = entry.getValue();
                    break;
                }
            }
        }
        if (dest == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();

        if (dest.equals("HUB")) {
            // Returning up: back to the ward this mine's gate belongs to, so players surface
            // where they went down rather than at spawn.
            String rank = rankForLanding(key);
            Location back = rank == null ? plugin.builder().getHubSpawn() : plugin.builder().wardSpot(rank);
            ride(p, back, "§7Riding the cage up...");
            return;
        }

        if (!plugin.ranks().canAccessMine(p, dest)) {
            p.sendMessage("§cYou haven't unlocked Mine " + dest + " yet.");
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 0.8f);
            return;
        }
        ride(p, plugin.builder().safeMineSpot(dest), "§7Riding the cage down to Mine " + dest + "...");
    }

    /** Which mine a rim landing pad belongs to, matched on block coordinates. */
    private String rankForLanding(Location pad) {
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (!d.hasMine()) continue;
            if (d.landing[0] == pad.getBlockX() && d.landing[1] == pad.getBlockY()
                    && d.landing[2] == pad.getBlockZ()) {
                return d.rank;
            }
        }
        return null;
    }

    private void ride(Player p, Location to, String message) {
        p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 0.7f);
        p.teleport(to);
        p.sendMessage(message);
        p.playSound(to, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.4f);
    }
}
