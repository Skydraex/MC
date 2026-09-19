package com.lukeprison.prison;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Audits the world that was actually built, against the layout that says what should be there.
 *
 * This exists because of how every serious bug in this map has been found: somebody walked
 * into a wall. The Python checker (tools/map_check.py) proves the GEOMETRY is sound before a
 * block is placed — but it cannot prove the builder placed what the geometry describes, and
 * the gap between those two has cost a round of testing every single time:
 *
 *   all thirty wards sealed          the doorway was cut in the wrong wall
 *   the fishing lobby sealed         its opening was never cut at all
 *   the green and all three grounds  same, three more times
 *   suffocating on /warp             the landing was inside the mine's shell
 *   floating, blank signs            no solid block behind them, so they were culled
 *
 * Every one of those is mechanical and cheap to detect in-world. None of them needed a human
 * to notice. So this walks the built world and reports them, in seconds, on demand or at the
 * end of a build.
 *
 * It does NOT judge how anything looks — that still needs eyes. It answers the narrower
 * question the layout cannot: is what was built the thing that was specified?
 */
public class MapAuditor {

    /** One thing wrong, and where. */
    public record Finding(String category, String detail, int x, int y, int z) {
        @Override
        public String toString() {
            return detail + "  §8(" + x + ", " + y + ", " + z + ")";
        }
    }

    private final World world;
    private final WorldBuilder builder;
    private final List<Finding> findings = new ArrayList<>();
    private final Map<String, Integer> checked = new LinkedHashMap<>();

    public MapAuditor(WorldBuilder builder) {
        this.builder = builder;
        this.world = builder.getWorld();
    }

    public List<Finding> getFindings() {
        return findings;
    }

    private void fail(String category, String detail, int x, int y, int z) {
        findings.add(new Finding(category, detail, x, y, z));
    }

    private void count(String category, int n) {
        checked.merge(category, n, Integer::sum);
    }

    // ==================================================================================

    public void runAll() {
        findings.clear();
        checked.clear();
        auditDoorways();
        auditHubWalkways();
        auditShafts();
        auditRing();
        auditMineAccess();
        auditSpawns();
        auditSigns();
        auditCells();
        auditExposedBedrock();
        auditLighting();
    }

    /**
     * Every doorway the layout says must exist is actually open.
     *
     * This is the single most valuable check here. A sealed doorway looks like nothing at
     * all — the world is finished, the wall is tidy, and the area behind it is simply
     * unreachable forever.
     */
    private void auditDoorways() {
        int n = 0;
        for (MapLayout.Doorway d : MapLayout.DOORWAYS) {
            int base = d.level().equals("MINE") ? MapLayout.MINE_RIM_Y : WorldBuilder.Y;
            int[] r = d.rect();
            int cx = (r[0] + r[2]) / 2, cz = (r[1] + r[3]) / 2;

            boolean passable = true;
            for (int dy = 1; dy <= 2; dy++) {                 // a player is two blocks tall
                if (world.getBlockAt(cx, base + dy, cz).getType().isSolid()) passable = false;
            }
            if (!world.getBlockAt(cx, base, cz).getType().isSolid()) {
                fail("doorway", "no floor in the doorway " + d.a() + " -> " + d.b(), cx, base, cz);
            }
            if (!passable) {
                fail("doorway", "SEALED: " + d.a() + " -> " + d.b(), cx, base + 1, cz);
            }
            n++;
        }
        count("doorways", n);
    }

    /**
     * The hub's own walkways: the perimeter ring, all the way round, and the four spokes.
     *
     * Added after the cell wing was found sitting eight blocks inside the ring, sealing the
     * whole north-west of the hub. Every gate behind it — mine A among them — could only be
     * reached down a three-block strip between the wing and the outer wall, and nothing said
     * so. The hub looked finished. It was cut in half.
     *
     * The underground ring had this check from the start; the surface one never did, which is
     * the only reason the wing shipped. A walkway you cannot walk is the same bug at either
     * level, so it is now the same check.
     */
    private void auditHubWalkways() {
        int n = 0;
        int y = WorldBuilder.Y;
        int mid = (WorldBuilder.RING_IN + WorldBuilder.RING_OUT) / 2;

        // The perimeter ring: a closed square loop at Chebyshev radius `mid`.
        for (int a = -mid; a <= mid; a++) {
            for (int[] pt : new int[][]{{a, -mid}, {a, mid}, {-mid, a}, {mid, a}}) {
                n += walkable("ring walkway", pt[0], y, pt[1]) ? 1 : 0;
            }
        }

        // The four spokes, from the plaza out to the ring. Starts clear of the watchtower
        // plinth, which is a step up onto the tower rather than a walkway.
        for (int m = 4; m <= WorldBuilder.RING_OUT; m++) {
            for (int[] pt : new int[][]{{0, -m}, {0, m}, {-m, 0}, {m, 0}}) {
                n += walkable("spoke", pt[0], y, pt[1]) ? 1 : 0;
            }
        }
        count("hub walkway tiles", n);
    }

