package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A real jail, because this is a prison.
 *
 * Staff put someone in it for a number of minutes; they are teleported into a cell block
 * under the hub and cannot leave it, mine, build or warp until the sentence runs out, at
 * which point they are released automatically. A sentence survives logging out — the clock
 * runs whether or not they are online, but they go straight back in if they rejoin early,
 * so quitting neither serves the time faster nor escapes it.
 */
public class JailManager implements Listener, Runnable {

    private final PrisonPlugin plugin;
    /** player -> when their sentence ends. */
    private final Map<UUID, Long> sentences = new HashMap<>();

    public JailManager(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this, 20L * 10, 20L * 10);
    }

    public boolean isJailed(Player p) {
        Long until = sentences.get(p.getUniqueId());
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            release(p);
            return false;
        }
        return true;
    }

    public long minutesLeft(Player p) {
        Long until = sentences.get(p.getUniqueId());
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 60_000L);
    }

    public void jail(Player p, long minutes, String by) {
        sentences.put(p.getUniqueId(), System.currentTimeMillis() + minutes * 60_000L);
        plugin.teleports().forced(p, () -> p.teleport(plugin.builder().jailSpawn()));
        p.sendMessage("§4§lJAILED §7for §f" + minutes + " minutes§7 by " + by + ".");
        p.sendMessage("§7You cannot leave, mine or build until it is served.");
        plugin.getLogger().info("[staff] " + by + " jailed " + p.getName() + " for " + minutes + "m");
    }

    public void release(Player p) {
        if (sentences.remove(p.getUniqueId()) == null) return;
        plugin.teleports().forced(p, () -> p.teleport(plugin.builder().getHubSpawn()));
        p.sendMessage("§aYour sentence is served. Stay out of trouble.");
    }

    /** Sweeps for served sentences, so release happens without the player doing anything. */
    @Override
    public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Long until = sentences.get(p.getUniqueId());
            if (until != null && System.currentTimeMillis() >= until) release(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Back in the cell if the sentence is still running.
        if (isJailed(e.getPlayer())) {
            plugin.teleports().forced(e.getPlayer(), () -> e.getPlayer().teleport(plugin.builder().jailSpawn()));
            e.getPlayer().sendMessage("§4Still jailed — §f" + minutesLeft(e.getPlayer())
                    + " minutes§4 left.");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null || !isJailed(e.getPlayer())) return;
        if (plugin.builder().inJail(e.getTo())) return;
        plugin.teleports().forced(e.getPlayer(), () -> e.getPlayer().teleport(plugin.builder().jailSpawn()));
        e.getPlayer().sendMessage("§cYou are jailed for another "
                + minutesLeft(e.getPlayer()) + " minutes.");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (isJailed(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cNot while you are jailed.");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (isJailed(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    /** Teleport commands consult this so a jailed player cannot warp out. */
    public boolean blockTeleport(Player p) {
        if (!isJailed(p)) return false;
        p.sendMessage("§cYou are jailed for another " + minutesLeft(p) + " minutes.");
        return true;
    }
}
