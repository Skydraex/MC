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
 * v2 layout: a single large square HUB with every mine, and every non-mine feature
 * (fishing, crates, yard, cells), reachable by its own direct gate straight off one of
 * the hub's four walls — a corridor, then a ward/antechamber, then the room itself.
 * Nothing is chained through anything else. Every rectangle here (hub, wards, mines,
 * special rooms, text label zones) was computed and validated for zero overlap and
 * full reachability by tools/layout_gen.py before this file was written — see
 * MapLayout.java and RankMineData.java, which are baked in from that exact output.
 * WorldBuilder never recomputes positions; it only builds what those two files say.
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

    public static final int Y = 95; // one floor height everywhere

    private static final int[] HUB = MapLayout.HUB;
    private static final int CORRIDOR_LEN = MapLayout.CORRIDOR_LEN, WARD_DEPTH = MapLayout.WARD_DEPTH;
    private static final int[] STARTER = MapLayout.STARTER;
    private static final int[] INTAKE_WARD = MapLayout.INTAKE_WARD;
    private static final int[] INTAKE_CORRIDOR = MapLayout.INTAKE_CORRIDOR;
    private static final int INTAKE_GATE_Z = MapLayout.INTAKE_GATE_Z;

    private static final MapLayout.Special FISHING = MapLayout.special("FISHING");
    private static final MapLayout.Special CRATES = MapLayout.special("CRATES");
    private static final MapLayout.Special YARD = MapLayout.special("YARD");
    private static final MapLayout.Special CELLS = MapLayout.special("CELLS");

    // Logging/farm sit further out past the fishing room, same relative idea as before,
    // just along the fishing room's own outward (north) axis instead of along X.
    private static final int[] LOGGING = {FISHING.room[0], FISHING.room[1] - 120, FISHING.room[2], FISHING.room[1] - 20};
    private static final int[] FARM    = {FISHING.room[0], LOGGING[1] - 120, FISHING.room[2], LOGGING[1] - 20};

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
            int[] sp = sellSignSpot(d);
            sellSigns.put(new Location(world, sp[0], Y + 2, sp[1]),
                    new SellSignListener.SellSignData(d.rank, prices));
        }
        registerPondBounds();
        registerCrateLocations();
        registerPvpZones();
        registerCellRects();
        hubSpawn = new Location(world, HUB[0] + 10.5, Y + 1, 0.5);
        starterSpawn = new Location(world, STARTER[0] + 8.5, Y + 1, INTAKE_GATE_Z + 0.5);
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

    // ==================================================================================
    // Wall geometry helpers — every mine and every special room sits on one of the hub's
    // four walls ("N","E","S","W"). These convert that into concrete coordinates so the
    // rest of this file never hand-picks an axis.
    // ==================================================================================

    private static boolean isNS(String wall) { return wall.equals("N") || wall.equals("S"); }

    /** The coordinate along the wall's own plane where a far rect (mine or special room)
     *  touches its ward — i.e. the rect's near-hub edge. */
    private static int nearEdge(String wall, int[] r) {
        return switch (wall) {
            case "N" -> r[3]; // max z
            case "S" -> r[1]; // min z
            case "E" -> r[0]; // min x
            default  -> r[2]; // "W" — max x
        };
    }

    /** The centre of a rect along its "along-wall" axis (its width, not its depth). */
    private static int alongCenter(String wall, int[] r) {
        return isNS(wall) ? (r[0] + r[2]) / 2 : (r[1] + r[3]) / 2;
    }

    /** Which way a doorway should be carved to sit flush in this wall (see Architect.doorway). */
    private static boolean doorAlongZ(String wall) { return !isNS(wall); }

    /** BlockFont axis so text above a gate reads correctly to someone approaching it. */
    private static BlockFont.Axis labelAxis(String wall) {
        return switch (wall) {
            case "N" -> BlockFont.Axis.POS_X;
            case "S" -> BlockFont.Axis.NEG_X;
            case "E" -> BlockFont.Axis.POS_Z;
            default  -> BlockFont.Axis.NEG_Z; // W
        };
    }

    private static BlockFace outwardFace(String wall) {
        return switch (wall) {
            case "N" -> BlockFace.NORTH;
            case "S" -> BlockFace.SOUTH;
            case "E" -> BlockFace.EAST;
            default  -> BlockFace.WEST;
        };
    }

    private static BlockFace inwardFace(String wall) { return outwardFace(wall).getOppositeFace(); }

    private int[] mineRect(RankMineData.Def d) { return new int[]{d.x1, d.z1, d.x2, d.z2}; }
    private int[] wardRect(RankMineData.Def d) { return new int[]{d.wx1, d.wz1, d.wx2, d.wz2}; }

    private static int directionSign(String wall) { return (wall.equals("S") || wall.equals("E")) ? 1 : -1; }

    private int[] sellSignSpot(RankMineData.Def d) {
        int c = alongCenter(d.wall, mineRect(d));
        int edge = nearEdge(d.wall, mineRect(d));
        int off = directionSign(d.wall) * -2; // just inside the ward, next to the gate
        return isNS(d.wall) ? new int[]{c + 3, edge + off} : new int[]{edge + off, c + 3};
    }

    // ---- Sky platform: solid ground under every region so nothing is a void trap ----

    private void buildSkyPlatform() {
        List<int[]> footprints = new ArrayList<>(List.of(
                pad(STARTER, 4), pad(INTAKE_WARD, 4), pad(INTAKE_CORRIDOR, 2),
                combinedBlanket(20, HUB, CELLS.room, CRATES.room, YARD.room,
                        CELLS.ward, CRATES.ward, YARD.ward, FISHING.ward),
                pad(FISHING.room, 6), pad(LOGGING, 6), pad(FARM, 6)
        ));
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (d.rank.equals("FREE")) continue;
            footprints.add(pad(mineRect(d), 3));
            footprints.add(pad(wardRect(d), 3));
        }
        for (int[] f : footprints) {
            for (int x = f[0]; x <= f[2]; x++) {
                for (int z = f[1]; z <= f[3]; z++) {
                    arch.set(x, Y - 1, z, Material.DEEPSLATE);
                    arch.set(x, Y - 2, z, Material.DEEPSLATE);
                    arch.set(x, Y - 3, z, Material.DEEPSLATE_BRICKS); // hidden support layer — never real bedrock
                }
            }
        }
    }

    private int[] combinedBlanket(int padding, int[]... regions) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] r : regions) {
            minX = Math.min(minX, r[0]); minZ = Math.min(minZ, r[1]);
            maxX = Math.max(maxX, r[2]); maxZ = Math.max(maxZ, r[3]);
        }
        return new int[]{minX - padding, minZ - padding, maxX + padding, maxZ + padding};
    }

    private int[] pad(int[] r, int p) { return new int[]{r[0] - p, r[1] - p, r[2] + p, r[3] + p}; }

    // ---- Starter yard and prison bus ----

    private void buildStarterArea() {
        int[] r = STARTER;
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(Material.GRAY_CONCRETE_POWDER, Material.GRAVEL, 25));
        arch.prisonWall(r[0], Y + 1, r[1], r[2], r[3], 8, 6);
        arch.barbedWireTop(r[0], r[1], r[2], r[3], Y + 9);
        for (int x = r[0] + 4; x <= r[2] - 4; x += 8) {
            for (int dy = 1; dy <= 3; dy++) {
                arch.set(x, Y + dy, r[1] + 2, Material.IRON_BARS);
                arch.set(x, Y + dy, r[3] - 2, Material.IRON_BARS);
            }
            arch.set(x, Y + 4, r[1] + 2, Material.LANTERN);
            arch.set(x, Y + 4, r[3] - 2, Material.LANTERN);
        }
        // Server name on the yard's own east wall (the only place this text appears now —
        // the old hub had a second, colliding copy of this; that's gone).
        int nameW = BlockFont.width("SKY PRISON");
        int midZ = (r[1] + r[3]) / 2;
        BlockFont.write(world, "SKY PRISON", r[2] - 1, Y + 16, midZ - nameW / 2, BlockFont.Axis.POS_Z, Material.LIGHT_BLUE_CONCRETE);
        for (int z = midZ - nameW / 2 - 1; z <= midZ + nameW / 2 + 1; z++) {
            for (int dy = 9; dy <= 17; dy++) arch.set(r[2], Y + dy, z, arch.pick(Architect.PRISON_STONE));
        }
        buildPrisonBus(r[0] + 4, Y + 1, INTAKE_GATE_Z - 2);
        sign(r[0] + 12, Y + 2, INTAKE_GATE_Z, BlockFace.EAST, "§8§lINTAKE", "Welcome to", "Sky Prison.", "Head east →");
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

    // ---- Intake corridor: starter -> hub's west wall (north corner buffer) ----

    private void buildIntakeCorridor() {
        int[] r = INTAKE_WARD;
        arch.prisonHall(r[0], r[1], r[2], r[3], Y, 7, Material.POLISHED_ANDESITE, Material.POLISHED_BLACKSTONE, 9);
        int z = INTAKE_GATE_Z;
        int x0 = r[0] + 6;
        sign(x0, Y + 2, z - 3, BlockFace.SOUTH, "§6Step 1", "Mine blocks in", "your rank's mine", "to earn money.");
        sign(x0 + 10, Y + 2, z - 3, BlockFace.SOUTH, "§6Step 2", "Sell at the", "sell sign, or", "use /autosell");
        sign(x0 + 20, Y + 2, z - 3, BlockFace.SOUTH, "§6Step 3", "/rankup to climb", "from A to Z,", "then prestige.");
        sign(x0 + 30, Y + 2, z - 3, BlockFace.SOUTH, "§6Step 4", "Spend tokens on", "pickaxe enchants", "with /enchant");
        sign(x0 + 38, Y + 2, z - 3, BlockFace.SOUTH, "§bFishing", "Unlocks at rank " + FishingData.UNLOCK_RANK, "Own level ladder", "/fishing");
    }

    public Location wardenNpcLocation() { return new Location(world, INTAKE_WARD[0] + 5.5, Y + 1, INTAKE_GATE_Z + 0.5); }
    public Location quartermasterNpcLocation() { return new Location(world, INTAKE_WARD[2] - 5.5, Y + 1, INTAKE_GATE_Z + 0.5); }

    // ---- Hub ----

    private static final int HUB_HEIGHT = 18;

    private void buildHub() {
        int[] r = HUB;
        arch.prisonHall(r[0], r[1], r[2], r[3], Y, HUB_HEIGHT, Material.POLISHED_ANDESITE, Material.POLISHED_BLACKSTONE, 14);
        arch.windowRun(r[0], Y + 1, r[1], r[2], 8, 5, Material.IRON_BARS, Material.STONE_BRICK_STAIRS);
        arch.windowRun(r[0], Y + 1, r[3], r[2], 8, 5, Material.IRON_BARS, Material.STONE_BRICK_STAIRS);

        for (int x = r[0] + 1; x <= r[2] - 1; x++) {
            arch.placeSlab(x, Y + 8, r[1] + 1, Material.STONE_BRICK_SLAB, true);
            arch.placeSlab(x, Y + 8, r[3] - 1, Material.STONE_BRICK_SLAB, true);
        }
        for (int z = r[1] + 1; z <= r[3] - 1; z++) {
            arch.placeSlab(r[0] + 1, Y + 8, z, Material.STONE_BRICK_SLAB, true);
            arch.placeSlab(r[2] - 1, Y + 8, z, Material.STONE_BRICK_SLAB, true);
        }

        // Central dais and landmark column — the one thing every gate in the hub can see.
        int cx = 0, cz = 0;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 8) continue;
                arch.set(cx + dx, Y, cz + dz, Material.POLISHED_BLACKSTONE);
                if (Math.abs(dx) + Math.abs(dz) <= 5) arch.set(cx + dx, Y + 1, cz + dz, Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        for (int dy = 2; dy <= 10; dy++) arch.set(cx, Y + dy, cz, Material.CHISELED_POLISHED_BLACKSTONE);
        arch.set(cx, Y + 11, cz, Material.SEA_LANTERN);
        for (int dy = 12; dy <= HUB_HEIGHT; dy++) arch.set(cx, Y + dy, cz, Material.IRON_BARS);

        // Red-wool PvP lane from the dais straight down to the Yard's own gate on the south wall.
        int yardMid = alongCenter("S", YARD.ward);
        for (int z = 7; z <= HUB[3] - CORRIDOR_LEN - 1; z++) {
            for (int dx = -1; dx <= 1; dx++) arch.set(yardMid + dx, Y, z, Material.RED_WOOL);
        }
        pvpLane = new int[]{yardMid - 1, 7, yardMid + 1, HUB[3] - CORRIDOR_LEN - 1};
        sign(yardMid + 3, Y + 2, 12, BlockFace.WEST, "§c§lRED WOOL", "means", "§cPVP IS ON", "");
        sign(yardMid - 3, Y + 2, 12, BlockFace.EAST, "§7§lGRAY FLOOR", "means", "§aPVP IS OFF", "");

        // The one and only "SKY PRISON" sign inside the hub — a small plaque by the dais,
        // not another giant wall banner competing with anything else.
        sign(cx - 1, Y + 2, cz - 8, BlockFace.SOUTH, "§b§lSKY PRISON", "/prison  menu", "/warps  mines", "/fishing  ponds");

        // Wayfinding board near the dais listing every wall's contents, since with 26 mines
        // spread across 4 walls a compass reference is worth more than giant banners per gate
        // (every gate already gets its own "MINE X" label right above it — see buildWard).
        sign(cx + 1, Y + 2, cz - 8, BlockFace.SOUTH, "§6§lWHERE TO", "N: Fishing + A-J", "E: Crates + K-P", "S: Yard + Q-U   W: Cells + V-Z");
    }

    private int[] pvpLane;

    // ---- Cell block ----

    private static final int CELL_TIER_HEIGHT = 6;

    /** The cell block's entrance is on whichever wall of CELLS.room faces the hub (its
     *  near edge, from the shared wall table) — one vestibule row/column right there,
     *  rather than assuming a fixed compass direction. */
    private void registerCellRects() {
        cellRects.clear();
        int[] r = CELLS.room;
        boolean ns = isNS(CELLS.wall);
        int usableWidth = (ns ? (r[2] - r[0]) : (r[3] - r[1])) - 4;
        int perRow = Math.max(1, usableWidth / CELL_SIZE);
        int cellNumber = 1;
        for (int tier = 0; tier < CELL_TIERS; tier++) {
            int y = Y + tier * CELL_TIER_HEIGHT;
            for (int col = 0; col < perRow; col++) {
                if (col == vestibuleCol(perRow)) continue;
                int[] a = rowSpot(r, col, 1, ns);
                cellRects.put(cellNumber, new CellManager.CellRect(cellNumber, a[0], a[1], a[0] + (ns ? CELL_SIZE - 1 : 0), a[1] + (ns ? 0 : CELL_SIZE - 1), y));
                cellNumber++;
            }
            for (int col = 0; col < perRow; col++) {
                int[] a = rowSpot(r, col, 2, ns);
                cellRects.put(cellNumber, new CellManager.CellRect(cellNumber, a[0], a[1], a[0] + (ns ? CELL_SIZE - 1 : 0), a[1] + (ns ? 0 : CELL_SIZE - 1), y));
                cellNumber++;
            }
        }
    }

    /** Column nearest the room's hub-facing edge holds the vestibule, not column 0 — which
     *  end that is depends on the wall (see nearEdge). */
    private int vestibuleCol(int perRow) {
        return (CELLS.wall.equals("S") || CELLS.wall.equals("E")) ? 0 : perRow - 1;
    }

    /** World (x,z) of a given (col, rowSide) cell slot, generic across N/S vs E/W rooms. */
    private int[] rowSpot(int[] r, int col, int rowSide, boolean ns) {
        int startAlong = (ns ? r[0] : r[1]) + 2;
        int along = startAlong + col * CELL_SIZE;
        int depth0 = (ns ? r[1] : r[0]) + 1;
        int depth = rowSide == 1 ? depth0 : depth0 + CELL_SIZE + CORRIDOR_WIDTH;
        return ns ? new int[]{along, depth} : new int[]{depth, along};
    }

    private void buildCellBlock() {
        int[] r = CELLS.room;
        boolean ns = isNS(CELLS.wall);
        int hallHeight = CELL_TIERS * CELL_TIER_HEIGHT + 3;
        arch.prisonHall(r[0], r[1], r[2], r[3], Y, hallHeight, Material.POLISHED_DEEPSLATE, Material.POLISHED_BLACKSTONE, 12);

        int usableWidth = (ns ? (r[2] - r[0]) : (r[3] - r[1])) - 4;
        int perRow = Math.max(1, usableWidth / CELL_SIZE);
        int cellNumber = 1;
        int vestibule = vestibuleCol(perRow);

        for (int tier = 0; tier < CELL_TIERS; tier++) {
            int y = Y + tier * CELL_TIER_HEIGHT;
            for (int col = 0; col < perRow; col++) {
                int[] a = rowSpot(r, col, 1, ns);
                if (col == vestibule) {
                    for (int dx = 0; dx < CELL_SIZE; dx++) for (int dz = 0; dz < CELL_SIZE; dz++) {
                        int vx = ns ? a[0] + dx : a[0] + dz, vz = ns ? a[1] + dz : a[1] + dx;
                        arch.set(vx, y, vz, Material.POLISHED_DEEPSLATE);
                        for (int dy = 1; dy <= 3; dy++) arch.set(vx, y + dy, vz, Material.AIR);
                    }
                } else {
                    buildCell(a[0], y, a[1], cellNumber++, ns, true);
                }
                int[] b = rowSpot(r, col, 2, ns);
                buildCell(b[0], y, b[1], cellNumber++, ns, false);
            }
            int corridorD1 = (ns ? r[1] : r[0]) + 1 + CELL_SIZE, corridorD2 = corridorD1 + CORRIDOR_WIDTH - 1;
            int alongStart = (ns ? r[0] : r[1]) + 1, alongEnd = alongStart + perRow * CELL_SIZE;
            for (int a = alongStart; a <= alongEnd; a++) {
                for (int dd = corridorD1; dd <= corridorD2; dd++) {
                    int cx = ns ? a : dd, cz = ns ? dd : a;
                    arch.set(cx, y, cz, Material.POLISHED_DEEPSLATE);
                }
            }
        }
        sign(r[0] + 1, Y + 2, r[1] + 2, BlockFace.EAST, "§8§lCELL BLOCK", (cellNumber - 1) + " cells", CELL_TIERS + " tiers", "");
    }

    private void buildCell(int x, int y, int z, int number, boolean ns, boolean firstRow) {
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
        int doorD = CELL_SIZE / 2;
        BlockFace doorFacing = firstRow ? (ns ? BlockFace.SOUTH : BlockFace.EAST) : (ns ? BlockFace.NORTH : BlockFace.WEST);
        for (int i = 1; i < CELL_SIZE - 1; i++) {
            for (int dy = 1; dy <= 3; dy++) {
                int wx = ns ? x + i : x + (firstRow ? CELL_SIZE - 1 : 0);
                int wz = ns ? z + (firstRow ? CELL_SIZE - 1 : 0) : z + i;
                arch.set(wx, y + dy, wz, i == doorD ? Material.AIR : Material.IRON_BARS);
            }
        }
        placeBed(x + 1, y + 1, z + 1, doorFacing);
        arch.set(x + CELL_SIZE - 2, y + 1, z + 1, Material.BARREL);
        arch.set(x + CELL_SIZE - 2, y + 3, z + CELL_SIZE / 2, Material.LANTERN);
        signNoHologram(x + doorD, y + 2, z - 1, doorFacing.getOppositeFace(), "§7Cell", "§f#" + number, "", "");
    }

    // ---- Crates ----

    private void registerCrateLocations() {
        crateLocations.clear();
        int[] r = CRATES.room;
        int mid = alongCenter(CRATES.wall, r);
        int span = isNS(CRATES.wall) ? (r[2] - r[0]) : (r[3] - r[1]);
        int spacing = span / (CrateData.CRATES.size() + 1);
        int i = 0;
        int depthMid = isNS(CRATES.wall) ? (r[1] + r[3]) / 2 : (r[0] + r[2]) / 2;
        for (CrateData.Crate crate : CrateData.CRATES.values()) {
            i++;
            int along = (isNS(CRATES.wall) ? r[0] : r[1]) + spacing * i;
            int x = isNS(CRATES.wall) ? along : depthMid;
            int z = isNS(CRATES.wall) ? depthMid : along;
            crateLocations.put(new Location(world, x, Y + 2, z), crate.id);
        }
    }

    private void buildCrateHall() {
        int[] r = CRATES.room;
        arch.prisonHall(r[0], r[1], r[2], r[3], Y, 9, Material.POLISHED_DIORITE, Material.POLISHED_BLACKSTONE, 8);
        int labelAlong = alongCenter(CRATES.wall, r);
        BlockFont.write(world, "CRATES",
                isNS(CRATES.wall) ? labelAlong + BlockFont.width("CRATES") / 2 : r[0] + 1,
                Y + 9,
                isNS(CRATES.wall) ? r[1] + 1 : labelAlong - BlockFont.width("CRATES") / 2,
                labelAxis(CRATES.wall), Material.LIGHT_BLUE_CONCRETE);
        for (Map.Entry<Location, String> e : crateLocations.entrySet()) {
            Location l = e.getKey();
            CrateData.Crate crate = CrateData.CRATES.get(e.getValue());
            arch.set(l.getBlockX(), Y + 1, l.getBlockZ(), Material.CHISELED_DEEPSLATE);
            arch.set(l.getBlockX(), Y + 2, l.getBlockZ(), Material.ENDER_CHEST);
            sign(l.getBlockX(), Y + 3, l.getBlockZ() + 1, BlockFace.SOUTH, "§6§l" + crate.display, "Right-click", "with a key", "to open");
        }
        sign(r[0] + 1, Y + 2, r[1] + 2, BlockFace.EAST, "§6§lCRATES", "Keys drop from", "mining and", "fishing.");
    }

    // ---- PvP: The Yard and ward pits ----

    private void registerPvpZones() {
        pvpZones.clear();
        int[] r = YARD.room;
        pvpZones.add(new PvpZoneManager.Zone("The Yard", r[0], Y, r[1], r[2], Y + 10, r[3]));
        pvpZones.add(new PvpZoneManager.Zone("Hub PvP Lane", pvpLaneOrDefault()[0], Y, pvpLaneOrDefault()[1], pvpLaneOrDefault()[2], Y + 3, pvpLaneOrDefault()[3]));
        for (String rank : new String[]{"F", "M", "T"}) {
            RankMineData.Def d = RankMineData.RANKS.get(rank);
            if (d == null) continue;
            int[] p = pitRect(d);
            pvpZones.add(new PvpZoneManager.Zone(rank + "-Ward Pit", p[0], Y, p[1], p[2], Y + 8, p[3]));
        }
    }

    /** registerPvpZones can run before buildHub has computed pvpLane (bounds registration
     *  happens every boot; the hub itself is only built on first boot) — fall back to a
     *  sane default so /prisonadmin & friends never see a null zone on a restart. */
    private int[] pvpLaneOrDefault() {
        if (pvpLane != null) return pvpLane;
        int yardMid = alongCenter("S", YARD.ward);
        return new int[]{yardMid - 1, 7, yardMid + 1, HUB[3] - CORRIDOR_LEN - 1};
    }

    private void buildYard() { buildArena(YARD.room, "The Yard", true); }

    private void buildWardPits() {
        for (String rank : new String[]{"F", "M", "T"}) {
            RankMineData.Def d = RankMineData.RANKS.get(rank);
            if (d == null) continue;
            buildArena(pitRect(d), rank + "-Ward Pit", false);
        }
    }

    /** A pit tucked just outside its ward, on the side away from the mine/hub axis — offset
     *  along the "across" direction so it never collides with the ward's own corridor. */
    private int[] pitRect(RankMineData.Def d) {
        int[] w = wardRect(d);
        boolean ns = isNS(d.wall);
        int size = 20;
        if (ns) {
            int x1 = w[0] - size - 6, x2 = w[0] - 6;
            return new int[]{x1, w[1], x2, w[3]};
        } else {
            int z1 = w[1] - size - 6, z2 = w[1] - 6;
            return new int[]{w[0], z1, w[2], z2};
        }
    }

    private void buildArena(int[] r, String name, boolean grand) {
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(Material.RED_WOOL, Material.RED_CONCRETE, 15));
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
        int[] sp = sellSignSpot(d);
        sign(sp[0], Y + 2, sp[1], inwardFace(d.wall), "§a[Sell]", "Mine " + d.rank + " ores", "Right-click", "holding ore");
    }

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

    /**
     * Clads all four exterior faces of the mine's bedrock shell, not just two — so no raw
     * bedrock is ever visible from any approach angle, on any of the four walls a mine can
     * sit on. Orientation-agnostic by construction: it walls the whole perimeter uniformly.
     */
    private void cladMineFacade(RankMineData.Def d) {
        MineTheme mt = themeFor(d.rank);
        int height = d.y2 - d.y1;
        for (int x = d.x1 - 1; x <= d.x2 + 1; x++) {
            for (int z = d.z1 - 1; z <= d.z2 + 1; z++) {
                boolean onWall = x == d.x1 - 1 || x == d.x2 + 1 || z == d.z1 - 1 || z == d.z2 + 1;
                if (!onWall) continue;
                for (int y = 0; y <= height; y++) {
                    boolean pillar = ((Math.floorMod(x - d.x1, 10)) < 2) || ((Math.floorMod(z - d.z1, 10)) < 2);
                    boolean band = y == height / 3 || y == height / 3 + 1;
                    boolean trim = y == 0 || y == height;
                    Material mat = pillar ? mt.pillar
                            : trim ? mt.trim
                            : band ? (((x + z) % 2 == 0) ? mt.bandA : mt.bandB)
                            : arch.pick(Architect.WARD_INDUSTRIAL);
                    arch.set(x, d.y1 + y, z, mat);
                }
            }
        }
        for (int x = d.x1 - 1; x <= d.x2 + 1; x++) {
            for (int z = d.z1 - 1; z <= d.z2 + 1; z++) {
                arch.set(x, d.y2 + 1, z, mt.roof);
            }
        }
    }

    private void buildWard(RankMineData.Def d) {
        int[] w = wardRect(d);
        arch.prisonHall(w[0], w[1], w[2], w[3], Y, 9, Material.POLISHED_DEEPSLATE, Material.POLISHED_BLACKSTONE, 8);

        int gate = nearEdge(d.wall, mineRect(d));
        int center = alongCenter(d.wall, mineRect(d));
        boolean ns = isNS(d.wall);

        // Ender chests flanking the mine gate.
        int off = -directionSign(d.wall) * 2;
        if (ns) {
            arch.set(center - 5, Y + 1, gate + off, Material.ENDER_CHEST);
            arch.set(center + 5, Y + 1, gate + off, Material.ENDER_CHEST);
        } else {
            arch.set(gate + off, Y + 1, center - 5, Material.ENDER_CHEST);
            arch.set(gate + off, Y + 1, center + 5, Material.ENDER_CHEST);
        }

        // The mine gate archway, with the rank letter and "MINE" lettering above it, facing
        // whoever approaches from the ward — labelAxis(d.wall) guarantees this is never
        // mirrored/backwards regardless of which of the four walls this mine sits on.
        for (int a = -4; a <= 4; a++) {
            for (int dy = 1; dy <= 6; dy++) {
                boolean frame = Math.abs(a) == 4 || dy == 6;
                Material mat = frame ? Material.SMOOTH_QUARTZ : Material.IRON_BARS;
                if (ns) arch.set(center + a, Y + dy, gate, mat); else arch.set(gate, Y + dy, center + a, mat);
            }
        }
        for (int a = -5; a <= 5; a++) {
            Material mat = (a % 3 == 0) ? Material.IRON_BARS : Material.COBWEB;
            if (ns) arch.set(center + a, Y + 7, gate, mat); else arch.set(gate, Y + 7, center + a, mat);
        }
        int rankLabelX = ns ? center - 2 : gate;
        int rankLabelZ = ns ? gate : center - 2;
        int mineLabelX = ns ? center - BlockFont.width("MINE") / 2 : gate;
        int mineLabelZ = ns ? gate : center - BlockFont.width("MINE") / 2;
        if (ns) {
            BlockFont.write(world, d.rank, center - 2, Y + 15, gate, labelAxis(d.wall), Material.LIGHT_BLUE_CONCRETE);
            BlockFont.write(world, "MINE", center - BlockFont.width("MINE") / 2, Y + 26, gate, labelAxis(d.wall), Material.LIGHT_BLUE_CONCRETE);
        } else {
            BlockFont.write(world, d.rank, gate, Y + 15, center - 2, labelAxis(d.wall), Material.LIGHT_BLUE_CONCRETE);
            BlockFont.write(world, "MINE", gate, Y + 26, center - BlockFont.width("MINE") / 2, labelAxis(d.wall), Material.LIGHT_BLUE_CONCRETE);
        }

        sign(w[0] + 1, Y + 2, w[1] + 4, BlockFace.EAST, "§6§l" + d.rank + "-WARD", "Mine " + d.rank,
                "Rare: " + prettyName(d.rare), String.format("%.0f%% rare", rarePercentFor(d.rank)));
    }

    // ---- Fishing area ----

    private void registerPondBounds() {
        int[] r = FISHING.room;
        int x = r[0] + 10;
        for (FishingData.Pond pond : FishingData.PONDS.values()) {
            int size = 18 + pond.tier;
            pond.x1 = x; pond.x2 = Math.min(x + size, r[2] - 10);
            pond.z2 = r[3] - 10; pond.z1 = Math.max(pond.z2 - size, r[1] + 10);
            pond.y = Y;
            x = pond.x2 + 8;
            if (x > r[2] - 20) x = r[0] + 10; // wrap to a second row if we run out of width
        }
    }

    private void buildFishingArea() {
        int[] r = FISHING.room;
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(Material.GRASS_BLOCK, Material.MOSS_BLOCK, 12, Material.COARSE_DIRT, 6));
        arch.detailedWall(r[0], Y + 1, r[1], r[2], r[3], 3, Architect.FISHING_STONE,
                Material.CHISELED_SANDSTONE, Material.SMOOTH_SANDSTONE, 8);
        for (int x = r[0]; x <= r[2]; x += 8) { arch.set(x, Y + 4, r[1], Material.LANTERN); arch.set(x, Y + 4, r[3], Material.LANTERN); }
        for (FishingData.Pond pond : FishingData.PONDS.values()) digPond(pond);
        sign(r[0] + 2, Y + 2, r[3] - 2, BlockFace.SOUTH, "§b§lFISHING", "/fishing ponds", "/sellfish to", "cash in");
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

    // ---- Logging yard and farm: stacked further out past the fishing room ----

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
    }

    private void plantTree(int x, int y, int z) {
        for (int dy = 0; dy < 5; dy++) arch.set(x, y + dy, z, Material.OAK_LOG);
        for (int dy = 3; dy <= 5; dy++) {
            int radius = dy == 5 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && dy < 5) continue;
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && Math.random() < 0.5) continue;
                    arch.set(x + dx, y + dy, z + dz, Material.OAK_LEAVES);
                }
            }
        }
    }

    public void regrowTrees() {
        for (int[] base : treeBases) {
            if (world.getBlockAt(base[0], base[1] + 2, base[2]).getType() == Material.OAK_LOG) continue;
            plantTree(base[0], base[1], base[2]);
        }
    }

    private void buildAnimalPens() {
        int[] r = FARM;
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(Material.GRASS_BLOCK, Material.COARSE_DIRT, 12));
        arch.detailedWall(r[0], Y + 1, r[1], r[2], r[3], 3, Architect.FISHING_STONE,
                Material.STRIPPED_OAK_LOG, Material.SMOOTH_SANDSTONE, 8);
        int midX = (r[0] + r[2]) / 2;
        for (int z = r[1] + 1; z < r[3]; z++) arch.set(midX, Y + 1, z, Material.OAK_FENCE);

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
            cs.setDelay(400);
            cs.update(true, false);
        }
    }

    // ---- Doorways and corridors: carved/built last so nothing can brick them over ----

    private void carveAllDoorways() {
        Material f = Material.SMOOTH_QUARTZ;

        // Starter -> intake corridor -> hub, all sharing the same Z band (INTAKE_GATE_Z).
        arch.doorway(STARTER[2], Y + 1, INTAKE_GATE_Z, true, 3, 5, f);
        arch.doorway(INTAKE_WARD[0], Y + 1, INTAKE_GATE_Z, true, 3, 5, f);
        arch.doorway(INTAKE_WARD[2], Y + 1, INTAKE_GATE_Z, true, 3, 5, f);
        arch.doorway(HUB[0], Y + 1, INTAKE_GATE_Z, true, 3, 5, f);
        connector((INTAKE_WARD[2] + HUB[0]) / 2, INTAKE_GATE_Z - 3, INTAKE_GATE_Z + 3, 3);
        walkway(INTAKE_WARD[2] + 1, HUB[0] - 1, INTAKE_GATE_Z, 3);

        // Every mine: hub wall -> corridor -> ward -> mine, entirely self-contained.
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (d.rank.equals("FREE")) continue;
            buildMineConnection(d);
        }

        // Every special (fishing/crates/yard/cells): identical shape, off its own wall.
        for (MapLayout.Special sp : MapLayout.SPECIALS) buildSpecialConnection(sp);

        // Logging/farm stack north of the fishing room.
        int logMidAlong = (LOGGING[0] + LOGGING[2]) / 2;
        arch.doorway(logMidAlong, Y + 1, FISHING.room[1], false, 2, 4, Material.SMOOTH_SANDSTONE);
        arch.doorway(logMidAlong, Y + 1, LOGGING[3], false, 2, 4, Material.SMOOTH_SANDSTONE);
        connector(logMidAlong, LOGGING[3] + 1, FISHING.room[1] - 1, 2);

        int farmMidAlong = (FARM[0] + FARM[2]) / 2;
        arch.doorway(farmMidAlong, Y + 1, LOGGING[1], false, 2, 4, Material.SMOOTH_SANDSTONE);
        arch.doorway(farmMidAlong, Y + 1, FARM[3], false, 2, 4, Material.SMOOTH_SANDSTONE);
        connector(farmMidAlong, FARM[3] + 1, LOGGING[1] - 1, 2);

        // Ward pits.
        for (String rank : new String[]{"F", "M", "T"}) {
            RankMineData.Def d = RankMineData.RANKS.get(rank);
            if (d == null) continue;
            int[] w = wardRect(d);
            int[] p = pitRect(d);
            boolean ns = isNS(d.wall);
            int mid = alongCenter(d.wall, w);
            if (ns) {
                arch.doorway(w[0], Y + 1, mid, true, 1, 4, Material.CHISELED_DEEPSLATE);
                arch.doorway(p[2], Y + 1, mid, true, 1, 4, Material.CHISELED_DEEPSLATE);
                connector((w[0] + p[2]) / 2, mid - 1, mid + 1, 1); // tiny stub, real link below
                walkway(p[2] + 1, w[0] - 1, mid, 1);
            } else {
                arch.doorway(mid, Y + 1, w[1], false, 1, 4, Material.CHISELED_DEEPSLATE);
                arch.doorway(mid, Y + 1, p[3], false, 1, 4, Material.CHISELED_DEEPSLATE);
                connector(mid, p[3] + 1, w[1] - 1, 1);
            }
        }
    }

    /** Doorway + floored corridor + doorway, connecting the hub straight to one mine's ward,
     *  then the ward straight to the mine. Nothing here depends on which wall it's on. */
    private void buildMineConnection(RankMineData.Def d) {
        Material f = Material.SMOOTH_QUARTZ;
        int[] w = wardRect(d);
        boolean alongZ = doorAlongZ(d.wall);
        int center = alongCenter(d.wall, w);
        int hubEdge = switch (d.wall) { case "N" -> HUB[1]; case "S" -> HUB[3]; case "E" -> HUB[2]; default -> HUB[0]; };
        int wardOuter = nearEdge(oppositeWall(d.wall), w); // the ward edge touching the hub side
        arch.doorway(alongZ ? hubEdge : center, Y + 1, alongZ ? center : hubEdge, alongZ, 3, 5, f);
        arch.doorway(alongZ ? wardOuter : center, Y + 1, alongZ ? center : wardOuter, alongZ, 3, 5, f);
        if (alongZ) connector(center, hubEdge, wardOuter, 3); else walkway(Math.min(hubEdge, wardOuter), Math.max(hubEdge, wardOuter), center, 3);

        int gate = nearEdge(d.wall, mineRect(d));
        arch.doorway(alongZ ? gate : center, Y + 1, alongZ ? center : gate, alongZ, 1, 4, Material.CHISELED_DEEPSLATE);
        for (int a = -1; a <= 1; a++) for (int dy = 1; dy <= 3; dy++) {
            if (alongZ) arch.set(gate, Y + dy, center + a, Material.AIR); else arch.set(center + a, Y + dy, gate, Material.AIR);
        }
    }

    private void buildSpecialConnection(MapLayout.Special sp) {
        Material f = Material.SMOOTH_QUARTZ;
        boolean alongZ = doorAlongZ(sp.wall);
        int center = alongCenter(sp.wall, sp.ward);
        int hubEdge = switch (sp.wall) { case "N" -> HUB[1]; case "S" -> HUB[3]; case "E" -> HUB[2]; default -> HUB[0]; };
        int wardOuter = nearEdge(oppositeWall(sp.wall), sp.ward);
        int roomGate = nearEdge(sp.wall, sp.room);

        arch.doorway(alongZ ? hubEdge : center, Y + 1, alongZ ? center : hubEdge, alongZ, 3, 6, f);
        arch.doorway(alongZ ? wardOuter : center, Y + 1, alongZ ? center : wardOuter, alongZ, 3, 6, f);
        if (alongZ) connector(center, hubEdge, wardOuter, 3); else walkway(Math.min(hubEdge, wardOuter), Math.max(hubEdge, wardOuter), center, 3);
        arch.doorway(alongZ ? roomGate : center, Y + 1, alongZ ? center : roomGate, alongZ, 3, 5, f);

        // Small directory sign right at the hub-facing gate of every special room.
        int signAt = wardOuter;
        BlockFace face = inwardFace(sp.wall);
        if (alongZ) sign(signAt, Y + 2, center - 6, face, "§b§l" + sp.name, "", "", "");
        else sign(center - 6, Y + 2, signAt, face, "§b§l" + sp.name, "", "", "");
    }

    private static String oppositeWall(String wall) {
        return switch (wall) { case "N" -> "S"; case "S" -> "N"; case "E" -> "W"; default -> "E"; };
    }

    /** Road-style walkway: dark surface with quartz lane markings, iron-bar fencing, lamp posts. */
    private void walkway(int x1, int x2, int centreZ, int halfWidth) {
        if (x2 < x1) return;
        for (int x = x1; x <= x2; x++) {
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                boolean edge = Math.abs(dz) == halfWidth;
                boolean marking = dz == 0 && (x % 6) < 3;
                arch.set(x, Y, centreZ + dz, edge ? Material.STONE_BRICKS : marking ? Material.BLACK_CONCRETE : Material.SMOOTH_STONE);
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
        int minX = HUB[0], maxX = HUB[2], minZ = HUB[1], maxZ = HUB[3];
        for (int[] b : mineBounds.values()) {
            minX = Math.min(minX, b[0]); maxX = Math.max(maxX, b[3]);
            minZ = Math.min(minZ, b[2]); maxZ = Math.max(maxZ, b[5]);
        }
        for (int[] r : new int[][]{STARTER, INTAKE_WARD, FISHING.room, LOGGING, FARM, CRATES.room, YARD.room, CELLS.room}) {
            minX = Math.min(minX, r[0]); maxX = Math.max(maxX, r[2]);
            minZ = Math.min(minZ, r[1]); maxZ = Math.max(maxZ, r[3]);
        }
        minX -= 20; maxX += 20; minZ -= 20; maxZ += 20;
        world.getWorldBorder().setCenter((minX + maxX) / 2.0, (minZ + maxZ) / 2.0);
        world.getWorldBorder().setSize(Math.max(maxX - minX, maxZ - minZ) + 40);
    }

    private Material safeMaterial(String name) {
        try { return Material.valueOf(name); } catch (IllegalArgumentException e) { return Material.STONE; }
    }

    public void resetMine(String rank) {
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