    /** Solid to stand on, two blocks of clear air above. Reports once and returns. */
    private boolean walkable(String what, int x, int y, int z) {
        if (!world.getBlockAt(x, y, z).getType().isSolid()) {
            fail(what, "no floor on the " + what + " here", x, y, z);
            return false;
        }
        for (int dy = 1; dy <= 2; dy++) {
            if (world.getBlockAt(x, y + dy, z).getType().isSolid()) {
                fail(what, "the " + what + " is blocked here", x, y + dy, z);
                return false;
            }
        }
        return true;
    }

    /**
     * You can walk from a mine's lift pad down onto its ore. On foot, without jumping.
     *
     * This is deliberately a reachability test and not a check that the stairs were built
     * where the builder meant to build them. The pit stair WAS built, on every mine, exactly
     * as written — and on all seven north-wall mines the cage landing was then laid straight
     * over its head, so the guard rail ran unbroken the whole way round and the only way into
     * the ore was to jump in and be stuck. Asking "is the stair there?" would have passed.
     * The question worth asking is the player's: can I get down?
     *
     * So it floods the rim from the landing, the way a player walks it — step up or down one,
     * two blocks of headroom — and succeeds only if that flood reaches the ore.
     */
    private void auditMineAccess() {
        int n = 0;
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (!d.hasMine()) continue;
            n++;
            int[] plot = d.plot;
            int lx = d.landing[0], lz = d.landing[2];

            java.util.Set<Long> seen = new java.util.HashSet<>();
            java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
            int startY = standY(lx, lz, MapLayout.MINE_RIM_Y);
            if (startY == Integer.MIN_VALUE) {
                fail("mine access", "mine " + d.rank + ": nowhere to stand on the lift pad",
                        lx, MapLayout.MINE_RIM_Y, lz);
                continue;
            }
            queue.add(new int[]{lx, startY, lz});
            seen.add(key(lx, lz));
            boolean reachedOre = false;

            while (!queue.isEmpty() && !reachedOre) {
                int[] cur = queue.poll();
                for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = cur[0] + step[0], nz = cur[2] + step[1];
                    if (nx < plot[0] || nx > plot[2] || nz < plot[1] || nz > plot[3]) continue;
                    if (!seen.add(key(nx, nz))) continue;
                    int ny = standY(nx, nz, cur[1]);
                    if (ny == Integer.MIN_VALUE) continue;
                    if (ny <= d.oreTop + 1) { reachedOre = true; break; }   // feet on the ore
                    queue.add(new int[]{nx, ny, nz});
                }
            }
            if (!reachedOre) {
                fail("mine access", "mine " + d.rank
                        + ": the pit cannot be entered on foot from the lift pad — "
                        + "no gap in the guard rail leads down to the ore", lx, startY, lz);
            }
        }
        count("mines walked into", n);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /**
     * The y a player standing at (x, z) ends up on, coming from height {@code fromY}: at most
     * one block up or one down, with two blocks of headroom. {@link Integer#MIN_VALUE} if they
     * cannot stand there at all.
     */
    private int standY(int x, int z, int fromY) {
        for (int y : new int[]{fromY + 1, fromY, fromY - 1}) {
            if (!world.getBlockAt(x, y - 1, z).getType().isSolid()) continue;
            if (world.getBlockAt(x, y, z).getType().isSolid()) continue;
            if (world.getBlockAt(x, y + 1, z).getType().isSolid()) continue;
            return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Each mine's walk-down shaft is clear from the ward to the ring, at every step. */
    private void auditShafts() {
        int n = 0;
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (!d.hasMine()) continue;
            int[] s = d.shaft;
            boolean outwardIsZ = d.wall.equals("N") || d.wall.equals("S");
            int step = (d.wall.equals("S") || d.wall.equals("E")) ? 1 : -1;
            int near = outwardIsZ ? (step > 0 ? s[1] : s[3]) : (step > 0 ? s[0] : s[2]);
            int far = outwardIsZ ? (step > 0 ? s[3] : s[1]) : (step > 0 ? s[2] : s[0]);
            int mid = outwardIsZ ? (s[0] + s[2]) / 2 : (s[1] + s[3]) / 2;

            int drop = WorldBuilder.Y - MapLayout.MINE_RIM_Y;
            int length = Math.abs(far - near);
            int blocked = 0;

            for (int i = 0; i <= length; i++) {
                int out = near + step * i;
                int floorY = WorldBuilder.Y - Math.min(i, drop);
                int x = outwardIsZ ? mid : out;
                int z = outwardIsZ ? out : mid;

                // Head height must be clear, and there must be something underfoot.
                if (world.getBlockAt(x, floorY + 1, z).getType().isSolid()
                        || world.getBlockAt(x, floorY + 2, z).getType().isSolid()) {
                    if (blocked++ == 0) {
                        fail("shaft", "mine " + d.rank + " shaft is blocked", x, floorY + 1, z);
                    }
                }
                if (world.getBlockAt(x, floorY - 1, z).getType().isAir()
                        && world.getBlockAt(x, floorY, z).getType().isAir()) {
                    fail("shaft", "mine " + d.rank + " shaft has a hole in the floor", x, floorY, z);
                    break;
                }
                n++;
            }
        }
        count("shaft tiles", n);
    }

    /** The ring concourse is continuous — you can walk the whole loop. */
    private void auditRing() {
        int n = 0, y = MapLayout.MINE_RIM_Y;
        for (int[] leg : MapLayout.RING) {
            boolean alongX = (leg[2] - leg[0]) >= (leg[3] - leg[1]);
            int lo = alongX ? leg[0] : leg[1];
            int hi = alongX ? leg[2] : leg[3];
            int mid = alongX ? (leg[1] + leg[3]) / 2 : (leg[0] + leg[2]) / 2;

            for (int a = lo; a <= hi; a += 2) {
                int x = alongX ? a : mid;
                int z = alongX ? mid : a;
                if (world.getBlockAt(x, y + 1, z).getType().isSolid()) {
                    fail("ring", "the ring concourse is blocked here", x, y + 1, z);
                }
                if (!world.getBlockAt(x, y, z).getType().isSolid()) {
                    fail("ring", "the ring concourse has no floor here", x, y, z);
                }
                n++;
            }
        }
        count("ring tiles", n);
    }

    /**
     * Nowhere a player is put down has them inside a block.
     *
     * Warping into Mine A and immediately suffocating is exactly this check failing, and it
     * covers every spawn, landing and lift pad at once rather than the one somebody tried.
     */
    private void auditSpawns() {
        Map<String, Location> spots = new LinkedHashMap<>();
        spots.put("hub spawn", builder.getHubSpawn());
        spots.put("starter spawn", builder.getStarterSpawn());
        spots.put("jail", builder.jailSpawn());
        spots.put("enchanter", builder.enchanterLocation());
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (d.hasMine()) spots.put("mine " + d.rank + " landing", builder.safeMineSpot(d.rank));
        }

        for (Map.Entry<String, Location> e : spots.entrySet()) {
            Location l = e.getValue();
            if (l == null) continue;
            int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            if (world.getBlockAt(x, y, z).getType().isSolid()
                    || world.getBlockAt(x, y + 1, z).getType().isSolid()) {
                fail("spawn", e.getKey() + " is inside a block — you would suffocate", x, y, z);
            }
            if (!world.getBlockAt(x, y - 1, z).getType().isSolid()) {
                fail("spawn", e.getKey() + " has nothing underfoot — you would fall", x, y - 1, z);
            }
        }
        count("spawn points", spots.size());
        auditLiftPads();
    }

    /**
     * A lift pad is the block you STAND ON, not a place a player is put down, so the rules
     * are the other way round: the pad itself must be solid, and the space above it clear.
     *
     * The first version of this audit checked pads as if they were spawn points and reported
     * all 52 of them as fatal — "inside a block, you would suffocate" — which was the audit
     * being wrong, not the world. A check that cries wolf on every pad is worse than no check,
     * because the real findings are then buried in its noise.
     */
    private void auditLiftPads() {
        int n = 0;
        for (Map.Entry<Location, String> pad : builder.getLiftPads().entrySet()) {
            Location l = pad.getKey();
            int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            if (!world.getBlockAt(x, y, z).getType().isSolid()) {
                fail("lift", "the pad for " + pad.getValue() + " is not solid — "
                        + "nobody can stand on it to trigger it", x, y, z);
            }
            for (int dy = 1; dy <= 2; dy++) {
                if (world.getBlockAt(x, y + dy, z).getType().isSolid()) {
                    fail("lift", "the pad for " + pad.getValue() + " is buried — "
                            + "there is no room to stand on it", x, y + dy, z);
                    break;
                }
            }
            n++;
        }
        count("lift pads", n);
    }

    /**
     * Every sign that was requested is still there.
     *
     * A wall sign with no solid block behind it is removed by the server the next time that
     * chunk revalidates, which is why signs appeared blank or vanished: they were placed,
     * and then quietly culled. Comparing what was asked for against what survived catches it.
     */
    private void auditSigns() {
        int n = 0, missing = 0;
        for (int[] p : builder.getRequestedSignPositions()) {
            Block b = world.getBlockAt(p[0], p[1], p[2]);
            if (!b.getType().toString().contains("SIGN")) {
                if (missing++ < 8) {
                    fail("sign", "a sign was placed here but is gone (nothing solid behind it)",
                            p[0], p[1], p[2]);
                }
            }
            n++;
        }
        if (missing >= 8) {
            fail("sign", "...and " + (missing - 8) + " more culled signs", 0, 0, 0);
        }
        count("signs", n);
    }

    /** Every cell is enclosed, has a floor, and has a way in. */
    private void auditCells() {
        int n = 0;
        for (CellManager.CellRect c : builder.getCellRects().values()) {
            int y = c.y();
            boolean floor = true, wayIn = false;
            for (int x = c.x1(); x <= c.x2(); x++) {
                for (int z = c.z1(); z <= c.z2(); z++) {
                    if (!world.getBlockAt(x, y, z).getType().isSolid()) floor = false;
                }
            }
            // A gap anywhere on the perimeter at head height is the door.
            for (int x = c.x1(); x <= c.x2() && !wayIn; x++) {
                for (int z = c.z1(); z <= c.z2() && !wayIn; z++) {
                    boolean edge = x == c.x1() || x == c.x2() || z == c.z1() || z == c.z2();
                    if (edge && !world.getBlockAt(x, y + 1, z).getType().isSolid()) wayIn = true;
                }
            }
            if (!floor) fail("cell", "cell #" + c.number() + " has a hole in its floor",
                    c.x1(), y, c.z1());
            if (!wayIn) fail("cell", "cell #" + c.number() + " has no door", c.x1(), y + 1, c.z1());
            n++;
        }
        count("cells", n);
    }

    /**
     * No bedrock is visible from anywhere a player stands.
     *
     * Bedrock is the structural backstop everywhere on this map, and seeing it is the single
     * thing that breaks the illusion hardest. Samples the walkable plane rather than every
     * block, which is enough: exposed bedrock is never one isolated block.
     */
    private void auditExposedBedrock() {
        int n = 0, found = 0;
        List<int[]> planes = new ArrayList<>();
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (d.hasMine()) planes.add(new int[]{d.plot[0], d.plot[1], d.plot[2], d.plot[3],
                    MapLayout.MINE_RIM_Y});
        }
        planes.add(new int[]{MapLayout.HUB[0], MapLayout.HUB[1], MapLayout.HUB[2], MapLayout.HUB[3],
                WorldBuilder.Y});

        for (int[] p : planes) {
            for (int x = p[0]; x <= p[2]; x += 3) {
                for (int z = p[1]; z <= p[3]; z += 3) {
                    for (int dy = 0; dy <= 3; dy++) {
                        if (world.getBlockAt(x, p[4] + dy, z).getType() == Material.BEDROCK) {
                            if (found++ < 6) {
                                fail("bedrock", "bedrock is visible from the walkway",
                                        x, p[4] + dy, z);
                            }
                        }
                        n++;
                    }
                }
            }
        }
        if (found >= 6) fail("bedrock", "...and " + (found - 6) + " more exposed bedrock blocks", 0, 0, 0);
        count("bedrock samples", n);
    }

    /**
     * Nowhere players stand is dark enough to spawn mobs.
     *
     * Two separate places, because they fail for different reasons and the fix differs.
     * The first version sampled the whole rim rectangle — which includes the pit in its
     * middle — and reported everything as "walkway is dark". Every single point it named
     * was actually over the ore, so the label sent me looking at the wrong thing twice.
     */
    private void auditLighting() {
        int n = 0, darkWalk = 0, darkPit = 0;
        for (RankMineData.Def d : RankMineData.RANKS.values()) {
            if (!d.hasMine()) continue;
            int[] rim = d.rim, pit = d.pit;

            // The walkway: the rim band, explicitly NOT the hole in the middle of it.
            for (int x = rim[0]; x <= rim[2]; x += 6) {
                for (int z = rim[1]; z <= rim[3]; z += 6) {
                    if (x >= pit[0] && x <= pit[2] && z >= pit[1] && z <= pit[3]) continue;
                    Block b = world.getBlockAt(x, MapLayout.MINE_RIM_Y + 1, z);
                    if (!b.getType().isAir()) continue;
                    if (b.getLightLevel() < 8 && darkWalk++ < 4) {
                        fail("light", "mine " + d.rank + " WALKWAY is dark (level "
                                + b.getLightLevel() + ")", x, MapLayout.MINE_RIM_Y + 1, z);
                    }
                    n++;
                }
            }

            // The ore face: the first air block above the ore, which is where a player
            // stands to mine and where anything hostile would spawn.
            for (int x = pit[0] + 2; x <= pit[2] - 2; x += 6) {
                for (int z = pit[1] + 2; z <= pit[3] - 2; z += 6) {
                    Block b = world.getBlockAt(x, d.oreTop + 1, z);
                    if (!b.getType().isAir()) continue;
                    if (b.getLightLevel() < 8 && darkPit++ < 4) {
                        fail("light", "mine " + d.rank + " ORE FACE is dark (level "
                                + b.getLightLevel() + ")", x, d.oreTop + 1, z);
                    }
                    n++;
                }
            }
        }
        if (darkWalk > 4) fail("light", "...and " + (darkWalk - 4) + " more dark walkway tiles", 0, 0, 0);
        if (darkPit > 4) fail("light", "...and " + (darkPit - 4) + " more dark ore tiles", 0, 0, 0);
        count("light samples", n);
    }

    // ==================================================================================

    /** Prints the result. Same output whether a player asked or the build did. */
    public void report(CommandSender to) {
        to.sendMessage("§8§m                                        ");
        to.sendMessage("§6§lMAP AUDIT");
        for (Map.Entry<String, Integer> e : checked.entrySet()) {
            to.sendMessage("§8  checked §7" + String.format("%,d", e.getValue()) + " " + e.getKey());
        }

        if (findings.isEmpty()) {
            to.sendMessage("§a§lPASS §7— every doorway open, every shaft clear, nothing");
            to.sendMessage("§7standing in a block, no culled signs, no visible bedrock.");
            return;
        }

        Map<String, Integer> byCategory = new LinkedHashMap<>();
        for (Finding f : findings) byCategory.merge(f.category(), 1, Integer::sum);

        to.sendMessage("§c§lFAIL §7— " + findings.size() + " problem(s):");
        for (Map.Entry<String, Integer> e : byCategory.entrySet()) {
            to.sendMessage("§c  " + e.getValue() + "x §7" + e.getKey());
        }
        to.sendMessage("§8  ---");
        int shown = 0;
        for (Finding f : findings) {
            if (shown++ >= 20) {
                to.sendMessage("§8  ...and " + (findings.size() - 20) + " more (see console)");
                break;
            }
            to.sendMessage("§7  " + f);
        }
    }
}
