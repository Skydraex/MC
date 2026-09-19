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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Places every block in the prison.
 *
 * Geometry is never computed here — it comes from {@link MapLayout} and {@link RankMineData},
 * which are generated from tools/layout_gen.py and validated by tools/map_check.py. This class
 * only builds what those say.
 *
 * Shape of the map
 * ----------------
 * A compact hub (136x136) whose perimeter carries GATES ONLY. Each gate opens through a short
 * corridor into a ward. A mine's ward holds a cage lift down to its pit on the underground mine
 * level; a room gate's ward opens straight into its room. Because mine footprints never touch
 * the hub wall, the hub stays walkable however large the late mines get.
 *
 * Inside the hub, four quadrant buildings (cell wing, canteen, workout yard, commissary) sit
 * around a central watchtower plaza, linked by safe walkways. The open floor between them is
 * red wool: step off the path and PvP is live.
 *
 * Two rules this build holds to everywhere
 * ----------------------------------------
 *  1. No player ever sees bedrock or void. Bedrock exists only as a hidden backstop behind
 *     decorative cladding, and every platform gets an underside and a stair skirt.
 *  2. Nothing is placed where a player can end up inside it. Spawns, lift landings and reset
 *     evacuations all resolve to verified air above solid floor.
 */
public class WorldBuilder {

    public static final int Y = 95;                 // hub / surface floor height

    private static final int[] HUB = MapLayout.HUB;
    private static final int HUB_HALF = MapLayout.HUB_HALF;
    private static final int HUB_HEIGHT = 22;

    // Hub interior zoning, measured from the centre outward.
    private static final int PLAZA_R = 11;          // central watchtower plaza
    private static final int SPOKE_HALF = 6;        // half width of the four radial walkways
    private static final int BUILDING_IN = 14;      // quadrant buildings start here
    private static final int BUILDING_OUT = 50;     // ...and end here
    private static final int RING_IN = 56, RING_OUT = 65;   // perimeter walkway, in front of gates

    // Underground mine level.
    private static final int ORE_BOTTOM = MapLayout.MINE_ORE_BOTTOM;
    private static final int ORE_TOP = MapLayout.MINE_ORE_TOP;
    private static final int RIM_Y = MapLayout.MINE_RIM_Y;
    private static final int PIT_CEILING = RIM_Y + 14;

    private static final int CELL_TIERS = 3, CELL_TIER_H = 7;

    private final Plugin plugin;
    private final World world;
    private final Architect arch;

    private final Map<String, int[]> mineBounds = new LinkedHashMap<>();
    private final Map<Location, SellSignListener.SellSignData> sellSigns = new HashMap<>();
    private final Map<Location, String> crateLocations = new HashMap<>();
    private final List<PvpZoneManager.Zone> pvpZones = new ArrayList<>();
    private final Map<Integer, CellManager.CellRect> cellRects = new LinkedHashMap<>();
    private final List<int[]> treeBases = new ArrayList<>();

    /** Lift pads: pad location -> destination key ("A".."Z" to descend, "HUB" to return). */
    private final Map<Location, String> liftPads = new HashMap<>();

    private Location hubSpawn, starterSpawn;

    private record PendingSign(int x, int y, int z, BlockFace facing, String[] lines) { }
    private record PendingHologram(double x, double y, double z, String[] lines) { }

    private final List<PendingSign> signs = new ArrayList<>();
    private final List<PendingHologram> holograms = new ArrayList<>();

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
    public int[] getLoggingBounds() { return MapLayout.ground("LOGGING").area(); }
    public Map<Integer, CellManager.CellRect> getCellRects() { return cellRects; }
    public Map<Location, String> getLiftPads() { return liftPads; }

    // ==================================================================================
    // Registration — runs every boot, cheap, places no blocks
    // ==================================================================================

    public void registerBounds() {
        mineBounds.clear();
        sellSigns.clear();
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (!d.hasMine()) continue;
            // Kept in the historic {x1,y1,z1,x2,y2,z2} shape — every other class reads this.
            mineBounds.put(d.rank, new int[]{d.pit[0], d.oreBottom, d.pit[1],
                                             d.pit[2], d.oreTop, d.pit[3]});
            Map<Material, Double> prices = new HashMap<>();
            prices.put(safeMaterial(d.filler), d.fillerPrice);
            prices.put(safeMaterial(d.common), d.commonPrice);
            prices.put(safeMaterial(d.rare), d.rarePrice);
            int[] sp = sellSignSpot(d);
            sellSigns.put(new Location(world, sp[0], sp[1], sp[2]),
                    new SellSignListener.SellSignData(d.rank, prices));
        }
        registerPondBounds();
        registerCrateLocations();
        registerPvpZones();
        registerCellRects();
        registerLiftPads();

