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
    private final Map<UUID, Long> lastDaily = new HashMap<>();
    private final Map<UUID, Integer> dailyStreak = new HashMap<>();
    private final Map<UUID, java.util.Set<Long>> milestonesHit = new HashMap<>();
    private final Map<Integer, UUID> cellOwners = new HashMap<>();
    private final Map<Integer, Long> cellExpiry = new HashMap<>();
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
            lastDaily.put(id, yaml.getLong(key + ".daily.last", 0));
            dailyStreak.put(id, yaml.getInt(key + ".daily.streak", 0));
            java.util.Set<Long> ms = new java.util.HashSet<>();
            for (long m : yaml.getLongList(key + ".milestones")) ms.add(m);
            milestonesHit.put(id, ms);
            int cell = yaml.getInt(key + ".cell", -1);
            if (cell > 0) {
                cellOwners.put(cell, id);
                cellExpiry.put(cell, yaml.getLong(key + ".cellExpiry", 0));
            }
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
            yaml.set(e.getKey() + ".daily.last", lastDaily.getOrDefault(e.getKey(), 0L));
            yaml.set(e.getKey() + ".daily.streak", dailyStreak.getOrDefault(e.getKey(), 0));
            yaml.set(e.getKey() + ".milestones", new java.util.ArrayList<>(milestonesHit.getOrDefault(e.getKey(), java.util.Set.of())));
            int owned = -1;
            for (Map.Entry<Integer, UUID> c : cellOwners.entrySet()) if (c.getValue().equals(e.getKey())) owned = c.getKey();
            yaml.set(e.getKey() + ".cell", owned);
            yaml.set(e.getKey() + ".cellExpiry", owned > 0 ? cellExpiry.getOrDefault(owned, 0L) : 0L);
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

    // ---- Prestige perks ----
    /** Sell-price multiplier: +3% per prestige, capped at +150%. Modest, but permanent. */
    public double getSellMultiplier(Player p) {
        return 1.0 + Math.min(1.5, getPrestige(p) * 0.03);
    }

    // ---- Daily rewards ----
    public long getLastDaily(Player p) { return lastDaily.getOrDefault(p.getUniqueId(), 0L); }
    public int getDailyStreak(Player p) { return dailyStreak.getOrDefault(p.getUniqueId(), 0); }
    public void recordDaily(Player p, int streak) {
        lastDaily.put(p.getUniqueId(), System.currentTimeMillis());
        dailyStreak.put(p.getUniqueId(), streak);
    }

    // ---- Milestones ----
    public boolean claimMilestone(Player p, long threshold) {
        return milestonesHit.computeIfAbsent(p.getUniqueId(), k -> new java.util.HashSet<>()).add(threshold);
    }

    // ---- Cells ----
    public UUID cellOwner(int cell) { return cellOwners.get(cell); }
    public Integer cellOf(Player p) {
        for (Map.Entry<Integer, UUID> c : cellOwners.entrySet()) if (c.getValue().equals(p.getUniqueId())) return c.getKey();
        return null;
    }
    private static final long CELL_RENT_DURATION = 72L * 60 * 60 * 1000;

    public void claimCell(Player p, int cell) {
        cellOwners.put(cell, p.getUniqueId());
        cellExpiry.put(cell, System.currentTimeMillis() + CELL_RENT_DURATION);
    }
    public void unclaimCell(int cell) {
        cellOwners.remove(cell);
        cellExpiry.remove(cell);
    }
    public long getCellExpiry(int cell) { return cellExpiry.getOrDefault(cell, 0L); }
    /** Resets the rental clock to a fresh 72 hours from now. */
    public void renewCell(int cell) { cellExpiry.put(cell, System.currentTimeMillis() + CELL_RENT_DURATION); }
    public Map<Integer, Long> allCellExpiry() { return cellExpiry; }
    public Map<Integer, UUID> allCellOwners() { return cellOwners; }
    public java.util.Set<UUID> knownPlayers() { return ranks.keySet(); }
    public Map<UUID, Long> allBlocksMined() { return blocksMined; }
    public Map<UUID, Integer> allPrestige() { return prestige; }

    /** Wipes a player back to a brand-new state so the intro can be replayed. */
    public void resetPlayer(Player p) {
        UUID id = p.getUniqueId();
        ranks.put(id, "A");
        prestige.put(id, 0);
        tokens.put(id, 0L);
        blocksMined.put(id, 0L);
        autoSell.remove(id);
        kitGiven.remove(id);
        lastDaily.remove(id);
        dailyStreak.remove(id);
        milestonesHit.remove(id);
        Integer cell = cellOf(p);
        if (cell != null) cellOwners.remove(cell);
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
