package com.lukeprison.prison;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Drives the whole fishing loop: gates the area behind a prison rank, gates each pond behind a
 * fishing level, replaces vanilla loot with the custom fish table, and awards fishing XP.
 */
public class FishingListener implements Listener {

    private final PrisonPlugin plugin;
    private final Random random = new Random();

    /** Chance that a vanilla catch is allowed through instead of being replaced. */
    private static final int VANILLA_PASSTHROUGH_PERCENT = 1;

    public FishingListener(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public static NamespacedKey fishKey() {
        return new NamespacedKey(PrisonPlugin.get(), "fish_value");
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = e.getPlayer();

        // Gate 1: the fishing area is locked until the player reaches the unlock rank.
        if (!plugin.ranks().canAccessMine(p, FishingData.UNLOCK_RANK)) {
            e.setCancelled(true);
            p.sendMessage("§cFishing unlocks at rank " + FishingData.UNLOCK_RANK + ".");
            return;
        }

        Location hook = e.getHook().getLocation();
        FishingData.Pond pond = plugin.fishing().pondAt(hook);

        // Fishing outside a designated pond catches nothing worthwhile.
        if (pond == null) {
            e.setCancelled(true);
            p.sendMessage("§7Nothing lives in this water. Try the fishing area.");
            return;
        }

        int level = plugin.fishing().getLevel(p);

        // Gate 2: each pond needs a fishing level.
        if (level < pond.requiredLevel) {
            e.setCancelled(true);
            p.sendMessage("§c" + pond.name + " requires fishing level " + pond.requiredLevel
                    + " §7(you are level " + level + ")");
            return;
        }

        // Suppress vanilla loot. Without this, ponds become a mending-book farm.
        boolean allowVanilla = random.nextInt(100) < VANILLA_PASSTHROUGH_PERCENT;
        if (!allowVanilla && e.getCaught() instanceof Item caughtItem) {
            FishingData.Fish fish = FishingData.roll(pond);
            caughtItem.setItemStack(buildFish(fish));
        }

        // XP and level-up notification.
        int before = level;
        plugin.fishing().addXp(p, pond.xpPerCatch);
        int after = plugin.fishing().getLevel(p);
        if (after > before) {
            p.sendMessage("§b§lFishing level up! §fYou are now level " + after + ".");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            int newPond = FishingData.maxPondFor(after);
            int oldPond = FishingData.maxPondFor(before);
            if (newPond > oldPond) {
                FishingData.Pond unlocked = FishingData.PONDS.get(newPond);
                p.sendMessage("§aYou've unlocked §f" + unlocked.name + "§a!");
            }
        }
    }

    /** Builds a named fish item carrying its sell value in persistent data. */
    public static ItemStack buildFish(FishingData.Fish fish) {
        ItemStack item = new ItemStack(fish.icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b" + fish.display);
        List<String> lore = new ArrayList<>();
        lore.add("§7Value: §a$" + String.format("%,.0f", fish.value));
        lore.add("§8Sell at the fishing hut.");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(fishKey(), PersistentDataType.DOUBLE, fish.value);
        item.setItemMeta(meta);
        return item;
    }

    /** Reads the stored value off a fish item, or 0 if it isn't one of ours. */
    public static double valueOf(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return 0;
        Double v = item.getItemMeta().getPersistentDataContainer()
                .get(fishKey(), PersistentDataType.DOUBLE);
        return v == null ? 0 : v;
    }
}
