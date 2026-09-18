package com.lukeprison.prison;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

/**
 * Constructs the entire physical server: starter area and prison bus, intake corridor,
 * hub, 100+ cells, a ward antechamber per rank fronting its mine, and the tiered fishing area.
 *
 * Block placement happens ONCE, on first boot. A marker file then short-circuits the build on
 * every later restart, while bounds are still registered in memory (cheap) so gameplay logic
 * keeps working. Without this the server would spend minutes re-placing millions of blocks
 * every single start.
 */
public class WorldBuilder {

    private final Plugin plugin;
    private final World world;
    private final Map<String, int[]> mineBounds = new LinkedHashMap<>();
    private final Map<Location, SellSignListener.SellSignData> sellSigns = new HashMap<>();
    private Location hubSpawn;
    private Location starterSpawn;

    // ---- Layout constants -------------------------------------------------
    private static final int GROUND_Y = 94;
    private static final int ENTRANCE_Z = 3;

    private static final int STARTER_X1 = -150, STARTER_X2 = -110;
    private static final int STARTER_Z1 = -18, STARTER_Z2 = 18;
    private static final int CORRIDOR_X1 = -110, CORRIDOR_X2 = -41;
    private static final int HUB_X1 = -40, HUB_X2 = -5, HUB_Z1 = -20, HUB_Z2 = 20;

    private static final int CELL_Z_START = 40;
    private static final int CELLS_PER_ROW = 10;
    private static final int CELL_ROWS = 10;      // 10 x 10 = 100 cells
    private static final int CELL_SIZE = 5;

    private static final int FISH_Z_BASE = -80;   // ponds run away from the prison on -Z

    public WorldBuilder(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public Map<String, int[]> getMineBounds() { return mineBounds; }
    public Map<Location, SellSignListener.SellSignData> getSellSigns() { return sellSigns; }
    public Location getHubSpawn() { return hubSpawn; }
    public Location getStarterSpawn() { return starterSpawn; }

    private File markerFile() {
        return new File(plugin.getDataFolder(), "world-built.marker");
    }

    public boolean alreadyBuilt() {
        return markerFile().exists();
    }

    /**
     * Registers every zone's bounds and sell-sign data in memory. Always runs, and places no
     * blocks, so restarts stay fast.
     */
    public void registerBounds() {
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (d.rank.equals("FREE")) continue;
            mineBounds.put(d.rank, new int[]{d.x1, d.y1, d.z1, d.x2, d.y2, d.z2});

            Material filler = safeMaterial(d.filler);
            Material common = safeMaterial(d.common);
            Material rare = safeMaterial(d.rare);
            Map<Material, Double> prices = new HashMap<>();
            prices.put(filler, d.fillerPrice);
            prices.put(common, d.commonPrice);
            prices.put(rare, d.rarePrice);

            Location signLoc = new Location(world, d.x1 - 1, d.y1 + 1, d.z1 + ENTRANCE_Z);
            sellSigns.put(signLoc, new SellSignListener.SellSignData(d.rank, prices));
        }
        registerPondBounds();
        registerCrateLocations();
        buildPvpArenas(false); // register zone bounds only; no block placement
        hubSpawn = new Location(world, HUB_X1 + 5.5, GROUND_Y + 1, 0.5);
        starterSpawn = new Location(world, STARTER_X1 + 6.5, GROUND_Y + 1, 0.5);
        world.setSpawnLocation(HUB_X1 + 5, GROUND_Y + 1, 0);
    }

    /** Full block placement. Only called on first boot. */
    public void buildAll() {
        plugin.getLogger().info("First boot detected \u2014 building the prison. This takes a few minutes.");
        buildStarterArea();
        buildIntakeCorridor();
        buildHub();
        buildCellBlock();
        for (RankMineData.Def def : RankMineData.RANKS.values()) {
            if (def.rank.equals("FREE")) continue;
            buildMine(def);
            buildWard(def);
        }
        connectMineWalkways();
        buildFishingArea();
        buildCrateRoom();
        buildPvpArenas(true);
        setupWorldBorder();
        try {
            plugin.getDataFolder().mkdirs();
            markerFile().createNewFile();
        } catch (Exception e) {
            plugin.getLogger().warning("Could not write world-built marker; the world may rebuild next boot.");
        }
        plugin.getLogger().info("World build complete.");
    }

