package com.lukeprison.prison;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks fishing XP/levels and resolves which pond a location sits in. */
public class FishingManager {

    private final Plugin plugin;
    private final Map<UUID, Long> xp = new HashMap<>();
    private File file;
    private YamlConfiguration yaml;

    public FishingManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "fishing.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            xp.put(UUID.fromString(key), yaml.getLong(key + ".xp", 0));
        }
    }

    public void save() {
        if (yaml == null) return;
        for (Map.Entry<UUID, Long> e : xp.entrySet()) {
            yaml.set(e.getKey() + ".xp", e.getValue());
        }
        try { yaml.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    public long getXp(Player p) { return xp.getOrDefault(p.getUniqueId(), 0L); }
    public int getLevel(Player p) { return FishingData.levelForXp(getXp(p)); }
    public void addXp(Player p, long amount) { xp.merge(p.getUniqueId(), amount, Long::sum); }

    /** XP still needed for the next level, or 0 at the cap. */
    public long xpToNext(Player p) {
        int lvl = getLevel(p);
        if (lvl >= 60) return 0;
        return FishingData.xpForLevel(lvl + 1) - getXp(p);
    }

    /** Which pond (if any) contains this location. Bounds are set during the world build. */
    public FishingData.Pond pondAt(Location loc) {
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        for (FishingData.Pond pond : FishingData.PONDS.values()) {
            if (x >= pond.x1 && x <= pond.x2 && z >= pond.z1 && z <= pond.z2) return pond;
        }
        return null;
    }
}
