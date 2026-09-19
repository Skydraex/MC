package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replaces vanilla /help.
 *
 * Bukkit's own /help lists every command from every plugin — Vault, EssentialsX and this one —
 * which on this server runs to 21 pages of mostly irrelevant entries, with the prison's own
 * commands scattered through them. A new player has no way to find what they need.
 *
 * This is a category menu instead: eight topics, each opening a page of the commands that
 * actually matter for that topic, written as "what you want to do" rather than as a command dump.
 *
 * /help is intercepted at the command-preprocess stage rather than registered as a command,
 * because a plugin command named "help" does not reliably win against Bukkit's built-in one.
 */
public class HelpGUI implements Listener {

    private static final String MAIN_TITLE = ChatColor.DARK_GRAY + "Help";
    private static final String SUB_PREFIX = ChatColor.DARK_GRAY + "Help: ";

    private record Entry(String command, String what) { }

    private record Category(String name, Material icon, String blurb, List<Entry> entries) { }

    private final List<Category> categories = new ArrayList<>();

    /**
     * Every command this menu advertises, so the audit can check they all actually exist.
     * A help entry for a command nobody registered is worse than no help entry at all.
     */
    public java.util.List<String> advertisedCommands() {
        java.util.List<String> out = new ArrayList<>();
        for (Category cat : categories) {
            for (Entry e : cat.entries()) {
                String first = e.command().trim().split("\\s+")[0];
                if (first.startsWith("/")) out.add(first.substring(1).toLowerCase());
            }
        }
        return out;
    }
    private final Map<String, Category> byName = new LinkedHashMap<>();

    public HelpGUI() {
        add(new Category("Getting Started", Material.WOODEN_PICKAXE,
                "Your first ten minutes", List.of(
                new Entry("/spawn", "Back to the hub from anywhere"),
                new Entry("/mine A", "Go straight to your first mine"),
                new Entry("/sell", "Sell everything you have mined"),
                new Entry("/autosell", "Sell as you mine, no clicking"),
                new Entry("/prison", "The main menu — everything in one place"))));

        add(new Category("Mining & Money", Material.DIAMOND_PICKAXE,
                "Digging, selling, and earning", List.of(
                new Entry("/mine <rank>", "Warp to any mine at or below your rank"),
                new Entry("/warps", "Pick a mine from a menu instead"),
                new Entry("/sell", "Sell your inventory of mined blocks"),
                new Entry("/autosell", "Toggle instant selling while mining"),
                new Entry("/shop", "Buy and sell gear"))));

        add(new Category("Ranks & Prestige", Material.GOLDEN_HELMET,
                "Working your way from A to Free", List.of(
                new Entry("/rank", "Your current rank and progress"),
                new Entry("/rankup", "Rank up, or browse all 27 ranks"),
                new Entry("/prestige", "Reset for prestige once you reach Free"),
                new Entry("/top", "Leaderboards — money, blocks, prestige"))));

        add(new Category("Enchants", Material.ENCHANTING_TABLE,
                "Enchanting your pickaxe", List.of(
                new Entry("/enchant", "The enchant menu"),
                new Entry("Efficiency", "Mine faster — unlocks at rank A"),
                new Entry("Fortune", "More drops per block — rank C"),
                new Entry("Explosive", "Chance to blast 3x3x3 — rank M"))));

        add(new Category("Fishing", Material.FISHING_ROD,
                "The ponds out on the grounds", List.of(
                new Entry("/fishing", "Browse the ponds and your level"),
                new Entry("/sellfish", "Cash in everything you have caught"),
                new Entry("Unlocks at E", "Fishing opens up at rank E"),
                new Entry("Ten ponds", "Each needs a higher fishing level"))));

        add(new Category("Your Cell", Material.IRON_BARS,
                "Claiming somewhere of your own", List.of(
                new Entry("/cell", "Claim, visit or release a cell"),
                new Entry("/cell home", "Teleport to your cell"),
                new Entry("Building", "You may build inside your own cell"),
                new Entry("Bigger cells", "Higher tiers hold larger cells"))));

        add(new Category("PvP & Crates", Material.IRON_SWORD,
                "Risk, reward and keys", List.of(
                new Entry("Red floor", "Red wool means PvP is live"),
                new Entry("Grey paths", "Walkways and buildings are safe"),
                new Entry("The Yard", "The main arena, off the south wall"),
                new Entry("Crates", "Keys drop from mining and fishing"),
                new Entry("/coinflip", "Gamble against another player"))));

        add(new Category("Everything Else", Material.BOOK,
                "The rest of the commands", List.of(
                new Entry("/daily", "Claim your daily reward"),
                new Entry("/top", "Leaderboards"),
                new Entry("/coinflip", "Open or create a coinflip"),
                new Entry("/shop", "The server shop"),
                new Entry("/spawn", "Return to the hub"))));
    }

    private void add(Category c) {
        categories.add(c);
        byName.put(c.name(), c);
    }

    /** Intercepts /help and /? before Bukkit's own handler sees them. */
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().trim().toLowerCase();
        if (!(msg.equals("/help") || msg.startsWith("/help ")
                || msg.equals("/?") || msg.startsWith("/? "))) {
            return;
        }
        e.setCancelled(true);
        open(e.getPlayer());
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, MAIN_TITLE);
        int slot = 10;
        for (Category c : categories) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + c.blurb());
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Click to open");
            inv.setItem(slot, item(c.icon(), ChatColor.AQUA + "" + ChatColor.BOLD + c.name(), lore));
            slot++;
            if (slot == 17) slot = 19;      // skip the frame column
        }
        p.openInventory(inv);
    }

    private void openCategory(Player p, Category c) {
        int rows = Math.max(3, (int) Math.ceil((c.entries().size() + 2) / 9.0) + 1);
        Inventory inv = Bukkit.createInventory(null, rows * 9, SUB_PREFIX + c.name());
        int slot = 9;
        for (Entry entry : c.entries()) {
            inv.setItem(slot++, item(Material.PAPER,
                    ChatColor.YELLOW + entry.command(),
                    List.of(ChatColor.GRAY + entry.what())));
        }
        inv.setItem(inv.getSize() - 1, item(Material.ARROW,
                ChatColor.WHITE + "Back", List.of(ChatColor.GRAY + "Return to the topic list")));
        p.openInventory(inv);
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        boolean main = title.equals(MAIN_TITLE);
        boolean sub = title.startsWith(SUB_PREFIX);
        if (!main && !sub) return;

        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        if (sub) {
            if (name.equals("Back")) open(p);
            return;
        }
        Category c = byName.get(name);
        if (c != null) openCategory(p, c);
    }
}
