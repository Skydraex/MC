package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Token shop for pickaxe enchants — opened with /enchant while holding a pickaxe. */
public class EnchantGUI implements Listener {

    private static final String TITLE = "§8§lEnchant Pickaxe";
    private final PrisonPlugin plugin;

    public EnchantGUI(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!PickaxeEnchants.isPickaxe(hand)) {
            p.sendMessage("§cHold your pickaxe to enchant it.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        int slot = 0;
        for (PickaxeEnchants.EnchantDef def : PickaxeEnchants.ENCHANTS.values()) {
            int lvl = PickaxeEnchants.getLevel(hand, def.id);
            inv.setItem(slot++, buildItem(def, lvl, plugin.ranks().getTokens(p)));
        }

        // Token balance display at the bottom.
        ItemStack info = new ItemStack(Material.SUNFLOWER);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§e§lYour Tokens: §f" + plugin.ranks().getTokens(p));
        List<String> lore = new ArrayList<>();
        lore.add("§7Earn tokens by mining blocks.");
        lore.add("§7Left-click an enchant to buy the next level.");
        im.setLore(lore);
        info.setItemMeta(im);
        inv.setItem(22, info);

        p.openInventory(inv);
    }

    private ItemStack buildItem(PickaxeEnchants.EnchantDef def, int currentLevel, long tokens) {
        ItemStack item = new ItemStack(def.icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§l" + def.display);

        List<String> lore = new ArrayList<>();
        lore.add("§7" + def.description);
        lore.add("");
        lore.add("§7Level: §f" + currentLevel + "§7/§f" + def.maxLevel);
        if (currentLevel >= def.maxLevel) {
            lore.add("§aMaxed out!");
        } else {
            long cost = def.costFor(currentLevel + 1);
            lore.add("§7Next level cost: §e" + cost + " tokens");
            lore.add(tokens >= cost ? "§aClick to purchase" : "§cNot enough tokens");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;

        String name = e.getCurrentItem().getItemMeta().getDisplayName();
        PickaxeEnchants.EnchantDef target = null;
        for (PickaxeEnchants.EnchantDef def : PickaxeEnchants.ENCHANTS.values()) {
            if (name.equals("§b§l" + def.display)) { target = def; break; }
        }
        if (target == null) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!PickaxeEnchants.isPickaxe(hand)) {
            p.sendMessage("§cYou need to be holding your pickaxe.");
            p.closeInventory();
            return;
        }

        int lvl = PickaxeEnchants.getLevel(hand, target.id);
        if (lvl >= target.maxLevel) {
            p.sendMessage("§eThat enchant is already maxed.");
            return;
        }

        long cost = target.costFor(lvl + 1);
        if (!plugin.ranks().spendTokens(p, cost)) {
            p.sendMessage("§cYou need " + cost + " tokens for that (you have " + plugin.ranks().getTokens(p) + ").");
            return;
        }

        PickaxeEnchants.setLevel(hand, target.id, lvl + 1);
        p.sendMessage("§a" + target.display + " upgraded to level " + (lvl + 1) + "!");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        open(p); // refresh the menu
    }
}
