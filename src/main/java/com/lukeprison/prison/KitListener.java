package com.lukeprison.prison;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/** Gives every new player a starter pickaxe + torches on first join, and drops them in the hub. */
public class KitListener implements Listener {

    private final PrisonPlugin plugin;
    private final Location hubSpawn;

    public KitListener(PrisonPlugin plugin, Location hubSpawn) {
        this.plugin = plugin;
        this.hubSpawn = hubSpawn;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (plugin.ranks().hasReceivedKit(p)) return;

        p.getInventory().addItem(new ItemStack(Material.WOODEN_PICKAXE));
        p.getInventory().addItem(new ItemStack(Material.TORCH, 16));
        plugin.ranks().markKitGiven(p);

        if (hubSpawn != null) p.teleport(hubSpawn);

        p.sendMessage("§aWelcome to the prison. You've been given a starter pickaxe — head out to Mine A to begin.");
    }
}
