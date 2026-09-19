package com.lukeprison.prison;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;

import java.util.Random;

/**
 * Reusable building techniques, applied so generated structures read as deliberately designed
 * rather than as boxes.
 *
 * The techniques here are the standard ones builders use:
 *  - texture noise: mix a base block with cracked/mossy/chiseled variants instead of one flat material
 *  - the 1-block recess rule: push wall panels back, bring pillars and trim forward
 *  - vertical rhythm: repeating pillars at a fixed interval
 *  - trim: stair and slab courses at floor and roof line, window sills, cornices
 *  - overhangs: inverted stairs under the roof edge to break the box silhouette
 *  - integrated lighting: light sources set into the architecture, not dotted on the floor
 */
public class Architect {

    private final World world;
    private final Random random = new Random();

    public Architect(World world) {
        this.world = world;
    }

    /** A weighted palette: the first entry dominates, later entries appear as accents. */
    public static class Palette {
        public final Material base;
        public final Material[] accents;
        public final int[] accentPercent;

        public Palette(Material base, Material[] accents, int[] accentPercent) {
            this.base = base;
            this.accents = accents;
            this.accentPercent = accentPercent;
        }

        public static Palette of(Material base, Material a1, int p1, Material a2, int p2) {
            return new Palette(base, new Material[]{a1, a2}, new int[]{p1, p2});
        }

        public static Palette of(Material base, Material a1, int p1) {
            return new Palette(base, new Material[]{a1}, new int[]{p1});
        }
    }

    // ---- Standard palettes, chosen so each zone reads as its own place ----

    /**
     * Sky Prison's own fabric: cool, weathered deepslate rather than the warm stone-brick grey
     * most prison servers default to — reads darker and colder, matching a facility floating in
     * open sky rather than dug into a hillside.
     */
    public static final Palette PRISON_STONE = Palette.of(
            Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS, 16, Material.DEEPSLATE_TILES, 10);

    /**
     * Textured tuff for the hub. Noise only works on blocks that have surface texture —
     * on flat concrete it reads as pixel static, which is why concrete was dropped.
     */
    public static final Palette HUB_STONE = Palette.of(
            Material.TUFF_BRICKS, Material.POLISHED_TUFF, 14, Material.CHISELED_TUFF, 4);

    /** Darker, heavier stone for cell blocks. */
    public static final Palette CELL_STONE = Palette.of(
            Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS, 20, Material.DEEPSLATE_TILES, 12);

    /** Rusted industrial look for ward antechambers. */
    public static final Palette WARD_INDUSTRIAL = Palette.of(
            Material.DEEPSLATE_TILES, Material.CRACKED_DEEPSLATE_TILES, 18, Material.POLISHED_BASALT, 8);

    /** Warm sandstone for the fishing area, to contrast the prison's grey. */
    public static final Palette FISHING_STONE = Palette.of(
            Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, 22, Material.SANDSTONE, 12);

    public Material pick(Palette p) {
        int roll = random.nextInt(100);
        int acc = 0;
        for (int i = 0; i < p.accents.length; i++) {
            acc += p.accentPercent[i];
            if (roll < acc) return p.accents[i];
        }
        return p.base;
    }

    public void set(int x, int y, int z, Material mat) {
        world.getBlockAt(x, y, z).setType(mat, false);
        if (CONNECTABLE.contains(mat)) connectables.add(new int[]{x, y, z});
    }

    // ==================================================================================
    // Connection states
    //
    // set() deliberately places with applyPhysics=false: physics on a multi-million-block
    // build is ruinously slow, and it would also let gravity blocks fall mid-build. The
    // cost is that connectable blocks — fences, iron bars, glass panes, walls — never get
    // their connection state computed, so every one of them stays a lone unconnected post.
    // That is why the animal pens looked like loose stakes and every walkway railing read
    // as a row of separate bars.
    //
    // Rather than paying for physics everywhere, we record just the connectable blocks as
    // they are placed and compute their connections explicitly at the end of the build.
    // Deterministic, and it costs one pass over a few thousand blocks instead of physics
    // on all of them.
    // ==================================================================================

    private final java.util.List<int[]> connectables = new java.util.ArrayList<>();

    private static final java.util.Set<Material> CONNECTABLE = connectableMaterials();