        hubSpawn = new Location(world, 0.5, Y + 1, PLAZA_R + 3.5, 180f, 0f);
        int[] st = MapLayout.STARTER;
        // Stand ON the bus floor, not in it, facing east toward the intake gate.
        starterSpawn = new Location(world, st[0] + 10.5, Y + 2, MapLayout.INTAKE_CENTRE + 0.5, -90f, 0f);
        world.setSpawnLocation(0, Y + 1, PLAZA_R + 3);
    }

    // ==================================================================================
    // Full build — first boot only
    // ==================================================================================

    public void buildAll() {
        signs.clear();
        holograms.clear();

        buildSurfacePlatform();
        buildHubShell();
        buildHubFloor();
        buildWatchtower();
        buildCellWing();
        buildCanteen();
        buildWorkoutYard();
        buildCommissary();

        for (MapLayout.Gate g : MapLayout.GATES) buildGate(g);
        for (MapLayout.Room r : MapLayout.ROOMS) buildRoom(r);

        buildStarterYard();
        buildGrounds();

        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (d.hasMine()) buildMinePit(d);
        }

        int linked = arch.relinkConnectables();
        plugin.getLogger().info("Prison built. Connection states applied to " + linked + " blocks.");
        writeMarker();
    }

    // ==================================================================================
    // Platforms — the map floats, so every walkable area needs an underside and a skirt
    // or players look straight into the void
    // ==================================================================================

    private void buildSurfacePlatform() {
        List<int[]> areas = new ArrayList<>();
        areas.add(pad(HUB, 3));
        for (MapLayout.Gate g : MapLayout.GATES) {
            areas.add(pad(g.corridor(), 3));
            areas.add(pad(g.ward(), 3));
        }
        for (MapLayout.Room r : MapLayout.ROOMS) areas.add(pad(r.room(), 4));
        areas.add(pad(MapLayout.STARTER, 5));
        areas.add(pad(MapLayout.STARTER_LINK, 3));
        areas.add(pad(MapLayout.GREEN, 5));
        for (MapLayout.Ground g : MapLayout.GROUNDS) {
            areas.add(pad(g.area(), 5));
            areas.add(pad(g.link(), 3));
        }
        for (int[] a : areas) slab(a, Y);
    }

    /** A platform with a finished underside and a stair skirt, so its edge reads as masonry. */
    private void slab(int[] r, int top) {
        for (int x = r[0]; x <= r[2]; x++) {
            for (int z = r[1]; z <= r[3]; z++) {
                arch.set(x, top - 1, z, arch.pick(Architect.PRISON_STONE));
                arch.set(x, top - 2, z, Material.DEEPSLATE);
                arch.set(x, top - 3, z, Material.DEEPSLATE);
                arch.set(x, top - 4, z, Material.DEEPSLATE);
                // The bedrock backstop is inset two blocks from every edge. Laid flush it showed
                // on the platform's cut face, which is the bedrock visible from the walkways.
                boolean interior = x > r[0] + 1 && x < r[2] - 1 && z > r[1] + 1 && z < r[3] - 1;
                if (interior) arch.set(x, top - 5, z, Material.BEDROCK);
            }
        }
        for (int x = r[0] - 1; x <= r[2] + 1; x++) {
            skirt(x, top - 1, r[1] - 1, BlockFace.SOUTH);
            skirt(x, top - 1, r[3] + 1, BlockFace.NORTH);
        }
        for (int z = r[1] - 1; z <= r[3] + 1; z++) {
            skirt(r[0] - 1, top - 1, z, BlockFace.EAST);
            skirt(r[2] + 1, top - 1, z, BlockFace.WEST);
        }
    }

    private void skirt(int x, int y, int z, BlockFace facing) {
        if (!world.getBlockAt(x, y, z).getType().isAir()) return;
        arch.placeStair(x, y, z, Material.DEEPSLATE_BRICK_STAIRS, facing, true);
        for (int dy = 1; dy <= 4; dy++) arch.set(x, y - dy, z, Material.DEEPSLATE);
    }

    private int[] pad(int[] r, int p) { return new int[]{r[0] - p, r[1] - p, r[2] + p, r[3] + p}; }

    // ==================================================================================
    // Hub shell
    // ==================================================================================

    private void buildHubShell() {
        arch.prisonWall(HUB[0], Y + 1, HUB[1], HUB[2], HUB[3], HUB_HEIGHT, 8);
        arch.barbedWireTop(HUB[0], HUB[1], HUB[2], HUB[3], Y + HUB_HEIGHT + 1);

        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                buildGuardTower(sx * (HUB_HALF - 3), sz * (HUB_HALF - 3));
            }
        }
        for (int x = HUB[0] + 1; x <= HUB[2] - 1; x++) {
            arch.placeSlab(x, Y + HUB_HEIGHT, HUB[1] + 1, Material.DEEPSLATE_BRICK_SLAB, true);
            arch.placeSlab(x, Y + HUB_HEIGHT, HUB[3] - 1, Material.DEEPSLATE_BRICK_SLAB, true);
        }
        for (int z = HUB[1] + 1; z <= HUB[3] - 1; z++) {
            arch.placeSlab(HUB[0] + 1, Y + HUB_HEIGHT, z, Material.DEEPSLATE_BRICK_SLAB, true);
            arch.placeSlab(HUB[2] - 1, Y + HUB_HEIGHT, z, Material.DEEPSLATE_BRICK_SLAB, true);
        }
    }

    private void buildGuardTower(int cx, int cz) {
        int h = HUB_HEIGHT + 8;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                for (int dy = 1; dy <= h; dy++) {
                    if (!edge) { arch.set(cx + dx, Y + dy, cz + dz, Material.AIR); continue; }
                    boolean window = dy > h - 5 && dy < h - 1;
                    arch.set(cx + dx, Y + dy, cz + dz,
                            window ? Material.GRAY_STAINED_GLASS_PANE : arch.pick(Architect.PRISON_STONE));
                }
            }
        }
        int ladderZ = cz + (cz < 0 ? 2 : -2);
        for (int dy = 1; dy <= h - 2; dy++) arch.set(cx, Y + dy, ladderZ, Material.LADDER);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) arch.set(cx + dx, Y + h + 1, cz + dz, Material.POLISHED_DEEPSLATE);
        }
        arch.set(cx, Y + h, cz, Material.SEA_LANTERN);
    }

    // ==================================================================================
    // Hub floor — walkways are safe, everything else is red wool and PvP is live
    // ==================================================================================

    private boolean inRing(int x, int z) {
        int m = Math.max(Math.abs(x), Math.abs(z));
        return m >= RING_IN && m <= RING_OUT;
    }

    private boolean onSpoke(int x, int z) {
        int m = Math.max(Math.abs(x), Math.abs(z));
        return m <= RING_OUT && (Math.abs(x) <= SPOKE_HALF || Math.abs(z) <= SPOKE_HALF);
    }

    private boolean inPlaza(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z)) <= PLAZA_R;
    }

    private boolean inQuadrantBuilding(int x, int z) {
        return Math.abs(x) >= BUILDING_IN && Math.abs(x) <= BUILDING_OUT
                && Math.abs(z) >= BUILDING_IN && Math.abs(z) <= BUILDING_OUT;
    }

    private void buildHubFloor() {
        for (int x = HUB[0] + 1; x <= HUB[2] - 1; x++) {
            for (int z = HUB[1] + 1; z <= HUB[3] - 1; z++) {
                boolean safe = inRing(x, z) || onSpoke(x, z) || inPlaza(x, z) || inQuadrantBuilding(x, z);
                if (safe) {
                    boolean centreLine = onSpoke(x, z) && !inPlaza(x, z)
                            && (Math.abs(x) <= 1 || Math.abs(z) <= 1);
                    arch.set(x, Y, z, centreLine ? Material.BLACK_CONCRETE
                            : ((x + z) % 9 == 0 ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE));
                } else {
                    arch.set(x, Y, z, Material.RED_WOOL);
                }
                for (int dy = 1; dy <= 3; dy++) arch.set(x, Y + dy, z, Material.AIR);
            }
        }
        // A red concrete kerb wherever safe floor meets PvP floor, so the boundary is obvious
        // at a glance rather than something you discover by dying.
        for (int x = HUB[0] + 2; x <= HUB[2] - 2; x++) {
            for (int z = HUB[1] + 2; z <= HUB[3] - 2; z++) {
                if (world.getBlockAt(x, Y, z).getType() != Material.RED_WOOL) continue;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    Material n = world.getBlockAt(x + d[0], Y, z + d[1]).getType();
                    if (n != Material.RED_WOOL && n != Material.RED_CONCRETE && !n.isAir()) {
                        arch.set(x, Y, z, Material.RED_CONCRETE);
                        break;
                    }
                }
            }
        }
        for (int d = PLAZA_R + 8; d <= RING_IN - 6; d += 14) {
            for (int[] p : new int[][]{{d, SPOKE_HALF}, {-d, SPOKE_HALF}, {d, -SPOKE_HALF}, {-d, -SPOKE_HALF},
                                       {SPOKE_HALF, d}, {SPOKE_HALF, -d}, {-SPOKE_HALF, d}, {-SPOKE_HALF, -d}}) {
                lampPost(p[0], p[1]);
            }
        }
    }

    private void lampPost(int x, int z) {
        for (int dy = 1; dy <= 4; dy++) arch.set(x, Y + dy, z, Material.POLISHED_BLACKSTONE_WALL);
        arch.set(x, Y + 5, z, Material.SEA_LANTERN);
        arch.placeSlab(x, Y + 6, z, Material.POLISHED_BLACKSTONE_SLAB, false);
    }

    private void buildWatchtower() {
        for (int dx = -PLAZA_R; dx <= PLAZA_R; dx++) {
            for (int dz = -PLAZA_R; dz <= PLAZA_R; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > PLAZA_R + 4) continue;
                arch.set(dx, Y, dz, ((dx + dz) % 2 == 0) ? Material.POLISHED_BLACKSTONE
                        : Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) arch.set(dx, Y + 1, dz, Material.CHISELED_POLISHED_BLACKSTONE);
        }
        for (int dy = 2; dy <= 16; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                    arch.set(dx, Y + dy, dz, corner ? Material.POLISHED_DEEPSLATE
                            : (dy % 4 == 0 ? Material.CHISELED_DEEPSLATE : Material.DEEPSLATE_BRICKS));
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) arch.placeSlab(dx, Y + 17, dz, Material.DEEPSLATE_BRICK_SLAB, false);
        }
        arch.set(0, Y + 18, 0, Material.SEA_LANTERN);

        signOn(0, Y + 2, -4, BlockFace.NORTH, "§b§lSKY PRISON", "N: Mines A-G", "E: Mines H-N", "/help for more");
        signOn(0, Y + 2, 4, BlockFace.SOUTH, "§6§lWHERE TO", "S: Mines O-U", "W: Mines V-Z", "Fishing / Crates");
        signOn(-4, Y + 2, 0, BlockFace.WEST, "§c§lRED FLOOR", "means PvP is ON.", "Stay on the paths", "to stay safe.");
        signOn(4, Y + 2, 0, BlockFace.EAST, "§a§lGREY PATHS", "are safe zones.", "Walkways, plaza", "and buildings.");
    }

    // ==================================================================================
    // Quadrant buildings
    // ==================================================================================

    private int[] quadrant(int sx, int sz) {
        return new int[]{
                Math.min(sx * BUILDING_IN, sx * BUILDING_OUT),
                Math.min(sz * BUILDING_IN, sz * BUILDING_OUT),
                Math.max(sx * BUILDING_IN, sx * BUILDING_OUT),
                Math.max(sz * BUILDING_IN, sz * BUILDING_OUT)};
    }

    private void quadrantShell(int[] r, int height, Architect.Palette wall, Material floor, Material band) {
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, new Architect.Palette(floor, new Material[]{floor}, new int[]{0}));
        arch.floorBand(r[0], r[1], r[2], r[3], Y, band);
        arch.clear(r[0] + 1, Y + 1, r[1] + 1, r[2] - 1, Y + height, r[3] - 1);
        arch.detailedWall(r[0], Y + 1, r[1], r[2], r[3], height, wall,
                Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_TILES, 6);
        arch.fillFlat(r[0], r[1], r[2], r[3], Y + height + 1, Architect.PRISON_STONE);
        arch.roofWithOverhang(r[0], r[1], r[2], r[3], Y + height + 1,
                Material.DEEPSLATE_TILES, Material.DEEPSLATE_BRICK_STAIRS);
        arch.recessedLightPanels(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, Y + height, 9);

        // Wall lanterns at head height as well. Recessed ceiling panels alone leave a tall room
        // gloomy at floor level — the cell wing in particular was pitch black to walk through.
        for (int x = r[0] + 4; x <= r[2] - 4; x += 6) {
            arch.set(x, Y + 4, r[1] + 1, Material.LANTERN);
            arch.set(x, Y + 4, r[3] - 1, Material.LANTERN);
        }
        for (int z = r[1] + 4; z <= r[3] - 4; z += 6) {
            arch.set(r[0] + 1, Y + 4, z, Material.LANTERN);
            arch.set(r[2] - 1, Y + 4, z, Material.LANTERN);
        }
    }

    /**
     * Cuts a doorway in the building face nearest the hub centre and runs a safe walkway spur
     * back to the spoke, so nothing is reached by crossing the PvP floor. Returns the door's
     * (x,z) so callers can hang a sign beside it.
     */
    private int[] quadrantDoor(int[] r) {
        int x = (Math.abs(r[0]) < Math.abs(r[2])) ? r[0] : r[2];
        int z = (r[1] + r[3]) / 2;
        arch.doorway(x, Y + 1, z, true, 2, 5, Material.POLISHED_DEEPSLATE);
        int step = x < 0 ? 1 : -1;
        for (int cx = x; Math.abs(cx) > SPOKE_HALF; cx += step) {
            for (int dz = -2; dz <= 2; dz++) arch.set(cx, Y, z + dz, Material.SMOOTH_STONE);
        }
        return new int[]{x, z};
    }

    // ---- Cell wing ---------------------------------------------------------------

    private int[] cellWing() { return quadrant(-1, -1); }

    private int cellSizeForTier(int tier) { return 6 + tier * 2; }   // 6x6, 8x8, 10x10

    /** Cell origins for one tier: two rows either side of a central corridor, stair bay skipped. */
    private List<int[]> cellSpots(int[] r, int size) {
        List<int[]> out = new ArrayList<>();
        int corridor = 6;
        int usable = (r[2] - r[0]) - 4;
        int perRow = Math.max(1, usable / size);
        int stairCol = perRow / 2;
        for (int col = 0; col < perRow; col++) {
            if (col == stairCol) continue;
            int x = r[0] + 2 + col * size;
            out.add(new int[]{x, r[1] + 2});
            out.add(new int[]{x, r[1] + 2 + size + corridor});
        }
        return out;
    }

    private void registerCellRects() {
        cellRects.clear();
        int[] r = cellWing();
        int n = 1;
        for (int tier = 0; tier < CELL_TIERS; tier++) {
            int y = Y + tier * CELL_TIER_H;
            int size = cellSizeForTier(tier);
            for (int[] s : cellSpots(r, size)) {
                cellRects.put(n, new CellManager.CellRect(n, s[0], s[1],
                        s[0] + size - 1, s[1] + size - 1, y));
                n++;
            }
        }
    }

    private void buildCellWing() {
        int[] r = cellWing();
        int height = CELL_TIERS * CELL_TIER_H + 4;
        quadrantShell(r, height, Architect.CELL_STONE, Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_TILES);
        int[] door = quadrantDoor(r);

        int n = 1;
        for (int tier = 0; tier < CELL_TIERS; tier++) {
            int y = Y + tier * CELL_TIER_H;
            int size = cellSizeForTier(tier);
            int usableW = (r[2] - r[0]) - 4;
            int stairColX = r[0] + 2 + ((Math.max(1, usableW / size)) / 2) * size;
            if (tier > 0) {
                for (int x = r[0] + 1; x <= r[2] - 1; x++) {
                    for (int z = r[1] + 1; z <= r[3] - 1; z++) {
                        // Leave the stairwell open. The previous version filled the whole floor,
                        // re-sealing the hole the tier below had just punched for its staircase —
                        // which is why the stairs went nowhere.
                        boolean stairwell = x >= stairColX - 2 && x <= stairColX + 5
                                && z >= r[1] + 2 && z <= r[1] + 3 + CELL_TIER_H + 1;
                        if (stairwell) continue;
                        arch.set(x, y, z, Material.POLISHED_DEEPSLATE);
                    }
                }
            }
            for (int[] s : cellSpots(r, size)) buildCell(s[0], y, s[1], size, n++);

            if (tier < CELL_TIERS - 1) buildCellStair(stairColX, y, r[1] + 3);
            // Corridor lighting: without it the upper tiers are pitch dark.
            for (int x = r[0] + 4; x <= r[2] - 4; x += 7) {
                arch.set(x, y + CELL_TIER_H - 2, r[1] + 2 + size + 2, Material.SEA_LANTERN);
            }
        }
        signOn(door[0] + 1, Y + 2, door[1] + 3, BlockFace.SOUTH,
                "§8§lCELL BLOCK", (n - 1) + " cells", "Higher tiers", "= bigger cells");
    }

    /** A real staircase between tiers. The previous build stacked three tiers with no way up. */
    private void buildCellStair(int x, int y, int z) {
        for (int step = 0; step < CELL_TIER_H; step++) {
            for (int dx = 0; dx < 4; dx++) {
                arch.set(x + dx, y + step, z + step, Material.POLISHED_DEEPSLATE);
                for (int dy = 1; dy <= 3; dy++) arch.set(x + dx, y + step + dy, z + step, Material.AIR);
            }
            arch.set(x - 1, y + step + 1, z + step, Material.POLISHED_DEEPSLATE_WALL);
            arch.set(x + 4, y + step + 1, z + step, Material.POLISHED_DEEPSLATE_WALL);
        }
        // Open the tier floor above so the stair arrives somewhere instead of into a ceiling.
        for (int dx = -1; dx <= 4; dx++) {
            for (int dz = 0; dz <= CELL_TIER_H; dz++) arch.set(x + dx, y + CELL_TIER_H, z + dz, Material.AIR);
        }
    }

    private void buildCell(int x, int y, int z, int size, int number) {
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                arch.set(x + dx, y, z + dz, Material.POLISHED_DEEPSLATE);
                arch.set(x + dx, y + CELL_TIER_H - 1, z + dz, Material.DEEPSLATE_TILES);
                for (int dy = 1; dy < CELL_TIER_H - 1; dy++) {
                    boolean wall = dx == 0 || dx == size - 1 || dz == 0 || dz == size - 1;
                    arch.set(x + dx, y + dy, z + dz, wall ? arch.pick(Architect.CELL_STONE) : Material.AIR);
                }
            }
        }
        int doorAt = size / 2;
        for (int i = 1; i < size - 1; i++) {
            for (int dy = 1; dy <= 3; dy++) {
                arch.set(x + i, y + dy, z + size - 1, i == doorAt ? Material.AIR : Material.IRON_BARS);
            }
        }
        placeBed(x + 1, y + 1, z + 1, BlockFace.SOUTH);
        arch.set(x + size - 2, y + 1, z + 1, Material.BARREL);
        arch.set(x + size - 2, y + 3, z + size - 2, Material.LANTERN);
        // Sign on the solid pier beside the door, so it has backing and survives a chunk reload.
        signOn(x + doorAt + 1, y + 2, z + size - 1, BlockFace.SOUTH,
                "§7Cell", "§f#" + number, size + "x" + size, "/cell claim");
    }

    // ---- Canteen -----------------------------------------------------------------

    private void buildCanteen() {
        int[] r = quadrant(1, -1);
        quadrantShell(r, 10, Architect.HUB_STONE, Material.SMOOTH_STONE, Material.POLISHED_ANDESITE);
        int[] door = quadrantDoor(r);

        int counterX = r[2] - 4;
        for (int z = r[1] + 3; z <= r[3] - 3; z++) {
            arch.set(counterX, Y + 1, z, Material.SMOOTH_QUARTZ);
            arch.placeSlab(counterX, Y + 2, z, Material.SMOOTH_QUARTZ_SLAB, false);
            if (Math.floorMod(z, 4) == 0) {
                arch.set(counterX + 1, Y + 1, z, Material.BLAST_FURNACE);
                arch.set(counterX + 1, Y + 3, z, Material.LANTERN);
            } else if (Math.floorMod(z, 4) == 2) {
                arch.set(counterX + 1, Y + 1, z, Material.SMOKER);
            }
        }
        for (int x = r[0] + 5; x <= counterX - 6; x += 7) {
            for (int z = r[1] + 5; z <= r[3] - 6; z += 6) buildMessTable(x, z);
        }
        signOn(door[0] + 1, Y + 2, door[1] + 3, BlockFace.SOUTH,
                "§e§lCANTEEN", "Mess hall.", "Chow is served", "at the counter.");
    }

    private void buildMessTable(int x, int z) {
        for (int dx = 0; dx < 4; dx++) arch.placeSlab(x + dx, Y + 1, z, Material.SMOOTH_STONE_SLAB, true);
        for (int dx = 0; dx < 4; dx += 3) {
            arch.placeStair(x + dx, Y + 1, z - 1, Material.DEEPSLATE_TILE_STAIRS, BlockFace.NORTH, false);
            arch.placeStair(x + dx, Y + 1, z + 1, Material.DEEPSLATE_TILE_STAIRS, BlockFace.SOUTH, false);
        }
        arch.set(x + 1, Y + 2, z, Material.LANTERN);
    }

    // ---- Workout yard ------------------------------------------------------------

    private void buildWorkoutYard() {
        int[] r = quadrant(-1, 1);
        // Open air: low walls and a caged perimeter, so it reads as a yard rather than a room.
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(
                Material.SMOOTH_STONE, Material.ANDESITE, 18, Material.POLISHED_ANDESITE, 10));
        arch.detailedWall(r[0], Y + 1, r[1], r[2], r[3], 4, Architect.HUB_STONE,
                Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_TILES, 6);
        for (int x = r[0]; x <= r[2]; x++) {
            for (int dy = 5; dy <= 7; dy++) {
                arch.set(x, Y + dy, r[1], Material.IRON_BARS);
                arch.set(x, Y + dy, r[3], Material.IRON_BARS);
            }
        }
        for (int z = r[1]; z <= r[3]; z++) {
            for (int dy = 5; dy <= 7; dy++) {
                arch.set(r[0], Y + dy, z, Material.IRON_BARS);
                arch.set(r[2], Y + dy, z, Material.IRON_BARS);
            }
        }
        int[] door = quadrantDoor(r);

        for (int x = r[0] + 5; x <= r[0] + 17; x += 6) {
            arch.set(x, Y + 1, r[1] + 5, Material.ANVIL);
            arch.set(x, Y + 1, r[1] + 7, Material.IRON_BLOCK);
            arch.set(x, Y + 2, r[1] + 7, Material.IRON_BARS);
        }
        for (int x = r[0] + 6; x <= r[2] - 6; x += 8) {
            for (int dy = 1; dy <= 4; dy++) arch.set(x, Y + dy, r[3] - 6, Material.IRON_BARS);
            arch.set(x, Y + 4, r[3] - 6, Material.IRON_BARS);
        }
        for (int x = r[0] + 3; x <= r[2] - 3; x++) {
            arch.set(x, Y, r[1] + 3, Material.ORANGE_TERRACOTTA);
            arch.set(x, Y, r[3] - 3, Material.ORANGE_TERRACOTTA);
        }
        for (int z = r[1] + 3; z <= r[3] - 3; z++) {
            arch.set(r[0] + 3, Y, z, Material.ORANGE_TERRACOTTA);
            arch.set(r[2] - 3, Y, z, Material.ORANGE_TERRACOTTA);
        }
        signOn(door[0] + 1, Y + 2, door[1] + 3, BlockFace.SOUTH,
                "§6§lWORKOUT YARD", "Weights, bars", "and a running", "track.");
    }

    // ---- Commissary --------------------------------------------------------------

    private void buildCommissary() {
        int[] r = quadrant(1, 1);
        quadrantShell(r, 10, Architect.HUB_STONE, Material.SMOOTH_STONE, Material.POLISHED_ANDESITE);
        int[] door = quadrantDoor(r);

        for (int z = r[1] + 5; z <= r[3] - 5; z += 5) {
            arch.set(r[0] + 4, Y + 1, z, Material.BARREL);
            arch.set(r[0] + 5, Y + 1, z, Material.SMOOTH_QUARTZ);
            arch.set(r[0] + 5, Y + 3, z, Material.LANTERN);
        }
        String[][] board = {
                {"§b§lGETTING STARTED", "Mine in Mine A,", "then /sell what", "you dig up."},
                {"§b§lRANKING UP", "/rankup when you", "can afford it.", "A to Z to Free."},
                {"§b§lTOKENS", "Earned by mining.", "Spend them at", "/enchant."},
                {"§b§lYOUR CELL", "/cell claim in", "the cell block.", "Build inside it."},
        };
        int z = r[1] + 6;
        for (String[] lines : board) {
            signOn(r[2] - 1, Y + 3, z, BlockFace.WEST, lines[0], lines[1], lines[2], lines[3]);
            z += 5;
        }
        signOn(door[0] + 1, Y + 2, door[1] + 3, BlockFace.SOUTH,
                "§a§lCOMMISSARY", "Shops and info.", "/shop  /prison", "");
    }

    // ==================================================================================
    // Gates, wards and cage lifts
    // ==================================================================================

    private void buildGate(MapLayout.Gate g) {
        boolean alongZ = !isNS(g.wall());
        int[] c = g.corridor(), w = g.ward();

        arch.fill(c[0], Y + 1, c[1], c[2], Y + 6, c[3], Architect.WARD_INDUSTRIAL);
        arch.fillFlat(c[0], c[1], c[2], c[3], Y, Architect.Palette.of(
                Material.SMOOTH_STONE, Material.POLISHED_ANDESITE, 20));
        if (alongZ) arch.clear(c[0], Y + 1, c[1] + 1, c[2], Y + 4, c[3] - 1);
        else arch.clear(c[0] + 1, Y + 1, c[1], c[2] - 1, Y + 4, c[3]);
        for (int x = c[0]; x <= c[2]; x++) {
            for (int z = c[1]; z <= c[3]; z++) arch.set(x, Y + 5, z, Material.SEA_LANTERN);
        }

        arch.prisonHall(w[0], w[1], w[2], w[3], Y, 9, Material.POLISHED_DEEPSLATE,
                Material.DEEPSLATE_TILES, 7);

        int hubEdge = hubEdgeOn(g.wall());
        int wardNear = hubFacingEdge(g.wall(), w);
        int half = MapLayout.GATE_W / 2;
        if (alongZ) {
            arch.doorway(hubEdge, Y + 1, g.centre(), true, half, 6, Material.SMOOTH_QUARTZ);
            arch.doorway(wardNear, Y + 1, g.centre(), true, half, 6, Material.SMOOTH_QUARTZ);
        } else {
            arch.doorway(g.centre(), Y + 1, hubEdge, false, half, 6, Material.SMOOTH_QUARTZ);
            arch.doorway(g.centre(), Y + 1, wardNear, false, half, 6, Material.SMOOTH_QUARTZ);
        }

        buildGateNameplate(g);

        // The intake ward is the only gate entered from BOTH sides: the hub through its corridor,
        // and the starter yard through its outer wall. Open that side too, or arrivals walk out
        // of the bus and straight into a dead end.
        if (g.kind().equals("intake")) {
            int outer = outerEdge(g.wall(), w);
            if (alongZ) arch.doorway(outer, Y + 1, g.centre(), true, half, 6, Material.SMOOTH_QUARTZ);
            else arch.doorway(g.centre(), Y + 1, outer, false, half, 6, Material.SMOOTH_QUARTZ);
        }

        if (g.kind().equals("mine")) buildMineWard(g);
        else if (g.kind().equals("intake")) buildIntakeWard(g);
    }

    /**
     * The name of wherever a gate leads, written above it on the HUB-facing side, so from the
     * plaza you can read where each of the thirty gates goes. Previously the only labels lived
     * inside the wards, which meant every gate looked identical from the hub.
     *
     * The backing panel goes up first and the glyph plane sits one block proud of it, facing
     * into the hub — nothing is ever placed in front of the letters. (The old starter banner
     * was written into a wall and then backed on BOTH sides, which entombed it.)
     */
    /**
     * Labels a gate on the HUB-facing side, so from the plaza you can read where each of the
     * thirty gates goes.
     *
     * Two hard constraints learned the hard way:
     *  - Gates sit 12 blocks apart and BlockFont needs 6 blocks per character, so TWO characters
     *    is the absolute maximum before neighbouring labels collide. "MINE A" was never going to
     *    fit; the old code silently truncated room names to nonsense like "CRA".
     *  - BlockFont treats y as the glyph's TOP row and draws DOWNWARD. Every backing panel here
     *    used to be built upward from that point, leaving the bottom four rows of each label with
     *    no wall behind them — which is why letters looked doubled and seemed to float.
     *
     * So: a big rank letter for mines, and a hologram over every gate carrying the full name.
     */
    private void buildGateNameplate(MapLayout.Gate g) {
        boolean alongZ = !isNS(g.wall());
        int hubEdge = hubEdgeOn(g.wall());
        int inward = -outwardSign(g.wall());
        int topY = Y + 15;                       // glyph top row; glyphs run down to topY-6

        if (g.kind().equals("mine")) {
            String label = g.name();             // one character, always fits the pitch
            int panelW = BlockFont.width(label);

            for (int a = -panelW / 2 - 2; a <= panelW / 2 + 2; a++) {
                for (int y = topY - 8; y <= topY + 2; y++) {
                    int px = alongZ ? hubEdge : g.centre() + a;
                    int pz = alongZ ? g.centre() + a : hubEdge;
                    arch.set(px, y, pz, arch.pick(Architect.PRISON_STONE));
                }
            }
            // Start coordinate depends on which way the axis advances: POS_* runs forward from
            // the left edge, NEG_* runs backward from the right edge.
            // Start where the reader sees the first letter: the low end for the axes that run
            // positive, the high end for those that run negative.
            int startAlong = switch (g.wall()) {
                case "N", "E" -> g.centre() - panelW / 2;
                default -> g.centre() + panelW / 2;     // S and W run negatively
            };
            int gx = alongZ ? hubEdge + inward : startAlong;
            int gz = alongZ ? startAlong : hubEdge + inward;
            BlockFont.write(world, label, gx, topY, gz, nameplateAxis(g.wall()), Material.LIGHT_BLUE_CONCRETE);
        }

        // Full name in floating text just inside the gate — readable from anywhere in the hub,
        // and it always turns to face you, which no block-letter label can do.
        String full = switch (g.kind()) {
            case "mine" -> "§b§lMINE " + g.name();
            case "intake" -> "§8§lINTAKE";
            default -> "§6§l" + g.name();
        };
        String sub = switch (g.name()) {
            case "FISHING" -> "§7Ponds, logging and farm";
            case "CRATES" -> "§7Open crates with keys";
            case "YARD" -> "§7PvP arena";
            case "INTAKE" -> "§7The way you came in";
            default -> "§7Cage lift to the pit";
        };
        int hx = alongZ ? hubEdge + inward * 3 : g.centre();
        int hz = alongZ ? g.centre() : hubEdge + inward * 3;
        hologramAt(hx + 0.5, Y + 6.0, hz + 0.5, full, sub);

        for (int side : new int[]{-1, 1}) {
            int a = side * (MapLayout.GATE_W / 2 + 2);
            int px = alongZ ? hubEdge + inward : g.centre() + a;
            int pz = alongZ ? g.centre() + a : hubEdge + inward;
            arch.set(px, Y + 5, pz, Material.LANTERN);
        }
    }

    /** A nameplate must read correctly from inside the hub, whichever wall carries it. */
    private BlockFont.Axis nameplateAxis(String wall) {
        // Text runs toward the reader's right. Standing in the hub you face the wall, so:
        // north wall -> facing north -> right is +X; east wall -> facing east -> right is +Z.
        return switch (wall) {
            case "N" -> BlockFont.Axis.POS_X;
            case "S" -> BlockFont.Axis.NEG_X;
            case "E" -> BlockFont.Axis.POS_Z;
            default -> BlockFont.Axis.NEG_Z;
        };
    }

    /** A mine ward holds the cage: a barred car with a lodestone plate that drops you to the pit. */
    private void buildMineWard(MapLayout.Gate g) {
        RankMineData.Def d = RankMineData.RANKS.get(g.name());
        if (d == null || !d.hasMine()) return;
        int[] w = g.ward();
        int cx = (w[0] + w[2]) / 2, cz = (w[1] + w[3]) / 2;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                boolean entrance = dx == 0 && dz == -2;
                arch.set(cx + dx, Y, cz + dz, Material.POLISHED_BLACKSTONE);
                for (int dy = 1; dy <= 4; dy++) {
                    arch.set(cx + dx, Y + dy, cz + dz,
                            (edge && !entrance) ? Material.IRON_BARS : Material.AIR);
                }
                if (edge) arch.set(cx + dx, Y + 5, cz + dz, Material.POLISHED_BLACKSTONE);
            }
        }
        arch.set(cx, Y + 5, cz, Material.SEA_LANTERN);
        arch.set(cx, Y, cz, Material.LODESTONE);

        signOn(cx + 2, Y + 2, cz + 2, BlockFace.SOUTH, "§b§lMINE " + d.rank,
                "Stand on the plate", "to descend.", "");
        signOn(cx - 2, Y + 2, cz + 2, BlockFace.SOUTH, "§6Ore", prettyName(d.common),
                "Rare: " + prettyName(d.rare), String.format("%.0f%% rare", rarePercentFor(d.rank)));
    }

    private void buildIntakeWard(MapLayout.Gate g) {
        int[] w = g.ward();
        signOn(w[0] + 2, Y + 2, (w[1] + w[3]) / 2, BlockFace.EAST,
                "§8§lINTAKE", "Welcome to", "Sky Prison.", "Head east.");
    }

    public Location wardenNpcLocation() {
        int[] w = MapLayout.gate("INTAKE").ward();
        return new Location(world, w[0] + 3.5, Y + 1, (w[1] + w[3]) / 2.0 - 3.5);
    }

    public Location quartermasterNpcLocation() {
        int[] w = MapLayout.gate("INTAKE").ward();
        return new Location(world, w[0] + 3.5, Y + 1, (w[1] + w[3]) / 2.0 + 3.5);
    }

    // ==================================================================================
    // Room gates
    // ==================================================================================

    private void buildRoom(MapLayout.Room room) {
        int[] r = room.room();
        boolean alongZ = !isNS(room.wall());
        int nearEdge = hubFacingEdge(room.wall(), r);
        int centre = alongZ ? (r[1] + r[3]) / 2 : (r[0] + r[2]) / 2;

        switch (room.name()) {
            case "CRATES" -> buildCrateHall(r);
            case "YARD" -> buildPvpYard(r);
            default -> buildFishingLobby(r);
        }
        if (alongZ) arch.doorway(nearEdge, Y + 1, centre, true, 3, 6, Material.SMOOTH_QUARTZ);
        else arch.doorway(centre, Y + 1, nearEdge, false, 3, 6, Material.SMOOTH_QUARTZ);
    }

    private void buildCrateHall(int[] r) {
        arch.prisonHall(r[0], r[1], r[2], r[3], Y, 10, Material.POLISHED_DIORITE,
                Material.POLISHED_BLACKSTONE, 8);
        for (Map.Entry<Location, String> e : crateLocations.entrySet()) {
            Location l = e.getKey();
            CrateData.Crate crate = CrateData.CRATES.get(e.getValue());
            if (crate == null) continue;
            int x = l.getBlockX(), z = l.getBlockZ();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) arch.set(x + dx, Y + 1, z + dz, Material.POLISHED_BLACKSTONE);
            }
            arch.set(x, Y + 2, z, Material.CHISELED_DEEPSLATE);
            arch.set(x, Y + 3, z, Material.ENDER_CHEST);
            arch.set(x - 1, Y + 3, z, Material.LANTERN);
            arch.set(x + 1, Y + 3, z, Material.LANTERN);
            // A hologram, not a sign: nothing solid backs a plinth top, and an unsupported wall
            // sign would be culled on the next chunk load.
            hologramAt(x + 0.5, Y + 4.6, z + 0.5, "§6§l" + crate.display, "§7Right-click with a key");
        }
    }

    private void buildPvpYard(int[] r) {
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(
                Material.RED_WOOL, Material.RED_CONCRETE, 14));
        arch.detailedWall(r[0], Y + 1, r[1], r[2], r[3], 7, Architect.PRISON_STONE,
                Material.POLISHED_BLACKSTONE, Material.RED_CONCRETE, 6);
        for (int x = r[0]; x <= r[2]; x++) {
            for (int dy = 8; dy <= 10; dy++) {
                arch.set(x, Y + dy, r[1], Material.IRON_BARS);
                arch.set(x, Y + dy, r[3], Material.IRON_BARS);
            }
        }
        int cx = (r[0] + r[2]) / 2, cz = (r[1] + r[3]) / 2;
        for (int[] o : new int[][]{{-9, -9}, {9, -9}, {-9, 9}, {9, 9}, {0, 0}}) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    for (int dy = 1; dy <= 3; dy++) {
                        arch.set(cx + o[0] + dx, Y + dy, cz + o[1] + dz,
                                shell ? Material.POLISHED_BLACKSTONE_BRICKS : Material.AIR);
                    }
                }
            }
        }
        hologramAt(cx + 0.5, Y + 5.0, cz + 0.5, "§c§lTHE YARD", "§7PvP — you drop everything on death");
    }

    private void buildFishingLobby(int[] r) {
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(
                Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, 20));
        arch.detailedWall(r[0], Y + 1, r[1], r[2], r[3], 6, Architect.FISHING_STONE,
                Material.CHISELED_SANDSTONE, Material.SMOOTH_SANDSTONE, 6);
        arch.clear(r[0] + 1, Y + 1, r[1] + 1, r[2] - 1, Y + 5, r[3] - 1);
        // Open the far side onto the grounds.
        arch.doorway((r[0] + r[2]) / 2, Y + 1, r[1], false, 4, 6, Material.CHISELED_SANDSTONE);
        signOn(r[0] + 2, Y + 2, r[3] - 1, BlockFace.SOUTH, "§b§lTHE GROUNDS",
                "Ponds, logging", "and the farm", "are through here.");
    }

    // ==================================================================================
    // The grounds: ponds, logging and farm around one shared green
    // ==================================================================================

    private void buildGrounds() {
        int[] gr = MapLayout.GREEN;
        arch.fillFlat(gr[0], gr[1], gr[2], gr[3], Y, Architect.Palette.of(
                Material.GRASS_BLOCK, Material.MOSS_BLOCK, 14, Material.PODZOL, 6));
        for (int x = gr[0]; x <= gr[2]; x++) {
            arch.set(x, Y + 1, gr[1], Material.MOSSY_COBBLESTONE_WALL);
            arch.set(x, Y + 1, gr[3], Material.MOSSY_COBBLESTONE_WALL);
        }
        for (int z = gr[1]; z <= gr[3]; z++) {
            arch.set(gr[0], Y + 1, z, Material.MOSSY_COBBLESTONE_WALL);
            arch.set(gr[2], Y + 1, z, Material.MOSSY_COBBLESTONE_WALL);
        }
        int cgx = (gr[0] + gr[2]) / 2, cgz = (gr[1] + gr[3]) / 2;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 6) continue;
                arch.set(cgx + dx, Y, cgz + dz, Material.MOSSY_COBBLESTONE);
            }
        }
        for (int dy = 1; dy <= 4; dy++) arch.set(cgx, Y + dy, cgz, Material.MOSSY_COBBLESTONE_WALL);
        arch.set(cgx, Y + 5, cgz, Material.SEA_LANTERN);

        for (MapLayout.Ground g : MapLayout.GROUNDS) {
            carveLink(g.link());
            switch (g.name()) {
                case "PONDS" -> buildPonds(g.area());
                case "LOGGING" -> buildLoggingYard(g.area());
                default -> buildFarm(g.area());
            }
        }
    }

    private void carveLink(int[] l) {
        arch.fillFlat(l[0], l[1], l[2], l[3], Y, Architect.Palette.of(
                Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, 25));
        for (int x = l[0]; x <= l[2]; x++) {
            for (int z = l[1]; z <= l[3]; z++) {
                for (int dy = 1; dy <= 5; dy++) arch.set(x, Y + dy, z, Material.AIR);
            }
        }
    }

    private void buildPonds(int[] r) {
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(
                Material.GRASS_BLOCK, Material.MOSS_BLOCK, 12, Material.COARSE_DIRT, 6));
        arch.detailedWall(r[0], Y + 1, r[1], r[2], r[3], 4, Architect.FISHING_STONE,
                Material.CHISELED_SANDSTONE, Material.SMOOTH_SANDSTONE, 8);
        for (FishingData.Pond pond : FishingData.PONDS.values()) digPond(pond);
        signOn(r[0] + 2, Y + 2, r[1] + 1, BlockFace.NORTH, "§b§lFISHING PONDS",
                "/fishing to browse", "/sellfish to", "cash in.");
    }

    private void registerPondBounds() {
        int[] r = MapLayout.ground("PONDS").area();
        final int SIZE = 8, PITCH_X = 12, PITCH_Z = 13, MARGIN = 4;
        int x = r[0] + MARGIN, z = r[1] + MARGIN;
        for (FishingData.Pond pond : FishingData.PONDS.values()) {
            if (x + SIZE > r[2] - MARGIN) { x = r[0] + MARGIN; z += PITCH_Z; }
            pond.x1 = x; pond.x2 = x + SIZE;
            pond.z1 = z; pond.z2 = z + SIZE;
            pond.y = Y;
            x += PITCH_X;
        }
    }

    private void digPond(FishingData.Pond pond) {
        for (int x = pond.x1; x <= pond.x2; x++) {
            for (int z = pond.z1; z <= pond.z2; z++) {
                boolean rim = x == pond.x1 || x == pond.x2 || z == pond.z1 || z == pond.z2;
                if (rim) { arch.set(x, Y, z, Material.SMOOTH_SANDSTONE); continue; }
                arch.set(x, Y, z, Material.WATER);
                arch.set(x, Y - 1, z, Material.WATER);
                arch.set(x, Y - 2, z, tierBed(pond.tier));
            }
        }
        hologramAt((pond.x1 + pond.x2) / 2.0 + 0.5, Y + 2.2, (pond.z1 + pond.z2) / 2.0 + 0.5,
                "§b§l" + pond.name, "§7Tier " + pond.tier + " · level " + pond.requiredLevel);
    }

    private Material tierBed(int tier) {
        if (tier <= 2) return Material.GRAVEL;
        if (tier <= 4) return Material.SAND;
        if (tier <= 6) return Material.PRISMARINE;
        if (tier <= 8) return Material.DARK_PRISMARINE;
        return Material.SCULK;
    }

    private void buildLoggingYard(int[] r) {
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(
                Material.GRASS_BLOCK, Material.COARSE_DIRT, 10, Material.PODZOL, 8));
        arch.detailedWall(r[0], Y + 1, r[1], r[2], r[3], 4, Architect.FISHING_STONE,
                Material.STRIPPED_OAK_LOG, Material.SMOOTH_SANDSTONE, 10);
        treeBases.clear();
        for (int x = r[0] + 8; x <= r[2] - 8; x += 9) {
            for (int z = r[1] + 8; z <= r[3] - 8; z += 9) treeBases.add(new int[]{x, Y + 1, z});
        }
        for (int[] b : treeBases) plantTree(b[0], b[1], b[2]);
        signOn(r[0] + 2, Y + 2, r[3] - 1, BlockFace.SOUTH, "§2§lLOGGING YARD",
                "Chop the trees.", "They regrow", "over time.");
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

    private void buildFarm(int[] r) {
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(
                Material.GRASS_BLOCK, Material.COARSE_DIRT, 12));
        arch.detailedWall(r[0], Y + 1, r[1], r[2], r[3], 4, Architect.FISHING_STONE,
                Material.STRIPPED_OAK_LOG, Material.SMOOTH_SANDSTONE, 8);

        // Two pens sharing a central fence line, with real gates. Architect.relinkConnectables()
        // computes the fence joins after the build — without it each post stands alone and
        // animals (and players) walk straight between them.
        int midX = (r[0] + r[2]) / 2;
        int penZ1 = r[1] + 4, penZ2 = r[3] - 4;
        for (int z = penZ1; z <= penZ2; z++) arch.set(midX, Y + 1, z, Material.OAK_FENCE);
        for (int[] pen : new int[][]{{r[0] + 4, midX}, {midX, r[2] - 4}}) {
            for (int x = pen[0]; x <= pen[1]; x++) {
                arch.set(x, Y + 1, penZ1, Material.OAK_FENCE);
                arch.set(x, Y + 1, penZ2, Material.OAK_FENCE);
            }
            for (int z = penZ1; z <= penZ2; z++) {
                arch.set(pen[0], Y + 1, z, Material.OAK_FENCE);
                arch.set(pen[1], Y + 1, z, Material.OAK_FENCE);
            }
            arch.set((pen[0] + pen[1]) / 2, Y + 1, penZ2, Material.OAK_FENCE_GATE);
        }
        placeAnimalSpawner(r[0] + (midX - r[0]) / 2, Y + 1, (penZ1 + penZ2) / 2, EntityType.COW);
        placeAnimalSpawner(midX + (r[2] - midX) / 2, Y + 1, (penZ1 + penZ2) / 2, EntityType.PIG);

        int bx = midX - 6, bz = r[1] + 1;
        arch.detailedWall(bx, Y + 1, bz, bx + 12, bz + 2, 5, Architect.Palette.of(
                        Material.SPRUCE_PLANKS, Material.STRIPPED_SPRUCE_LOG, 30),
                Material.SPRUCE_LOG, Material.SPRUCE_PLANKS, 4);
        arch.roofWithOverhang(bx, bz, bx + 12, bz + 2, Y + 7,
                Material.DARK_OAK_PLANKS, Material.DARK_OAK_STAIRS);
        signOn(r[0] + 2, Y + 2, r[1] + 1, BlockFace.NORTH, "§6§lFARM",
                "Cows west,", "pigs east.", "Mind the gates.");
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

    // ==================================================================================
    // Starter yard and prison bus
    // ==================================================================================

    private void buildStarterYard() {
        int[] r = MapLayout.STARTER;
        arch.fillFlat(r[0], r[1], r[2], r[3], Y, Architect.Palette.of(
                Material.GRAY_CONCRETE_POWDER, Material.GRAVEL, 22, Material.ANDESITE, 10));
        arch.prisonWall(r[0], Y + 1, r[1], r[2], r[3], 10, 6);
        arch.barbedWireTop(r[0], r[1], r[2], r[3], Y + 11);
        arch.clear(r[0] + 1, Y + 1, r[1] + 1, r[2] - 1, Y + 10, r[3] - 1);

        // The banner stands on its own free-standing plinth, clear of the yard wall, with the
        // glyph planes one block proud on each face. The previous build wrote the glyphs INTO
        // the wall and then filled backing on both sides of them, entombing the text.
        int bannerX = r[2] - 8;
        int midZ = (r[1] + r[3]) / 2;
        int nameW = BlockFont.width("SKY PRISON");
        // Glyph top row is Y+15 and BlockFont draws DOWNWARD, so the letters occupy Y+9..Y+15.
        // The panel has to cover that whole span: backing only the top of it (as the first
        // version did) left the lower rows see-through, so the two mirrored faces showed
        // through each other and the text looked doubled.
        for (int z = midZ - nameW / 2 - 2; z <= midZ + nameW / 2 + 2; z++) {
            for (int dy = 7; dy <= 17; dy++) arch.set(bannerX, Y + dy, z, arch.pick(Architect.PRISON_STONE));
            arch.set(bannerX, Y + 6, z, Material.POLISHED_BLACKSTONE);
            arch.set(bannerX, Y + 18, z, Material.POLISHED_BLACKSTONE);
            for (int dy = 1; dy <= 4; dy++) {
                if (Math.floorMod(z, 6) == 0) arch.set(bannerX, Y + dy, z, Material.POLISHED_BLACKSTONE_WALL);
            }
        }
        BlockFont.write(world, "SKY PRISON", bannerX - 1, Y + 15, midZ - nameW / 2,
                BlockFont.Axis.POS_Z, Material.LIGHT_BLUE_CONCRETE);
        BlockFont.write(world, "SKY PRISON", bannerX + 1, Y + 15, midZ + nameW / 2,
                BlockFont.Axis.NEG_Z, Material.LIGHT_BLUE_CONCRETE);

        buildPrisonBus(r[0] + 6, Y + 1, MapLayout.INTAKE_CENTRE);

        int[] link = MapLayout.STARTER_LINK;
        arch.fillFlat(link[0], link[1], link[2], link[3], Y, Architect.Palette.of(
                Material.SMOOTH_STONE, Material.POLISHED_ANDESITE, 20));
        for (int x = link[0]; x <= link[2]; x++) {
            for (int z = link[1]; z <= link[3]; z++) {
                for (int dy = 1; dy <= 5; dy++) arch.set(x, Y + dy, z, Material.AIR);
            }
            if ((x - link[0]) % 6 == 0) {
                arch.set(x, Y + 1, link[1] - 1, Material.POLISHED_BLACKSTONE_WALL);
                arch.set(x, Y + 2, link[1] - 1, Material.LANTERN);
                arch.set(x, Y + 1, link[3] + 1, Material.POLISHED_BLACKSTONE_WALL);
                arch.set(x, Y + 2, link[3] + 1, Material.LANTERN);
            }
        }
        arch.doorway(r[2], Y + 1, MapLayout.INTAKE_CENTRE, true, 3, 5, Material.SMOOTH_QUARTZ);
    }

    /**
     * The prison bus: cab, windowed passenger bay, wheels, and a rear door facing the prison.
     *
     * Players spawn standing on its floor at Y+2. The old build spawned them at Y+1, which is
     * the floor block itself, so arrivals materialised inside it — and it never set a yaw, so
     * they faced away from the prison as they stepped off.
     */
    private void buildPrisonBus(int x, int y, int z) {
        int len = 16, halfW = 2;
        for (int dx = 0; dx < len; dx++) {
            for (int dz = -halfW; dz <= halfW; dz++) {
                arch.set(x + dx, y, z + dz, Material.POLISHED_BLACKSTONE);      // chassis / floor
                for (int dy = 1; dy <= 3; dy++) arch.set(x + dx, y + dy, z + dz, Material.AIR);
                arch.set(x + dx, y + 4, z + dz, Material.GRAY_CONCRETE);        // roof
            }
            boolean window = dx > 2 && dx < len - 2 && dx % 2 == 0;
            for (int dy = 1; dy <= 3; dy++) {
                Material side = (dy == 2 && window) ? Material.GRAY_STAINED_GLASS_PANE : Material.GRAY_CONCRETE;
                arch.set(x + dx, y + dy, z - halfW, side);
                arch.set(x + dx, y + dy, z + halfW, side);
            }
            if (dx % 4 == 2) arch.set(x + dx, y + 3, z, Material.LANTERN);
        }
        // Cab at the west end.
        for (int dz = -halfW; dz <= halfW; dz++) {
            for (int dy = 1; dy <= 3; dy++) arch.set(x, y + dy, z + dz, Material.GRAY_CONCRETE);
        }
        arch.set(x, y + 2, z, Material.GLASS_PANE);
        arch.set(x - 1, y + 2, z - 1, Material.REDSTONE_LAMP);
        arch.set(x - 1, y + 2, z + 1, Material.REDSTONE_LAMP);
        // Rear door, east end, facing the prison — this is the way out.
        for (int dz = -halfW; dz <= halfW; dz++) {
            for (int dy = 1; dy <= 3; dy++) {
                arch.set(x + len - 1, y + dy, z + dz,
                        (Math.abs(dz) <= 1 && dy <= 3) ? Material.AIR : Material.IRON_BARS);
            }
        }
        // Step down to the yard so you walk out rather than drop.
        for (int dz = -1; dz <= 1; dz++) arch.set(x + len, y - 1, z + dz, Material.POLISHED_BLACKSTONE);
        for (int dx : new int[]{2, len - 5}) {
            for (int dz : new int[]{-halfW - 1, halfW + 1}) {
                arch.set(x + dx, y, z + dz, Material.BLACK_CONCRETE);
                arch.set(x + dx + 1, y, z + dz, Material.BLACK_CONCRETE);
            }
        }
        hologramAt(x + len + 1.5, y + 2.5, z + 0.5, "§8§lINTAKE", "§7Step off and head east");
    }

    // ==================================================================================
    // Mine level
    // ==================================================================================

    /** Themed dressing so mines do not all read the same the whole way from A to Z. */
    private record MineTheme(Material pillar, Material trim, Material band, Material floor) { }

    private static final MineTheme[] MINE_THEMES = {
            new MineTheme(Material.COBBLESTONE, Material.STONE_BRICKS, Material.ANDESITE, Material.SMOOTH_STONE),
            new MineTheme(Material.POLISHED_ANDESITE, Material.SMOOTH_STONE, Material.GRAY_TERRACOTTA, Material.POLISHED_ANDESITE),
            new MineTheme(Material.POLISHED_BASALT, Material.SMOOTH_QUARTZ, Material.CYAN_TERRACOTTA, Material.SMOOTH_QUARTZ),
            new MineTheme(Material.CUT_COPPER, Material.WAXED_CUT_COPPER, Material.ORANGE_TERRACOTTA, Material.CUT_COPPER),
            new MineTheme(Material.POLISHED_DEEPSLATE, Material.CHISELED_DEEPSLATE, Material.DEEPSLATE_TILES, Material.DEEPSLATE_BRICKS),
            new MineTheme(Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, Material.GILDED_BLACKSTONE, Material.POLISHED_BLACKSTONE),
            new MineTheme(Material.AMETHYST_BLOCK, Material.CALCITE, Material.PURPLE_TERRACOTTA, Material.CALCITE),
    };

    private MineTheme themeFor(String rank) {
        int idx = RankMineData.RANKS.keySet().stream().toList().indexOf(rank);
        return MINE_THEMES[Math.max(0, idx / 4) % MINE_THEMES.length];
    }

    /**
     * One mine: a self-contained underground chamber holding an open ore pit, a lit walkway
     * around its rim, and the cage landing.
     *
     * Built as a ROOM — floor, four walls, ceiling — rather than by carving a hole out of a
     * solid rock mass. The first version filled the entire mine field solid before excavating,
     * which came to roughly 9.8 million blocks and would have frozen the server for minutes on
     * first boot. Surfaces only brings a mine down to tens of thousands.
     *
     * Bedrock is still the structural backstop, but it sits one layer behind stone cladding on
     * every face, and ProtectionListener only permits breaking the mine's own ore materials, so
     * there is no way to reach it. Nobody should ever see bedrock down here.
     */
    private void buildMinePit(RankMineData.Def d) {
        int[] p = d.pit, rim = d.rim;
        MineTheme mt = themeFor(d.rank);

        int ox1 = rim[0] - 1, oz1 = rim[1] - 1, ox2 = rim[2] + 1, oz2 = rim[3] + 1;
        int floorY = d.oreBottom - 1;      // the pit's own floor surface
        int ceilY = PIT_CEILING;

        // --- Chamber walls: clad inside, bedrock backstop outside -------------------
        for (int x = ox1; x <= ox2; x++) {
            for (int z = oz1; z <= oz2; z++) {
                boolean wall = x == ox1 || x == ox2 || z == oz1 || z == oz2;
                if (!wall) continue;
                for (int y = floorY - 1; y <= ceilY; y++) {
                    boolean pillar = Math.floorMod(x - ox1, 8) == 0 || Math.floorMod(z - oz1, 8) == 0;
                    boolean band = y == RIM_Y + 3 || y == RIM_Y + 4;
                    Material mat = pillar ? mt.pillar() : band ? mt.band()
                            : (y == floorY - 1 || y == ceilY) ? mt.trim()
                            : arch.pick(Architect.WARD_INDUSTRIAL);
                    arch.set(x, y, z, mat);
                }
            }
        }
        for (int x = ox1 - 1; x <= ox2 + 1; x++) {
            for (int z = oz1 - 1; z <= oz2 + 1; z++) {
                boolean ring = x == ox1 - 1 || x == ox2 + 1 || z == oz1 - 1 || z == oz2 + 1;
                if (!ring) continue;
                for (int y = floorY - 2; y <= ceilY + 1; y++) arch.set(x, y, z, Material.BEDROCK);
            }
        }

        // --- Ceiling and the apron of solid ground the rim walkway sits on ----------
        for (int x = ox1; x <= ox2; x++) {
            for (int z = oz1; z <= oz2; z++) {
                arch.set(x, ceilY, z, mt.trim());
                arch.set(x, ceilY + 1, z, Material.BEDROCK);
                boolean overPit = x >= p[0] && x <= p[2] && z >= p[1] && z <= p[3];
                if (overPit) continue;
                for (int y = floorY - 1; y < RIM_Y; y++) arch.set(x, y, z, Material.DEEPSLATE);
                arch.set(x, floorY - 2, z, Material.BEDROCK);
            }
        }

        // --- Pit floor: clad stone over a hidden bedrock backstop -------------------
        for (int x = p[0]; x <= p[2]; x++) {
            for (int z = p[1]; z <= p[3]; z++) {
                arch.set(x, floorY, z, mt.floor());
                arch.set(x, floorY - 1, z, Material.BEDROCK);
            }
        }
        // --- Pit sides, from the floor up to the rim -------------------------------
        for (int x = p[0] - 1; x <= p[2] + 1; x++) {
            for (int z = p[1] - 1; z <= p[3] + 1; z++) {
                boolean side = x == p[0] - 1 || x == p[2] + 1 || z == p[1] - 1 || z == p[3] + 1;
                if (!side) continue;
                for (int y = floorY; y <= RIM_Y - 1; y++) arch.set(x, y, z, mt.trim());
            }
        }

        fillOre(d);
        dressRim(d, mt);
        buildCageLanding(d);
    }

    /** The walkway around the pit: surface, guard rail, lamp posts and a stair down into the ore. */
    private void dressRim(RankMineData.Def d, MineTheme mt) {
        int[] p = d.pit, rim = d.rim;
        for (int x = rim[0]; x <= rim[2]; x++) {
            for (int z = rim[1]; z <= rim[3]; z++) {
                boolean overPit = x >= p[0] && x <= p[2] && z >= p[1] && z <= p[3];
                if (overPit) continue;
                arch.set(x, RIM_Y, z, ((x + z) % 7 == 0) ? mt.band() : mt.floor());
                // Guard rail right on the lip, so nobody walks into the hole by accident.
                boolean lip = x == p[0] - 1 || x == p[2] + 1 || z == p[1] - 1 || z == p[3] + 1;
                if (lip) arch.set(x, RIM_Y + 1, z, Material.POLISHED_BLACKSTONE_WALL);
            }
        }
        // Lamp posts at the rim corners and along its length.
        for (int x = rim[0] + 2; x <= rim[2] - 2; x += 9) {
            for (int z : new int[]{rim[1] + 2, rim[3] - 2}) minerLamp(x, z);
        }
        for (int z = rim[1] + 2; z <= rim[3] - 2; z += 9) {
            for (int x : new int[]{rim[0] + 2, rim[2] - 2}) minerLamp(x, z);
        }
        // Pit lighting from above, on the ceiling, so the ore face is lit but nothing is
        // embedded in the ore where it would be mined away on the first pass.
        for (int x = p[0] + 5; x <= p[2] - 5; x += 10) {
            for (int z = p[1] + 5; z <= p[3] - 5; z += 10) {
                arch.set(x, PIT_CEILING - 1, z, Material.SEA_LANTERN);
                arch.set(x, PIT_CEILING - 2, z, Material.IRON_BARS);
            }
        }
        // A stair down into the pit on the side opposite the cage, so you can walk out of the
        // hole once you have mined down, rather than being stuck in it.
        int sx = (p[0] + p[2]) / 2;
        for (int step = 0; step <= RIM_Y - d.oreTop; step++) {
            for (int w = -1; w <= 1; w++) {
                arch.set(sx + w, RIM_Y - step, p[3] + 1 - step, mt.trim());
                for (int dy = 1; dy <= 3; dy++) {
                    arch.set(sx + w, RIM_Y - step + dy, p[3] + 1 - step, Material.AIR);
                }
            }
        }
    }

    private void minerLamp(int x, int z) {
        for (int dy = 1; dy <= 3; dy++) arch.set(x, RIM_Y + dy, z, Material.POLISHED_BLACKSTONE_WALL);
        arch.set(x, RIM_Y + 4, z, Material.LANTERN);
    }

    /** The barred alcove the cage lift sets you down in: on the rim, over solid floor. */
    private void buildCageLanding(RankMineData.Def d) {
        int lx = d.landing[0], lz = d.landing[2];
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                arch.set(lx + dx, RIM_Y, lz + dz, Material.POLISHED_BLACKSTONE);
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                boolean doorway = dz == 2 && Math.abs(dx) <= 1;
                for (int dy = 1; dy <= 4; dy++) {
                    arch.set(lx + dx, RIM_Y + dy, lz + dz,
                            (edge && !doorway) ? Material.IRON_BARS : Material.AIR);
                }
                if (edge) arch.set(lx + dx, RIM_Y + 5, lz + dz, Material.POLISHED_BLACKSTONE);
            }
        }
        arch.set(lx, RIM_Y + 5, lz, Material.SEA_LANTERN);
        arch.set(lx, RIM_Y, lz, Material.LODESTONE);

        int[] sp = sellSignSpot(d);
        signOn(sp[0], sp[1], sp[2], BlockFace.SOUTH, "§a[Sell]", "Mine " + d.rank + " ore",
                "Right-click", "holding ore");
        hologramAt(lx + 0.5, RIM_Y + 3.0, lz + 0.5, "§b§lMINE " + d.rank,
                "§7Stand on the plate to return");
    }

    /** The sell sign sits on the landing alcove's solid corner post, which always backs it. */
    private int[] sellSignSpot(RankMineData.Def d) {
        return new int[]{d.landing[0] + 2, d.landing[1] + 2, d.landing[2] + 2};
    }

    private void fillOre(RankMineData.Def d) {
        Material filler = safeMaterial(d.filler), common = safeMaterial(d.common), rare = safeMaterial(d.rare);
        double rarePct = rarePercentFor(d.rank);
        for (int x = d.pit[0]; x <= d.pit[2]; x++) {
            for (int z = d.pit[1]; z <= d.pit[3]; z++) {
                for (int y = d.oreBottom; y <= d.oreTop; y++) {
                    arch.set(x, y, z, pickOre(filler, common, rare, rarePct));
                }
            }
        }
    }

    public void resetMine(String rank) {
        RankMineData.Def d = RankMineData.RANKS.get(rank);
        if (d == null || !d.hasMine()) return;
        fillOre(d);
    }

    public double percentRemaining(String rank) {
        RankMineData.Def d = RankMineData.RANKS.get(rank);
        if (d == null || !d.hasMine()) return 1.0;
        long total = 0, solid = 0;
        for (int x = d.pit[0]; x <= d.pit[2]; x += 2) {
            for (int z = d.pit[1]; z <= d.pit[3]; z += 2) {
                for (int y = d.oreBottom; y <= d.oreTop; y += 2) {
                    total++;
                    if (!world.getBlockAt(x, y, z).getType().isAir()) solid++;
                }
            }
        }
        return total == 0 ? 1.0 : (double) solid / total;
    }

    /**
     * A guaranteed-safe standing spot for a mine: just inside its cage landing, which sits on
     * the rim over solid floor and never over the pit.
     *
     * Both the /mine warp and the reset evacuation use this. Previously each computed its own
     * coordinates from the mine's corner — the warp landed players two blocks INSIDE a solid
     * cube of ore (suffocation on every mine), and the evacuation assumed every mine's entrance
     * faced west, which was true for fewer than half of them.
     */
    public Location safeMineSpot(String rank) {
        RankMineData.Def d = RankMineData.RANKS.get(rank);
        if (d == null || !d.hasMine()) return getHubSpawn();
        return new Location(world, d.landing[0] + 0.5, d.landing[1] + 1, d.landing[2] + 0.5, 180f, 0f);
    }

    private double rarePercentFor(String rank) {
        int idx = 0, i = 0;
        for (String r : RankMineData.RANKS.keySet()) {
            if (r.equals(rank)) { idx = i; break; }
            i++;
        }
        return 2.0 + (10.0 * idx / Math.max(1, RankMineData.RANKS.size() - 1));
    }

    private Material pickOre(Material filler, Material common, Material rare, double rarePct) {
        double roll = Math.random() * 100;
        if (roll < rarePct) return rare;
        if (roll < rarePct + 28) return common;
        return filler;
    }

    // ==================================================================================
    // Registration helpers
    // ==================================================================================

    private void registerCrateLocations() {
        crateLocations.clear();
        int[] r = MapLayout.room("CRATES").room();
        int cz = (r[1] + r[3]) / 2;
        int x = r[0] + 5;
        for (String key : CrateData.CRATES.keySet()) {
            crateLocations.put(new Location(world, x, Y + 3, cz), key);
            x += 5;
        }
    }

    private void registerPvpZones() {
        pvpZones.clear();
        int[] yard = MapLayout.room("YARD").room();
        pvpZones.add(new PvpZoneManager.Zone("The Yard", yard[0], Y, yard[1], yard[2], Y + 12, yard[3]));
        // The hub's open floor. PvpZoneManager checks the block underfoot, so the red wool IS
        // the zone: walkways, plaza and building floors are grey and therefore safe.
        // redFloorOnly: the hub rectangle contains walkways, the plaza and four buildings,
        // all of which stay safe. Only blocks with red wool underfoot are live.
        pvpZones.add(new PvpZoneManager.Zone("Hub Yard",
                HUB[0] + 1, Y, HUB[1] + 1, HUB[2] - 1, Y + 6, HUB[3] - 1, true));
    }

    private void registerLiftPads() {
        liftPads.clear();
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (!d.hasMine()) continue;
            int[] w = MapLayout.gate(d.rank).ward();
            liftPads.put(new Location(world, (w[0] + w[2]) / 2, Y, (w[1] + w[3]) / 2), d.rank);
            liftPads.put(new Location(world, d.landing[0], d.landing[1], d.landing[2]), "HUB");
        }
    }

    /** A safe standing spot in the middle of one of the grounds zones. */
    public Location groundSpot(String name) {
        int[] r = MapLayout.ground(name).area();
        return new Location(world, (r[0] + r[2]) / 2.0 + 0.5, Y + 1, (r[1] + r[3]) / 2.0 + 0.5);
    }

    /** A safe standing spot just inside one of the room gates. */
    public Location roomSpot(String name) {
        MapLayout.Room room = MapLayout.room(name);
        int[] r = room.room();
        return new Location(world, (r[0] + r[2]) / 2.0 + 0.5, Y + 1, (r[1] + r[3]) / 2.0 + 0.5);
    }

    /** The cell wing's door, on the ground tier. */
    public Location cellWingSpot() {
        int[] r = cellWing();
        return new Location(world, (Math.abs(r[0]) < Math.abs(r[2]) ? r[0] : r[2]) + 2.5,
                Y + 1, (r[1] + r[3]) / 2.0 + 0.5);
    }

    /** True if this position is inside any mine's pit chamber. */
    public boolean isInAnyMine(int x, int y, int z) {
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (!d.hasMine()) continue;
            int[] rim = d.rim;
            if (x >= rim[0] - 2 && x <= rim[2] + 2 && z >= rim[1] - 2 && z <= rim[3] + 2
                    && y >= d.oreBottom - 2 && y <= PIT_CEILING) {
                return true;
            }
        }
        return false;
    }

    /** The ward cage a mine's lift returns you to. */
    public Location wardSpot(String rank) {
        int[] w = MapLayout.gate(rank).ward();
        return new Location(world, (w[0] + w[2]) / 2 + 0.5, Y + 1, (w[1] + w[3]) / 2 + 0.5, 0f, 0f);
    }

    // ==================================================================================
    // Signs and holograms
    // ==================================================================================

    private void signOn(int x, int y, int z, BlockFace facing, String... lines) {
        signs.add(new PendingSign(x, y, z, facing, lines));
    }

    /** A free-floating label, for places where no solid block can back a sign. */
    private void hologramAt(double x, double y, double z, String... lines) {
        holograms.add(new PendingHologram(x, y, z, lines));
    }

    /**
     * Places wall signs, but ONLY where a solid block backs them.
     *
     * An unsupported wall sign survives placement (the build runs with physics off) and is then
     * culled the moment the chunk reloads and the server revalidates it. That is why the old map
     * filled up with floating text and no signs: the sign vanished, and the hologram that the
     * old code spawned alongside EVERY sign stayed behind. Holograms are now opt-in, for the
     * handful of labels that genuinely cannot sit on a wall.
     */
    public void applySigns() {
        int placed = 0, skipped = 0;
        for (PendingSign ps : signs) {
            Block b = world.getBlockAt(ps.x, ps.y, ps.z);
            Block support = b.getRelative(ps.facing.getOppositeFace());
            if (!support.getType().isOccluding()) { skipped++; continue; }
            b.setType(Material.OAK_WALL_SIGN, false);
            if (b.getBlockData() instanceof WallSign ws) {
                ws.setFacing(ps.facing);
                b.setBlockData(ws, false);
            }
            if (b.getState() instanceof Sign s) {
                for (int i = 0; i < 4 && i < ps.lines.length; i++) {
                    s.getSide(Side.FRONT).setLine(i, ps.lines[i] == null ? "" : ps.lines[i]);
                }
                s.setWaxed(true);
                s.update(true, false);
                placed++;
            }
        }
        plugin.getLogger().info("Signs placed: " + placed
                + (skipped > 0 ? " (" + skipped + " skipped — no solid backing block)" : ""));
    }

    public void spawnHolograms() {
        for (PendingHologram h : holograms) {
            Hologram.spawn(new Location(world, h.x, h.y, h.z), h.lines);
        }
    }

    // ==================================================================================
    // Small helpers
    // ==================================================================================

    private static boolean isNS(String wall) { return wall.equals("N") || wall.equals("S"); }

    private static int outwardSign(String wall) { return (wall.equals("S") || wall.equals("E")) ? 1 : -1; }

    /**
     * The hub's own outer edge on a given wall.
     *
     * Kept separate from {@link #hubFacingEdge} on purpose. The two used to be one method with a
     * "near" flag, which silently meant opposite things depending on whether you passed the hub
     * rect or a ward rect — the hub's near edge on its north wall is its MINIMUM z, but a ward
     * sitting outside that wall faces the hub with its MAXIMUM z. Every ward doorway was
     * therefore carved into its outer wall while its hub-facing wall stayed solid, sealing all
     * thirty gates shut.
     */
    private static int hubEdgeOn(String wall) {
        return switch (wall) {
            case "N" -> HUB[1];
            case "S" -> HUB[3];
            case "E" -> HUB[2];
            default -> HUB[0];
        };
    }

    /** The edge of a rect OUTSIDE the hub that faces back toward it. */
    private static int hubFacingEdge(String wall, int[] r) {
        return switch (wall) {
            case "N" -> r[3];
            case "S" -> r[1];
            case "E" -> r[0];
            default -> r[2];
        };
    }

    /** The far edge of a rect outside the hub — the side pointing away from it. */
    private static int outerEdge(String wall, int[] r) {
        return switch (wall) {
            case "N" -> r[1];
            case "S" -> r[3];
            case "E" -> r[2];
            default -> r[0];
        };
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
        if (raw == null || raw.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String p : raw.toLowerCase().split("_")) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private Material safeMaterial(String name) {
        Material m = Material.matchMaterial(name);
        return m == null ? Material.STONE : m;
    }

    // ---- Build-once marker --------------------------------------------------------

    private File markerFile() { return new File(plugin.getDataFolder(), "world-built.marker"); }

    /**
     * Whether the prison already exists in the world.
     *
     * The marker file alone is NOT evidence, and trusting it was a real bug: the marker lives in
     * the plugin's data folder, so deleting the `prison` world folder — the documented way to
     * force a clean rebuild — leaves the marker behind. The plugin then skipped construction and
     * dropped players into an empty void world with no way to recover short of finding and
     * deleting a file nobody would think to look for.
     *
     * So the world itself is the source of truth: the marker is only a fast path, and we confirm
     * against a block that only exists if the build actually ran. Delete the world and you get a
     * rebuild, every time, with no second step.
     */
    public boolean alreadyBuilt() {
        if (!markerFile().exists()) return false;
        if (!worldLooksBuilt()) {
            plugin.getLogger().warning("Build marker found but the world is empty "
                    + "(the prison world folder was probably deleted). Rebuilding.");
            markerFile().delete();
            return false;
        }
        return true;
    }

    /** The watchtower plinth at the hub's centre — present if and only if the build ran. */
    private boolean worldLooksBuilt() {
        return !world.getBlockAt(0, Y, 0).getType().isAir();
    }

    private void writeMarker() {
        try {
            File f = markerFile();
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            f.createNewFile();
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write the build marker: " + e.getMessage());
        }
    }
}
