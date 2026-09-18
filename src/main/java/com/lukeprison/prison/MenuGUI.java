package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The central /prison menu and the /warps mine-teleport menu. */
public class MenuGUI implements Listener {

    private static final String MENU_TITLE = "§8§lPrison Menu";
    private static final String WARPS_TITLE = "§8§lMine Warps";

    private final PrisonPlugin plugin;
    private final RanksGUI ranksGUI;
    private final EnchantGUI enchantGUI;
    private final Map<String, int[]> mineBounds;

    public MenuGUI(PrisonPlugin plugin, RanksGUI ranksGUI, EnchantGUI enchantGUI, Map<String, int[]> mineBounds) {
        this.plugin = plugin;
        this.ranksGUI = ranksGUI;
        this.enchantGUI = enchantGUI;
        this.mineBounds = mineBounds;
    }

    public void openMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);

        inv.setItem(10, item(Material.GOLD_BLOCK, "§6§lRanks",
                "§7View all ranks and rank up.",
                "§7Current: §e" + plugin.ranks().getRank(p)));
        inv.setItem(11, item(Material.ENCHANTING_TABLE, "§b§lEnchants",
                "§7Spend tokens on pickaxe enchants.",
                "§7Tokens: §e" + plugin.ranks().getTokens(p)));
        inv.setItem(12, item(Material.COMPASS, "§a§lMine Warps",
                "§7Teleport to any mine you've unlocked."));
        inv.setItem(13, item(Material.HOPPER, "§d§lAuto-Sell",
                "§7Sell blocks instantly as you mine.",
                "§7Status: " + (plugin.ranks().isAutoSell(p) ? "§aON" : "§cOFF"),
                "§7Click to toggle."));
        inv.setItem(14, item(Material.DIAMOND_PICKAXE, "§f§lYour Stats",
                "§7Blocks mined: §f" + plugin.ranks().getBlocksMined(p),
                "§7Prestige: §f" + plugin.ranks().getPrestige(p),
                "§7Balance: §a$" + String.format("%.2f", plugin.economy().getBalance(p))));
        inv.setItem(16, item(Material.NETHER_STAR, "§5§lPrestige",
                "§7Reset to rank A for a permanent bonus.",
                "§7Requires rank Free."));

        p.openInventory(inv);
    }

    public void openWarps(Player p) {
        List<RankMineData.Def> all = new ArrayList<>(RankMineData.RANKS.values());
        int size = ((all.size() + 8) / 9) * 9;
        Inventory inv = Bukkit.createInventory(null, size, WARPS_TITLE);

        for (int i = 0; i < all.size(); i++) {
            RankMineData.Def def = all.get(i);
            if (def.rank.equals("FREE")) continue;
            boolean unlocked = plugin.ranks().canAccessMine(p, def.rank);
            inv.setItem(i, item(
                    unlocked ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    (unlocked ? "§a§lMine " : "§7§lMine ") + def.rank,
                    unlocked ? "§7Click to teleport." : "§cLocked — rank up to access.",
                    "§7Ore: §f" + def.common));
        }
        p.openInventory(inv);
    }

    private ItemStack item(Material mat, String name, String... loreLines) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        for (String l : loreLines) lore.add(l);
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!MENU_TITLE.equals(title) && !WARPS_TITLE.equals(title)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;

        String name = e.getCurrentItem().getItemMeta().getDisplayName();

        if (MENU_TITLE.equals(title)) {
            switch (name) {
                case "§6§lRanks" -> { p.closeInventory(); ranksGUI.open(p); }
                case "§b§lEnchants" -> { p.closeInventory(); enchantGUI.open(p); }
                case "§a§lMine Warps" -> openWarps(p);
                case "§d§lAuto-Sell" -> {
                    boolean on = plugin.ranks().toggleAutoSell(p);
                    p.sendMessage(on ? "§aAuto-sell enabled." : "§cAuto-sell disabled.");
                    openMenu(p);
                }
                case "§5§lPrestige" -> {
                    p.closeInventory();
                    p.performCommand("prestige");
                }
                default -> { }
            }
            return;
        }

        // Warps menu: teleport into the clicked mine if unlocked.
        if (!name.contains("Mine ")) return;
        String rank = name.substring(name.indexOf("Mine ") + 5).trim();
        if (!plugin.ranks().canAccessMine(p, rank)) {
            p.sendMessage("§cYou haven't unlocked that mine yet.");
            return;
        }
        int[] b = mineBounds.get(rank);
        if (b == null) return;
        // Drop them just inside the entrance, on the floor.
        Location loc = new Location(p.getWorld(), b[0] + 2.5, b[1] + 1, b[2] + 3.5);
        p.teleport(loc);
        p.sendMessage("§aWarped to Mine " + rank + ".");
        p.closeInventory();
    }
}
