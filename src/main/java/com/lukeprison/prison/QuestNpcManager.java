package com.lukeprison.prison;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Quest NPCs in the intake corridor. Uses plain villagers rather than a dependency like
 * Citizens, so the server needs no extra plugins.
 */
public class QuestNpcManager implements Listener {

    public static final String WARDEN_NAME = "\u00a76\u00a7lWarden";
    public static final String QUARTERMASTER_NAME = "\u00a7e\u00a7lQuartermaster";

    private final PrisonPlugin plugin;
    private final WorldBuilder builder;

    public QuestNpcManager(PrisonPlugin plugin, WorldBuilder builder) {
        this.plugin = plugin;
        this.builder = builder;
    }

    /** Spawns the NPCs if they aren't already present. Safe to call every boot. */
    public void spawnNpcs() {
        spawnIfMissing(builder.wardenNpcLocation(), WARDEN_NAME, Villager.Profession.NITWIT);
        spawnIfMissing(builder.quartermasterNpcLocation(), QUARTERMASTER_NAME, Villager.Profession.TOOLSMITH);
    }

    private void spawnIfMissing(Location loc, String name, Villager.Profession profession) {
        if (loc == null || loc.getWorld() == null) return;
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 6, 6, 6)) {
            if (e instanceof Villager v && name.equals(v.getCustomName())) return; // already there
        }
        Villager v = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        v.setCustomName(name);
        v.setCustomNameVisible(true);
        v.setProfession(profession);
        v.setAI(false);          // stay put
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setPersistent(true);
        v.setRemoveWhenFarAway(false);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager v)) return;
        String name = v.getCustomName();
        if (name == null) return;
        Player p = e.getPlayer();

        if (WARDEN_NAME.equals(name)) {
            e.setCancelled(true);
            p.sendMessage("");
            p.sendMessage("\u00a76\u00a7lWarden: \u00a7fWelcome to the prison, inmate.");
            p.sendMessage("\u00a77Work the mines, sell your haul, and buy your way up the ranks.");
            p.sendMessage("\u00a77See the \u00a7eQuartermaster\u00a77 down the corridor for your pickaxe.");
            p.sendMessage("\u00a77Reach rank \u00a7f" + FishingData.UNLOCK_RANK
                    + "\u00a77 and the fishing ponds open up to you.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 0.8f);
            return;
        }

        if (QUARTERMASTER_NAME.equals(name)) {
            e.setCancelled(true);
            if (plugin.ranks().hasReceivedKit(p)) {
                p.sendMessage("\u00a7e\u00a7lQuartermaster: \u00a7fYou've had your kit. Get to work.");
                return;
            }
            ItemStack pick = new ItemStack(Material.STONE_PICKAXE);
            PickaxeEnchants.refreshLore(pick);
            p.getInventory().addItem(pick);
            p.getInventory().addItem(new ItemStack(Material.TORCH, 16));
            plugin.ranks().markKitGiven(p);
            p.sendMessage("\u00a7e\u00a7lQuartermaster: \u00a7fHere. Don't lose it.");
            p.sendMessage("\u00a7aReceived a stone pickaxe and torches.");
            p.sendMessage("\u00a77Head east to A-Ward and start mining. \u00a7f/prison\u00a77 opens the menu.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1f, 1f);
        }
    }

    /** NPCs must not be killable. */
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Villager v)) return;
        String name = v.getCustomName();
        if (WARDEN_NAME.equals(name) || QUARTERMASTER_NAME.equals(name)) {
            e.setCancelled(true);
        }
    }
}
