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
                "§7Apply enchants to your pickaxe.",
                "§7At the enchanter, in the hub."));
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
        inv.setItem(19, item(Material.EMERALD, "§a§lShop",
                "§7Buy gear, food and pickaxes.",
                "§7Rank-gated, so no shortcuts."));
        inv.setItem(20, item(Material.CLOCK, "§e§lDaily Reward",
                "§7Claim your streak bonus.",
                "§7/daily"));
        inv.setItem(21, item(Material.GOLD_INGOT, "§6§lCoinflips",
                "§7Wager money against other players.",
                "§7/coinflip"));
        inv.setItem(22, item(Material.PLAYER_HEAD, "§b§lLeaderboards",
                "§7Top money, blocks, prestige, fishing.",
                "§7/top"));
        inv.setItem(23, item(Material.IRON_DOOR, "§7§lYour Cell",
                "§7Claim or visit your cell.",
                "§7/cell"));

        p.openInventory(inv);
    }

    /**
     * Every destination on the map, not only the mines.
     *
     * This used to list the 26 mines and nothing else, so there was no way to reach the ponds,
     * the logging yard, the farm, the crate hall or the Yard from a menu — the one place a
     * player looks for "where can I go".
     *
     * Mines fill the top rows; the places anyone can visit sit on the bottom row.
     */
    public void openWarps(Player p) {
        List<RankMineData.Def> mines = new ArrayList<>();
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (d.hasMine()) mines.add(d);
        }
        int mineRows = (mines.size() + 8) / 9;
        Inventory inv = Bukkit.createInventory(null, (mineRows + 1) * 9, WARPS_TITLE);

        for (int i = 0; i < mines.size(); i++) {
            RankMineData.Def def = mines.get(i);
            boolean unlocked = plugin.ranks().canAccessMine(p, def.rank);
            inv.setItem(i, item(
                    unlocked ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    (unlocked ? "§a§lMine " : "§7§lMine ") + def.rank,
                    unlocked ? "§7Click to teleport." : "§cLocked — rank up to access.",
                    "§7Ore: §f" + def.common));
        }

        int base = mineRows * 9;
        inv.setItem(base, item(Material.BEACON, "§b§lThe Hub", "§7Back to spawn."));
        inv.setItem(base + 1, item(Material.FISHING_ROD, "§b§lFishing Ponds", "§7Out on the grounds."));
        inv.setItem(base + 2, item(Material.OAK_LOG, "§2§lLogging Yard", "§7Chop trees for drops."));
        inv.setItem(base + 3, item(Material.WHEAT, "§6§lThe Farm", "§7Cows and pigs."));
        inv.setItem(base + 4, item(Material.ENDER_CHEST, "§6§lCrate Hall", "§7Open crates with keys."));
        inv.setItem(base + 5, item(Material.IRON_SWORD, "§c§lThe Yard", "§cPvP zone.", "§7You drop everything on death."));
        inv.setItem(base + 6, item(Material.IRON_BARS, "§7§lCell Block", "§7Claim or visit your cell."));
        p.openInventory(inv);
    }

    /** Teleports to one of the non-mine destinations on the warps menu. */
    private boolean warpToPlace(Player p, String name) {
        WorldBuilder b = plugin.builder();
        Location dest = switch (name) {
            case "The Hub" -> b.getHubSpawn();
            case "Fishing Ponds" -> b.groundSpot("PONDS");
            case "Logging Yard" -> b.groundSpot("LOGGING");
            case "The Farm" -> b.groundSpot("FARM");
            case "Crate Hall" -> b.roomSpot("CRATES");
            case "The Yard" -> b.roomSpot("YARD");
            case "Cell Block" -> b.cellWingSpot();
            default -> null;
        };
        if (dest == null) return false;
        p.teleport(dest);
        p.sendMessage("§aWarped to " + name + ".");
        return true;
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
                case "§a§lMine Warps", "§a§lWarps" -> openWarps(p);
                case "§a§lShop" -> { p.closeInventory(); p.performCommand("shop"); }
                case "§e§lDaily Reward" -> { p.closeInventory(); p.performCommand("daily"); }
                case "§6§lCoinflips" -> { p.closeInventory(); p.performCommand("coinflip"); }
                case "§b§lLeaderboards" -> { p.closeInventory(); p.performCommand("top"); }
                case "§7§lYour Cell" -> { p.closeInventory(); p.performCommand("cell home"); }
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

        // Warps menu.
        String plain = org.bukkit.ChatColor.stripColor(name);
        if (plain != null && plain.startsWith("Mine ")) {
            warpToMine(p, plain.substring(5).trim());
            p.closeInventory();
            return;
        }
        if (plain != null && warpToPlace(p, plain)) p.closeInventory();
    }

    /** Shared by the GUI click above and the plain "/mine <rank>" text command — same rule
     *  either way: a player can only warp to their own rank's mine or anything lower. */
    public boolean warpToMine(Player p, String rank) {
        rank = rank.toUpperCase();
        if (!RankMineData.RANKS.containsKey(rank) || rank.equals("FREE")) {
            p.sendMessage("§cThere's no mine called \"" + rank + "\".");
            return false;
        }
        if (!plugin.ranks().canAccessMine(p, rank)) {
            p.sendMessage("§cYou haven't unlocked Mine " + rank + " yet.");
            return false;
        }
        if (!mineBounds.containsKey(rank)) return false;
        // Always the mine's cage landing: on the rim, over solid floor, never over the pit and
        // never inside the ore. Computing a spot from the mine's corner (as this used to) put
        // players two blocks inside a solid cube of ore and suffocated them on every warp.
        p.teleport(plugin.builder().safeMineSpot(rank));
        p.sendMessage("§aWarped to Mine " + rank + ".");
        return true;
    }
}
