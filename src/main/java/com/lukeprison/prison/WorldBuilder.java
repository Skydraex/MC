package com.lukeprison.prison;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

/**
 * Constructs the entire prison into a void world so it floats in the sky.
 *
 * Layout is a fixed set of reserved regions that never overlap, all sharing one floor height.
 * Rooms are built first, then every doorway is carved in a final pass — carving as you go is
 * how a later room ends up bricking over an earlier room's exit.
 *
 * Block placement runs once; a marker file skips it on later boots while bounds still register.
 */
public class WorldBuilder {

    private final Plugin plugin;
    private final World world;
    private final Architect arch;
    private final Map<String, int[]> mineBounds = new LinkedHashMap<>();
    private final Map<Location, SellSignListener.SellSignData> sellSigns = new HashMap<>();
    private final Map<Location, String> crateLocations = new LinkedHashMap<>();
    private final List<PvpZoneManager.Zone> pvpZones = new ArrayList<>();
    private final List<PendingSign> signs = new ArrayList<>();
    private Location hubSpawn;
    private Location starterSpawn;
    private final Map<Integer, CellManager.CellRect> cellRects = new LinkedHashMap<>();

    // ---- Master layout. Every region here is disjoint from every other. ----
    public static final int Y = 95;               // one floor height everywhere
    private static final int ENTRANCE_Z = 3;      // shared alignment for the whole ward line

    private static final int[] STARTER   = {-170, -20, -130, 20};
    private static final int[] CORRIDOR  = {-130,  -5,  -76,  5};
    private static final int[] HUB       = { -75, -30,  -15, 30};
    private static final int[] CELLS     = { -75,  45,  -15, 100};
    private static final int[] CRATES    = { -75, -75,  -45, -45};
    private static final int[] YARD      = { -40, -75,  -15, -45};
    private static final int[] FISH_PATH = {  -8,-100,   -2, -1};
    private static final int FISH_Z1 = -240, FISH_Z2 = -100;

    private static final int CELLS_PER_ROW = 10, CELL_ROWS = 4, CELL_SIZE = 7, CELL_TIERS = 3;

    private record PendingSign(int x, int y, int z, BlockFace facing, String[] lines) { }

    public WorldBuilder(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        this.arch = new Architect(world);
    }

    public World getWorld() { return world; }
    public Map<String, int[]> getMineBounds() { return mineBounds; }
    public Map<Location, SellSignListener.SellSignData> getSellSigns() { return sellSigns; }
    public Map<Location, String> getCrateLocations() { return crateLocations; }
    public List<PvpZoneManager.Zone> getPvpZones() { return pvpZones; }
    public Location getHubSpawn() { return hubSpawn; }
    public Location getStarterSpawn() { return starterSpawn; }
    public Map<Integer, CellManager.CellRect> getCellRects() { return cellRects; }

    /**
     * The marker lives inside the world's own folder, not the plugin's data folder — tying it
     * to that specific world's lifecycle. This is what went wrong last time: a marker from a
     * previous world persisted after switching to a fresh one, so the new world was skipped
     * entirely and stayed empty. Deleting the world folder now always invalidates the marker
     * along with it, and a genuinely new world always rebuilds.
     */
    private File markerFile() { return new File(world.getWorldFolder(), "prison-built.marker"); }
    public boolean alreadyBuilt() { return markerFile().exists(); }

