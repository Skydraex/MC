package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Combat tagging.
 *
 * The Yard has open PvP and nothing stopped a losing player from disconnecting a heartbeat
 * before they died, keeping everything they were carrying. That single exploit is enough to
 * make a PvP area pointless, which is why combat tagging is on every prison server of this
 * era's plugin list.
 *
 * Fifteen seconds. While tagged you cannot warp, go home, or log out safely — quitting kills
 * you and drops your inventory exactly as dying would have.
 */
public class CombatTagListener implements Listener {

    private static final long TAG_MS = 15_000;

    private final PrisonPlugin plugin;
    private final Map<UUID, Long> tagged = new HashMap<>();

    public CombatTagListener(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    /** Seconds left on the tag, or 0. */
    public int secondsLeft(Player p) {
        Long until = tagged.get(p.getUniqueId());
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            tagged.remove(p.getUniqueId());
            return 0;
        }
        return (int) Math.ceil(left / 1000.0);
    }

    public boolean isTagged(Player p) {
        return secondsLeft(p) > 0;
    }

    /**
     * True if the player may NOT do the thing they just tried. Used by every teleport
     * command, because escaping a fight by warping out is the same exploit as logging out.
     */
    public boolean blockTeleport(Player p) {
        int left = secondsLeft(p);
        if (left <= 0) return false;
        p.sendMessage("§cYou are in combat — wait §f" + left + "s§c.");
        return true;
    }

    private void tag(Player p) {
        boolean wasClear = !isTagged(p);
        tagged.put(p.getUniqueId(), System.currentTimeMillis() + TAG_MS);
        if (wasClear) {
            p.sendMessage("§c§lCOMBAT §7— you cannot warp or log out for 15 seconds.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (e.getDamager() instanceof Player direct) {
            attacker = direct;
        } else if (e.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null || attacker.equals(victim)) return;

        tag(victim);
        tag(attacker);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!isTagged(p)) return;
        // Logging out mid-fight is treated as dying, which is the whole point.
        tagged.remove(p.getUniqueId());
        p.setHealth(0.0);
        Bukkit.broadcastMessage("§8" + p.getName() + " §7logged out in combat.");
        plugin.getLogger().info(p.getName() + " combat-logged and was killed.");
    }
}
