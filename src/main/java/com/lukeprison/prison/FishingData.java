package com.lukeprison.prison;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Pond tiers, fishing level requirements, and the custom fish loot tables. */
public class FishingData {

    /** Prison rank required before the fishing area opens at all. */
    public static final String UNLOCK_RANK = "E";

    public static class Fish {
        public final String id, display;
        public final Material icon;
        public final double value;
        public final int weight; // relative spawn chance within its pond

        public Fish(String id, String display, Material icon, double value, int weight) {
            this.id = id;
            this.display = display;
            this.icon = icon;
            this.value = value;
            this.weight = weight;
        }
    }

    public static class Pond {
        public final int tier;
        public final String name;
        public final int requiredLevel;
        public final int xpPerCatch;
        public final List<Fish> fish = new ArrayList<>();
        // Physical bounds, filled in by WorldBuilder when it builds the area.
        public int x1, z1, x2, z2, y;

        public Pond(int tier, String name, int requiredLevel, int xpPerCatch) {
            this.tier = tier;
            this.name = name;
            this.requiredLevel = requiredLevel;
            this.xpPerCatch = xpPerCatch;
        }
    }

    public static final Map<Integer, Pond> PONDS = new LinkedHashMap<>();

    private static Pond pond(int tier, String name, int reqLevel, int xp, Fish... fishes) {
        Pond p = new Pond(tier, name, reqLevel, xp);
        for (Fish f : fishes) p.fish.add(f);
        PONDS.put(tier, p);
        return p;
    }

    static {
        // Values are deliberately modest relative to mining. Fishing is meant to be a relaxed
        // side income you can drift into when bored of the mines — pleasant, steadily rewarding,
        // but never the optimal way to earn. Top-tier catches are roughly comparable to a good
        // minute of mining, not a shortcut past it.
        pond(1, "Drainage Pool", 1, 5,
                new Fish("minnow", "Sewer Minnow", Material.COD, 8, 60),
                new Fish("carp", "Grey Carp", Material.COD, 18, 30),
                new Fish("eel", "Pipe Eel", Material.SALMON, 40, 10));

        pond(2, "Yard Pond", 3, 8,
                new Fish("perch", "Yard Perch", Material.COD, 25, 60),
                new Fish("bass", "Rockwall Bass", Material.SALMON, 55, 30),
                new Fish("catfish", "Iron Catfish", Material.SALMON, 110, 10));

        pond(3, "Quarry Lake", 6, 12,
                new Fish("trout", "Quarry Trout", Material.SALMON, 70, 60),
                new Fish("pike", "Slate Pike", Material.SALMON, 150, 30),
                new Fish("sturgeon", "Deep Sturgeon", Material.PUFFERFISH, 300, 10));

        pond(4, "Cold Spring", 10, 18,
                new Fish("char", "Frost Char", Material.SALMON, 160, 60),
                new Fish("grayling", "Pale Grayling", Material.COD, 320, 30),
                new Fish("icefish", "Glacier Fish", Material.PUFFERFISH, 650, 10));

        pond(5, "Sunken Basin", 15, 25,
                new Fish("snapper", "Basin Snapper", Material.TROPICAL_FISH, 320, 60),
                new Fish("ray", "Silt Ray", Material.TROPICAL_FISH, 640, 30),
                new Fish("angler", "Blind Angler", Material.PUFFERFISH, 1300, 10));

        pond(6, "Emerald Reservoir", 21, 35,
                new Fish("emeraldfin", "Emerald Fin", Material.TROPICAL_FISH, 600, 60),
                new Fish("jadecarp", "Jade Carp", Material.TROPICAL_FISH, 1200, 30),
                new Fish("verdant", "Verdant Leviathan", Material.PUFFERFISH, 2400, 10));

        pond(7, "Obsidian Depths", 28, 50,
                new Fish("shadowfish", "Shadowfish", Material.TROPICAL_FISH, 1100, 60),
                new Fish("voidscale", "Voidscale", Material.TROPICAL_FISH, 2200, 30),
                new Fish("obsidianpike", "Obsidian Pike", Material.PUFFERFISH, 4200, 10));

        pond(8, "Magma Vent", 36, 70,
                new Fish("cinderfish", "Cinderfish", Material.TROPICAL_FISH, 1900, 60),
                new Fish("emberray", "Ember Ray", Material.TROPICAL_FISH, 3800, 30),
                new Fish("magmaeel", "Magma Eel", Material.PUFFERFISH, 7000, 10));

        pond(9, "Sculk Sink", 45, 95,
                new Fish("echofish", "Echofish", Material.TROPICAL_FISH, 3000, 60),
                new Fish("wardenfin", "Warden Fin", Material.TROPICAL_FISH, 6000, 30),
                new Fish("deeplurker", "Deep Lurker", Material.PUFFERFISH, 11000, 10));

        pond(10, "The Abyss", 55, 130,
                new Fish("abyssal", "Abyssal Drifter", Material.TROPICAL_FISH, 5000, 60),
                new Fish("starfish", "Starlight Ray", Material.TROPICAL_FISH, 9500, 30),
                new Fish("leviathan", "The Leviathan", Material.PUFFERFISH, 18000, 10));
    }

    private static final Random RANDOM = new Random();

    /** Weighted random pick from a pond's table. */
    public static Fish roll(Pond pond) {
        int total = 0;
        for (Fish f : pond.fish) total += f.weight;
        int r = RANDOM.nextInt(total);
        int acc = 0;
        for (Fish f : pond.fish) {
            acc += f.weight;
            if (r < acc) return f;
        }
        return pond.fish.get(0);
    }

    /** XP needed to reach a given fishing level. Deliberately steep at the top end. */
    public static long xpForLevel(int level) {
        if (level <= 1) return 0;
        return Math.round(80 * Math.pow(level - 1, 2.15));
    }

    public static int levelForXp(long xp) {
        int lvl = 1;
        while (lvl < 60 && xp >= xpForLevel(lvl + 1)) lvl++;
        return lvl;
    }

    /** The highest pond tier a given fishing level can access. */
    public static int maxPondFor(int level) {
        int best = 0;
        for (Pond p : PONDS.values()) {
            if (level >= p.requiredLevel) best = Math.max(best, p.tier);
        }
        return best;
    }
}
