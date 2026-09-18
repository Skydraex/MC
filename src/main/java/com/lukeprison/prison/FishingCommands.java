package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** The /fishing pond menu, /sellfish, and /spawn. */
public class FishingCommands implements Listener {

    private static final String TITLE = "\u00a78\u00a7lFishing Ponds";
    private final PrisonPlugin plugin;

    public FishingCommands(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void openPonds(Player p) {
        if (!plugin.ranks().canAccessMine(p, FishingData.UNLOCK_RANK)) {
            p.sendMessage("\u00a7cFishing unlocks at rank " + FishingData.UNLOCK_RANK + ".");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        int level = plugin.fishing().getLevel(p);

        for (FishingData.Pond pond : FishingData.PONDS.values()) {
            boolean unlocked = level >= pond.requiredLevel;
            ItemStack it = new ItemStack(unlocked ? Material.LIME_STAINED_GLASS_PANE
                    : Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName((unlocked ? "\u00a7b\u00a7l" : "\u00a77\u00a7l") + pond.name);
            List<String> lore = new ArrayList<>();
            lore.add("\u00a77Tier " + pond.tier);
            lore.add("\u00a77Requires fishing level \u00a7f" + pond.requiredLevel);
            lore.add("");
            for (FishingData.Fish f : pond.fish) {
                lore.add("\u00a78- \u00a7b" + f.display + " \u00a7a$" + String.format("%,.0f", f.value));
            }
            lore.add("");
            lore.add(unlocked ? "\u00a7aClick to travel here" : "\u00a7cLocked");
            meta.setLore(lore);
            it.setItemMeta(meta);
            inv.setItem(pond.tier - 1, it);
        }

        ItemStack info = new ItemStack(Material.FISHING_ROD);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("\u00a7b\u00a7lFishing Level: \u00a7f" + level);
        List<String> lore = new ArrayList<>();
        lore.add("\u00a77XP: \u00a7f" + plugin.fishing().getXp(p));
        long toNext = plugin.fishing().xpToNext(p);
        lore.add(toNext > 0 ? "\u00a77Next level in: \u00a7f" + toNext + " XP" : "\u00a7aMax level");
        lore.add("");
        lore.add("\u00a77Everyone fishes the same ponds \u2014");
        lore.add("\u00a77higher levels just reach further out.");
        im.setLore(lore);
        info.setItemMeta(im);
        inv.setItem(22, info);

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;

        String name = e.getCurrentItem().getItemMeta().getDisplayName();
        for (FishingData.Pond pond : FishingData.PONDS.values()) {
            if (!name.endsWith(pond.name)) continue;
            if (plugin.fishing().getLevel(p) < pond.requiredLevel) {
                p.sendMessage("\u00a7cYou need fishing level " + pond.requiredLevel + " for that pond.");
                return;
            }
            Location loc = new Location(p.getWorld(), pond.x1 + 2.5, pond.y + 1, pond.z2 + 2.5);
            p.teleport(loc);
            p.sendMessage("\u00a7aTravelled to " + pond.name + ".");
            p.closeInventory();
            return;
        }
    }

    public static class Fishing implements CommandExecutor {
        private final FishingCommands gui;
        public Fishing(FishingCommands gui) { this.gui = gui; }
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (s instanceof Player p) gui.openPonds(p);
            return true;
        }
    }

    /** Sells every custom fish in the player's inventory. */
    public static class SellFish implements CommandExecutor {
        private final PrisonPlugin plugin;
        public SellFish(PrisonPlugin plugin) { this.plugin = plugin; }
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (!(s instanceof Player p)) return true;
            double total = 0;
            int count = 0;
            ItemStack[] contents = p.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item == null) continue;
                double each = FishingListener.valueOf(item);
                if (each <= 0) continue;
                total += each * item.getAmount();
                count += item.getAmount();
                p.getInventory().setItem(i, null);
            }
            if (count == 0) {
                p.sendMessage("\u00a7cYou have no fish to sell.");
                return true;
            }
            plugin.economy().depositPlayer(p, total);
            p.sendMessage("\u00a7aSold " + count + " fish for \u00a76$" + String.format("%,.2f", total));
            return true;
        }
    }

    /** /spawn puts players in the hub — deliberately past the starter area. */
    public static class Spawn implements CommandExecutor {
        private final PrisonPlugin plugin;
        public Spawn(PrisonPlugin plugin) { this.plugin = plugin; }
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (!(s instanceof Player p)) return true;
            Location hub = plugin.builder().getHubSpawn();
            if (hub == null) {
                p.sendMessage("\u00a7cSpawn isn't ready yet.");
                return true;
            }
            p.teleport(hub);
            p.sendMessage("\u00a7aWelcome back to the hub.");
            return true;
        }
    }
}
