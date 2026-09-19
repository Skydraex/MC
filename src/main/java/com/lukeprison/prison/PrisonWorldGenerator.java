package com.lukeprison.prison;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Generates the countryside the prison stands in.
 *
 * The world used to generate nothing at all, so the prison was a set of platforms floating in
 * empty sky: look past the wall and you saw the void, and the mine chambers hanging in it.
 * Rather than BUILD a landscape block by block — which would be millions of blocks on first boot
 * — this generates it. Chunk generation is lazy and effectively free, so the land extends as far
 * as anyone walks without costing the build anything.
 *
 * Two bands, deliberately:
 *
 *   y >= GROUND_FLOOR   solid ground: stone, then dirt, then grass, with gentle hills
 *   y <  GROUND_FLOOR   left empty, and this is where the mine chambers live
 *
 * The mines are sealed rooms with their own floors, walls and ceilings, so nothing is visible
 * from inside them and players cannot reach the gap. Keeping that band empty is what lets a mine
 * be built as surfaces only instead of carved out of solid rock.
 *
 * Inside the prison's own footprint the ground is flattened to one height so the built platforms
 * sit on level land; outside it rolls into hills, and a populator scatters trees and flowers, so
 * the place reads as a prison out in open country rather than in a city.
 */
public class PrisonWorldGenerator extends ChunkGenerator {

    /** Ground exists from here up. Below this is the mine level. */
    public static final int GROUND_FLOOR = 86;

    /** The flat height the prison's own platforms sit on. */
    public static final int COMPOUND_Y = 94;

    /** Half-width of the flattened area, comfortably around everything the builder places. */
    private static final int COMPOUND_HALF = 238;

    /**
     * The prison stands on a PLATEAU, ringed by a cliff and a moat.
     *
     * It used to sit on flat fields that ran right up to the wall, which made the boundary
     * an arbitrary line in a meadow — you could see there was nothing stopping you but the
     * barrier blocks. A plateau does the work the wall cannot: the ground itself says there
     * is no way off, and the countryside beyond stays fully in view, which is the point.
     *
     *   |d| <= COMPOUND_HALF   the plateau, flat, where everything is built
     *   .. CLIFF_END           the cliff face, dropping fast
     *   .. MOAT_END            the moat, flooded to MOAT_LEVEL
     *   beyond                 open country, rising back into hills
     */
    private static final int CLIFF_END = COMPOUND_HALF + 14;
    private static final int MOAT_END = CLIFF_END + 26;
    /** Outer edge of the water, so the causeway knows how far it has to reach. */
    public static final int MOAT_OUTER_EDGE = MOAT_END;
    private static final int MOAT_FLOOR = GROUND_FLOOR + 1;
    private static final int MOAT_LEVEL = GROUND_FLOOR + 4;

    /** Distance over which the far bank blends into open hills. */
    private static final int BLEND = 90;

    private static final long SEED = 0x5C1FEE1DL;

    // ---- Simple deterministic value noise; no dependencies, same result every boot ----

