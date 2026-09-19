package com.lukeprison.prison;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One place that decides whether a player is allowed to teleport.
 *
 * There are nine teleport sites across the plugin — warps, the mine menu, cell home, the
 * lifts, the fishing menu — and gating each one individually means the tenth will be
 * written without a check. This listens for the teleport itself, so every route out is
 * covered, including any added later.
 *
 * Two things block a teleport: being jailed, and being in combat. Both are escapes from a
 * consequence, which is the same exploit wearing different clothes.
 *
 * Teleports the SERVER performs for safety — putting someone in the jail, or evacuating a
 * mine that is about to reset — are exempt, because being trapped is worse than either
 * thing this prevents.
 */
public class TeleportGuard implements Listener {

    private final PrisonPlugin plugin;
    private final Set<UUID> bypassing = new HashSet<>();

    public TeleportGuard(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    /** Runs a teleport the guard must not block. */
    public void forced(Player p, Runnable teleport) {
        bypassing.add(p.getUniqueId());
        try {
            teleport.run();
        } finally {
            bypassing.remove(p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (bypassing.contains(p.getUniqueId())) return;

        // Walking through a portal or being pulled by an ender pearl is not a command.
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN
                && e.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) {
            return;
        }

        if (plugin.jail() != null && plugin.jail().isJailed(p)
                && !plugin.builder().inJail(e.getTo())) {
            e.setCancelled(true);
            p.sendMessage("§cYou are jailed for another "
                    + plugin.jail().minutesLeft(p) + " minutes.");
            return;
        }
        if (plugin.combat() != null && plugin.combat().blockTeleport(p)) {
            e.setCancelled(true);
        }
    }
}
