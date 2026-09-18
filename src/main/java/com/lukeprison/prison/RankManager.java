package com.lukeprison.prison;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RankManager {

    private final Plugin plugin;
    private final Map<UUID, String> ranks = new HashMap<>();
    private final Map<UUID, Integer> prestige = new HashMap<>();
    private final java.util.Set<UUID> kitGiven = new java.util.HashSet<>();
    private final Map<UUID, Long> tokens = new HashMap<>();
    private final Map<UUID, Long> blocksMined = new HashMap<>();
    private final java.util.Set<UUID> autoSell = new java.util.HashSet<>();
    private File file;
    private YamlConfiguration yaml;

    public RankManager(Plugin plugin) { this.plugin = plugin; }

    public void load() {
        file = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            UUID id = UUID.fromString(key);
            ranks.put(id, yaml.getString(key + ".rank", "A"));
            prestige.put(id, yaml.getInt(key + ".prestige", 0));
            if (yaml.getBoolean(key + ".kit", false)) kitGiven.add(id);
            tokens.put(id, yaml.getLong(key + ".tokens", 0));
            blocksMined.put(id, yaml.getLong(key + ".blocks", 0));
            if (yaml.getBoolean(key + ".autosell", false)) autoSell.add(id);
        }
    }

    public void save() {
        if (yaml == null) return;
        for (Map.Entry<UUID, String> e : ranks.entrySet()) {
            yaml.set(e.getKey() + ".rank", e.getValue());
            yaml.set(e.getKey() + ".prestige", prestige.getOrDefault(e.getKey(), 0));
            yaml.set(e.getKey() + ".kit", kitGiven.contains(e.getKey()));
            yaml.set(e.getKey() + ".tokens", tokens.getOrDefault(e.getKey(), 0L));
            yaml.set(e.getKey() + ".blocks", blocksMined.getOrDefault(e.getKey(), 0L));
            yaml.set(e.getKey() + ".autosell", autoSell.contains(e.getKey()));
        }
        try { yaml.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    public long getTokens(Player p) { return tokens.getOrDefault(p.getUniqueId(), 0L); }
    public void addTokens(Player p, long amount) { tokens.merge(p.getUniqueId(), amount, Long::sum); }
    public boolean spendTokens(Player p, long amount) {
        long have = getTokens(p);
        if (have < amount) return false;
        tokens.put(p.getUniqueId(), have - amount);
        return true;
    }

    public long getBlocksMined(Player p) { return blocksMined.getOrDefault(p.getUniqueId(), 0L); }
    public void addBlockMined(Player p) { blocksMined.merge(p.getUniqueId(), 1L, Long::sum); }

    public boolean isAutoSell(Player p) { return autoSell.contains(p.getUniqueId()); }
    public boolean toggleAutoSell(Player p) {
        if (autoSell.contains(p.getUniqueId())) { autoSell.remove(p.getUniqueId()); return false; }
        autoSell.add(p.getUniqueId());
        return true;
    }

    /** Wipes a player back to a brand-new state so the intro can be replayed. */
    public void resetPlayer(Player p) {
        UUID id = p.getUniqueId();
        ranks.put(id, "A");
        prestige.put(id, 0);
        tokens.put(id, 0L);
        blocksMined.put(id, 0L);
        autoSell.remove(id);
        kitGiven.remove(id);
        p.recalculatePermissions();
    }

    public boolean hasReceivedKit(Player p) { return kitGiven.contains(p.getUniqueId()); }
    public void markKitGiven(Player p) { kitGiven.add(p.getUniqueId()); }

    public String getRank(Player p) { return ranks.getOrDefault(p.getUniqueId(), "A"); }
    public int getPrestige(Player p) { return prestige.getOrDefault(p.getUniqueId(), 0); }

    public void setRank(Player p, String rank) {
        ranks.put(p.getUniqueId(), rank);
        p.recalculatePermissions();
    }

    public boolean rankUp(Player p) {
        String current = getRank(p);
        RankMineData.Def def = RankMineData.RANKS.get(current);
        if (def == null || def.next.equals(current)) return false; // already at Free
        RankMineData.Def next = RankMineData.RANKS.get(def.next);
        double cost = next.cost;
        if (!PrisonPlugin.get().economy().has(p, cost)) return false;
        PrisonPlugin.get().economy().withdrawPlayer(p, cost);
        setRank(p, def.next);
        return true;
    }

    public boolean canAccessMine(Player p, String mineRank) {
        String current = getRank(p);
        // Alphabetical/ordinal comparison: player can mine at their current rank's mine and any lower one.
        int playerIdx = rankIndex(current);
        int mineIdx = rankIndex(mineRank);
        return playerIdx >= mineIdx;
    }

    private int rankIndex(String rank) {
        int i = 0;
        for (String r : RankMineData.RANKS.keySet()) {
            if (r.equals(rank)) return i;
            i++;
        }
        return 0;
    }

    public boolean prestige(Player p) {
        if (!getRank(p).equals("FREE")) return false;
        double cost = RankMineData.RANKS.get("FREE").cost;
        if (!PrisonPlugin.get().economy().has(p, cost)) return false;
        PrisonPlugin.get().economy().withdrawPlayer(p, cost);
        prestige.merge(p.getUniqueId(), 1, Integer::sum);
        setRank(p, "A");
        return true;
    }
}
