package com.lukeprison.prison;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/** Puts brand-new players at the prison bus in the starter yard and points them down the intake corridor. */
public class KitListener implements Listener {

    private final PrisonPlugin plugin;
    private final Location starterSpawn;

    public KitListener(PrisonPlugin plugin, Location starterSpawn) {
        this.plugin = plugin;
        this.starterSpawn = starterSpawn;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (plugin.ranks().hasReceivedKit(p)) return;

        // New players arrive at the prison bus in the starter yard. The Quartermaster NPC in the
        // intake corridor hands out the actual pickaxe, so no gear is given here \u2014 that keeps the
        // tutorial flow intact instead of short-circuiting it.
        if (starterSpawn != null) p.teleport(starterSpawn);

        p.sendMessage("");
        p.sendMessage("\u00a78\u00a7l\u00bb \u00a76\u00a7lYOU HAVE ARRIVED AT THE PRISON");
        p.sendMessage("\u00a77Step off the bus and follow the corridor east.");
        p.sendMessage("\u00a77Speak to the \u00a76Warden\u00a77 and the \u00a7eQuartermaster\u00a77 on your way in.");
        p.sendMessage("");
    }
}
