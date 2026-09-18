package com.lukeprison.prison;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class SellSignListener implements Listener {

    public static class SellSignData {
        public final String rank;
        public final Map<Material, Double> prices;
        public SellSignData(String rank, Map<Material, Double> prices) {
            this.rank = rank;
            this.prices = prices;
        }
    }

    private final PrisonPlugin plugin;
    private final Map<Location, SellSignData> sellSigns;

    public SellSignListener(PrisonPlugin plugin, Map<Location, SellSignData> sellSigns) {
        this.plugin = plugin;
        this.sellSigns = sellSigns;
    }

    private String pretty(Material mat) {
        StringBuilder sb = new StringBuilder();
        for (String part : mat.name().toLowerCase().split("_")) {
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        SellSignData data = sellSigns.get(e.getClickedBlock().getLocation());
        if (data == null) return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            p.sendMessage("§cHold the item you want to sell first.");
            return;
        }

        Double unitPrice = data.prices.get(hand.getType());
        if (unitPrice == null) {
            p.sendMessage("§cThis sign only buys ores from Mine " + data.rank + ".");
            return;
        }

        int amount = hand.getAmount();
        Material sold = hand.getType();   // capture before the stack is emptied
        double total = unitPrice * amount;
        hand.setAmount(0);
        plugin.economy().depositPlayer(p, total);
        p.sendMessage("§aSold " + amount + " x " + pretty(sold) + " for §6$" + String.format("%,.2f", total));
    }
}