    /** Registers all bounds in memory. Runs every boot; places nothing. */
    public void registerBounds() {
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (d.rank.equals("FREE")) continue;
            mineBounds.put(d.rank, new int[]{d.x1, d.y1, d.z1, d.x2, d.y2, d.z2});
            Map<Material, Double> prices = new HashMap<>();
            prices.put(safeMaterial(d.filler), d.fillerPrice);
            prices.put(safeMaterial(d.common), d.commonPrice);
            prices.put(safeMaterial(d.rare), d.rarePrice);
            sellSigns.put(new Location(world, d.x1 - 1, Y + 2, d.z1 + ENTRANCE_Z + 2),
                    new SellSignListener.SellSignData(d.rank, prices));
        }
        registerPondBounds();
        registerCrateLocations();
        registerPvpZones();
        hubSpawn = new Location(world, (HUB[0] + HUB[2]) / 2.0 + 0.5, Y + 1, 0.5);
        starterSpawn = new Location(world, STARTER[0] + 8.5, Y + 1, 0.5);
        world.setSpawnLocation(hubSpawn.getBlockX(), Y + 1, 0);
    }

    /** Full block placement. Only called on first boot. */
    public void buildAll() {
        plugin.getLogger().info("First boot — building Sky Prison. This takes a few minutes.");

        buildSkyPlatform();
        buildStarterArea();
        buildIntakeCorridor();
        buildHub();
        buildCellBlock();
        buildCrateHall();
        buildYard();
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (d.rank.equals("FREE")) continue;
            buildMine(d);
            buildWard(d);
        }
        buildWardWalkways();
        buildWardPits();
        buildFishingArea();

        // Openings go in last so nothing can brick them over.
        carveAllDoorways();

        setupWorldBorder();
        try {
            markerFile().getParentFile().mkdirs();
            markerFile().createNewFile();
        } catch (Exception e) {
            plugin.getLogger().warning("Could not write world-built marker.");
        }
        plugin.getLogger().info("World build complete.");
    }

    /**
     * Re-applies every sign's text a moment after the world is ready. Sign block entities set
     * during a synchronous bulk build don't always reach clients; writing them again from a
     * scheduled task does. Also waxes them so players can't open the edit screen.
     */
    public void applySigns() {
        for (PendingSign ps : signs) {
            Block b = world.getBlockAt(ps.x, ps.y, ps.z);
            if (b.getType() != Material.OAK_WALL_SIGN) {
                b.setType(Material.OAK_WALL_SIGN, false);
                if (b.getBlockData() instanceof WallSign ws) {
                    ws.setFacing(ps.facing);
                    b.setBlockData(ws, false);
                }
            }
            if (b.getState() instanceof Sign s) {
                for (int i = 0; i < 4 && i < ps.lines.length; i++) {
                    s.getSide(Side.FRONT).setLine(i, ps.lines[i]);
                }
                s.setWaxed(true);
                s.update(true, false);
            }
        }
    }

    // ---- Sky platform: solid ground under every region so nothing is a void trap ----

    private void buildSkyPlatform() {
        int[][] footprints = {
                pad(STARTER, 4), pad(CORRIDOR, 3), pad(HUB, 4), pad(CELLS, 4),
                pad(CRATES, 4), pad(YARD, 4), pad(FISH_PATH, 3),
                {-20, FISH_Z1 - 6, 440, FISH_Z2 + 6},
                {HUB[2], -45, lastMineX2() + 30, 30}   // the ward line, incl. pits
        };
        for (int[] f : footprints) {
            for (int x = f[0]; x <= f[2]; x++) {
                for (int z = f[1]; z <= f[3]; z++) {
                    arch.set(x, Y - 1, z, Material.DEEPSLATE);
                    arch.set(x, Y - 2, z, Material.DEEPSLATE);
                    arch.set(x, Y - 3, z, Material.BEDROCK);
                }
            }
        }
    }

    private int[] pad(int[] r, int p) { return new int[]{r[0] - p, r[1] - p, r[2] + p, r[3] + p}; }

    private int lastMineX2() {
        int max = 0;
        for (RankMineData.Def d : RankMineData.RANKS.values()) max = Math.max(max, d.x2);
        return max;
    }

    // ---- Starter yard and prison bus ----

    private void buildStarterArea() {
        int[] r = STARTER;
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(Material.GRAY_CONCRETE_POWDER, Material.GRAVEL, 25));
        arch.prisonWall(r[0], Y + 1, r[1], r[2], r[3], 8, 6);
        arch.barbedWireTop(r[0], r[1], r[2], r[3], Y + 9);
        // Open-air yard: lamp posts inside the wall line rather than a ceiling.
        for (int x = r[0] + 4; x <= r[2] - 4; x += 8) {
            for (int dy = 1; dy <= 3; dy++) {
                arch.set(x, Y + dy, r[1] + 2, Material.IRON_BARS);
                arch.set(x, Y + dy, r[3] - 2, Material.IRON_BARS);
            }
            arch.set(x, Y + 4, r[1] + 2, Material.LANTERN);
            arch.set(x, Y + 4, r[3] - 2, Material.LANTERN);
        }
        // Server name across the yard's east wall, above the exit, read facing east.
        int nameW = BlockFont.width("SKY PRISON");
        BlockFont.write(world, "SKY PRISON", r[2] - 1, Y + 16, -nameW / 2, BlockFont.Axis.POS_Z, Material.LIGHT_BLUE_CONCRETE);
        // Backing panel so the letters read against the sky.
        for (int z = -nameW / 2 - 1; z <= nameW / 2 + 1; z++) {
            for (int dy = 9; dy <= 17; dy++) arch.set(r[2], Y + dy, z, arch.pick(Architect.PRISON_STONE));
        }
        buildPrisonBus(r[0] + 4, Y + 1, -3);
        sign(r[0] + 12, Y + 2, -1, BlockFace.EAST, "§8§lINTAKE", "Welcome to", "Sky Prison.", "Head east →");
    }

    private void buildPrisonBus(int x, int y, int z) {
        for (int dx = 0; dx < 12; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                for (int dy = 0; dy < 4; dy++) {
                    boolean shell = dx == 0 || dx == 11 || dz == 0 || dz == 4 || dy == 0 || dy == 3;
                    boolean window = dy == 2 && dx > 1 && dx < 10 && (dz == 0 || dz == 4);
                    arch.set(x + dx, y + dy, z + dz,
                            !shell ? Material.AIR : window ? Material.GRAY_STAINED_GLASS : Material.YELLOW_CONCRETE);
                }
            }
        }
        for (int dx : new int[]{2, 9}) {
            for (int dz : new int[]{0, 4}) arch.set(x + dx, y - 1, z + dz, Material.BLACK_CONCRETE);
        }
        arch.set(x + 11, y + 1, z + 2, Material.AIR);
        arch.set(x + 11, y + 2, z + 2, Material.AIR);
    }

    // ---- Intake corridor ----

    private void buildIntakeCorridor() {
        int[] r = CORRIDOR;
        arch.prisonHall(r[0], r[1], r[2], r[3], Y, 7, Material.POLISHED_ANDESITE, Material.POLISHED_BLACKSTONE, 9);
        arch.ceilingStrip(r[0] + 1, r[2] - 1, Y + 8, 0, Material.SEA_LANTERN);

        // Tutorial signs along the north wall, facing into the corridor.
        int z = r[1] + 1;
        sign(r[0] + 10, Y + 2, z, BlockFace.SOUTH, "§6Step 1", "Mine blocks in", "your rank's mine", "to earn money.");
        sign(r[0] + 20, Y + 2, z, BlockFace.SOUTH, "§6Step 2", "Sell at the", "sell sign, or", "use /autosell");
        sign(r[0] + 30, Y + 2, z, BlockFace.SOUTH, "§6Step 3", "/rankup to climb", "from A to Z,", "then prestige.");
        sign(r[0] + 40, Y + 2, z, BlockFace.SOUTH, "§6Step 4", "Spend tokens on", "pickaxe enchants", "with /enchant");
        sign(r[0] + 48, Y + 2, z, BlockFace.SOUTH, "§bFishing", "Unlocks at rank " + FishingData.UNLOCK_RANK, "Own level ladder", "/fishing");
    }

    public Location wardenNpcLocation() { return new Location(world, CORRIDOR[0] + 5.5, Y + 1, 0.5); }
    public Location quartermasterNpcLocation() { return new Location(world, CORRIDOR[2] - 5.5, Y + 1, 0.5); }

    // ---- Hub ----

    private static final int HUB_HEIGHT = 18;

    private void buildHub() {
        int[] r = HUB;
        arch.prisonHall(r[0], r[1], r[2], r[3], Y, HUB_HEIGHT, Material.POLISHED_ANDESITE, Material.POLISHED_BLACKSTONE, 10);
        arch.windowRun(r[0], Y + 1, r[1], r[2], 6, 5, Material.IRON_BARS, Material.STONE_BRICK_STAIRS);
        arch.windowRun(r[0], Y + 1, r[3], r[2], 6, 5, Material.IRON_BARS, Material.STONE_BRICK_STAIRS);

        // A second-storey gallery ledge around the hall gives the height a reason to exist.
        for (int x = r[0] + 1; x <= r[2] - 1; x++) {
            arch.placeSlab(x, Y + 8, r[1] + 1, Material.STONE_BRICK_SLAB, true);
            arch.placeSlab(x, Y + 8, r[3] - 1, Material.STONE_BRICK_SLAB, true);
        }
        for (int z = r[1] + 1; z <= r[3] - 1; z++) {
            arch.placeSlab(r[0] + 1, Y + 8, z, Material.STONE_BRICK_SLAB, true);
            arch.placeSlab(r[2] - 1, Y + 8, z, Material.STONE_BRICK_SLAB, true);
        }

        // Central dais and landmark column.
        int cx = (r[0] + r[2]) / 2, cz = 0;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 6) continue;
                arch.set(cx + dx, Y, cz + dz, Material.POLISHED_BLACKSTONE);
                if (Math.abs(dx) + Math.abs(dz) <= 4) arch.set(cx + dx, Y + 1, cz + dz, Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        for (int dy = 2; dy <= 9; dy++) arch.set(cx, Y + dy, cz, Material.CHISELED_POLISHED_BLACKSTONE);
        arch.set(cx, Y + 10, cz, Material.SEA_LANTERN);
        // Hanging chains from the ceiling down to the column top, as in the reference hall.
        for (int dy = 11; dy <= HUB_HEIGHT; dy++) arch.set(cx, Y + dy, cz, Material.IRON_BARS);

        // Red-wool PvP lane from the Yard door straight up to the dais. Inside red = PvP on.
        int yardMid = (YARD[0] + YARD[2]) / 2;
        for (int z = r[1] + 1; z <= -7; z++) {
            for (int dx = -1; dx <= 1; dx++) arch.set(yardMid + dx, Y, z, Material.RED_WOOL);
        }
        pvpLane = new int[]{yardMid - 1, r[1] + 1, yardMid + 1, -7};
        sign(yardMid + 3, Y + 2, -12, BlockFace.WEST, "§c§lRED WOOL", "means", "§cPVP IS ON", "");
        sign(yardMid - 3, Y + 2, -12, BlockFace.EAST, "§7§lGRAY FLOOR", "means", "§aPVP IS OFF", "");

        // Server name inside, above the east gate, read facing west from the ward walkway.
        int nameW = BlockFont.width("SKY PRISON");
        BlockFont.write(world, "SKY PRISON", r[2], Y + HUB_HEIGHT - 2, nameW / 2, BlockFont.Axis.NEG_Z, Material.LIGHT_BLUE_CONCRETE);

        // Ender chests flanking the grand gate, as prison hubs do.
        arch.set(r[2] - 2, Y + 1, ENTRANCE_Z - 5, Material.ENDER_CHEST);
        arch.set(r[2] - 2, Y + 1, ENTRANCE_Z + 5, Material.ENDER_CHEST);

        sign(r[0] + 1, Y + 2, 8, BlockFace.EAST, "§b§lSKY PRISON", "/prison  menu", "/warps  mines", "/fishing  ponds");
        sign(r[0] + 1, Y + 2, -8, BlockFace.EAST, "§6§lWHERE TO", "N: Cell block", "S: Crates & Yard", "E: The Wards");
    }

    private int[] pvpLane;

    // ---- Cell block: two tiers of 7x7 cells on railed gantries ----

    private static final int CELL_TIER_HEIGHT = 6;

    /** Computes every cell's rectangle. Cheap math only; runs every boot so /cell always works. */
    private void registerCellRects() {
        cellRects.clear();
        int[] r = CELLS;
        int startX = r[0] + 3;
        int rowPitch = CELL_SIZE + 4;
        int cellNumber = 1;
        for (int tier = 0; tier < CELL_TIERS; tier++) {
            int y = Y + tier * CELL_TIER_HEIGHT;
            for (int row = 0; row < CELL_ROWS; row++) {
                int cz = r[1] + 6 + row * rowPitch;
                for (int col = 0; col < CELLS_PER_ROW; col++) {
                    int x = startX + col * CELL_SIZE;
                    cellRects.put(cellNumber, new CellManager.CellRect(cellNumber, x, cz, x + CELL_SIZE - 1, cz + CELL_SIZE - 1, y));
                    cellNumber++;
                }
            }
        }
    }

    private void buildCellBlock() {
        int[] r = CELLS;
        int hallHeight = CELL_TIERS * CELL_TIER_HEIGHT + 4;
        arch.prisonHall(r[0], r[1], r[2], r[3], Y, hallHeight, Material.POLISHED_DEEPSLATE, Material.POLISHED_BLACKSTONE, 12);
        // Long strip lights the length of the hall, like the reference.
        for (int z = r[1] + 6; z < r[3]; z += 8) arch.ceilingStrip(r[0] + 2, r[2] - 2, Y + hallHeight + 1, z, Material.SEA_LANTERN);

        int startX = r[0] + 3;
        int rowPitch = CELL_SIZE + 4;
        int cellNumber = 1;
        for (int tier = 0; tier < CELL_TIERS; tier++) {
            int y = Y + tier * CELL_TIER_HEIGHT;
            for (int row = 0; row < CELL_ROWS; row++) {
                int cz = r[1] + 6 + row * rowPitch;
                for (int col = 0; col < CELLS_PER_ROW; col++) {
                    buildCell(startX + col * CELL_SIZE, y, cz, cellNumber++);
                }
                // Gantry in front of the row, with an iron-bar rail on upper tiers.
                for (int x = startX - 1; x <= startX + CELLS_PER_ROW * CELL_SIZE; x++) {
                    for (int dz = 1; dz <= 3; dz++) arch.set(x, y, cz - dz, Material.POLISHED_DEEPSLATE);
                    if (tier > 0) arch.set(x, y + 1, cz - 3, Material.IRON_BARS);
                }
            }
        }
        // Stairs linking each tier at the east end.
        for (int tier = 0; tier < CELL_TIERS - 1; tier++) {
            int base = Y + tier * CELL_TIER_HEIGHT;
            for (int i = 0; i < CELL_TIER_HEIGHT; i++) {
                arch.placeStair(r[2] - 3, base + i, r[1] + 3 + i, Material.DEEPSLATE_BRICK_STAIRS, BlockFace.NORTH, false);
                arch.set(r[2] - 2, base + i, r[1] + 3 + i, Material.POLISHED_DEEPSLATE);
                arch.set(r[2] - 4, base + i, r[1] + 3 + i, Material.IRON_BARS);
            }
        }
        BlockFont.write(world, "CELLS", (r[0] + r[2]) / 2 - BlockFont.width("CELLS") / 2, Y + hallHeight - 1, r[3] - 1, BlockFont.Axis.POS_X, Material.LIGHT_BLUE_CONCRETE);
        sign(r[0] + 1, Y + 2, r[1] + 2, BlockFace.EAST, "§8§lCELL BLOCK", (CELL_ROWS * CELLS_PER_ROW * CELL_TIERS) + " cells", CELL_TIERS + " tiers", "");
    }

    private void buildCell(int x, int y, int z, int number) {
        for (int dx = 0; dx < CELL_SIZE; dx++) {
            for (int dz = 0; dz < CELL_SIZE; dz++) {
                arch.set(x + dx, y, z + dz, Material.POLISHED_DEEPSLATE);
                arch.set(x + dx, y + 5, z + dz, Material.DEEPSLATE_TILES);
                for (int dy = 1; dy <= 4; dy++) {
                    boolean wall = dx == 0 || dx == CELL_SIZE - 1 || dz == 0 || dz == CELL_SIZE - 1;
                    boolean front = dz == 0 && dx > 0 && dx < CELL_SIZE - 1;
                    arch.set(x + dx, y + dy, z + dz,
                            !wall ? Material.AIR : front ? Material.IRON_BARS : arch.pick(Architect.CELL_STONE));
                }
            }
        }
        // Door in the barred front.
        arch.set(x + 3, y + 1, z, Material.AIR);
        arch.set(x + 3, y + 2, z, Material.AIR);
        arch.placeStair(x + 3, y + 3, z, Material.DEEPSLATE_BRICK_STAIRS, BlockFace.SOUTH, true);
        // Furnishings: proper two-block bed, storage, wall lantern.
        placeBed(x + 2, y + 1, z + CELL_SIZE - 2, BlockFace.WEST);
        arch.set(x + CELL_SIZE - 2, y + 1, z + CELL_SIZE - 2, Material.BARREL);
        arch.set(x + CELL_SIZE - 2, y + 3, z + 1, Material.LANTERN);
        sign(x + 5, y + 2, z + 1, BlockFace.SOUTH, "§7Cell", "§f#" + number, "", "");
    }

    // ---- Crate hall ----

    private void registerCrateLocations() {
        crateLocations.clear();
        int[] r = CRATES;
        int spacing = (r[2] - r[0]) / (CrateData.CRATES.size() + 1);
        int i = 0;
        for (CrateData.Crate crate : CrateData.CRATES.values()) {
            i++;
            crateLocations.put(new Location(world, r[0] + spacing * i, Y + 2, (r[1] + r[3]) / 2), crate.id);
        }
    }

    private void buildCrateHall() {
        int[] r = CRATES;
        arch.prisonHall(r[0], r[1], r[2], r[3], Y, 9, Material.POLISHED_DIORITE, Material.POLISHED_BLACKSTONE, 8);
        BlockFont.write(world, "CRATES", (r[0] + r[2]) / 2 + BlockFont.width("CRATES") / 2, Y + 9, r[1] + 1, BlockFont.Axis.NEG_X, Material.LIGHT_BLUE_CONCRETE);
        for (Map.Entry<Location, String> e : crateLocations.entrySet()) {
            Location l = e.getKey();
            CrateData.Crate crate = CrateData.CRATES.get(e.getValue());
            arch.set(l.getBlockX(), Y + 1, l.getBlockZ(), Material.CHISELED_DEEPSLATE);
            arch.set(l.getBlockX(), Y + 2, l.getBlockZ(), Material.ENDER_CHEST);
            arch.placeSlab(l.getBlockX() - 1, Y + 1, l.getBlockZ(), Material.POLISHED_DEEPSLATE_SLAB, false);
            arch.placeSlab(l.getBlockX() + 1, Y + 1, l.getBlockZ(), Material.POLISHED_DEEPSLATE_SLAB, false);
            sign(l.getBlockX(), Y + 3, l.getBlockZ() + 1, BlockFace.SOUTH, "§6§l" + crate.display, "Right-click", "with a key", "to open");
        }
        sign(r[0] + 1, Y + 2, r[1] + 2, BlockFace.EAST, "§6§lCRATES", "Keys drop from", "mining and", "fishing.");
    }

    // ---- PvP: The Yard (off the hub) and ward pits ----

    private void registerPvpZones() {
        pvpZones.clear();
        int[] r = YARD;
        pvpZones.add(new PvpZoneManager.Zone("The Yard", r[0], Y, r[1], r[2], Y + 10, r[3]));
        int yardMid = (YARD[0] + YARD[2]) / 2;
        pvpZones.add(new PvpZoneManager.Zone("Hub PvP Lane", yardMid - 1, Y, HUB[1] + 1, yardMid + 1, Y + 3, -7));
        for (String rank : new String[]{"F", "M", "T"}) {
            int[] p = pitRect(RankMineData.RANKS.get(rank));
            if (p != null) pvpZones.add(new PvpZoneManager.Zone(rank + "-Ward Pit", p[0], Y, p[1], p[2], Y + 8, p[3]));
        }
    }

    private int[] wardRect(RankMineData.Def d) { return new int[]{d.x1 - 24, -8, d.x1 - 4, 14}; }
    private int[] pitRect(RankMineData.Def d) {
        if (d == null) return null;
        int[] w = wardRect(d);
        return new int[]{w[0], -36, w[2], -16};
    }

    private void buildYard() {
        buildArena(YARD, "The Yard", true);
    }

    private void buildWardPits() {
        for (String rank : new String[]{"F", "M", "T"}) {
            int[] p = pitRect(RankMineData.RANKS.get(rank));
            if (p != null) buildArena(p, rank + "-Ward Pit", false);
        }
    }

    private void buildArena(int[] r, String name, boolean grand) {
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(Material.RED_WOOL, Material.RED_CONCRETE, 15));
        // Bordered floor ring and low walls, open-topped so fights are visible.
        for (int x = r[0]; x <= r[2]; x++) { arch.set(x, Y, r[1], Material.RED_CONCRETE); arch.set(x, Y, r[3], Material.RED_CONCRETE); }
        for (int z = r[1]; z <= r[3]; z++) { arch.set(r[0], Y, z, Material.RED_CONCRETE); arch.set(r[2], Y, z, Material.RED_CONCRETE); }
        arch.detailedWall(r[0], Y + 1, r[1], r[2], r[3], 4, Architect.Palette.of(Material.RED_CONCRETE, Material.RED_TERRACOTTA, 20),
                Material.DEEPSLATE_BRICKS, Material.POLISHED_BLACKSTONE, 6);
        for (int[] c : new int[][]{{r[0], r[1]}, {r[0], r[3]}, {r[2], r[1]}, {r[2], r[3]}}) {
            for (int dy = 5; dy <= 7; dy++) arch.set(c[0], Y + dy, c[1], Material.DEEPSLATE_BRICKS);
            arch.set(c[0], Y + 8, c[1], Material.REDSTONE_LAMP);
        }
        if (grand) {
            int mx = (r[0] + r[2]) / 2, mz = (r[1] + r[3]) / 2;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) arch.set(mx + dx, Y + 1, mz + dz, Material.DEEPSLATE_BRICK_WALL);
            }
        }
        sign(r[0] + 1, Y + 2, r[1] + 2, BlockFace.EAST, "§c§lPVP ZONE", name, "Drop all items", "on death!");
    }

    // ---- Mines and wards ----

    private double rarePercentFor(String rank) {
        int idx = 0, i = 0;
        for (String r : RankMineData.RANKS.keySet()) { if (r.equals(rank)) { idx = i; break; } i++; }
        return 2.0 + (10.0 * idx / Math.max(1, RankMineData.RANKS.size() - 1));
    }

    private void buildMine(RankMineData.Def d) {
        Material filler = safeMaterial(d.filler), common = safeMaterial(d.common), rare = safeMaterial(d.rare);
        double rarePct = rarePercentFor(d.rank);
        for (int x = d.x1; x <= d.x2; x++) for (int z = d.z1; z <= d.z2; z++) for (int y = d.y1; y <= d.y2; y++) {
            boolean edge = x == d.x1 || x == d.x2 || z == d.z1 || z == d.z2 || y == d.y1 || y == d.y2;
            arch.set(x, y, z, edge ? Material.BEDROCK : pickOre(filler, common, rare, rarePct));
        }
        placeMineLights(d);
        // Sell sign on the ward-facing wall, beside the entrance.
        sign(d.x1 - 1, Y + 2, d.z1 + ENTRANCE_Z + 2, BlockFace.WEST, "§a[Sell]", "Mine " + d.rank + " ores", "Right-click", "holding ore");
    }

    private void buildWard(RankMineData.Def d) {
        int[] w = wardRect(d);
        arch.prisonHall(w[0], w[1], w[2], w[3], Y, 9, Material.POLISHED_DEEPSLATE, Material.POLISHED_BLACKSTONE, 8);
        // Iron-bar rails flanking the route through to the mine.
        for (int x = w[0] + 1; x < w[2]; x++) {
            arch.set(x, Y + 1, ENTRANCE_Z - 3, Material.IRON_BARS);
            arch.set(x, Y + 1, ENTRANCE_Z + 3, Material.IRON_BARS);
        }
        // Ender chests either side of the mine approach.
        arch.set(w[2] - 2, Y + 1, ENTRANCE_Z - 5, Material.ENDER_CHEST);
        arch.set(w[2] - 2, Y + 1, ENTRANCE_Z + 5, Material.ENDER_CHEST);

        // The mine gate: an archway on the mine's face with the rank letter in quartz above it,
        // cobweb wire along the top — the single most recognisable prison-server image.
        int gx = d.x1;
        for (int dz = -4; dz <= 4; dz++) {
            for (int dy = 1; dy <= 6; dy++) {
                boolean frame = Math.abs(dz) == 4 || dy == 6;
                arch.set(gx, Y + dy, ENTRANCE_Z + dz, frame ? Material.SMOOTH_QUARTZ : Material.IRON_BARS);
            }
        }
        for (int dz = -5; dz <= 5; dz++) arch.set(gx, Y + 7, ENTRANCE_Z + dz, (dz % 3 == 0) ? Material.IRON_BARS : Material.COBWEB);
        BlockFont.write(world, d.rank, gx, Y + 15, ENTRANCE_Z - 2, BlockFont.Axis.POS_Z, Material.LIGHT_BLUE_CONCRETE);
        BlockFont.write(world, "MINE", gx, Y + 26, ENTRANCE_Z - BlockFont.width("MINE") / 2, BlockFont.Axis.POS_Z, Material.LIGHT_BLUE_CONCRETE);

        sign(w[0] + 1, Y + 2, w[1] + 4, BlockFace.EAST, "§6§l" + d.rank + "-WARD", "Mine " + d.rank,
                "Rare: " + prettyName(d.rare), String.format("%.0f%% rare", rarePercentFor(d.rank)));
    }

    /** Wide, railed, lit walkways linking hub → A-Ward and every mine → next ward. */
    private void buildWardWalkways() {
        List<RankMineData.Def> ordered = new ArrayList<>();
        for (RankMineData.Def d : RankMineData.RANKS.values()) if (!d.rank.equals("FREE")) ordered.add(d);

        // Hub east gate to A-Ward.
        walkway(HUB[2] + 1, wardRect(ordered.get(0))[0] - 1, ENTRANCE_Z, 3);
        for (int i = 0; i < ordered.size(); i++) {
            RankMineData.Def d = ordered.get(i);
            int[] w = wardRect(d);
            walkway(w[2] + 1, d.x1 - 1, ENTRANCE_Z, 2);                  // ward → its mine
            if (i + 1 < ordered.size()) {
                walkway(d.x2 + 1, wardRect(ordered.get(i + 1))[0] - 1, ENTRANCE_Z, 2); // mine → next ward
            }
        }
        // Path from the hub walkway south to the fishing area.
        for (int z = FISH_PATH[1]; z <= FISH_PATH[3]; z++) {
            for (int x = FISH_PATH[0]; x <= FISH_PATH[2]; x++) {
                boolean edge = x == FISH_PATH[0] || x == FISH_PATH[2];
                arch.set(x, Y, z, edge ? Material.POLISHED_BLACKSTONE : Material.SMOOTH_STONE);
                if (edge) arch.set(x, Y + 1, z, Material.POLISHED_BLACKSTONE_WALL);
            }
            if (z % 8 == 0) { arch.set(FISH_PATH[0] - 1, Y + 2, z, Material.LANTERN); }
        }
    }

    /** Road-style walkway: dark surface with quartz lane markings, iron-bar fencing, lamp posts. */
    private void walkway(int x1, int x2, int centreZ, int halfWidth) {
        if (x2 < x1) return;
        for (int x = x1; x <= x2; x++) {
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                boolean edge = Math.abs(dz) == halfWidth;
                boolean marking = dz == 0 && (x % 6) < 3;
                arch.set(x, Y, centreZ + dz, edge ? Material.STONE_BRICKS : marking ? Material.QUARTZ_SLAB : Material.BLACK_CONCRETE);
                if (marking) arch.set(x, Y, centreZ + dz, Material.BLACK_CONCRETE);
                if (edge) arch.set(x, Y + 1, centreZ + dz, Material.IRON_BARS);
            }
            if (halfWidth > 1 && (x - x1) % 8 == 4) {
                for (int dy = 1; dy <= 3; dy++) arch.set(x, Y + dy, centreZ - halfWidth, Material.IRON_BARS);
                arch.set(x, Y + 4, centreZ - halfWidth, Material.LANTERN);
                for (int dy = 1; dy <= 3; dy++) arch.set(x, Y + dy, centreZ + halfWidth, Material.IRON_BARS);
                arch.set(x, Y + 4, centreZ + halfWidth, Material.LANTERN);
            }
        }
    }

    // ---- Fishing area ----

    private void registerPondBounds() {
        int x = 0;
        for (FishingData.Pond pond : FishingData.PONDS.values()) {
            int size = 22 + pond.tier * 2;
            pond.x1 = x; pond.x2 = x + size;
            pond.z2 = FISH_Z2 - 12; pond.z1 = pond.z2 - size;
            pond.y = Y;
            x = pond.x2 + 12;
        }
    }

    private void buildFishingArea() {
        int x1 = -20, x2 = 440;
        arch.fillFlat(x1, FISH_Z1, x2, FISH_Z2, Y, Architect.Palette.of(Material.GRASS_BLOCK, Material.MOSS_BLOCK, 12, Material.COARSE_DIRT, 6));
        // Perimeter wall with sandstone posts so the edge reads as a promenade, not a cliff.
        arch.detailedWall(x1, Y + 1, FISH_Z1, x2, FISH_Z2, 3, Architect.FISHING_STONE,
                Material.CHISELED_SANDSTONE, Material.SMOOTH_SANDSTONE, 8);
        for (int x = x1; x <= x2; x += 8) { arch.set(x, Y + 4, FISH_Z1, Material.LANTERN); arch.set(x, Y + 4, FISH_Z2, Material.LANTERN); }
        for (FishingData.Pond pond : FishingData.PONDS.values()) digPond(pond);
        // A sell hut with a fish sign near the entrance path.
        sign(FISH_PATH[2] + 2, Y + 2, FISH_Z2 - 2, BlockFace.SOUTH, "§b§lFISHING", "/fishing ponds", "/sellfish to", "cash in");
    }

    private void digPond(FishingData.Pond pond) {
        for (int x = pond.x1; x <= pond.x2; x++) for (int z = pond.z1; z <= pond.z2; z++) {
            boolean rim = x == pond.x1 || x == pond.x2 || z == pond.z1 || z == pond.z2;
            if (rim) { arch.set(x, Y, z, Material.SMOOTH_SANDSTONE); continue; }
            arch.set(x, Y, z, Material.WATER);
            arch.set(x, Y - 1, z, Material.WATER);
            arch.set(x, Y - 2, z, tierBed(pond.tier));
        }
        sign(pond.x1 + 2, Y + 2, pond.z2 + 2, BlockFace.SOUTH, "§b§l" + pond.name, "Tier " + pond.tier, "Req. level " + pond.requiredLevel, "/fishing");
    }

    private Material tierBed(int tier) {
        if (tier <= 2) return Material.GRAVEL;
        if (tier <= 4) return Material.SAND;
        if (tier <= 6) return Material.PRISMARINE;
        if (tier <= 8) return Material.DARK_PRISMARINE;
        return Material.SCULK;
    }

    // ---- Doorways: carved last so nothing can brick them over ----

    private void carveAllDoorways() {
        Material f = Material.SMOOTH_QUARTZ;
        arch.doorway(STARTER[2], Y + 1, 0, true, 1, 4, f);                 // yard → corridor
        arch.doorway(CORRIDOR[0], Y + 1, 0, true, 1, 4, f);                 // corridor west (same wall)
        arch.doorway(CORRIDOR[2], Y + 1, 0, true, 1, 4, f);                 // corridor → hub
        arch.doorway(HUB[0], Y + 1, 0, true, 1, 4, f);                      // hub west
        arch.doorway(HUB[2], Y + 1, ENTRANCE_Z, true, 3, 6, f);             // hub → wards: grand gate
        int hubMidX = (HUB[0] + HUB[2]) / 2;
        arch.doorway(hubMidX, Y + 1, HUB[3], false, 2, 5, f);               // hub → cell block
        arch.doorway(hubMidX, Y + 1, CELLS[1], false, 2, 5, f);
        int crateMid = (CRATES[0] + CRATES[2]) / 2, yardMid = (YARD[0] + YARD[2]) / 2;
        arch.doorway(crateMid, Y + 1, HUB[1], false, 1, 4, f);              // hub → crate hall
        arch.doorway(crateMid, Y + 1, CRATES[3], false, 1, 4, f);
        arch.doorway(yardMid, Y + 1, HUB[1], false, 1, 4, f);               // hub → yard
        arch.doorway(yardMid, Y + 1, YARD[3], false, 1, 4, f);
        // Connector paths between hub and its three annexes.
        connector(hubMidX, HUB[3] + 1, CELLS[1] - 1, 2);
        connector(crateMid, CRATES[3] + 1, HUB[1] - 1, 1);
        connector(yardMid, YARD[3] + 1, HUB[1] - 1, 1);

        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (d.rank.equals("FREE")) continue;
            int[] w = wardRect(d);
            arch.doorway(w[0], Y + 1, ENTRANCE_Z, true, 1, 4, Material.CHISELED_DEEPSLATE);   // ward west
            arch.doorway(w[2], Y + 1, ENTRANCE_Z, true, 1, 4, Material.CHISELED_DEEPSLATE);   // ward east
            for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 3; dy++) {
                arch.set(d.x1, Y + dy, d.z1 + ENTRANCE_Z + dz, Material.AIR);               // mine west door
                arch.set(d.x2, Y + dy, d.z1 + ENTRANCE_Z + dz, Material.AIR);               // mine east door
            }
            int[] p = pitRect(d);
            if (p != null && List.of("F", "M", "T").contains(d.rank)) {
                int mid = (w[0] + w[2]) / 2;
                arch.doorway(mid, Y + 1, w[1], false, 1, 4, Material.CHISELED_DEEPSLATE);  // ward south
                arch.doorway(mid, Y + 1, p[3], false, 1, 4, Material.CHISELED_DEEPSLATE);  // pit north
                connector(mid, p[3] + 1, w[1] - 1, 1);
            }
        }
        // Fishing path into the fishing area's north wall.
        arch.doorway((FISH_PATH[0] + FISH_PATH[2]) / 2, Y + 1, FISH_Z2, false, 2, 4, Material.CHISELED_SANDSTONE);
    }

    /** A short floored, railed path along Z between two doorways. */
    private void connector(int centreX, int z1, int z2, int halfWidth) {
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int dx = -halfWidth - 1; dx <= halfWidth + 1; dx++) {
                boolean edge = Math.abs(dx) == halfWidth + 1;
                arch.set(centreX + dx, Y, z, edge ? Material.POLISHED_BLACKSTONE : Material.SMOOTH_STONE);
                for (int dy = 1; dy <= 4; dy++) arch.set(centreX + dx, Y + dy, z, edge ? Material.STONE_BRICKS : Material.AIR);
                arch.set(centreX + dx, Y + 5, z, Material.STONE_BRICKS);
            }
            if (z % 4 == 0) arch.set(centreX, Y + 4, z, Material.LANTERN);
        }
    }

    // ---- Helpers ----

    private void sign(int x, int y, int z, BlockFace facing, String... lines) {
        signs.add(new PendingSign(x, y, z, facing, lines));
    }

    private void placeBed(int x, int y, int z, BlockFace facing) {
        Block foot = world.getBlockAt(x, y, z);
        Block head = foot.getRelative(facing);
        foot.setType(Material.RED_BED, false);
        head.setType(Material.RED_BED, false);
        if (foot.getBlockData() instanceof Bed fb) { fb.setPart(Bed.Part.FOOT); fb.setFacing(facing); foot.setBlockData(fb, false); }
        if (head.getBlockData() instanceof Bed hb) { hb.setPart(Bed.Part.HEAD); hb.setFacing(facing); head.setBlockData(hb, false); }
    }

    private String prettyName(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String part : raw.toLowerCase().split("_")) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
        }
        String out = sb.toString().trim();
        return out.length() > 15 ? out.substring(0, 15) : out;
    }

    private Material pickOre(Material filler, Material common, Material rare, double rarePct) {
        double r = Math.random() * 100.0;
        if (r < rarePct) return rare;
        if (r < rarePct + 25.0) return common;
        return filler;
    }

    private void placeMineLights(RankMineData.Def d) {
        for (int x = d.x1 + 4; x < d.x2; x += 8) for (int z = d.z1 + 4; z < d.z2; z += 8) {
            arch.set(x, d.y2 - 1, z, Material.GLOWSTONE);
        }
    }

    private void setupWorldBorder() {
        int minX = STARTER[0] - 20, maxX = lastMineX2() + 40, minZ = FISH_Z1 - 20, maxZ = CELLS[3] + 20;
        for (int[] b : mineBounds.values()) maxZ = Math.max(maxZ, b[5] + 20);
        world.getWorldBorder().setCenter((minX + maxX) / 2.0, (minZ + maxZ) / 2.0);
        world.getWorldBorder().setSize(Math.max(maxX - minX, maxZ - minZ) + 40);
    }

    private Material safeMaterial(String name) {
        try { return Material.valueOf(name); } catch (IllegalArgumentException e) { return Material.STONE; }
    }

    public void resetMine(String rank) {
        int[] b = mineBounds.get(rank);
        if (b == null) return;
        RankMineData.Def d = RankMineData.RANKS.get(rank);
        Material filler = safeMaterial(d.filler), common = safeMaterial(d.common), rare = safeMaterial(d.rare);
        double rarePct = rarePercentFor(rank);
        for (int x = b[0] + 1; x < b[3]; x++) for (int z = b[2] + 1; z < b[5]; z++) for (int y = b[1] + 1; y < b[4]; y++) {
            world.getBlockAt(x, y, z).setType(pickOre(filler, common, rare, rarePct), false);
        }
        placeMineLights(d);
    }

    public double percentRemaining(String rank) {
        int[] b = mineBounds.get(rank);
        if (b == null) return 100;
        int total = 0, filled = 0, step = Math.max(1, (b[3] - b[0]) / 20);
        for (int x = b[0] + 1; x < b[3]; x += step) for (int z = b[2] + 1; z < b[5]; z += step) for (int y = b[1] + 1; y < b[4]; y += 2) {
            total++;
            if (world.getBlockAt(x, y, z).getType() != Material.AIR) filled++;
        }
        return total == 0 ? 100 : (100.0 * filled / total);
    }
}
