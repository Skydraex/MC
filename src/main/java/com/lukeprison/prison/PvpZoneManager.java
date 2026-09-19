package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * PvP zones, marked out with red wool in the traditional prison-server style.
 *
 * Inside a zone, players can hurt each other and a kill transfers the victim's inventory
 * straight to the killer. Outside, PvP is off entirely, so the risk is opt-in by walking in.
 */
public class PvpZoneManager implements Listener {

    public static class Zone {
        public final String name;
        public final int x1, y1, z1, x2, y2, z2;
        /**
         * When true this zone only applies where the player is actually STANDING ON red wool,
         * not everywhere inside its bounding box.
         *
         * The hub is one big rectangle containing walkways, the plaza and four buildings, all
         * of which must stay safe. Marking the whole rectangle as PvP — which is what this
         * class used to do — turned the entire hub hostile the moment you walked in. The red
         * floor is the contract with the player, so the floor is what decides.
         */
        public final boolean redFloorOnly;

        public Zone(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
            this(name, x1, y1, z1, x2, y2, z2, false);
        }

        public Zone(String name, int x1, int y1, int z1, int x2, int y2, int z2, boolean redFloorOnly) {
            this.name = name;
            this.x1 = Math.min(x1, x2); this.x2 = Math.max(x1, x2);
            this.y1 = Math.min(y1, y2); this.y2 = Math.max(y1, y2);
            this.z1 = Math.min(z1, z2); this.z2 = Math.max(z1, z2);
            this.redFloorOnly = redFloorOnly;
        }

        public boolean contains(Location loc) {
            int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
            if (!(x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2)) return false;
            return !redFloorOnly || standingOnRed(loc);
        }

        /** The block underfoot — checked one and two below, so jumping does not clear PvP. */
        private static boolean standingOnRed(Location loc) {
            if (loc.getWorld() == null) return false;
            for (int dy = 1; dy <= 2; dy++) {
                Material m = loc.getWorld().getBlockAt(
                        loc.getBlockX(), loc.getBlockY() - dy, loc.getBlockZ()).getType();
                if (m == Material.RED_WOOL || m == Material.RED_CONCRETE) return true;
                if (!m.isAir()) return false;   // standing on something solid that is not red
            }
            return false;
        }
    }

    private final PrisonPlugin plugin;
    private final List<Zone> zones = new ArrayList<>();
    private final Set<UUID> inside = new HashSet<>();

    public PvpZoneManager(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void addZone(Zone zone) {
        zones.add(zone);
    }

    public List<Zone> getZones() {
        return zones;
    }

    public Zone zoneAt(Location loc) {
        for (Zone z : zones) {
            if (z.contains(loc)) return z;
        }
        return null;
    }

    public boolean isInPvp(Player p) {
        return zoneAt(p.getLocation()) != null;
    }

    /** Warn players as they cross in and out, so entering is always a conscious choice. */
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        Player p = e.getPlayer();
        Zone zone = zoneAt(e.getTo());
        boolean wasInside = inside.contains(p.getUniqueId());

        if (zone != null && !wasInside) {
            inside.add(p.getUniqueId());
            p.sendMessage("§c§l⚠ ENTERING PVP: §f" + zone.name);
            p.sendMessage("§7You will §cdrop everything §7to your killer here.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f);
        } else if (zone == null && wasInside) {
            inside.remove(p.getUniqueId());
            p.sendMessage("§a§l✔ Left the PvP zone. §7You're safe again.");
        }
    }

    /** PvP only works inside a zone — elsewhere player damage is cancelled. */
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (e.getDamager() instanceof Player dp) {
            attacker = dp;
        } else if (e.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player sp) {
            attacker = sp;
        }
        if (attacker == null) return;

        // Both parties must be inside a zone, so nobody can be sniped from safety.
        if (zoneAt(victim.getLocation()) == null || zoneAt(attacker.getLocation()) == null) {
            e.setCancelled(true);
            attacker.sendMessage("§cYou can only fight inside a PvP zone.");
        }
    }

    /** A kill inside a zone hands the victim's inventory to the killer. */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (zoneAt(victim.getLocation()) == null) {
            // Outside PvP zones players keep everything — no punishing accidental deaths.
            e.setKeepInventory(true);
            e.getDrops().clear();
            return;
        }

        Player killer = victim.getKiller();
        if (killer == null) return;

        List<ItemStack> loot = new ArrayList<>(e.getDrops());
        e.getDrops().clear();

        int moved = 0;
        for (ItemStack item : loot) {
            if (item == null || item.getType().isAir()) continue;
            // Anything that won't fit falls at the killer's feet rather than vanishing.
            killer.getInventory().addItem(item).values()
                    .forEach(left -> killer.getWorld().dropItemNaturally(killer.getLocation(), left));
            moved++;
        }

        killer.sendMessage("§6You killed §f" + victim.getName() + "§6 and took their gear ("
                + moved + " stacks).");
        victim.sendMessage("§c§lYou were killed by §f" + killer.getName()
                + "§c and lost everything you carried.");
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);

        Bukkit.broadcastMessage("§c⚔ §f" + killer.getName() + " §7killed §f" + victim.getName()
                + " §7in a PvP zone.");
    }
}
