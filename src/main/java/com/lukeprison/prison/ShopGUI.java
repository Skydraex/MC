package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The server shop, and the main sink for money. Everything is gated by rank so the shop can't
 * short-circuit progression: you can't buy a diamond pickaxe before you're mining diamonds.
 */
public class ShopGUI implements Listener {

    private static final String TITLE = "§8§lPrison Shop";

    public record Item(String key, Material mat, int amount, double price, String requiredRank, String note) { }

    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    static {
        add(new Item("stone_pick", Material.STONE_PICKAXE, 1, 250, "A", "Replacement starter pick"));
        add(new Item("iron_pick", Material.IRON_PICKAXE, 1, 6_000, "E", "First real upgrade"));
        add(new Item("diamond_pick", Material.DIAMOND_PICKAXE, 1, 120_000, "M", "Only once you're mining diamonds"));
        add(new Item("fishing_rod", Material.FISHING_ROD, 1, 2_500, FishingData.UNLOCK_RANK, "For the ponds"));
        add(new Item("iron_helmet", Material.IRON_HELMET, 1, 4_000, "D", "PvP protection"));
        add(new Item("iron_chest", Material.IRON_CHESTPLATE, 1, 8_000, "D", "PvP protection"));
        add(new Item("iron_legs", Material.IRON_LEGGINGS, 1, 7_000, "D", "PvP protection"));
        add(new Item("iron_boots", Material.IRON_BOOTS, 1, 4_000, "D", "PvP protection"));
        add(new Item("iron_sword", Material.IRON_SWORD, 1, 5_000, "D", "For the Yard"));
        add(new Item("bow", Material.BOW, 1, 6_000, "G", "For the Yard"));
        add(new Item("arrows", Material.ARROW, 32, 1_500, "G", "32 arrows"));
        add(new Item("steak", Material.COOKED_BEEF, 16, 800, "A", "16 steak"));
        add(new Item("golden_apple", Material.GOLDEN_APPLE, 1, 15_000, "J", "Emergency heal"));
        add(new Item("torches", Material.TORCH, 32, 300, "A", "32 torches"));
        add(new Item("ender_chest", Material.ENDER_CHEST, 1, 40_000, "H", "Portable storage"));
    }

    private static void add(Item i) { ITEMS.put(i.key(), i); }

    private final PrisonPlugin plugin;

    public ShopGUI(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        int slot = 0;
        for (Item i : ITEMS.values()) {
            boolean unlocked = plugin.ranks().canAccessMine(p, i.requiredRank());
            ItemStack it = new ItemStack(unlocked ? i.mat() : Material.GRAY_STAINED_GLASS_PANE, Math.max(1, Math.min(64, i.amount())));
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName((unlocked ? "§f" : "§7") + pretty(i.mat()) + (i.amount() > 1 ? " x" + i.amount() : ""));
            List<String> lore = new ArrayList<>();
            lore.add("§7" + i.note());
            lore.add("");
            if (unlocked) {
                lore.add("§7Price: §a$" + String.format("%,.0f", i.price()));
                lore.add(plugin.economy().has(p, i.price()) ? "§aClick to buy" : "§cCan't afford");
            } else {
                lore.add("§cUnlocks at rank " + i.requiredRank());
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "shop_key"),
                    org.bukkit.persistence.PersistentDataType.STRING, i.key());
            it.setItemMeta(meta);
            inv.setItem(slot++, it);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;

        String key = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "shop_key"),
                org.bukkit.persistence.PersistentDataType.STRING);
        Item i = key == null ? null : ITEMS.get(key);
        if (i == null) return;

        if (!plugin.ranks().canAccessMine(p, i.requiredRank())) {
            p.sendMessage("§cThat unlocks at rank " + i.requiredRank() + ".");
            return;
        }
        if (!plugin.economy().has(p, i.price())) {
            p.sendMessage("§cYou need $" + String.format("%,.0f", i.price()) + ".");
            return;
        }
        if (p.getInventory().firstEmpty() == -1) {
            p.sendMessage("§cYour inventory is full.");
            return;
        }
        plugin.economy().withdrawPlayer(p, i.price());
        ItemStack bought = new ItemStack(i.mat(), i.amount());
        if (PickaxeEnchants.isPickaxe(bought)) PickaxeEnchants.refreshLore(bought);
        p.getInventory().addItem(bought);
        p.sendMessage("§aBought §f" + pretty(i.mat()) + (i.amount() > 1 ? " x" + i.amount() : "")
                + "§a for §6$" + String.format("%,.0f", i.price()));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        plugin.scoreboard().update(p);
        open(p);
    }

    private String pretty(Material mat) {
        StringBuilder sb = new StringBuilder();
        for (String part : mat.name().toLowerCase().split("_")) {
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    public static class Cmd implements CommandExecutor {
        private final ShopGUI gui;
        public Cmd(ShopGUI gui) { this.gui = gui; }
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (s instanceof Player p) gui.open(p);
            return true;
        }
    }
}
