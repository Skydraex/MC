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

    /**
     * The crate's full loot table with real odds, worked out from the reward weights rather than
     * written by hand, so it can never drift from what the crate actually rolls.
     */
    private void showContents(org.bukkit.entity.Player p, CrateData.Crate crate) {
        int total = 0;
        for (CrateData.Reward r : crate.rewards) total += r.weight;

        org.bukkit.inventory.Inventory inv = org.bukkit.Bukkit.createInventory(
                null, 27, "\u00a78" + crate.display + " \u2014 contents");

        double jackpot = CrateData.JACKPOT_CHANCE_PER_THOUSAND / 10.0;
        double standardShare = 100.0 - jackpot;

        int slot = 10;
        for (CrateData.Reward r : crate.rewards) {
            double pct = (r.weight / (double) total) * standardShare;
            org.bukkit.Material icon = switch (r.kind) {
                case MONEY -> org.bukkit.Material.GOLD_INGOT;
                case TOKENS -> org.bukkit.Material.SUNFLOWER;
                case ITEM -> r.material == null ? org.bukkit.Material.CHEST : r.material;
                case JACKPOT -> org.bukkit.Material.NETHER_STAR;
            };
            ItemStack it = new ItemStack(icon, Math.max(1, Math.min(64, r.amount)));
            org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
            if (m != null) {
                m.setDisplayName("\u00a7f" + r.display);
                m.setLore(java.util.List.of(String.format("\u00a77Chance: \u00a7e%.1f%%", pct)));
                it.setItemMeta(m);
            }
            inv.setItem(slot++, it);
            if (slot == 17) slot = 19;
        }

        ItemStack star = new ItemStack(org.bukkit.Material.NETHER_STAR);
        org.bukkit.inventory.meta.ItemMeta sm = star.getItemMeta();
        if (sm != null) {
            sm.setDisplayName("\u00a76\u00a7lJACKPOT");
            sm.setLore(java.util.List.of(
                    String.format("\u00a77Chance: \u00a7e%.1f%%", jackpot),
                    "\u00a77Warden's Pickaxe or Leviathan Rod",
                    "\u00a77Announced server-wide."));
            star.setItemMeta(sm);
        }
        inv.setItem(4, star);
        p.openInventory(inv);
        p.sendMessage("\u00a77Right-click the crate with a \u00a7f" + crate.keyName + "\u00a77 to open it.");
    }

    /** The contents preview is read-only; without this players could take the display items. */
    @EventHandler
    public void onPreviewClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (title != null && title.endsWith("\u2014 contents")) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        String crateId = crateBlocks.get(e.getClickedBlock().getLocation());
        if (crateId == null) return;

        // Left-click previews the loot table instead of opening. Without it the only way to
        // learn what is in a crate — or what the odds are — is to spend keys and infer.
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);
            CrateData.Crate crate = CrateData.CRATES.get(crateId);
            if (crate != null) showContents(e.getPlayer(), crate);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

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
