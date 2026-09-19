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

/**
 * The prison's enchanter: pickaxe upgrades, bought with money.
 *
 * It only opens at the enchanting hall in the hub, not from wherever a player happens to
 * be standing. That is deliberate — an enchanter everyone has to walk to is somewhere
 * players run into each other, which a command anybody can type from inside their own cell
 * is not.
 */
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
        if (!plugin.builder().inEnchantArea(p.getLocation())) {
            p.sendMessage("§cThe enchanter is in the middle of the hub, by the watchtower.");
            p.sendMessage("§7Walk to it — you cannot enchant from anywhere else.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        int slot = 0;
        for (PickaxeEnchants.EnchantDef def : PickaxeEnchants.ENCHANTS.values()) {
            int lvl = PickaxeEnchants.getLevel(hand, def.id);
            inv.setItem(slot++, buildItem(def, lvl, (long) plugin.economy().getBalance(p),
                    plugin.ranks().canAccessMine(p, def.requiredRank)));
        }

        // Balance display at the bottom.
        ItemStack info = new ItemStack(Material.GOLD_INGOT);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§6§lYour Balance: §f$" + String.format("%,.0f", plugin.economy().getBalance(p)));
        List<String> lore = new ArrayList<>();
        lore.add("§7Earn money by mining and selling.");
        lore.add("§7Left-click an enchant to buy the next level.");
        im.setLore(lore);
        info.setItemMeta(im);
        inv.setItem(22, info);

        p.openInventory(inv);
    }

    private ItemStack buildItem(PickaxeEnchants.EnchantDef def, int currentLevel, long balance,
                                boolean rankOk) {
        ItemStack item = new ItemStack(rankOk ? def.icon : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§l" + def.display);

        List<String> lore = new ArrayList<>();
        lore.add("§7" + def.description);
        lore.add("");
        if (!rankOk) {
            lore.add("§cLocked until rank " + def.requiredRank);
            lore.add("§8Earn it by ranking up.");
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;
        }
        lore.add("§7Level: §f" + currentLevel + "§7/§f" + def.maxLevel);
        if (currentLevel >= def.maxLevel) {
            lore.add("§aMaxed out!");
        } else {
            long cost = def.costFor(currentLevel + 1);
            lore.add("§7Next level cost: §6$" + String.format("%,d", cost));
            lore.add(balance >= cost ? "§aClick to purchase" : "§cNot enough money");
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

        if (!plugin.ranks().canAccessMine(p, target.requiredRank)) {
            p.sendMessage("§cThat enchant unlocks at rank " + target.requiredRank + ".");
            return;
        }

        int lvl = PickaxeEnchants.getLevel(hand, target.id);
        if (lvl >= target.maxLevel) {
            p.sendMessage("§eThat enchant is already maxed.");
            return;
        }

        long cost = target.costFor(lvl + 1);
        if (!plugin.economy().has(p, cost)) {
            p.sendMessage("§cThat costs §6$" + String.format("%,d", cost) + "§c and you have §6$"
                    + String.format("%,.0f", plugin.economy().getBalance(p)) + "§c.");
            return;
        }
        plugin.economy().withdrawPlayer(p, cost);

        PickaxeEnchants.setLevel(hand, target.id, lvl + 1);
        p.sendMessage("§a" + target.display + " upgraded to level " + (lvl + 1) + "!");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        open(p); // refresh the menu
    }
}