    private static java.util.Set<Material> connectableMaterials() {
        java.util.Set<Material> s = java.util.EnumSet.noneOf(Material.class);
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock()) continue;
            try {
                BlockData bd = m.createBlockData();
                if (bd instanceof org.bukkit.block.data.MultipleFacing
                        || bd instanceof org.bukkit.block.data.type.Wall) {
                    s.add(m);
                }
            } catch (Exception ignored) {
                // Not every Material can produce block data; those are simply not connectable.
            }
        }
        return s;
    }

    private static boolean isFence(Material m) { return m.name().endsWith("_FENCE"); }

    private static boolean isPaneLike(Material m) {
        return m == Material.IRON_BARS || m.name().endsWith("_PANE");
    }

    private static boolean isWallBlock(Material m) { return m.name().endsWith("_WALL"); }

    /** Whether {@code self} should visually join to whatever sits in {@code face}. */
    private boolean joins(Block self, BlockFace face) {
        Block n = self.getRelative(face);
        Material nm = n.getType();
        if (nm.isAir()) return false;

        Material sm = self.getType();
        // Like joins to like: fence to fence, bars/panes to bars/panes, wall to wall.
        if (isFence(sm) && (isFence(nm) || nm.name().endsWith("_FENCE_GATE"))) return true;
        if (isPaneLike(sm) && isPaneLike(nm)) return true;
        if (isWallBlock(sm) && isWallBlock(nm)) return true;

        // Anything connectable also joins to a solid full block behind it, which is what
        // makes a railing meet a pillar instead of stopping one block short of it.
        BlockData nd = n.getBlockData();
        if (nd instanceof org.bukkit.block.data.MultipleFacing
                || nd instanceof org.bukkit.block.data.type.Wall) {
            return false;   // connectable, but a different family — no join
        }
        return nm.isOccluding();
    }

    /**
     * Computes and applies connection state for every connectable block placed since the
     * last call. Run once at the end of a build, after all neighbours exist — running it
     * mid-build would link blocks to neighbours that have not been placed yet.
     */
    public int relinkConnectables() {
        int done = 0;
        for (int[] p : connectables) {
            Block b = world.getBlockAt(p[0], p[1], p[2]);
            BlockData bd = b.getBlockData();

            if (bd instanceof org.bukkit.block.data.type.Wall wall) {
                for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST,
                                                   BlockFace.SOUTH, BlockFace.WEST}) {
                    wall.setHeight(f, joins(b, f)
                            ? org.bukkit.block.data.type.Wall.Height.LOW
                            : org.bukkit.block.data.type.Wall.Height.NONE);
                }
                wall.setUp(true);
                b.setBlockData(wall, false);
                done++;
            } else if (bd instanceof org.bukkit.block.data.MultipleFacing mf) {
                for (BlockFace f : mf.getAllowedFaces()) {
                    mf.setFace(f, joins(b, f));
                }
                b.setBlockData(mf, false);
                done++;
            }
        }
        connectables.clear();
        return done;
    }

    /** Fills a solid box with palette noise. */
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Palette p) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, pick(p));
                }
            }
        }
    }

    public void fillFlat(int x1, int z1, int x2, int z2, int y, Palette p) {
        fill(x1, y, z1, x2, y, z2, p);
    }

    public void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, Material.AIR);
                }
            }
        }
    }

    /**
     * A wall with real depth: panels recessed one block, pillars of a contrasting material
     * standing proud at a regular interval, with a trim course top and bottom.
     *
     * This is the single biggest difference between a "box" and a building.
     */
    public void detailedWall(int x1, int y, int z1, int x2, int z2, int height,
                             Palette panel, Material pillar, Material trim, int pillarSpacing) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean onEdge = x == minX || x == maxX || z == minZ || z == maxZ;
                if (!onEdge) continue;

                // Pillars land on a fixed rhythm and at every corner.
                boolean corner = (x == minX || x == maxX) && (z == minZ || z == maxZ);
                boolean rhythm = ((x - minX) % pillarSpacing == 0 && (z == minZ || z == maxZ))
                        || ((z - minZ) % pillarSpacing == 0 && (x == minX || x == maxX));
                boolean isPillar = corner || rhythm;

                for (int dy = 0; dy < height; dy++) {
                    Material mat;
                    if (isPillar) {
                        mat = pillar;
                    } else if (dy == 0 || dy == height - 1) {
                        mat = trim;               // base course and cornice line
                    } else {
                        mat = pick(panel);
                    }
                    set(x, y + dy, z, mat);
                }
            }
        }
    }

    /**
     * A roof with an overhang: the slab course steps one block beyond the wall line and is
     * underlined with inverted stairs, which kills the flat-box silhouette.
     */
    public void roofWithOverhang(int x1, int z1, int x2, int z2, int y,
                                 Material roof, Material stairMat) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

        // Main roof slab.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                set(x, y, z, roof);
            }
        }

        // Overhanging lip, one block out on every side.
        for (int x = minX - 1; x <= maxX + 1; x++) {
            placeStair(x, y, minZ - 1, stairMat, BlockFace.SOUTH, true);
            placeStair(x, y, maxZ + 1, stairMat, BlockFace.NORTH, true);
        }
        for (int z = minZ - 1; z <= maxZ + 1; z++) {
            placeStair(minX - 1, y, z, stairMat, BlockFace.EAST, true);
            placeStair(maxX + 1, y, z, stairMat, BlockFace.WEST, true);
        }
    }

    /** Places a stair block facing a direction, optionally upside down (for cornices). */
    public void placeStair(int x, int y, int z, Material stairMat, BlockFace facing, boolean upsideDown) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(stairMat, false);
        BlockData data = b.getBlockData();
        if (data instanceof Stairs stairs) {
            stairs.setFacing(facing);
            stairs.setHalf(upsideDown ? org.bukkit.block.data.Bisected.Half.TOP
                    : org.bukkit.block.data.Bisected.Half.BOTTOM);
            b.setBlockData(stairs, false);
        }
    }

    public void placeSlab(int x, int y, int z, Material slabMat, boolean top) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(slabMat, false);
        BlockData data = b.getBlockData();
        if (data instanceof Slab slab) {
            slab.setType(top ? Slab.Type.TOP : Slab.Type.BOTTOM);
            b.setBlockData(slab, false);
        }
    }

    /**
     * Barred window slits cut into a wall run at a regular interval, with a stair sill beneath —
     * the detail that makes a blank wall read as a facade.
     */
    public void windowRun(int x1, int y, int z, int x2, int spacing, int sillHeight,
                          Material glass, Material sill) {
        for (int x = Math.min(x1, x2) + spacing; x < Math.max(x1, x2); x += spacing) {
            for (int dy = 0; dy < 2; dy++) {
                set(x, y + sillHeight + dy, z, glass);
            }
            placeStair(x, y + sillHeight - 1, z, sill, BlockFace.NORTH, true);
        }
    }

    /**
     * A lighting course set into the ceiling line rather than dotted around, plus hanging
     * lanterns for vertical interest.
     */
    public void integratedLighting(int x1, int z1, int x2, int z2, int ceilingY,
                                   int spacing, Material lightBlock) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        for (int x = minX + spacing / 2; x < maxX; x += spacing) {
            for (int z = minZ + spacing / 2; z < maxZ; z += spacing) {
                set(x, ceilingY, z, lightBlock);
                // Hang a lantern a block below so light reads at eye level too.
                set(x, ceilingY - 1, z, Material.LANTERN);
            }
        }
    }

    /** A floor trim band inset from the wall, grounding the room. */
    public void floorBand(int x1, int z1, int x2, int z2, int y, Material band) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        for (int x = minX + 1; x <= maxX - 1; x++) {
            set(x, y, minZ + 1, band);
            set(x, y, maxZ - 1, band);
        }
        for (int z = minZ + 1; z <= maxZ - 1; z++) {
            set(minX + 1, y, z, band);
            set(maxX - 1, y, z, band);
        }
    }

    /**
     * A prison wall in the TitanMC idiom: stone brick panels with texture noise, a nether brick
     * band at eye level (rows 2–3), chiseled trim at base and cornice, pillars on a rhythm.
     */
    public void prisonWall(int x1, int y, int z1, int x2, int z2, int height, int pillarSpacing) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean onEdge = x == minX || x == maxX || z == minZ || z == maxZ;
                if (!onEdge) continue;
                boolean corner = (x == minX || x == maxX) && (z == minZ || z == maxZ);
                boolean rhythm = ((x - minX) % pillarSpacing == 0 && (z == minZ || z == maxZ))
                        || ((z - minZ) % pillarSpacing == 0 && (x == minX || x == maxX));
                boolean isPillar = corner || rhythm;
                for (int dy = 0; dy < height; dy++) {
                    Material mat;
                    if (isPillar) {
                        mat = dy == 0 || dy == height - 1 ? Material.QUARTZ_PILLAR : Material.SMOOTH_QUARTZ;
                    } else if (dy == 0 || dy == height - 1) {
                        mat = Material.SMOOTH_QUARTZ;
                    } else if (dy == 2 || dy == 3) {
                        // Sky Prison's signature band: ice-cyan rather than the usual prison-server
                        // blood-red. Terracotta keeps the surface texture noise needs to read right.
                        mat = random.nextInt(100) < 15 ? Material.LIGHT_BLUE_TERRACOTTA : Material.CYAN_TERRACOTTA;
                    } else {
                        mat = pick(PRISON_STONE);
                    }
                    set(x, y + dy, z, mat);
                }
            }
        }
    }

    /** Cobweb "barbed wire" with iron-bar posts along a wall top — the classic prison silhouette. */
    public void barbedWireTop(int x1, int z1, int x2, int z2, int y) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean onEdge = x == minX || x == maxX || z == minZ || z == maxZ;
                if (!onEdge) continue;
                boolean post = (x + z) % 3 == 0;
                set(x, y, z, post ? Material.IRON_BARS : Material.COBWEB);
                if (post) set(x, y + 1, z, Material.COBWEB);
            }
        }
    }

    /**
     * Recessed ceiling light panels: a 3x3 of sea lantern set one block up into the ceiling,
     * framed by chiseled stone. Reads as institutional strip lighting rather than hung lamps.
     */
    public void recessedLightPanels(int x1, int z1, int x2, int z2, int ceilingY, int spacing) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        for (int cx = minX + spacing / 2; cx < maxX - 2; cx += spacing) {
            for (int cz = minZ + spacing / 2; cz < maxZ - 2; cz += spacing) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        boolean frame = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        set(cx + dx, ceilingY, cz + dz, frame ? Material.SMOOTH_QUARTZ : Material.AIR);
                        if (!frame) set(cx + dx, ceilingY + 1, cz + dz, Material.SEA_LANTERN);
                    }
                }
            }
        }
    }

    /** A long strip light down a ceiling, for corridors and halls. */
    public void ceilingStrip(int x1, int x2, int y, int z, Material light) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) set(x, y, z, light);
    }

    /**
     * A prison hall: prison-wall shell with eye-level band, solid roof, recessed light panels,
     * a floor with a trim band. The room primitive for everything inside the prison.
     */
    public void prisonHall(int x1, int z1, int x2, int z2, int y, int height,
                           Material floorMat, Material floorBand, int panelSpacing) {
        fillFlat(x1, z1, x2, z2, y, new Palette(floorMat, new Material[]{floorMat}, new int[]{0}));
        floorBand(x1, z1, x2, z2, y, floorBand);
        clear(x1 + 1, y + 1, z1 + 1, x2 - 1, y + height, z2 - 1);
        prisonWall(x1, y + 1, z1, x2, z2, height, 6);
        fillFlat(x1, z1, x2, z2, y + height + 1, PRISON_STONE);
        fillFlat(x1, z1, x2, z2, y + height + 2, PRISON_STONE);
        recessedLightPanels(x1 + 1, z1 + 1, x2 - 1, z2 - 1, y + height + 1, panelSpacing);
    }

    /**
     * Builds a complete detailed room in one call: floor with a trim band, depth-detailed walls,
     * an overhanging roof and integrated lighting.
     */
    public void room(int x1, int z1, int x2, int z2, int y, int height,
                     Palette wallPalette, Material pillar, Material trim,
                     Material floorMat, Material floorBand, Material roofMat,
                     Material stairMat, Material light) {
        fillFlat(x1, z1, x2, z2, y, new Palette(floorMat, new Material[]{floorMat}, new int[]{0}));
        floorBand(x1, z1, x2, z2, y, floorBand);
        clear(x1 + 1, y + 1, z1 + 1, x2 - 1, y + height - 1, z2 - 1);
        detailedWall(x1, y + 1, z1, x2, z2, height, wallPalette, pillar, trim, 5);
        roofWithOverhang(x1, z1, x2, z2, y + height + 1, roofMat, stairMat);
        integratedLighting(x1, z1, x2, z2, y + height, 7, light);
    }

    /** Carves a doorway and frames it with stairs so it reads as an entrance, not a hole. */
    public void doorway(int x, int y, int z, boolean alongZ, int halfWidth, int height, Material frame) {
        for (int d = -halfWidth; d <= halfWidth; d++) {
            for (int dy = 0; dy < height; dy++) {
                if (alongZ) set(x, y + dy, z + d, Material.AIR);
                else set(x + d, y + dy, z, Material.AIR);
            }
        }
        // Lintel above the opening.
        for (int d = -halfWidth - 1; d <= halfWidth + 1; d++) {
            if (alongZ) set(x, y + height, z + d, frame);
            else set(x + d, y + height, z, frame);
        }
    }
}
