package com.lukeprison.prison;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Player-run chest shops, created by placing a sign on a chest.
 *
 * Format:
 *   line 1: [Buy] or [Sell]   (from the shopper's point of view)
 *   line 2: quantity
 *   line 3: price
 *
 * "Buy" means the shopper buys from the owner's chest. "Sell" means the shopper sells into it.
 * Stock and money are both checked on every transaction, so a shop can never pay out money the
 * owner doesn't have or hand over items it doesn't hold.
 */
public class ShopSignListener implements Listener {

    private final PrisonPlugin plugin;

    public ShopSignListener(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey ownerKey() { return new NamespacedKey(PrisonPlugin.get(), "shop_owner"); }
    private NamespacedKey qtyKey() { return new NamespacedKey(PrisonPlugin.get(), "shop_qty"); }
    private NamespacedKey priceKey() { return new NamespacedKey(PrisonPlugin.get(), "shop_price"); }
    private NamespacedKey modeKey() { return new NamespacedKey(PrisonPlugin.get(), "shop_mode"); }

    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        String line0 = e.getLine(0);
        if (line0 == null) return;
        String mode;
        if (line0.equalsIgnoreCase("[Buy]")) mode = "BUY";
        else if (line0.equalsIgnoreCase("[Sell]")) mode = "SELL";
        else return;

        Player p = e.getPlayer();
        Block attached = findChest(e.getBlock());
        if (attached == null) {
            p.sendMessage("§cA shop sign must be placed on or directly above a chest.");
            e.setCancelled(true);
            return;
        }

        int qty;
        double price;
        try {
            qty = Integer.parseInt(e.getLine(1).trim());
            price = Double.parseDouble(e.getLine(2).trim().replace("$", "").replace(",", ""));
        } catch (Exception ex) {
            p.sendMessage("§cLine 2 must be a quantity and line 3 a price.");
            e.setCancelled(true);
            return;
        }

        if (qty <= 0 || qty > 2304 || price <= 0 || Double.isNaN(price) || Double.isInfinite(price)) {
            p.sendMessage("§cInvalid quantity or price.");
            e.setCancelled(true);
            return;
        }

        e.setLine(0, (mode.equals("BUY") ? "§a[Buy]" : "§6[Sell]"));
        e.setLine(1, "§f" + qty);
        e.setLine(2, "§f$" + String.format("%,.2f", price));
        e.setLine(3, "§7" + p.getName());

        // Persist the shop's terms on the sign itself.
        Block signBlock = e.getBlock();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!(signBlock.getState() instanceof Sign sign)) return;
            sign.getPersistentDataContainer().set(ownerKey(), PersistentDataType.STRING,
                    p.getUniqueId().toString());
            sign.getPersistentDataContainer().set(qtyKey(), PersistentDataType.INTEGER, qty);
            sign.getPersistentDataContainer().set(priceKey(), PersistentDataType.DOUBLE, price);
            sign.getPersistentDataContainer().set(modeKey(), PersistentDataType.STRING, mode);
            sign.update();
        });

        p.sendMessage("§aShop created. Stock the chest below it.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (!(e.getClickedBlock().getState() instanceof Sign sign)) return;

        String ownerId = sign.getPersistentDataContainer().get(ownerKey(), PersistentDataType.STRING);
        if (ownerId == null) return;
        e.setCancelled(true);

        Player shopper = e.getPlayer();
        UUID owner = UUID.fromString(ownerId);
        int qty = sign.getPersistentDataContainer().getOrDefault(qtyKey(), PersistentDataType.INTEGER, 0);
        double price = sign.getPersistentDataContainer().getOrDefault(priceKey(), PersistentDataType.DOUBLE, 0.0);
        String mode = sign.getPersistentDataContainer().get(modeKey(), PersistentDataType.STRING);

        Block chestBlock = findChest(e.getClickedBlock());
        if (chestBlock == null || !(chestBlock.getState() instanceof Chest chest)) {
            shopper.sendMessage("§cThis shop's chest is missing.");
            return;
        }

        // Owner clicking their own shop just sees its status.
        if (owner.equals(shopper.getUniqueId())) {
            shopper.sendMessage("§7Your shop — " + (mode.equals("BUY") ? "selling" : "buying")
                    + " §f" + qty + "§7 per trade at §a$" + String.format("%,.2f", price));
            return;
        }

        Inventory chestInv = chest.getInventory();
        OfflinePlayer ownerPlayer = plugin.getServer().getOfflinePlayer(owner);

        if ("BUY".equals(mode)) {
            // Shopper buys from the chest.
            ItemStack sample = firstStack(chestInv);
            if (sample == null) {
                shopper.sendMessage("§cThis shop is out of stock.");
                return;
            }
            if (countOf(chestInv, sample.getType()) < qty) {
                shopper.sendMessage("§cThis shop doesn't have enough stock.");
                return;
            }
            if (!plugin.economy().has(shopper, price)) {
                shopper.sendMessage("§cYou can't afford that.");
                return;
            }
            if (shopper.getInventory().firstEmpty() == -1) {
                shopper.sendMessage("§cYour inventory is full.");
                return;
            }

            plugin.economy().withdrawPlayer(shopper, price);
            plugin.economy().depositPlayer(ownerPlayer, price);
            chestInv.removeItem(new ItemStack(sample.getType(), qty));
            shopper.getInventory().addItem(new ItemStack(sample.getType(), qty));
            shopper.sendMessage("§aBought §f" + qty + "x " + pretty(sample.getType())
                    + "§a for §6$" + String.format("%,.2f", price));
            shopper.playSound(shopper.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
            notifyOwner(owner, "§aSold " + qty + "x " + pretty(sample.getType())
                    + " to " + shopper.getName() + " for $" + String.format("%,.2f", price));

        } else {
            // Shopper sells into the chest.
            ItemStack held = shopper.getInventory().getItemInMainHand();
            if (held == null || held.getType().isAir()) {
                shopper.sendMessage("§cHold the item you want to sell.");
                return;
            }
            if (countOf(shopper.getInventory(), held.getType()) < qty) {
                shopper.sendMessage("§cYou need " + qty + " of that item.");
                return;
            }
            if (!plugin.economy().has(ownerPlayer, price)) {
                shopper.sendMessage("§cThe shop owner can't afford to buy right now.");
                return;
            }
            if (chestInv.firstEmpty() == -1) {
                shopper.sendMessage("§cThis shop's chest is full.");
                return;
            }

            plugin.economy().withdrawPlayer(ownerPlayer, price);
            plugin.economy().depositPlayer(shopper, price);
            shopper.getInventory().removeItem(new ItemStack(held.getType(), qty));
            chestInv.addItem(new ItemStack(held.getType(), qty));
            shopper.sendMessage("§aSold §f" + qty + "x " + pretty(held.getType())
                    + "§a for §6$" + String.format("%,.2f", price));
            shopper.playSound(shopper.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
            notifyOwner(owner, "§aBought " + qty + "x " + pretty(held.getType())
                    + " from " + shopper.getName() + " for $" + String.format("%,.2f", price));
        }
    }

    /** Only the owner may break their shop sign or its chest. */
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Sign sign = null;
        if (b.getState() instanceof Sign s) {
            sign = s;
        } else if (b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST) {
            sign = findShopSignFor(b);
        }
        if (sign == null) return;

        String ownerId = sign.getPersistentDataContainer().get(ownerKey(), PersistentDataType.STRING);
        if (ownerId == null) return;

        if (!ownerId.equals(e.getPlayer().getUniqueId().toString())
                && !e.getPlayer().hasPermission("prison.admin")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cThat's someone else's shop.");
        }
    }

    private Sign findShopSignFor(Block chest) {
        for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.NORTH,
                org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST,
                org.bukkit.block.BlockFace.WEST}) {
            Block rel = chest.getRelative(face);
            if (rel.getState() instanceof Sign s
                    && s.getPersistentDataContainer().has(ownerKey(), PersistentDataType.STRING)) {
                return s;
            }
        }
        return null;
    }

    private Block findChest(Block signBlock) {
        Block below = signBlock.getRelative(0, -1, 0);
        if (below.getType() == Material.CHEST || below.getType() == Material.TRAPPED_CHEST) return below;
        for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST}) {
            Block rel = signBlock.getRelative(face);
            if (rel.getType() == Material.CHEST || rel.getType() == Material.TRAPPED_CHEST) return rel;
        }
        return null;
    }

    private ItemStack firstStack(Inventory inv) {
        for (ItemStack it : inv.getContents()) {
            if (it != null && !it.getType().isAir()) return it;
        }
        return null;
    }

    private int countOf(Inventory inv, Material mat) {
        int n = 0;
        for (ItemStack it : inv.getContents()) {
            if (it != null && it.getType() == mat) n += it.getAmount();
        }
        return n;
    }

    private void notifyOwner(UUID owner, String msg) {
        Player p = plugin.getServer().getPlayer(owner);
        if (p != null) p.sendMessage("§7[Shop] " + msg);
    }

    private String pretty(Material mat) {
        String[] parts = mat.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
