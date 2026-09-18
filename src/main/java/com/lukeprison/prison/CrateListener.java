package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Physical crates at spawn, key drops, and the opening sequence. */
public class CrateListener implements Listener {

    private final PrisonPlugin plugin;
    private final Random random = new Random();
    /** Crate block location -> crate id. */
    private final Map<Location, String> crateBlocks = new HashMap<>();

    /** Roughly 1 key per this many blocks mined / fish caught. */
    private static final int MINING_KEY_ODDS = 1200;
    private static final int FISHING_KEY_ODDS = 60;

    public CrateListener(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerCrate(Location loc, String crateId) {
        crateBlocks.put(loc, crateId);
    }

    /** Called from the mining loop; occasionally awards a Miner Key. */
    public void rollMiningKey(Player p, int blocksBroken) {
        for (int i = 0; i < blocksBroken; i++) {
            if (random.nextInt(MINING_KEY_ODDS) == 0) {
                giveKey(p, "miner");
                return;
            }
        }
    }

    /** Called from the fishing loop; occasionally awards an Angler Key. */
    public void rollFishingKey(Player p) {
        if (random.nextInt(FISHING_KEY_ODDS) == 0) {
            giveKey(p, "angler");
        }
    }

    public void giveKey(Player p, String crateId) {
        CrateData.Crate crate = CrateData.CRATES.get(crateId);
        if (crate == null) return;
        p.getInventory().addItem(CrateData.buildKey(crate, 1));
        p.sendMessage("§6You found a §f" + crate.display.replace(" Crate", " Key") + "§6!");
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.4f);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        String crateId = crateBlocks.get(e.getClickedBlock().getLocation());
        if (crateId == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        CrateData.Crate crate = CrateData.CRATES.get(crateId);
        if (crate == null) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        String heldKey = CrateData.keyTypeOf(hand);
        if (heldKey == null || !heldKey.equals(crateId)) {
            p.sendMessage("§cYou need a §f" + crate.keyName + "§c to open this crate.");
            return;
        }

        // Consume one key.
        hand.setAmount(hand.getAmount() - 1);

        if (CrateData.rollJackpot()) {
            ItemStack prize = CrateData.buildJackpot();
            p.getInventory().addItem(prize);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            String display = prize.getItemMeta().getDisplayName();
            Bukkit.broadcastMessage("§8§m----------------------------------------");
            Bukkit.broadcastMessage("§6§lJACKPOT! §f" + p.getName() + " §7won " + display);
            Bukkit.broadcastMessage("§8from a " + crate.display + " §7(0.1% chance)");
            Bukkit.broadcastMessage("§8§m----------------------------------------");
            return;
        }

        CrateData.Reward reward = CrateData.roll(crate);
        switch (reward.kind) {
            case MONEY -> {
                plugin.economy().depositPlayer(p, reward.money);
                p.sendMessage("§aYou won §6" + reward.display + "§a!");
            }
            case TOKENS -> {
                plugin.ranks().addTokens(p, reward.tokens);
                p.sendMessage("§aYou won §b" + reward.display + "§a!");
            }
            case ITEM -> {
                p.getInventory().addItem(new ItemStack(reward.material, reward.amount));
                p.sendMessage("§aYou won §f" + reward.display + "§a!");
            }
            default -> { }
        }
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
    }
}
