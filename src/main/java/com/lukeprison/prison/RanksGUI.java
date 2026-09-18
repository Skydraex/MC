package com.lukeprison.prison;

import org.bukkit.Bukkit;
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

/** The full-ranks menu opened by /rankup: shows every rank, its cost, its mine, and what it unlocks. */
public class RanksGUI implements Listener {

    private static final String TITLE = "§8§lPrison Ranks";
    private final PrisonPlugin plugin;

    public RanksGUI(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        List<RankMineData.Def> all = new ArrayList<>(RankMineData.RANKS.values());
        int size = ((all.size() + 8) / 9) * 9; // round up to a full row
        Inventory inv = Bukkit.createInventory(null, size, TITLE);

        String current = plugin.ranks().getRank(p);
        int currentIdx = indexOf(all, current);

        for (int i = 0; i < all.size(); i++) {
            RankMineData.Def def = all.get(i);
            inv.setItem(i, buildItem(def, i, currentIdx));
        }
        p.openInventory(inv);
    }

    private ItemStack buildItem(RankMineData.Def def, int idx, int currentIdx) {
        Material mat;
        String status;
        if (idx < currentIdx) {
            mat = Material.LIME_STAINED_GLASS_PANE;
            status = "§aCompleted";
        } else if (idx == currentIdx) {
            mat = Material.GOLD_BLOCK;
            status = "§6Current rank";
        } else if (idx == currentIdx + 1) {
            mat = Material.EMERALD_BLOCK;
            status = "§eNext — click to rank up!";
        } else {
            mat = Material.GRAY_STAINED_GLASS_PANE;
            status = "§7Locked";
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f§lRank " + def.rank);
        List<String> lore = new ArrayList<>();
        lore.add(status);
        lore.add("");
        lore.add("§7Cost: §a$" + String.format("%.0f", (double) def.cost));
        if (!def.rank.equals("FREE")) {
            lore.add("§7Unlocks: §fMine " + def.rank);
            lore.add("§7Ore mix: §f" + prettyMaterial(def.filler) + " / " + prettyMaterial(def.common) + " / " + prettyMaterial(def.rare));
            lore.add("§7Sell prices: §a$" + def.fillerPrice + " / $" + def.commonPrice + " / $" + def.rarePrice);
        } else {
            lore.add("§7Unlocks: §fPrestige eligibility");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String prettyMaterial(String raw) {
        String[] parts = raw.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private int indexOf(List<RankMineData.Def> all, String rank) {
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).rank.equals(rank)) return i;
        }
        return 0;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;

        String clickedRank = e.getCurrentItem().getItemMeta().getDisplayName()
                .replace("§f§lRank ", "");

        String current = plugin.ranks().getRank(p);
        RankMineData.Def def = RankMineData.RANKS.get(current);
        if (def == null || !def.next.equals(clickedRank)) {
            p.sendMessage("§cThat's not your next rank.");
            return;
        }

        boolean ok = plugin.ranks().rankUp(p);
        if (ok) {
            p.sendMessage("§aRanked up to §f" + clickedRank + "§a!");
            plugin.chat().updateTab(p);
            p.closeInventory();
        } else {
            p.sendMessage("§cYou can't afford that rank yet.");
        }
    }
}