    private static double hash(int x, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ SEED;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return ((h & 0xFFFFFFL) / (double) 0xFFFFFF) * 2.0 - 1.0;
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    private static double valueNoise(double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = smooth(x - x0), fz = smooth(z - z0);
        double n00 = hash(x0, z0), n10 = hash(x0 + 1, z0);
        double n01 = hash(x0, z0 + 1), n11 = hash(x0 + 1, z0 + 1);
        return (n00 * (1 - fx) + n10 * fx) * (1 - fz) + (n01 * (1 - fx) + n11 * fx) * fz;
    }

    /** How much of the open landscape shows through here: 0 on the plateau, 1 well beyond. */
    private static double openness(int x, int z) {
        int d = Math.max(Math.abs(x), Math.abs(z)) - MOAT_END;
        if (d <= 0) return 0;
        if (d >= BLEND) return 1;
        return smooth(d / (double) BLEND);
    }

    /** True where the ring of water sits. */
    public static boolean isMoat(int x, int z) {
        int d = Math.max(Math.abs(x), Math.abs(z));
        return d > CLIFF_END && d <= MOAT_END;
    }

    public static int surfaceHeight(int x, int z) {
        int d = Math.max(Math.abs(x), Math.abs(z));

        if (d <= COMPOUND_HALF) return COMPOUND_Y;

        if (d <= CLIFF_END) {
            // The cliff. Ragged rather than a clean bevel, so it reads as rock.
            double t = (d - COMPOUND_HALF) / (double) (CLIFF_END - COMPOUND_HALF);
            double ragged = valueNoise(x / 11.0, z / 11.0) * 2.2;
            return (int) Math.round(COMPOUND_Y - (COMPOUND_Y - MOAT_FLOOR) * smooth(t) + ragged);
        }

        if (d <= MOAT_END) return MOAT_FLOOR;

        // The far bank, climbing out of the moat into open country.
        double bank = smooth(Math.min(1.0, (d - MOAT_END) / 18.0));
        double base = MOAT_FLOOR + (COMPOUND_Y - 6 - MOAT_FLOOR) * bank;
        double open = openness(x, z);
        double hills = valueNoise(x / 70.0, z / 70.0) * 9 + valueNoise(x / 23.0, z / 23.0) * 3;
        return (int) Math.round(base + hills * open);
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        int min = data.getMinHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = chunkX * 16 + x, wz = chunkZ * 16 + z;
                int h = surfaceHeight(wx, wz);
                if (h <= GROUND_FLOOR) continue;

                // A bedrock pan under the countryside. It is never seen: the mines below have
                // their own ceilings, and nothing above can be dug through to reach it.
                data.setBlock(x, Math.max(min, GROUND_FLOOR), z, Material.BEDROCK);
                data.setRegion(x, GROUND_FLOOR + 1, z, x + 1, h - 3, z + 1, Material.STONE);

                boolean cliff = surfaceHeight(wx, wz) < COMPOUND_Y - 2
                        && Math.max(Math.abs(wx), Math.abs(wz)) <= CLIFF_END;
                if (cliff) {
                    // Bare rock on the cliff face — grass on a near-vertical drop looks wrong.
                    data.setRegion(x, h - 3, z, x + 1, h + 1, z + 1, Material.STONE);
                    data.setBlock(x, h, z, ((wx + wz) % 5 == 0)
                            ? Material.COBBLESTONE : Material.ANDESITE);
                } else {
                    data.setRegion(x, h - 3, z, x + 1, h, z + 1, Material.DIRT);
                    data.setBlock(x, h, z, Material.GRASS_BLOCK);
                }

                if (isMoat(wx, wz)) {
                    data.setBlock(x, h, z, Material.GRAVEL);
                    data.setRegion(x, h + 1, z, x + 1, MOAT_LEVEL + 1, z + 1, Material.WATER);
                }
            }
        }
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public List<BlockPopulator> getDefaultPopulators(org.bukkit.World world) {
        return List.of(new CountrysidePopulator());
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return Biome.PLAINS;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.PLAINS);
            }
        };
    }

    /**
     * Scatters trees, long grass and flowers, but only out where the land is open — the compound
     * itself stays clear so nothing grows through the prison's own floors.
     */
    private static class CountrysidePopulator extends BlockPopulator {

        @Override
        public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            for (int i = 0; i < 26; i++) {
                int wx = chunkX * 16 + random.nextInt(16);
                int wz = chunkZ * 16 + random.nextInt(16);
                if (openness(wx, wz) < 0.55) continue;

                int h = surfaceHeight(wx, wz);
                if (h <= GROUND_FLOOR) continue;
                int y = h + 1;
                if (!region.isInRegion(wx, y, wz)) continue;
                if (isMoat(wx, wz)) continue;
                if (region.getType(wx, h, wz) != Material.GRASS_BLOCK) continue;

                int roll = random.nextInt(100);
                if (roll < 6) {
                    plantTree(region, random, wx, y, wz);
                } else if (roll < 45) {
                    region.setType(wx, y, wz, Material.SHORT_GRASS);
                } else if (roll < 55) {
                    region.setType(wx, y, wz, random.nextBoolean()
                            ? Material.DANDELION : Material.POPPY);
                }
            }
        }

        private void plantTree(LimitedRegion region, Random random, int x, int y, int z) {
            int trunk = 4 + random.nextInt(3);
            for (int dy = 0; dy < trunk; dy++) {
                if (region.isInRegion(x, y + dy, z)) region.setType(x, y + dy, z, Material.OAK_LOG);
            }
            for (int dy = trunk - 2; dy <= trunk + 1; dy++) {
                int radius = (dy >= trunk) ? 1 : 2;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx == 0 && dz == 0 && dy < trunk) continue;
                        if (Math.abs(dx) == radius && Math.abs(dz) == radius && random.nextBoolean()) continue;
                        if (!region.isInRegion(x + dx, y + dy, z + dz)) continue;
                        if (region.getType(x + dx, y + dy, z + dz) != Material.AIR) continue;
                        region.setType(x + dx, y + dy, z + dz, Material.OAK_LEAVES);
                    }
                }
            }
        }
    }
}
