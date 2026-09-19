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
import org.bukkit.entity.EntityType;
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
    private final List<int[]> treeBases = new ArrayList<>(); // [x, y, z] trunk base per tree
    private Location hubSpawn;
    private Location starterSpawn;
    private final Map<Integer, CellManager.CellRect> cellRects = new LinkedHashMap<>();

    // ---- Master layout. Every region here is disjoint from every other. ----
    public static final int Y = 95;               // one floor height everywhere

    // The mine row is now two lanes branching off the hub's east wall instead of one long
    // line: odd-position ranks (A,C,E...) run out to the LEFT (positive Z), even-position
    // ranks (B,D,F...) run out to the RIGHT (negative Z), paired by distance from hub — A
    // and B are both "1st out", C and D both "2nd out", and so on. LEFT_ENTRANCE_Z and
    // RIGHT_ENTRANCE_Z are each constant across their whole lane because every mine in a
    // lane shares the same hub-side edge (see RankMineData's generator).
    // The gates need to fit inside the hub's own north/south walls without reaching into
    // CELLS (z1=45) or CRATES/YARD (z2=-45), so lane magnitude is kept well inside that ±45
    // envelope (see the overlap check tools/map_check.py runs after every edit here).
    private static final int LEFT_BASE = 32, RIGHT_BASE = -32;
    private static final int LEFT_ENTRANCE_Z = LEFT_BASE + 3, RIGHT_ENTRANCE_Z = RIGHT_BASE - 3;

    private static final int[] STARTER   = {-170, -20, -130, 20};
    private static final int[] CORRIDOR  = {-130,  -5,  -76,  5};
    // Bigger than before, and tall enough on the Z axis to reach both lanes' gates on its
    // east wall — one gate to the north (left lane), one to the south (right lane) — while
    // staying clear of CELLS to the south and CRATES/YARD to the north.
    private static final int[] HUB       = {-100, -40,  -15, 40};
    private static final int[] CELLS     = { -135,  45,  -15,  64};
    private static final int[] CRATES    = { -75, -75,  -45, -45};
    private static final int[] YARD      = { -40, -75,  -15, -45};
    // West of the hub/starter — negative X, same as before this redesign — so this whole
    // cluster can never collide with a mine or ward: every mine lane runs positive-X only.
    private static final int FISH_X1 = -460, FISH_X2 = -20;
    private static final int FISH_Z1 = -240, FISH_Z2 = -100;
    // A short straight corridor on the hub's own north wall, well west of the crate-hall and
    // yard doorways that already sit on that same wall (crateMid/yardMid), so fishing gets a
    // clean gate of its own instead of overlapping either of them.
    private static final int FISH_GATE_X = HUB[0] + 15;
    private static final int[] FISH_PATH = {FISH_GATE_X - 3, FISH_Z2, FISH_GATE_X + 3, HUB[1] - 1};
    private static final int[] LOGGING = {-580, -240, -480, -140};
    private static final int[] FARM    = {-580, -130, -480,  -30};
    private static final int CELL_SIZE = 6, CORRIDOR_WIDTH = 5;

    private static final int CELL_TIERS = 3;

    private record PendingSign(int x, int y, int z, BlockFace facing, boolean hologram, String[] lines) { }

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
    public List<int[]> getTreeBases() { return treeBases; }
    public int[] getLoggingBounds() { return LOGGING; }
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
            sellSigns.put(new Location(world, d.x1 - 1, Y + 2, fromNear(d, 5)),
                    new SellSignListener.SellSignData(d.rank, prices));
        }
        registerPondBounds();
        registerCrateLocations();
        registerPvpZones();
        // Off-centre, clear of the raised dais and its landmark column in the middle of the
        // room — the exact centre puts players inside the dais's solid raised platform.
        hubSpawn = new Location(world, HUB[0] + 10.5, Y + 1, 0.5);
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
        buildLoggingYard();
        buildAnimalPens();

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

    /**
     * Spawns a floating hologram beside every eligible sign, readable from any angle instead of
     * only face-on. Signs marked hologram=false are skipped — with 114 cells, giving every
     * single one its own always-visible floating label produced a wall of overlapping text
     * readable from across the whole room. Holograms are reserved for signs actually worth
     * reading from a distance (ward, hub, crate, tutorial signs); cell numbers stay as plain
     * signs you read by walking up to them, same as any real building's room numbers.
     * Called exactly once, right after the first build — never on later restarts, since
     * spawning entities isn't idempotent the way re-writing sign text is.
     */
    public void spawnHolograms() {
        for (PendingSign ps : signs) {
            if (!ps.hologram) continue;
            double ox = ps.facing.getModX() * 0.65;
            double oz = ps.facing.getModZ() * 0.65;
            Location base = new Location(world, ps.x + 0.5 + ox, ps.y + 0.55, ps.z + 0.5 + oz);
            java.util.List<String> lines = new java.util.ArrayList<>();
            for (String l : ps.lines) if (l != null && !l.isBlank()) lines.add(l);
            if (!lines.isEmpty()) Hologram.spawn(base, lines.toArray(new String[0]));
        }
    }

    // ---- Sky platform: solid ground under every region so nothing is a void trap ----

    private void buildSkyPlatform() {
        int[][] footprints = {
                pad(STARTER, 4), pad(CORRIDOR, 3),
                // The hub, cells, crates and yard are stitched together with narrow connector
                // corridors — tightly padding each room individually left real gaps between
                // them once the cell block was widened. One combined blanket under the whole
                // central complex removes that whole class of bug rather than chasing each path.
                combinedBlanket(10, HUB, CELLS, CRATES, YARD),
                pad(FISH_PATH, 4),
                // Same reasoning for the fishing/logging/farm cluster out past the corridor.
                combinedBlanket(15, new int[]{FISH_X1, FISH_Z1 - 6, FISH_X2, FISH_Z2 + 6}, LOGGING, FARM),
                wardLineFootprint()   // both lanes' wards + bypass corridors + pits
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

    /** The bounding box that encloses every given region, expanded by the given padding. */
    private int[] combinedBlanket(int padding, int[]... regions) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] r : regions) {
            minX = Math.min(minX, r[0]); minZ = Math.min(minZ, r[1]);
            maxX = Math.max(maxX, r[2]); maxZ = Math.max(maxZ, r[3]);
        }
        return new int[]{minX - padding, minZ - padding, maxX + padding, maxZ + padding};
    }

    private int[] pad(int[] r, int p) { return new int[]{r[0] - p, r[1] - p, r[2] + p, r[3] + p}; }

    private int lastMineX2() {
        int max = 0;
        for (RankMineData.Def d : RankMineData.RANKS.values()) max = Math.max(max, d.x2);
        return max;
    }

    /**
     * The X/Z box covering both lanes' wards, bypass corridors and pits — everything between
     * the hub and the mines that isn't a mine interior (mines lay their own floor in
     * buildMine and don't need this blanket). Computed from wardRect/pitRect across every
     * rank rather than hardcoded, so it can't silently fall out of sync if a lane constant
     * changes.
     */
    private int[] wardLineFootprint() {
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (d.rank.equals("FREE")) continue;
            int[] w = wardRect(d), p = pitRect(d);
            minZ = Math.min(minZ, Math.min(w[1], p[1]));
            maxZ = Math.max(maxZ, Math.max(w[3], p[3]));
        }
        return new int[]{HUB[2], minZ - 10, lastMineX2() + 30, maxZ + 10};
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
        // Fixed length regardless of how big the hub gets — this only needs to reach the Yard
        // door, not the whole hall.
        int yardMid = (YARD[0] + YARD[2]) / 2;
        for (int z = PVP_LANE_Z1; z <= -7; z++) {
            for (int dx = -1; dx <= 1; dx++) arch.set(yardMid + dx, Y, z, Material.RED_WOOL);
        }
        pvpLane = new int[]{yardMid - 1, PVP_LANE_Z1, yardMid + 1, -7};
        sign(yardMid + 3, Y + 2, -12, BlockFace.WEST, "§c§lRED WOOL", "means", "§cPVP IS ON", "");
        sign(yardMid - 3, Y + 2, -12, BlockFace.EAST, "§7§lGRAY FLOOR", "means", "§aPVP IS OFF", "");

        // Server name centred on the east wall, between the two mine-lane gates.
        int nameW = BlockFont.width("SKY PRISON");
        BlockFont.write(world, "SKY PRISON", r[2], Y + HUB_HEIGHT - 2, nameW / 2, BlockFont.Axis.NEG_Z, Material.LIGHT_BLUE_CONCRETE);

        // Ender chests flanking each of the two mine-lane gates.
        for (int gz : new int[]{LEFT_ENTRANCE_Z, RIGHT_ENTRANCE_Z}) {
            arch.set(r[2] - 2, Y + 1, gz - 5, Material.ENDER_CHEST);
            arch.set(r[2] - 2, Y + 1, gz + 5, Material.ENDER_CHEST);
        }
        // Signposted lane markers well before each gate so the split reads clearly from a
        // distance, not just at the doorway itself.
        BlockFont.write(world, "A-Y WARDS", r[2] - 1, Y + 9, LEFT_ENTRANCE_Z - BlockFont.width("A-Y WARDS") / 2, BlockFont.Axis.POS_Z, Material.LIME_CONCRETE);
        BlockFont.write(world, "B-Z WARDS", r[2] - 1, Y + 9, RIGHT_ENTRANCE_Z - BlockFont.width("B-Z WARDS") / 2, BlockFont.Axis.POS_Z, Material.ORANGE_CONCRETE);
        sign(r[2] - 3, Y + 2, LEFT_ENTRANCE_Z, BlockFace.WEST, "§a§lODD WARDS", "A, C, E...", "Left wing", "");
        sign(r[2] - 3, Y + 2, RIGHT_ENTRANCE_Z, BlockFace.WEST, "§6§lEVEN WARDS", "B, D, F...", "Right wing", "");

        sign(r[0] + 1, Y + 2, 8, BlockFace.EAST, "§b§lSKY PRISON", "/prison  menu", "/warps  mines", "/fishing  ponds");
        sign(r[0] + 1, Y + 2, -8, BlockFace.EAST, "§6§lWHERE TO", "N: Cell block", "S: Crates, Yard, Fishing", "E: The Wards (two wings)");
    }

    private int[] pvpLane;
    private static final int PVP_LANE_Z1 = -29;

    // ---- Cell block: two tiers of 7x7 cells on railed gantries ----

    private static final int CELL_TIER_HEIGHT = 6;

    /**
     * Computes every cell's rectangle. Cheap math only; runs every boot so /cell always works.
     * Column 0 of the north row is deliberately left empty — it's the entrance vestibule that
     * connects the hub's door straight into the central corridor, rather than the hub opening
     * into the solid back wall of a cell.
     */
    private void registerCellRects() {
        cellRects.clear();
        int[] r = CELLS;
        int usableWidth = (r[2] - r[0]) - 4;          // margin for the end walls
        int perRow = Math.max(1, usableWidth / CELL_SIZE);
        int startX = r[0] + 2;
        int cellNumber = 1;
        for (int tier = 0; tier < CELL_TIERS; tier++) {
            int y = Y + tier * CELL_TIER_HEIGHT;
            int northZ = r[1] + 1;
            for (int col = 0; col < perRow; col++) {
                if (col == 0) continue; // vestibule column, not a cell
                int x = startX + col * CELL_SIZE;
                cellRects.put(cellNumber, new CellManager.CellRect(cellNumber, x, northZ, x + CELL_SIZE - 1, northZ + CELL_SIZE - 1, y));
                cellNumber++;
            }
            int southZ = northZ + CELL_SIZE + CORRIDOR_WIDTH;
            for (int col = 0; col < perRow; col++) {
                int x = startX + col * CELL_SIZE;
                cellRects.put(cellNumber, new CellManager.CellRect(cellNumber, x, southZ, x + CELL_SIZE - 1, southZ + CELL_SIZE - 1, y));
                cellNumber++;
            }
        }
    }

    private void buildCellBlock() {
        int[] r = CELLS;
        int hallHeight = CELL_TIERS * CELL_TIER_HEIGHT + 3;
        arch.prisonHall(r[0], r[1], r[2], r[3], Y, hallHeight, Material.POLISHED_DEEPSLATE, Material.POLISHED_BLACKSTONE, 12);

        int usableWidth = (r[2] - r[0]) - 4;
        int perRow = Math.max(1, usableWidth / CELL_SIZE);
        int startX = r[0] + 2;
        int cellNumber = 1;
        int corridorMidZ = r[1] + 1 + CELL_SIZE + CORRIDOR_WIDTH / 2;

        for (int tier = 0; tier < CELL_TIERS; tier++) {
            int y = Y + tier * CELL_TIER_HEIGHT;
            int northZ = r[1] + 1;
            int southZ = northZ + CELL_SIZE + CORRIDOR_WIDTH;

            for (int col = 0; col < perRow; col++) {
                int x = startX + col * CELL_SIZE;
                if (col == 0) {
                    // Vestibule: clear floor and open air the full depth of the north row, so
                    // the hub's doorway (opened straight through the wall here) leads directly
                    // into the corridor instead of dead-ending against a cell's back wall.
                    for (int dx = 0; dx < CELL_SIZE; dx++) {
                        for (int dz = 0; dz < CELL_SIZE; dz++) {
                            arch.set(x + dx, y, northZ + dz, Material.POLISHED_DEEPSLATE);
                            for (int dy = 1; dy <= 3; dy++) arch.set(x + dx, y + dy, northZ + dz, Material.AIR);
                        }
                    }
                } else {
                    buildCell(x, y, northZ, cellNumber++, BlockFace.SOUTH);
                }
                buildCell(x, y, southZ, cellNumber++, BlockFace.NORTH);
            }

            // The central corridor floor and, on upper tiers, a railed gantry over the tier below.
            int corridorZ1 = northZ + CELL_SIZE, corridorZ2 = southZ - 1;
            for (int x = startX - 1; x <= startX + perRow * CELL_SIZE; x++) {
                for (int z = corridorZ1; z <= corridorZ2; z++) {
                    arch.set(x, y, z, Material.POLISHED_DEEPSLATE);
                }
            }
        }

        // Stairs up through every tier at the west end of the corridor, kept clear of any cell.
        int stairX = startX - 2;
        for (int tier = 0; tier < CELL_TIERS - 1; tier++) {
            int base = Y + tier * CELL_TIER_HEIGHT;
            for (int i = 0; i < CELL_TIER_HEIGHT; i++) {
                arch.placeStair(stairX, base + i, corridorMidZ - 2 + i, Material.DEEPSLATE_BRICK_STAIRS, BlockFace.SOUTH, false);
                arch.set(stairX, base + i, corridorMidZ - 2 + i, Material.DEEPSLATE_BRICK_STAIRS);
                for (int dz = -1; dz <= 0; dz++) {
                    arch.set(stairX, base + i + 1, corridorMidZ - 2 + i + dz, Material.AIR);
                }
            }
        }

        arch.doorway(HUB[0] + 18, Y + 1, r[1], false, 1, 4, Material.SMOOTH_QUARTZ);
        sign(r[0] + 1, Y + 2, r[1] + 2, BlockFace.EAST, "§8§lCELL BLOCK",
                (cellNumber - 1) + " cells", CELL_TIERS + " tiers", "");
    }

    /** A single cell, door facing the given direction into the central corridor, furnished. */
    private void buildCell(int x, int y, int z, int number, BlockFace doorFacing) {
        for (int dx = 0; dx < CELL_SIZE; dx++) {
            for (int dz = 0; dz < CELL_SIZE; dz++) {
                arch.set(x + dx, y, z + dz, Material.POLISHED_DEEPSLATE);
                arch.set(x + dx, y + 4, z + dz, Material.DEEPSLATE_TILES);
                for (int dy = 1; dy <= 3; dy++) {
                    boolean wall = dx == 0 || dx == CELL_SIZE - 1 || dz == 0 || dz == CELL_SIZE - 1;
                    arch.set(x + dx, y + dy, z + dz, wall ? arch.pick(Architect.CELL_STONE) : Material.AIR);
                }
            }
        }
        // Bars on the corridor-facing wall so the cell reads as a cell, with a real door gap.
        int doorDx = CELL_SIZE / 2;
        boolean faceSouth = doorFacing == BlockFace.SOUTH;
        int wallZ = faceSouth ? z + CELL_SIZE - 1 : z;
        for (int dx = 1; dx < CELL_SIZE - 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                arch.set(x + dx, y + dy, wallZ, dx == doorDx ? Material.AIR : Material.IRON_BARS);
            }
        }
        placeBed(x + 1, y + 1, z + (faceSouth ? 1 : CELL_SIZE - 2), faceSouth ? BlockFace.SOUTH : BlockFace.NORTH);
        arch.set(x + CELL_SIZE - 2, y + 1, z + (faceSouth ? 1 : CELL_SIZE - 2), Material.BARREL);
        arch.set(x + CELL_SIZE - 2, y + 3, z + CELL_SIZE / 2, Material.LANTERN);
        signNoHologram(x + doorDx, y + 2, faceSouth ? z + CELL_SIZE : z - 1, faceSouth ? BlockFace.SOUTH : BlockFace.NORTH,
                "§7Cell", "§f#" + number, "", "");
    }

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
        pvpZones.add(new PvpZoneManager.Zone("Hub PvP Lane", yardMid - 1, Y, PVP_LANE_Z1, yardMid + 1, Y + 3, -7));
        for (String rank : new String[]{"F", "M", "T"}) {
            int[] p = pitRect(RankMineData.RANKS.get(rank));
            if (p != null) pvpZones.add(new PvpZoneManager.Zone(rank + "-Ward Pit", p[0], Y, p[1], p[2], Y + 8, p[3]));
        }
    }

    // ---- Lane helpers: every mine belongs to the LEFT or RIGHT lane off the hub (see
    //      RankMineData.LANES). A lane's "near" edge is whichever of z1/z2 is on the hub
    //      side — z1 for the left lane, z2 for the right lane — and it's the same for every
    //      mine in that lane, since RankMineData pins it fixed per lane. ----
    private boolean isLeft(String rank) { return "LEFT".equals(RankMineData.LANES.get(rank)); }
    private int nearZ(RankMineData.Def d) { return isLeft(d.rank) ? d.z1 : d.z2; }
    private int fromNear(RankMineData.Def d, int amount) { return nearZ(d) + (isLeft(d.rank) ? amount : -amount); }
    /** The gate/approach Z for this mine's ward — constant across a whole lane. */
    private int entranceZ(RankMineData.Def d) { return fromNear(d, 3); }

    private int[] wardRect(RankMineData.Def d) {
        int ez = entranceZ(d);
        return new int[]{d.x1 - 24, ez - 11, d.x1 - 4, ez + 11};
    }

    private int[] pitRect(RankMineData.Def d) {
        if (d == null) return null;
        int[] w = wardRect(d);
        // Further out past the ward's own outer (bypass-side) edge — z1 is always that edge,
        // for either lane, since wardRect above is built symmetrically around entranceZ.
        return new int[]{w[0], w[1] - 28, w[2], w[1] - 8};
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
        cladMineFacade(d);
        // Sell sign on the ward-facing wall, beside the entrance.
        sign(d.x1 - 1, Y + 2, fromNear(d, 5), BlockFace.WEST, "§a[Sell]", "Mine " + d.rank + " ores", "Right-click", "holding ore");
    }

    /**
     * Cladding for a mine's two long exterior faces and roof, one block outside the bedrock
     * shell. The bedrock itself stays untouched (it's what protects the mine's contents) —
     * this is a decorative skin over the outside so the building reads as a facade, not a
     * bare grey box the size of a warehouse.
     */
    /**
     * Each rank gets its own facade theme instead of the same light-blue/cyan bands on every
     * mine, so the row reads as 26 different buildings, not one building copy-pasted 26 times.
     * Themes shift roughly every 4-5 ranks, echoing the classic "early ranks look rough, late
     * ranks look prestigious" read without needing bespoke terrain/foliage per mine (that's a
     * much bigger art pass — see the notes sent alongside this change).
     */
    private record MineTheme(Material pillar, Material trim, Material bandA, Material bandB, Material roof) { }

    private static final MineTheme[] MINE_THEMES = {
            new MineTheme(Material.COBBLESTONE, Material.STONE_BRICKS, Material.STONE, Material.ANDESITE, Material.STONE_BRICKS),
            new MineTheme(Material.POLISHED_ANDESITE, Material.SMOOTH_STONE, Material.LIGHT_GRAY_TERRACOTTA, Material.GRAY_TERRACOTTA, Material.SMOOTH_STONE),
            new MineTheme(Material.POLISHED_BASALT, Material.SMOOTH_QUARTZ, Material.LIGHT_BLUE_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.SMOOTH_QUARTZ),
            new MineTheme(Material.CUT_COPPER, Material.WAXED_CUT_COPPER, Material.ORANGE_TERRACOTTA, Material.TERRACOTTA, Material.WAXED_CUT_COPPER),
            new MineTheme(Material.POLISHED_DEEPSLATE, Material.CHISELED_DEEPSLATE, Material.DEEPSLATE_TILES, Material.DEEPSLATE_BRICKS, Material.CHISELED_DEEPSLATE),
            new MineTheme(Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, Material.GILDED_BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS),
            new MineTheme(Material.AMETHYST_BLOCK, Material.CALCITE, Material.PURPLE_TERRACOTTA, Material.MAGENTA_TERRACOTTA, Material.CALCITE),
    };

    private MineTheme themeFor(String rank) {
        int idx = RankMineData.RANKS.keySet().stream().toList().indexOf(rank);
        return MINE_THEMES[(idx / 4) % MINE_THEMES.length];
    }

    private void cladMineFacade(RankMineData.Def d) {
        MineTheme t = themeFor(d.rank);
        int height = d.y2 - d.y1;
        for (int x = d.x1; x <= d.x2; x++) {
            for (int y = 0; y <= height; y++) {
                boolean pillar = (x - d.x1) % 10 < 2;
                boolean band = y == height / 3 || y == height / 3 + 1;
                boolean trim = y == 0 || y == height;
                Material mat = pillar ? t.pillar
                        : trim ? t.trim
                        : band ? (x % 2 == 0 ? t.bandA : t.bandB)
                        : arch.pick(Architect.WARD_INDUSTRIAL);
                arch.set(x, d.y1 + y, d.z1 - 1, mat);
                arch.set(x, d.y1 + y, d.z2 + 1, mat);
            }
        }
        // A simple capped roof with an overhang lip, rather than a flat bedrock top.
        for (int x = d.x1 - 1; x <= d.x2 + 1; x++) {
            for (int z = d.z1 - 1; z <= d.z2 + 1; z++) {
                arch.set(x, d.y2 + 1, z, t.roof);
            }
        }
    }

    private void buildWard(RankMineData.Def d) {
        int[] w = wardRect(d);
        arch.prisonHall(w[0], w[1], w[2], w[3], Y, 9, Material.POLISHED_DEEPSLATE, Material.POLISHED_BLACKSTONE, 8);
        // Iron-bar rails flanking the route through to the mine.
        for (int x = w[0] + 1; x < w[2]; x++) {
            arch.set(x, Y + 1, entranceZ(d) - 3, Material.IRON_BARS);
            arch.set(x, Y + 1, entranceZ(d) + 3, Material.IRON_BARS);
        }
        // Ender chests either side of the mine approach.
        arch.set(w[2] - 2, Y + 1, entranceZ(d) - 5, Material.ENDER_CHEST);
        arch.set(w[2] - 2, Y + 1, entranceZ(d) + 5, Material.ENDER_CHEST);

        // The mine gate: an archway on the mine's face with the rank letter in quartz above it,
        // cobweb wire along the top — the single most recognisable prison-server image.
        int gx = d.x1;
        for (int dz = -4; dz <= 4; dz++) {
            for (int dy = 1; dy <= 6; dy++) {
                boolean frame = Math.abs(dz) == 4 || dy == 6;
                arch.set(gx, Y + dy, entranceZ(d) + dz, frame ? Material.SMOOTH_QUARTZ : Material.IRON_BARS);
            }
        }
        for (int dz = -5; dz <= 5; dz++) arch.set(gx, Y + 7, entranceZ(d) + dz, (dz % 3 == 0) ? Material.IRON_BARS : Material.COBWEB);
        BlockFont.write(world, d.rank, gx, Y + 15, entranceZ(d) - 2, BlockFont.Axis.POS_Z, Material.LIGHT_BLUE_CONCRETE);
        BlockFont.write(world, "MINE", gx, Y + 26, entranceZ(d) - BlockFont.width("MINE") / 2, BlockFont.Axis.POS_Z, Material.LIGHT_BLUE_CONCRETE);

        sign(w[0] + 1, Y + 2, w[1] + 4, BlockFace.EAST, "§6§l" + d.rank + "-WARD", "Mine " + d.rank,
                "Rare: " + prettyName(d.rare), String.format("%.0f%% rare", rarePercentFor(d.rank)));
    }

    /**
     * Each lane gets its own single corridor running outside every mine in that lane (every
     * mine in a lane shares the same hub-side edge, so one bypass line sits clear of all of
     * them, however wide the late-game mines get). Every ward gets a short spur connecting it
     * to its lane's corridor from the ward's own outer wall. This is what stops wards from
     * only being reachable by walking straight through the mine in between them — the bypass
     * never enters a single mine's interior.
     */
    private static final int LEFT_BYPASS_Z = LEFT_ENTRANCE_Z - 18, RIGHT_BYPASS_Z = RIGHT_ENTRANCE_Z - 18;

    private List<RankMineData.Def> laneOrder(boolean left) {
        List<RankMineData.Def> out = new ArrayList<>();
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (d.rank.equals("FREE") || isLeft(d.rank) != left) continue;
            out.add(d);
        }
        return out;
    }

    private void buildWardWalkways() {
        buildLaneWalkways(true, LEFT_ENTRANCE_Z, LEFT_BYPASS_Z);
        buildLaneWalkways(false, RIGHT_ENTRANCE_Z, RIGHT_BYPASS_Z);

        // Direct path from the hub's own north wall down to the fishing area.
        for (int z = FISH_PATH[1]; z <= FISH_PATH[3]; z++) {
            for (int x = FISH_PATH[0]; x <= FISH_PATH[2]; x++) {
                boolean edge = x == FISH_PATH[0] || x == FISH_PATH[2];
                arch.set(x, Y, z, edge ? Material.POLISHED_BLACKSTONE : Material.SMOOTH_STONE);
                if (edge) arch.set(x, Y + 1, z, Material.POLISHED_BLACKSTONE_WALL);
            }
            if (z % 8 == 0) { arch.set(FISH_PATH[0] - 1, Y + 2, z, Material.LANTERN); }
        }
    }

    /** Builds one lane's whole walkway system: hub gate -> first ward, per-ward mine spurs,
     *  and the bypass corridor with a spur into every ward — same shape as the old single
     *  line, just parameterised so it can run twice, once per lane. */
    private void buildLaneWalkways(boolean left, int entranceZ, int bypassZ) {
        List<RankMineData.Def> ordered = laneOrder(left);
        if (ordered.isEmpty()) return;

        // Hub gate to the first ward in this lane.
        walkway(HUB[2] + 1, wardRect(ordered.get(0))[0] - 1, entranceZ, 3);

        // Each ward's own dead-end approach to its mine.
        for (RankMineData.Def d : ordered) {
            int[] w = wardRect(d);
            walkway(w[2] + 1, d.x1 - 1, entranceZ, 2);
        }

        // The bypass corridor for this lane, running the full length of its mine row.
        int firstX = wardRect(ordered.get(0))[0];
        int lastX = ordered.get(ordered.size() - 1).x2;
        walkway(firstX, lastX, bypassZ, 3);

        // A connector linking the hub's own gate straight to this lane's bypass.
        connector((HUB[2] + firstX) / 2, bypassZ, entranceZ, 3);

        // One spur per ward, from its outer wall down to the bypass corridor.
        for (RankMineData.Def d : ordered) {
            int[] w = wardRect(d);
            int midX = (w[0] + w[2]) / 2;
            arch.doorway(midX, Y + 1, w[1], false, 1, 4, Material.SMOOTH_QUARTZ);
            connector(midX, w[1] - 1, bypassZ + 3, 2);
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
        int x = FISH_X1 + 20;
        for (FishingData.Pond pond : FishingData.PONDS.values()) {
            int size = 22 + pond.tier * 2;
            pond.x1 = x; pond.x2 = x + size;
            pond.z2 = FISH_Z2 - 12; pond.z1 = pond.z2 - size;
            pond.y = Y;
            x = pond.x2 + 12;
        }
    }

    private void buildFishingArea() {
        int x1 = FISH_X1, x2 = FISH_X2;
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

    // ---- Logging yard: chop trees for wood, they regrow on a timer ----

    private void buildLoggingYard() {
        int[] r = LOGGING;
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(Material.GRASS_BLOCK, Material.COARSE_DIRT, 10, Material.PODZOL, 8));
        arch.detailedWall(r[0], Y + 1, r[1], r[2], r[3], 4, Architect.FISHING_STONE,
                Material.STRIPPED_OAK_LOG, Material.SMOOTH_SANDSTONE, 10);

        treeBases.clear();
        int spacing = 9;
        for (int x = r[0] + 6; x <= r[2] - 6; x += spacing) {
            for (int z = r[1] + 6; z <= r[3] - 6; z += spacing) {
                treeBases.add(new int[]{x, Y + 1, z});
            }
        }
        for (int[] base : treeBases) plantTree(base[0], base[1], base[2]);

        sign(r[0] + 2, Y + 2, r[1] + 2, BlockFace.EAST, "§2§lLOGGING YARD", "Chop the trees", "They regrow", "over time");
        // Path back toward the fishing area / prison.
        for (int x = r[0] - 10; x < r[0]; x++) {
            arch.set(x, Y, (r[1] + r[3]) / 2, Material.COARSE_DIRT);
        }
    }

    private void plantTree(int x, int y, int z) {
        for (int dy = 0; dy < 5; dy++) arch.set(x, y + dy, z, Material.OAK_LOG);
        for (int dy = 3; dy <= 5; dy++) {
            int radius = dy == 5 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && dy < 5) continue; // leave the trunk showing
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && Math.random() < 0.5) continue;
                    arch.set(x + dx, y + dy, z + dz, Material.OAK_LEAVES);
                }
            }
        }
    }

    /** Regrows any tree that's been chopped down. Called on a timer, same idea as a mine reset. */
    public void regrowTrees() {
        for (int[] base : treeBases) {
            if (world.getBlockAt(base[0], base[1] + 2, base[2]).getType() == Material.OAK_LOG) continue; // still standing
            plantTree(base[0], base[1], base[2]);
        }
    }

    // ---- Animal pens: cow and pig spawners for hide, meat, and wool-adjacent farming ----

    private void buildAnimalPens() {
        int[] r = FARM;
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(Material.GRASS_BLOCK, Material.COARSE_DIRT, 12));
        arch.detailedWall(r[0], Y + 1, r[1], r[2], r[3], 3, Architect.FISHING_STONE,
                Material.STRIPPED_OAK_LOG, Material.SMOOTH_SANDSTONE, 8);
        // A dividing fence splitting the pen into a cow side and a pig side.
        int midX = (r[0] + r[2]) / 2;
        for (int z = r[1] + 1; z < r[3]; z++) {
            arch.set(midX, Y + 1, z, Material.OAK_FENCE);
        }

        placeAnimalSpawner(r[0] + (midX - r[0]) / 2, Y + 1, (r[1] + r[3]) / 2, EntityType.COW);
        placeAnimalSpawner(midX + (r[2] - midX) / 2, Y + 1, (r[1] + r[3]) / 2, EntityType.PIG);

        sign(r[0] + 2, Y + 2, r[1] + 2, BlockFace.EAST, "§6§lFARM", "Cows: west pen", "Pigs: east pen", "Kill for drops");
    }

    private void placeAnimalSpawner(int x, int y, int z, EntityType type) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.SPAWNER, false);
        if (b.getState() instanceof org.bukkit.block.CreatureSpawner cs) {
            cs.setSpawnedType(type);
            cs.setSpawnRange(6);
            cs.setMaxNearbyEntities(8);
            cs.setDelay(400); // ~20s between spawn attempts
            cs.update(true, false);
        }
    }

    // ---- Doorways: carved last so nothing can brick them over ----

    private void carveAllDoorways() {
        Material f = Material.SMOOTH_QUARTZ;
        arch.doorway(STARTER[2], Y + 1, 0, true, 1, 4, f);                 // yard → corridor
        arch.doorway(CORRIDOR[0], Y + 1, 0, true, 1, 4, f);                 // corridor west (same wall)
        arch.doorway(CORRIDOR[2], Y + 1, 0, true, 1, 4, f);                 // corridor → hub
        arch.doorway(HUB[0], Y + 1, 0, true, 1, 4, f);                      // hub west
        arch.doorway(HUB[2], Y + 1, LEFT_ENTRANCE_Z, true, 3, 6, f);        // hub → left wing (odd wards)
        arch.doorway(HUB[2], Y + 1, RIGHT_ENTRANCE_Z, true, 3, 6, f);       // hub → right wing (even wards)
        int hubMidX = (HUB[0] + HUB[2]) / 2;
        arch.doorway(FISH_GATE_X, Y + 1, HUB[1], false, 3, 4, f);           // hub → fishing (north wall, direct)
        int cellEntranceX = CELLS[0] + 2 + CELL_SIZE / 2; // centre of the vestibule column
        arch.doorway(hubMidX, Y + 1, HUB[3], false, 2, 5, f);               // hub → cell block
        arch.doorway(cellEntranceX, Y + 1, CELLS[1], false, 2, 5, f);       // opens into the vestibule, not a cell wall
        int crateMid = (CRATES[0] + CRATES[2]) / 2, yardMid = (YARD[0] + YARD[2]) / 2;
        arch.doorway(crateMid, Y + 1, HUB[1], false, 1, 4, f);              // hub → crate hall
        arch.doorway(crateMid, Y + 1, CRATES[3], false, 1, 4, f);
        arch.doorway(yardMid, Y + 1, HUB[1], false, 1, 4, f);               // hub → yard
        arch.doorway(yardMid, Y + 1, YARD[3], false, 1, 4, f);
        // Connector paths between hub and its three annexes.
        // L-shaped route: the cell block is now much wider than the hub, so its entrance
        // (above the vestibule column) doesn't line up on the same X as the hub's own door.
        int cellCorridorZ = HUB[3] + 6;
        connector(hubMidX, HUB[3] + 1, cellCorridorZ, 2);
        walkway(cellEntranceX, hubMidX, cellCorridorZ, 2);
        connector(cellEntranceX, cellCorridorZ, CELLS[1] - 1, 2);
        connector(crateMid, CRATES[3] + 1, HUB[1] - 1, 1);
        connector(yardMid, YARD[3] + 1, HUB[1] - 1, 1);

        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (d.rank.equals("FREE")) continue;
            int[] w = wardRect(d);
            int ez = entranceZ(d);
            arch.doorway(w[0], Y + 1, ez, true, 1, 4, Material.CHISELED_DEEPSLATE);   // ward west
            arch.doorway(w[2], Y + 1, ez, true, 1, 4, Material.CHISELED_DEEPSLATE);   // ward east
            for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 3; dy++) {
                arch.set(d.x1, Y + dy, ez + dz, Material.AIR);               // mine door (single entrance — mines are dead-ends off their ward)
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

        // Fishing area's west edge → logging yard's east wall (logging/farm sit west of the
        // fishing area, on negative X, well clear of every mine which only ever runs positive X).
        int loggingMidZ = (LOGGING[1] + LOGGING[3]) / 2;
        arch.doorway(LOGGING[2], Y + 1, loggingMidZ, true, 2, 4, Material.SMOOTH_SANDSTONE);
        walkway(LOGGING[2] + 1, FISH_X1 - 1, loggingMidZ, 2);

        // Logging yard's south wall → farm's north wall.
        int farmMidX = (FARM[0] + FARM[2]) / 2;
        arch.doorway(farmMidX, Y + 1, LOGGING[3], false, 2, 4, Material.SMOOTH_SANDSTONE);
        arch.doorway(farmMidX, Y + 1, FARM[1], false, 2, 4, Material.SMOOTH_SANDSTONE);
        connector(farmMidX, LOGGING[3] + 1, FARM[1] - 1, 2);
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
        signs.add(new PendingSign(x, y, z, facing, true, lines));
    }

    /** For high-count, low-value labels (e.g. cell numbers) where a floating hologram per
     *  instance would just clutter the view — these stay as plain, read-up-close signs. */
    private void signNoHologram(int x, int y, int z, BlockFace facing, String... lines) {
        signs.add(new PendingSign(x, y, z, facing, false, lines));
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
        int minX = Math.min(STARTER[0], LOGGING[0]) - 20, maxX = Math.max(lastMineX2() + 40, FARM[2] + 20);
        int minZ = Math.min(FISH_Z1, HUB[1]) - 20, maxZ = Math.max(CELLS[3], HUB[3]) + 20;
        // Right-lane mines run toward very negative Z (their far edge is z1, not z2) — scan
        // both ends of every mine's box, not just z2, or the border would clip that whole lane.
        for (int[] b : mineBounds.values()) {
            minZ = Math.min(minZ, b[2] - 20);
            maxZ = Math.max(maxZ, b[5] + 20);
        }
        world.getWorldBorder().setCenter((minX + maxX) / 2.0, (minZ + maxZ) / 2.0);
        world.getWorldBorder().setSize(Math.max(maxX - minX, maxZ - minZ) + 40);
    }

    private Material safeMaterial(String name) {
        try { return Material.valueOf(name); } catch (IllegalArgumentException e) { return Material.STONE; }
    }

    public void resetMine(String rank) {
        // Guards against the exact crash we hit: if the world was never actually built (a stale
        // marker, or called too early), a reset must never try to write blocks across an area
        // whose chunks were never generated — that forces expensive synchronous world gen instead
        // of a fast disk read and can hang the main thread long enough for the watchdog to kill it.
        if (!alreadyBuilt()) return;
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

    /**
     * Samples a mine to see how mined-out it is. Only reads blocks in chunks that are already
     * loaded, so this can never force the server to synchronously generate distant chunks — a
     * mine nobody has visited yet just reports "not empty" and is skipped, which is correct
     * anyway (nobody's mined it).
     */
    public double percentRemaining(String rank) {
        int[] b = mineBounds.get(rank);
        if (b == null || !alreadyBuilt()) return 100;
        int total = 0, filled = 0, step = Math.max(1, (b[3] - b[0]) / 20);
        for (int x = b[0] + 1; x < b[3]; x += step) {
            for (int z = b[2] + 1; z < b[5]; z += step) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = b[1] + 1; y < b[4]; y += 2) {
                    total++;
                    if (world.getBlockAt(x, y, z).getType() != Material.AIR) filled++;
                }
            }
        }
        return total == 0 ? 100 : (100.0 * filled / total);
    }
}
