package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Cells are claimable homes. A cell costs money (a money sink), gives you /cell home, and locks
 * its storage barrel to you. One cell per player.
 */
public class CellManager implements Listener {

    public record CellRect(int number, int x1, int z1, int x2, int z2, int y) {
        boolean contains(Location l) {
            return l.getBlockX() >= x1 && l.getBlockX() <= x2 && l.getBlockZ() >= z1 && l.getBlockZ() <= z2
                    && l.getBlockY() >= y && l.getBlockY() <= y + 5;
        }
    }

    private static final double CLAIM_COST = 25_000;
    private static final double RENEW_COST = 5_000;

    /** " (Nh left)" / " (EXPIRED)" appended to /cell info, or blank if unowned. */
    private String timeLeftSuffix(int cell) {
        long expiry = plugin.ranks().getCellExpiry(cell);
        if (expiry <= 0) return "";
        long msLeft = expiry - System.currentTimeMillis();
        if (msLeft <= 0) return " §c(rental expired)";
        long hoursLeft = msLeft / (60 * 60 * 1000);
        return " §7(" + hoursLeft + "h left)";
    }

    /**
     * Called periodically. Any cell whose 72-hour rental has lapsed is released automatically,
     * so an inactive renter doesn't permanently squat on a cell someone else wants.
     */
    public void checkExpiredRentals() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, Long> e : new java.util.HashMap<>(plugin.ranks().allCellExpiry()).entrySet()) {
            if (e.getValue() > 0 && e.getValue() < now) {
                UUID former = plugin.ranks().cellOwner(e.getKey());
                plugin.ranks().unclaimCell(e.getKey());
                if (former != null) {
                    Player p = Bukkit.getPlayer(former);
                    if (p != null) p.sendMessage("§eYour cell rental expired and has been released.");
                }
            }
        }
    }

    private final PrisonPlugin plugin;
    private final Map<Integer, CellRect> cells;

    public CellManager(PrisonPlugin plugin, Map<Integer, CellRect> cells) {
        this.plugin = plugin;
        this.cells = cells;
    }

    private CellRect cellAt(Location l) {
        for (CellRect c : cells.values()) if (c.contains(l)) return c;
        return null;
    }

    /** Only the owner can open a barrel inside a claimed cell. */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.BARREL) return;
        CellRect cell = cellAt(e.getClickedBlock().getLocation());
        if (cell == null) return;
        UUID owner = plugin.ranks().cellOwner(cell.number());
        Player p = e.getPlayer();
        if (owner == null) {
            e.setCancelled(true);
            p.sendMessage("§7This cell is unclaimed. Stand inside and use §f/cell claim§7.");
            return;
        }
        if (!owner.equals(p.getUniqueId()) && !p.hasPermission("prison.admin")) {
            e.setCancelled(true);
            p.sendMessage("§cThat's someone else's cell.");
        }
    }

    public class Cmd implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (!(s instanceof Player p)) return true;
            String sub = a.length > 0 ? a[0].toLowerCase() : "help";

            switch (sub) {
                case "claim" -> {
                    if (plugin.ranks().cellOf(p) != null) {
                        p.sendMessage("§cYou already own cell #" + plugin.ranks().cellOf(p) + ". Unclaim it first.");
                        return true;
                    }
                    CellRect cell = cellAt(p.getLocation());
                    if (cell == null) {
                        p.sendMessage("§cStand inside the cell you want to claim.");
                        return true;
                    }
                    if (plugin.ranks().cellOwner(cell.number()) != null) {
                        p.sendMessage("§cCell #" + cell.number() + " is already taken.");
                        return true;
                    }
                    if (!plugin.economy().has(p, CLAIM_COST)) {
                        p.sendMessage("§cA cell costs §6$" + String.format("%,.0f", CLAIM_COST) + "§c.");
                        return true;
                    }
                    plugin.economy().withdrawPlayer(p, CLAIM_COST);
                    plugin.ranks().claimCell(p, cell.number());
                    p.sendMessage("§aCell #" + cell.number() + " is yours for 72 hours. §7/cell home §ato return, §7/cell renew §abefore it expires.");
                    p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 1f);
                    plugin.scoreboard().update(p);
                }
                case "home" -> {
                    Integer n = plugin.ranks().cellOf(p);
                    if (n == null) {
                        p.sendMessage("§cYou don't own a cell. Stand in one and §f/cell claim§c.");
                        return true;
                    }
                    CellRect cell = cells.get(n);
                    if (cell == null) return true;
                    p.teleport(new Location(plugin.builder().getWorld(), cell.x1() + 3.5, cell.y() + 1, cell.z1() + 2.5));
                    p.sendMessage("§7Welcome home.");
                }
                case "unclaim" -> {
                    Integer n = plugin.ranks().cellOf(p);
                    if (n == null) { p.sendMessage("§cYou don't own a cell."); return true; }
                    plugin.ranks().unclaimCell(n);
                    p.sendMessage("§eCell #" + n + " released. §7(No refund.)");
                }
                case "info" -> {
                    CellRect cell = cellAt(p.getLocation());
                    if (cell == null) { p.sendMessage("§7You're not in a cell."); return true; }
                    UUID owner = plugin.ranks().cellOwner(cell.number());
                    if (owner == null) {
                        p.sendMessage("§7Cell #" + cell.number() + " — §aunclaimed");
                    } else {
                        String who = plugin.getServer().getOfflinePlayer(owner).getName();
                        p.sendMessage("§7Cell #" + cell.number() + " — §f" + who + timeLeftSuffix(cell.number()));
                    }
                }
                case "renew" -> {
                    Integer n = plugin.ranks().cellOf(p);
                    if (n == null) { p.sendMessage("§cYou don't own a cell."); return true; }
                    if (!plugin.economy().has(p, RENEW_COST)) {
                        p.sendMessage("§cRenewing costs §6$" + String.format("%,.0f", RENEW_COST) + "§c.");
                        return true;
                    }
                    plugin.economy().withdrawPlayer(p, RENEW_COST);
                    plugin.ranks().renewCell(n);
                    p.sendMessage("§aCell #" + n + " renewed for another 72 hours.");
                }
                default -> {
                    p.sendMessage("§6/cell §7claim §8— rent the cell you're standing in for 72h ($" + String.format("%,.0f", CLAIM_COST) + ")");
                    p.sendMessage("§6/cell §7home §8— teleport to your cell");
                    p.sendMessage("§6/cell §7renew §8— extend your rental by 72h ($" + String.format("%,.0f", RENEW_COST) + ")");
                    p.sendMessage("§6/cell §7info §8— who owns this cell, and time left");
                    p.sendMessage("§6/cell §7unclaim §8— give up your cell early");
                }
            }
            return true;
        }
    }
}
