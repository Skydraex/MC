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

    /** Weathered institutional stone — the main prison fabric. */
    public static final Palette PRISON_STONE = Palette.of(
            Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, 18, Material.MOSSY_STONE_BRICKS, 9);

    /** Cold, clean concrete for the hub interior. */
    public static final Palette HUB_CONCRETE = Palette.of(
            Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE, 16, Material.POLISHED_ANDESITE, 10);

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
