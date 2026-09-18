package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Player-vs-player coinflip.
 *
 * Money is escrowed the moment a game is created, so a host can't create a wager they can't
 * cover and can't spend the stake while it's listed. The house takes a small cut, which also
 * acts as a slow drain on the money supply rather than pure circulation.
 */
public class CoinflipManager implements Listener {

    private static final String TITLE = "§8§lCoinflips";
    /** House cut on the pot. Keeps the wager loop from being a perfect-inflation machine. */
    private static final double HOUSE_CUT = 0.05;
    private static final double MIN_WAGER = 1000;

    public static class Game {
        public final UUID host;
        public final String hostName;
        public final double wager;
        public final long created;

        public Game(Player host, double wager) {
            this.host = host.getUniqueId();
            this.hostName = host.getName();
            this.wager = wager;
            this.created = System.currentTimeMillis();
        }
    }

    private final PrisonPlugin plugin;
    private final Map<UUID, Game> open = new LinkedHashMap<>();
    private final Random random = new Random();

    public CoinflipManager(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        int slot = 0;
        for (Game g : open.values()) {
            if (slot >= 45) break;
            ItemStack it = new ItemStack(Material.SUNFLOWER);
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName("§6" + g.hostName + "§7's coinflip");
            List<String> lore = new ArrayList<>();
            lore.add("§7Wager: §a$" + String.format("%,.0f", g.wager));
            lore.add("§7Pot: §a$" + String.format("%,.0f", g.wager * 2 * (1 - HOUSE_CUT)));
            lore.add("");
            lore.add(g.host.equals(p.getUniqueId())
                    ? "§cClick to cancel and refund"
                    : "§aClick to take this flip (50/50)");
            meta.setLore(lore);
            it.setItemMeta(meta);
            inv.setItem(slot++, it);
        }

        ItemStack info = new ItemStack(Material.GOLD_INGOT);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§e§lCreate a coinflip");
        List<String> lore = new ArrayList<>();
        lore.add("§7Use §f/coinflip <amount>");
        lore.add("§7Minimum: §a$" + String.format("%,.0f", MIN_WAGER));
        lore.add("§7House takes §f" + (int) (HOUSE_CUT * 100) + "%§7 of the pot.");
        lore.add("");
        lore.add("§8Your money is held while the flip is listed.");
        im.setLore(lore);
        info.setItemMeta(im);
        inv.setItem(49, info);

        p.openInventory(inv);
    }

    public void create(Player p, double wager) {
        if (wager < MIN_WAGER) {
            p.sendMessage("§cMinimum wager is $" + String.format("%,.0f", MIN_WAGER) + ".");
            return;
        }
        if (open.containsKey(p.getUniqueId())) {
            p.sendMessage("§cYou already have a coinflip open. Cancel it first.");
            return;
        }
        if (!plugin.economy().has(p, wager)) {
            p.sendMessage("§cYou can't afford that wager.");
            return;
        }
        // Escrow immediately so the stake can't be spent elsewhere while listed.
        plugin.economy().withdrawPlayer(p, wager);
        open.put(p.getUniqueId(), new Game(p, wager));
        p.sendMessage("§aCoinflip created for §6$" + String.format("%,.0f", wager) + "§a.");
        Bukkit.broadcastMessage("§6[Coinflip] §f" + p.getName() + " §7opened a flip for §a$"
                + String.format("%,.0f", wager) + "§7. §f/coinflip§7 to join.");
    }

    private void cancel(Player p, Game g) {
        open.remove(g.host);
        plugin.economy().depositPlayer(p, g.wager);
        p.sendMessage("§eCoinflip cancelled, $" + String.format("%,.0f", g.wager) + " refunded.");
    }

    private void play(Player challenger, Game g) {
        if (!plugin.economy().has(challenger, g.wager)) {
            challenger.sendMessage("§cYou can't afford that flip.");
            return;
        }
        // Re-check the game still exists — two players could click at the same moment.
        if (open.remove(g.host) == null) {
            challenger.sendMessage("§cThat coinflip was already taken.");
            return;
        }

        plugin.economy().withdrawPlayer(challenger, g.wager);

        double pot = g.wager * 2 * (1 - HOUSE_CUT);
        boolean hostWins = random.nextBoolean();

        Player host = Bukkit.getPlayer(g.host);
        String winnerName = hostWins ? g.hostName : challenger.getName();
        String loserName = hostWins ? challenger.getName() : g.hostName;

        if (hostWins) {
            if (host != null) {
                plugin.economy().depositPlayer(host, pot);
                host.sendMessage("§a§lYou won the coinflip! §f+$" + String.format("%,.0f", pot));
                host.playSound(host.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            } else {
                // Host offline: pay the account anyway so nobody loses money to timing.
                plugin.economy().depositPlayer(Bukkit.getOfflinePlayer(g.host), pot);
            }
            challenger.sendMessage("§cYou lost the coinflip. §7-$" + String.format("%,.0f", g.wager));
        } else {
            plugin.economy().depositPlayer(challenger, pot);
            challenger.sendMessage("§a§lYou won the coinflip! §f+$" + String.format("%,.0f", pot));
            challenger.playSound(challenger.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            if (host != null) {
                host.sendMessage("§cYou lost your coinflip to " + challenger.getName()
                        + ". §7-$" + String.format("%,.0f", g.wager));
            }
        }

        Bukkit.broadcastMessage("§6[Coinflip] §f" + winnerName + " §7beat §f" + loserName
                + " §7for §a$" + String.format("%,.0f", pot));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;

        String name = e.getCurrentItem().getItemMeta().getDisplayName();
        if (!name.contains("'s coinflip")) return;

        String hostName = org.bukkit.ChatColor.stripColor(name).replace("'s coinflip", "");
        Game target = null;
        for (Game g : open.values()) {
            if (g.hostName.equals(hostName)) { target = g; break; }
        }
        if (target == null) {
            p.sendMessage("§cThat coinflip is no longer available.");
            p.closeInventory();
            return;
        }

        if (target.host.equals(p.getUniqueId())) {
            cancel(p, target);
        } else {
            play(p, target);
        }
        p.closeInventory();
    }

    /** Refund every open wager on shutdown so escrowed money is never lost. */
    public void refundAll() {
        for (Game g : open.values()) {
            plugin.economy().depositPlayer(Bukkit.getOfflinePlayer(g.host), g.wager);
        }
        open.clear();
    }

    public static class Cmd implements CommandExecutor {
        private final CoinflipManager mgr;
        public Cmd(CoinflipManager mgr) { this.mgr = mgr; }
        @Override
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (!(s instanceof Player p)) return true;
            if (a.length == 0) {
                mgr.open(p);
                return true;
            }
            try {
                double amount = Double.parseDouble(a[0].replace(",", ""));
                if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
                    p.sendMessage("§cEnter a valid amount.");
                    return true;
                }
                mgr.create(p, Math.floor(amount));
            } catch (NumberFormatException ex) {
                p.sendMessage("§cUsage: /coinflip <amount>");
            }
            return true;
        }
    }
}