    // ---- Starter area -----------------------------------------------------

    private void buildStarterArea() {
        // Open-air arrival yard, walled so new players are funnelled toward the corridor.
        floor(STARTER_X1, STARTER_Z1, STARTER_X2, STARTER_Z2, GROUND_Y, Material.GRAY_CONCRETE);
        perimeter(STARTER_X1, STARTER_Z1, STARTER_X2, STARTER_Z2, GROUND_Y + 1, GROUND_Y + 6,
                Material.STONE_BRICKS);
        lightGrid(STARTER_X1, STARTER_Z1, STARTER_X2, STARTER_Z2, GROUND_Y + 6, 10, Material.SEA_LANTERN);

        buildPrisonBus(STARTER_X1 + 4, GROUND_Y + 1, -3);

        sign(STARTER_X1 + 9, GROUND_Y + 2, 0,
                "§8§lINTAKE", "Welcome to", "the Prison.", "Head east \u2192");

        // Opening from the yard into the intake corridor.
        carve(STARTER_X2, GROUND_Y + 1, 0, 1, 3);
    }

    /** A blocky prison transport bus the player spawns beside. */
    private void buildPrisonBus(int x, int y, int z) {
        for (int dx = 0; dx < 12; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                for (int dy = 0; dy < 4; dy++) {
                    boolean shell = dx == 0 || dx == 11 || dz == 0 || dz == 4 || dy == 0 || dy == 3;
                    Block b = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (shell) {
                        boolean window = dy == 2 && dx > 1 && dx < 10 && (dz == 0 || dz == 4);
                        b.setType(window ? Material.GRAY_STAINED_GLASS : Material.YELLOW_CONCRETE);
                    } else {
                        b.setType(Material.AIR);
                    }
                }
            }
        }
        // Wheels and a doorway out of the bus.
        for (int dx : new int[]{2, 9}) {
            for (int dz : new int[]{0, 4}) {
                world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.BLACK_CONCRETE);
            }
        }
        for (int dy = 1; dy <= 2; dy++) {
            world.getBlockAt(x + 11, y + dy, z + 2).setType(Material.AIR);
        }
    }

    // ---- Intake corridor (tutorial + NPCs) --------------------------------

    private void buildIntakeCorridor() {
        int z1 = -4, z2 = 4;
        floor(CORRIDOR_X1, z1, CORRIDOR_X2, z2, GROUND_Y, Material.SMOOTH_STONE);
        perimeter(CORRIDOR_X1, z1, CORRIDOR_X2, z2, GROUND_Y + 1, GROUND_Y + 5, Material.STONE_BRICKS);
        ceiling(CORRIDOR_X1, z1, CORRIDOR_X2, z2, GROUND_Y + 5, Material.STONE_BRICKS);
        lightGrid(CORRIDOR_X1, z1, CORRIDOR_X2, z2, GROUND_Y + 4, 8, Material.SEA_LANTERN);

        // Tutorial signage down the corridor wall.
        sign(CORRIDOR_X1 + 8, GROUND_Y + 2, z1 + 1,
                "§6Step 1", "Mine blocks in", "your rank's mine", "to earn money.");
        sign(CORRIDOR_X1 + 20, GROUND_Y + 2, z1 + 1,
                "§6Step 2", "Sell them at the", "sell sign, or use", "/autosell");
        sign(CORRIDOR_X1 + 32, GROUND_Y + 2, z1 + 1,
                "§6Step 3", "/rankup to climb", "from A to Z,", "then prestige.");
        sign(CORRIDOR_X1 + 44, GROUND_Y + 2, z1 + 1,
                "§6Step 4", "Spend tokens on", "pickaxe enchants", "with /enchant");
        sign(CORRIDOR_X1 + 56, GROUND_Y + 2, z1 + 1,
                "§bFishing", "Unlocks at rank " + FishingData.UNLOCK_RANK, "Own level ladder", "/fishing");

        // Opening into the hub.
        carve(CORRIDOR_X2, GROUND_Y + 1, 0, 1, 3);
    }

    /** Coordinates where quest NPCs are spawned (handled by QuestNpcManager). */
    public Location wardenNpcLocation() {
        return new Location(world, CORRIDOR_X1 + 4.5, GROUND_Y + 1, 0.5);
    }

    public Location quartermasterNpcLocation() {
        return new Location(world, CORRIDOR_X1 + 50.5, GROUND_Y + 1, 0.5);
    }

    // ---- Hub --------------------------------------------------------------

    private void buildHub() {
        int wallTop = GROUND_Y + 8;
        floor(HUB_X1, HUB_Z1, HUB_X2, HUB_Z2, GROUND_Y, Material.POLISHED_ANDESITE);
        hollow(HUB_X1, HUB_Z1, HUB_X2, HUB_Z2, GROUND_Y + 1, wallTop - 1);
        perimeter(HUB_X1, HUB_Z1, HUB_X2, HUB_Z2, GROUND_Y + 1, wallTop - 1, Material.GRAY_CONCRETE);
        ceiling(HUB_X1, HUB_Z1, HUB_X2, HUB_Z2, wallTop, Material.GRAY_CONCRETE);
        lightGrid(HUB_X1, HUB_Z1, HUB_X2, HUB_Z2, wallTop - 1, 6, Material.SEA_LANTERN);

        // Doorways: west to the intake corridor, east to the wards, north to the cell block.
        carve(HUB_X1, GROUND_Y + 1, 0, 1, 3);
        carve(HUB_X2, GROUND_Y + 1, ENTRANCE_Z, 1, 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                world.getBlockAt(HUB_X1 + 18 + dx, GROUND_Y + dy, HUB_Z2).setType(Material.AIR);
            }
        }

        sign(HUB_X1 + 6, GROUND_Y + 2, 2, "§6§lTHE HUB", "/prison  menu", "/warps  mines", "/fishing  ponds");

        // Walkway from the hub's east door to Mine A.
        RankMineData.Def mineA = RankMineData.RANKS.get("A");
        if (mineA != null) {
            for (int x = HUB_X2 + 1; x < mineA.x1; x++) {
                for (int z = ENTRANCE_Z - 2; z <= ENTRANCE_Z + 2; z++) {
                    world.getBlockAt(x, mineA.y1, z).setType(Material.SMOOTH_STONE);
                }
            }
        }
    }

    // ---- Cell block (100 cells) -------------------------------------------

    private void buildCellBlock() {
        int blockX1 = HUB_X1 + 10;
        int blockX2 = blockX1 + (CELLS_PER_ROW * CELL_SIZE) + 3;
        int blockZ1 = CELL_Z_START;
        int blockZ2 = blockZ1 + (CELL_ROWS * CELL_SIZE) + 3;

        floor(blockX1 - 2, blockZ1 - 4, blockX2 + 2, blockZ2 + 2, GROUND_Y, Material.POLISHED_ANDESITE);
        perimeter(blockX1 - 2, blockZ1 - 4, blockX2 + 2, blockZ2 + 2, GROUND_Y + 1, GROUND_Y + 7,
                Material.GRAY_CONCRETE);
        ceiling(blockX1 - 2, blockZ1 - 4, blockX2 + 2, blockZ2 + 2, GROUND_Y + 8, Material.GRAY_CONCRETE);
        lightGrid(blockX1 - 2, blockZ1 - 4, blockX2 + 2, blockZ2 + 2, GROUND_Y + 7, 7, Material.SEA_LANTERN);

        // Corridor opening back toward the hub.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                world.getBlockAt(HUB_X1 + 18 + dx, GROUND_Y + dy, blockZ1 - 4).setType(Material.AIR);
            }
        }

        int cellNumber = 1;
        for (int row = 0; row < CELL_ROWS; row++) {
            for (int col = 0; col < CELLS_PER_ROW; col++) {
                int cx = blockX1 + col * CELL_SIZE;
                int cz = blockZ1 + row * CELL_SIZE;
                buildCell(cx, cz, cellNumber++);
            }
        }

        sign(blockX1, GROUND_Y + 2, blockZ1 - 3,
                "§8§lCELL BLOCK", CELL_ROWS * CELLS_PER_ROW + " cells", "Claim one", "at your rank.");
    }

    private void buildCell(int x, int z, int number) {
        int y = GROUND_Y;
        for (int dx = 0; dx < CELL_SIZE; dx++) {
            for (int dz = 0; dz < CELL_SIZE; dz++) {
                for (int dy = 1; dy <= 4; dy++) {
                    boolean wall = dx == 0 || dx == CELL_SIZE - 1 || dz == 0 || dz == CELL_SIZE - 1;
                    Block b = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (dy == 4) {
                        b.setType(Material.GRAY_CONCRETE);
                    } else if (wall) {
                        // Front face is barred so the block reads as a row of cells.
                        boolean front = dz == 0 && dx > 0 && dx < CELL_SIZE - 1;
                        b.setType(front ? Material.IRON_BARS : Material.GRAY_CONCRETE);
                    } else {
                        b.setType(Material.AIR);
                    }
                }
            }
        }
        // Cell door, bed and a light.
        world.getBlockAt(x + 2, y + 1, z).setType(Material.AIR);
        world.getBlockAt(x + 2, y + 2, z).setType(Material.AIR);
        world.getBlockAt(x + 1, y + 1, z + CELL_SIZE - 2).setType(Material.RED_BED);
        world.getBlockAt(x + CELL_SIZE - 2, y + 3, z + CELL_SIZE - 2).setType(Material.LANTERN);
        sign(x + 3, y + 2, z, "§7Cell", "§f#" + number, "", "");
    }

    // ---- Mines and wards --------------------------------------------------

    /**
     * Rare-ore frequency climbs with rank: ~2% in Mine A up to ~12% in Mine Z, so rare blocks
     * stay rare but become meaningfully more common the deeper you get.
     */
    private double rarePercentFor(String rank) {
        int idx = 0, i = 0;
        for (String r : RankMineData.RANKS.keySet()) {
            if (r.equals(rank)) { idx = i; break; }
            i++;
        }
        int total = RankMineData.RANKS.size() - 1;
        return 2.0 + (10.0 * idx / Math.max(1, total));
    }

    private void buildMine(RankMineData.Def d) {
        Material filler = safeMaterial(d.filler);
        Material common = safeMaterial(d.common);
        Material rare = safeMaterial(d.rare);
        double rarePct = rarePercentFor(d.rank);

        for (int x = d.x1; x <= d.x2; x++) {
            for (int z = d.z1; z <= d.z2; z++) {
                for (int y = d.y1; y <= d.y2; y++) {
                    boolean edge = x == d.x1 || x == d.x2 || z == d.z1 || z == d.z2
                            || y == d.y1 || y == d.y2;
                    Block b = world.getBlockAt(x, y, z);
                    b.setType(edge ? Material.BEDROCK : pickOre(filler, common, rare, rarePct));
                }
            }
        }

        int entranceZ = d.z1 + ENTRANCE_Z;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = 1; dy <= 3; dy++) {
                world.getBlockAt(d.x1, d.y1 + dy, entranceZ + dz).setType(Material.AIR);
                world.getBlockAt(d.x2, d.y1 + dy, entranceZ + dz).setType(Material.AIR);
            }
        }

        placeMineLights(d);

        Block signBlock = world.getBlockAt(d.x1 - 1, d.y1 + 1, entranceZ);
        signBlock.setType(Material.OAK_SIGN);
        if (signBlock.getState() instanceof Sign s) {
            s.getSide(Side.FRONT).setLine(0, "[Sell]");
            s.getSide(Side.FRONT).setLine(1, "Mine " + d.rank + " ores");
            s.getSide(Side.FRONT).setLine(2, "Right-click");
            s.getSide(Side.FRONT).setLine(3, "to sell held");
            s.update(true);
        }
    }

    /** A small signed antechamber fronting each mine — the rank's "ward". */
    private void buildWard(RankMineData.Def d) {
        int entranceZ = d.z1 + ENTRANCE_Z;
        int wx2 = d.x1 - 2;
        int wx1 = wx2 - 9;
        int wz1 = entranceZ - 5;
        int wz2 = entranceZ + 5;

        floor(wx1, wz1, wx2, wz2, d.y1, Material.POLISHED_ANDESITE);
        perimeter(wx1, wz1, wx2, wz2, d.y1 + 1, d.y1 + 5, Material.GRAY_CONCRETE);
        ceiling(wx1, wz1, wx2, wz2, d.y1 + 6, Material.GRAY_CONCRETE);
        world.getBlockAt((wx1 + wx2) / 2, d.y1 + 5, entranceZ).setType(Material.SEA_LANTERN);

        // Doorways through both ends so the ward sits on the walkway.
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = 1; dy <= 3; dy++) {
                world.getBlockAt(wx1, d.y1 + dy, entranceZ + dz).setType(Material.AIR);
                world.getBlockAt(wx2, d.y1 + dy, entranceZ + dz).setType(Material.AIR);
            }
        }

        sign(wx1 + 2, d.y1 + 2, wz1 + 1,
                "§6§l" + d.rank + "-WARD",
                "Mine " + d.rank,
                "Rare: " + prettyName(d.rare),
                String.format("%.0f%% rare", rarePercentFor(d.rank)));
    }

    private void connectMineWalkways() {
        List<RankMineData.Def> ordered = new ArrayList<>();
        for (RankMineData.Def def : RankMineData.RANKS.values()) {
            if (!def.rank.equals("FREE")) ordered.add(def);
        }
        for (int i = 0; i < ordered.size() - 1; i++) {
            RankMineData.Def a = ordered.get(i);
            RankMineData.Def b = ordered.get(i + 1);
            int z = a.z1 + ENTRANCE_Z;
            for (int x = a.x2 + 1; x < b.x1; x++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.getBlockAt(x, a.y1, z + dz).setType(Material.SMOOTH_STONE);
                }
            }
        }
    }

    // ---- Fishing area -----------------------------------------------------

    /** Assigns each pond its bounds. Cheap; runs every boot so pond lookups work. */
    private void registerPondBounds() {
        int x = 0;
        for (FishingData.Pond pond : FishingData.PONDS.values()) {
            int size = 24 + pond.tier * 2;
            pond.x1 = x;
            pond.x2 = x + size;
            pond.z1 = FISH_Z_BASE - size;
            pond.z2 = FISH_Z_BASE;
            pond.y = GROUND_Y;
            x = pond.x2 + 10;
        }
    }

    private void buildFishingArea() {
        // One shared open-air area: every pond sits on the same walkable platform so players
        // fish alongside each other rather than in isolated instances.
        int platformX1 = -10;
        int platformX2 = 0;
        int minZ = FISH_Z_BASE;
        for (FishingData.Pond pond : FishingData.PONDS.values()) {
            platformX2 = Math.max(platformX2, pond.x2 + 6);
            minZ = Math.min(minZ, pond.z1 - 6);
        }
        floor(platformX1, minZ, platformX2, FISH_Z_BASE + 8, GROUND_Y, Material.GRASS_BLOCK);
        perimeter(platformX1, minZ, platformX2, FISH_Z_BASE + 8, GROUND_Y + 1, GROUND_Y + 4,
                Material.STONE_BRICK_WALL);

        for (FishingData.Pond pond : FishingData.PONDS.values()) {
            digPond(pond);
        }

        // Path linking the fishing area back to the prison walkway.
        for (int z = FISH_Z_BASE + 1; z <= ENTRANCE_Z; z++) {
            for (int dx = -2; dx <= 2; dx++) {
                world.getBlockAt(2 + dx, GROUND_Y, z).setType(Material.SMOOTH_STONE);
            }
        }
    }

    private void digPond(FishingData.Pond pond) {
        for (int x = pond.x1; x <= pond.x2; x++) {
            for (int z = pond.z1; z <= pond.z2; z++) {
                boolean rim = x == pond.x1 || x == pond.x2 || z == pond.z1 || z == pond.z2;
                if (rim) {
                    world.getBlockAt(x, GROUND_Y, z).setType(Material.STONE_BRICKS);
                    continue;
                }
                world.getBlockAt(x, GROUND_Y, z).setType(Material.WATER);
                world.getBlockAt(x, GROUND_Y - 1, z).setType(Material.WATER);
                world.getBlockAt(x, GROUND_Y - 2, z).setType(tierBed(pond.tier));
            }
        }
        sign(pond.x1 + 2, GROUND_Y + 1, pond.z2 + 1,
                "§b§l" + pond.name,
                "Tier " + pond.tier,
                "Req. level " + pond.requiredLevel,
                "/fishing");
    }

    /** Pond floor material shifts with tier so higher ponds read as more dangerous. */
    private Material tierBed(int tier) {
        if (tier <= 2) return Material.GRAVEL;
        if (tier <= 4) return Material.SAND;
        if (tier <= 6) return Material.PRISMARINE;
        if (tier <= 8) return Material.DARK_PRISMARINE;
        return Material.SCULK;
    }

    // ---- Crates -----------------------------------------------------------

    /** Records where each crate block sits so the listener can hook them up. */
    private final Map<Location, String> crateLocations = new LinkedHashMap<>();

    public Map<Location, String> getCrateLocations() { return crateLocations; }

    /** A small crate hall off the hub, with one podium per crate type. */
    /** Computes crate podium coordinates. Registration-only when placeBlocks is false. */
    private void registerCrateLocations() {
        crateLocations.clear();
        int rx1 = HUB_X1 + 2, rx2 = HUB_X1 + 22;
        int rz1 = HUB_Z1 - 22, rz2 = HUB_Z1 - 2;
        int i = 0;
        int spacing = (rx2 - rx1) / (CrateData.CRATES.size() + 1);
        for (CrateData.Crate crate : CrateData.CRATES.values()) {
            i++;
            int cx = rx1 + spacing * i;
            int cz = (rz1 + rz2) / 2;
            crateLocations.put(new Location(world, cx, GROUND_Y + 2, cz), crate.id);
        }
    }

    private void buildCrateRoom() {
        int rx1 = HUB_X1 + 2, rx2 = HUB_X1 + 22;
        int rz1 = HUB_Z1 - 22, rz2 = HUB_Z1 - 2;
        int y = GROUND_Y;

        floor(rx1, rz1, rx2, rz2, y, Material.POLISHED_DIORITE);
        hollow(rx1, rz1, rx2, rz2, y + 1, y + 6);
        perimeter(rx1, rz1, rx2, rz2, y + 1, y + 6, Material.DEEPSLATE_BRICKS);
        ceiling(rx1, rz1, rx2, rz2, y + 7, Material.DEEPSLATE_BRICKS);
        lightGrid(rx1, rz1, rx2, rz2, y + 6, 5, Material.SEA_LANTERN);

        // Doorway back into the hub.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                world.getBlockAt(rx1 + 10 + dx, y + dy, rz2).setType(Material.AIR);
            }
        }

        // One podium per crate, evenly spaced along the room.
        int i = 0;
        int spacing = (rx2 - rx1) / (CrateData.CRATES.size() + 1);
        for (CrateData.Crate crate : CrateData.CRATES.values()) {
            i++;
            int cx = rx1 + spacing * i;
            int cz = (rz1 + rz2) / 2;
            // Podium block with the crate chest on top.
            world.getBlockAt(cx, y + 1, cz).setType(Material.CHISELED_DEEPSLATE);
            Block crateBlock = world.getBlockAt(cx, y + 2, cz);
            crateBlock.setType(Material.ENDER_CHEST);
            crateLocations.put(crateBlock.getLocation(), crate.id);
            // Accent lighting around each podium.
            world.getBlockAt(cx - 1, y + 1, cz).setType(Material.POLISHED_DEEPSLATE_SLAB);
            world.getBlockAt(cx + 1, y + 1, cz).setType(Material.POLISHED_DEEPSLATE_SLAB);
            sign(cx, y + 2, cz - 1, "\u00a76\u00a7l" + crate.display, "Right-click", "with a key", "to open");
        }

        sign(rx1 + 10, y + 2, rz1 + 1, "\u00a76\u00a7lCRATES", "Keys drop from", "mining and", "fishing.");
    }

    // ---- PvP arenas -------------------------------------------------------

    private final List<PvpZoneManager.Zone> pvpZones = new ArrayList<>();

    public List<PvpZoneManager.Zone> getPvpZones() { return pvpZones; }

    /**
     * PvP arenas placed through the prison. Floors are lined with red wool, the long-standing
     * prison-server signal for "you can be killed and looted here".
     */
    private void buildPvpArenas(boolean placeBlocks) {
        pvpZones.clear();
        // A main arena just off the hub.
        addArena("The Yard", HUB_X1 + 4, GROUND_Y, HUB_Z2 + 6, HUB_X1 + 30, GROUND_Y + 10,
                HUB_Z2 + 30, true, placeBlocks);

        // Smaller contested arenas out among the wards, so higher ranks have their own.
        String[] wardRanks = {"F", "M", "T"};
        for (String rank : wardRanks) {
            RankMineData.Def d = RankMineData.RANKS.get(rank);
            if (d == null) continue;
            int ax1 = d.x1 - 14;
            int az1 = d.z1 + ENTRANCE_Z + 8;
            addArena(rank + "-Ward Pit", ax1, d.y1, az1, ax1 + 18, d.y1 + 8, az1 + 18,
                    false, placeBlocks);
        }
    }

    private void addArena(String name, int x1, int y, int z1, int x2, int yTop, int z2,
                          boolean grand, boolean placeBlocks) {
        pvpZones.add(new PvpZoneManager.Zone(name, x1, y, z1, x2, yTop, z2));
        if (!placeBlocks) return;
        // Red wool floor — the visual marker players recognise instantly.
        floor(x1, z1, x2, z2, y, Material.RED_WOOL);
        // Border ring in darker red so the boundary is unmistakable.
        perimeter(x1, z1, x2, z2, y, y, Material.RED_CONCRETE);
        // Low walls so fights stay inside, open-topped for visibility.
        perimeter(x1, z1, x2, z2, y + 1, y + 3, Material.RED_CONCRETE);
        hollow(x1 + 1, z1 + 1, x2 - 1, z2 - 1, y + 1, yTop);

        // Corner pillars and lighting for a built-on-purpose look.
        for (int[] corner : new int[][]{{x1, z1}, {x1, z2}, {x2, z1}, {x2, z2}}) {
            for (int dy = 1; dy <= 5; dy++) {
                world.getBlockAt(corner[0], y + dy, corner[1]).setType(Material.DEEPSLATE_BRICKS);
            }
            world.getBlockAt(corner[0], y + 6, corner[1]).setType(Material.REDSTONE_LAMP);
        }

        if (grand) {
            // Cover in the middle of the main arena so fights aren't pure open ground.
            int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        world.getBlockAt(mx + dx, y + 1, mz + dz).setType(Material.DEEPSLATE_BRICK_WALL);
                    }
                }
            }
        }

        // Entrances on two sides.
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = 1; dy <= 3; dy++) {
                world.getBlockAt(x1, y + dy, (z1 + z2) / 2 + dz).setType(Material.AIR);
                world.getBlockAt(x2, y + dy, (z1 + z2) / 2 + dz).setType(Material.AIR);
            }
        }

        sign(x1 + 2, y + 2, z1 + 1, "\u00a7c\u00a7lPVP ZONE", name, "Drop all items", "on death!");
    }

    // ---- Shared helpers ---------------------------------------------------

    private void floor(int x1, int z1, int x2, int z2, int y, Material mat) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                world.getBlockAt(x, y, z).setType(mat);
            }
        }
    }

    private void ceiling(int x1, int z1, int x2, int z2, int y, Material mat) {
        floor(x1, z1, x2, z2, y, mat);
    }

    private void hollow(int x1, int z1, int x2, int z2, int yFrom, int yTo) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int y = yFrom; y <= yTo; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
    }

    private void perimeter(int x1, int z1, int x2, int z2, int yFrom, int yTo, Material mat) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (x != minX && x != maxX && z != minZ && z != maxZ) continue;
                for (int y = yFrom; y <= yTo; y++) {
                    world.getBlockAt(x, y, z).setType(mat);
                }
            }
        }
    }

    private void lightGrid(int x1, int z1, int x2, int z2, int y, int spacing, Material mat) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        for (int x = minX + 2; x < maxX; x += spacing) {
            for (int z = minZ + 2; z < maxZ; z += spacing) {
                world.getBlockAt(x, y, z).setType(mat);
            }
        }
    }

    /** Carves a doorway of the given half-width and height centred on (x, z). */
    private void carve(int x, int y, int z, int halfWidth, int height) {
        for (int dz = -halfWidth; dz <= halfWidth; dz++) {
            for (int dy = 0; dy < height; dy++) {
                world.getBlockAt(x, y + dy, z + dz).setType(Material.AIR);
            }
        }
    }

    private void sign(int x, int y, int z, String l1, String l2, String l3, String l4) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.OAK_SIGN);
        if (b.getState() instanceof Sign s) {
            s.getSide(Side.FRONT).setLine(0, l1);
            s.getSide(Side.FRONT).setLine(1, l2);
            s.getSide(Side.FRONT).setLine(2, l3);
            s.getSide(Side.FRONT).setLine(3, l4);
            s.update(true);
        }
    }

    private String prettyName(String raw) {
        String[] parts = raw.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
        }
        String out = sb.toString().trim();
        return out.length() > 15 ? out.substring(0, 15) : out;
    }

    private Material pickOre(Material filler, Material common, Material rare, double rarePct) {
        double r = Math.random() * 100.0;
        if (r < rarePct) return rare;
        double commonPct = 25.0;
        if (r < rarePct + commonPct) return common;
        return filler;
    }

    private void placeMineLights(RankMineData.Def d) {
        for (int x = d.x1 + 4; x < d.x2; x += 8) {
            for (int z = d.z1 + 4; z < d.z2; z += 8) {
                world.getBlockAt(x, d.y2 - 1, z).setType(Material.GLOWSTONE);
            }
        }
    }

    private void setupWorldBorder() {
        int minX = STARTER_X1 - 20, maxX = HUB_X2, minZ = -30, maxZ = 30;
        for (int[] b : mineBounds.values()) {
            minX = Math.min(minX, b[0]);
            maxX = Math.max(maxX, b[3]);
            minZ = Math.min(minZ, b[2]);
            maxZ = Math.max(maxZ, b[5]);
        }
        for (FishingData.Pond pond : FishingData.PONDS.values()) {
            minX = Math.min(minX, pond.x1);
            maxX = Math.max(maxX, pond.x2);
            minZ = Math.min(minZ, pond.z1);
            maxZ = Math.max(maxZ, pond.z2);
        }
        maxZ = Math.max(maxZ, CELL_Z_START + (CELL_ROWS * CELL_SIZE) + 20);
        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double size = Math.max(maxX - minX, maxZ - minZ) + 80;
        world.getWorldBorder().setCenter(centerX, centerZ);
        world.getWorldBorder().setSize(size);
    }

    private Material safeMaterial(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Material.STONE;
        }
    }

    /** Called by MineResetTask to refill a mine's interior once it's mostly emptied out. */
    public void resetMine(String rank) {
        int[] b = mineBounds.get(rank);
        if (b == null) return;
        RankMineData.Def d = RankMineData.RANKS.get(rank);
        Material filler = safeMaterial(d.filler);
        Material common = safeMaterial(d.common);
        Material rare = safeMaterial(d.rare);
        double rarePct = rarePercentFor(rank);
        for (int x = b[0] + 1; x < b[3]; x++) {
            for (int z = b[2] + 1; z < b[5]; z++) {
                for (int y = b[1] + 1; y < b[4]; y++) {
                    world.getBlockAt(x, y, z).setType(pickOre(filler, common, rare, rarePct));
                }
            }
        }
        placeMineLights(d);
    }

    public double percentRemaining(String rank) {
        int[] b = mineBounds.get(rank);
        if (b == null) return 100;
        int total = 0, filled = 0;
        int step = Math.max(1, (b[3] - b[0]) / 20);
        for (int x = b[0] + 1; x < b[3]; x += step) {
            for (int z = b[2] + 1; z < b[5]; z += step) {
                for (int y = b[1] + 1; y < b[4]; y += 2) {
                    total++;
                    if (world.getBlockAt(x, y, z).getType() != Material.AIR) filled++;
                }
            }
        }
        return total == 0 ? 100 : (100.0 * filled / total);
    }
}
