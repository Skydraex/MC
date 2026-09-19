#!/usr/bin/env python3
"""
Regenerates RankMineData.java and MapLayout.java from tools/layout_gen.py's
validated geometry, while preserving every rank's existing economic fields
(cost, next, filler, common, rare, fillerPrice, commonPrice, rarePrice) by
extracting them out of the CURRENT RankMineData.java via regex first.

Run from the tools/ directory: `cd tools && python3 gen_java.py`
(it does `from layout_gen import get_layout`, a relative import, same as
layout_gen.py's own __main__ block and map_check.py both require).
"""
import re
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, HERE)
from layout_gen import get_layout  # noqa: E402

RANK_DATA_PATH = os.path.join(REPO, "src/main/java/com/lukeprison/prison/RankMineData.java")
MAP_LAYOUT_PATH = os.path.join(REPO, "src/main/java/com/lukeprison/prison/MapLayout.java")

RANKS_ORDER = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")

# ---------------------------------------------------------------------------
# Step 1: extract existing economic fields for every rank (including FREE)
# ---------------------------------------------------------------------------

PUT_RE = re.compile(
    r'RANKS\.put\("(?P<rank>\w+)",\s*new Def\(\s*'
    r'"(?P<rank2>\w+)",\s*(?P<cost>\d+),\s*"(?P<next>\w+)",\s*'
    r'"(?P<filler>\w+)",\s*"(?P<common>\w+)",\s*"(?P<rare>\w+)",\s*'
    r'(?P<fillerPrice>[\d.]+),\s*(?P<commonPrice>[\d.]+),\s*(?P<rarePrice>[\d.]+),\s*'
    r'"(?P<wall>\w+)",'
)

existing = {}
with open(RANK_DATA_PATH) as f:
    src = f.read()

for m in PUT_RE.finditer(src):
    d = m.groupdict()
    existing[d["rank"]] = {
        "cost": int(d["cost"]),
        "next": d["next"],
        "filler": d["filler"],
        "common": d["common"],
        "rare": d["rare"],
        "fillerPrice": float(d["fillerPrice"]),
        "commonPrice": float(d["commonPrice"]),
        "rarePrice": float(d["rarePrice"]),
    }

missing = [r for r in RANKS_ORDER + ["FREE"] if r not in existing]
if missing:
    print("ERROR: could not extract economic fields for:", missing)
    sys.exit(1)

# ---------------------------------------------------------------------------
# Step 2: pull the new, validated geometry out of layout_gen.get_layout()
# ---------------------------------------------------------------------------

layout = get_layout()
if layout["overlap_violations"] or layout["text_zone_violations"]:
    print("ERROR: layout_gen reports violations, refusing to generate Java from a bad layout")
    sys.exit(1)

regions = layout["regions"]
gates = layout["gates"]
hub_half = layout["hub_half"]
hub = layout["hub"]
specials_cfg = layout["specials"]


def r_int(rect):
    return [int(round(v)) for v in rect]


HUB_HALF = int(round(hub_half))
HUB = [-HUB_HALF, -HUB_HALF, HUB_HALF, HUB_HALF]

# Mine height band, unchanged from the previous file (a fixed vertical slab under the
# hub floor level Y=95; not part of the 2D radial layout tools/layout_gen.py governs).
MINE_Y1, MINE_Y2 = 95, 125

# ---------------------------------------------------------------------------
# Step 3: build each rank's Def geometry: mine rect (x1,y1,z1,x2,y2,z2) and
# ward rect (wx1,wz1,wx2,wz2), from regions["mine_<rank>"] / ["ward_<rank>"].
# ---------------------------------------------------------------------------

def def_line(rank):
    e = existing[rank]
    if rank == "FREE":
        return (
            f'        RANKS.put("FREE", new Def("FREE",{e["cost"]},"FREE","AIR","AIR","AIR",0,0,0,"N",'
            f'0,0,0,0,0,0,0,0,0,0));'
        )
    mine = r_int(regions[f"mine_{rank}"])
    ward = r_int(regions[f"ward_{rank}"])
    wall = gates[rank]["wall"]
    x1, z1, x2, z2 = mine
    wx1, wz1, wx2, wz2 = ward
    return (
        f'        RANKS.put("{rank}", new Def("{rank}",{e["cost"]},"{e["next"]}",'
        f'"{e["filler"]}","{e["common"]}","{e["rare"]}",'
        f'{e["fillerPrice"]},{e["commonPrice"]},{e["rarePrice"]},"{wall}",'
        f'{x1},{MINE_Y1},{z1},{x2},{MINE_Y2},{z2},'
        f'{wx1},{wz1},{wx2},{wz2}));'
    )


rank_lines = "\n".join(def_line(r) for r in RANKS_ORDER)
free_line = def_line("FREE")

RANK_MINE_DATA_JAVA = f"""package com.lukeprison.prison;
import java.util.LinkedHashMap;
import java.util.Map;
public class RankMineData {{
    /** A mine's full rectangle (excavated ore room), and the ward rectangle directly
     *  between it and its hub gate. Both are baked in from the validated radial-hub
     *  layout (tools/layout_gen.py) — WorldBuilder never re-derives these numbers,
     *  it only builds exactly what's here. */
    public static class Def {{
        public final String rank, next, filler, common, rare, wall;
        public final int cost, x1,y1,z1,x2,y2,z2, wx1,wz1,wx2,wz2;
        public final double fillerPrice, commonPrice, rarePrice;
        public Def(String rank,int cost,String next,String filler,String common,String rare,
                   double fillerPrice,double commonPrice,double rarePrice,String wall,
                   int x1,int y1,int z1,int x2,int y2,int z2,
                   int wx1,int wz1,int wx2,int wz2){{
            this.rank=rank;this.cost=cost;this.next=next;this.filler=filler;this.common=common;this.rare=rare;
            this.fillerPrice=fillerPrice;this.commonPrice=commonPrice;this.rarePrice=rarePrice;this.wall=wall;
            this.x1=x1;this.y1=y1;this.z1=z1;this.x2=x2;this.y2=y2;this.z2=z2;
            this.wx1=wx1;this.wz1=wz1;this.wx2=wx2;this.wz2=wz2;
        }}
    }}
    public static final Map<String, Def> RANKS = new LinkedHashMap<>();
    static {{
{rank_lines}
{free_line}
    }}
}}
"""

# ---------------------------------------------------------------------------
# Step 4: MapLayout.java — HUB, CORRIDOR_LEN/WARD_DEPTH, INTAKE + STARTER
# placement (sized/placed relative to the NEW, smaller hub), and SPECIALS.
# ---------------------------------------------------------------------------

CORRIDOR_LEN = int(round(regions["corridor_A"][3] - regions["corridor_A"][1])) if False else None
# (CORRIDOR_LEN/WARD_DEPTH are constants in layout_gen, re-import them directly instead
#  of trying to reverse-engineer them from a region — see below.)
import layout_gen  # noqa: E402
CORRIDOR_LEN = layout_gen.CORRIDOR_LEN
WARD_DEPTH = layout_gen.WARD_DEPTH

# Find the clear "corner buffer" gap on the west wall (the wall INTAKE enters through),
# nearest the NORTH corner, by looking at the actual placed regions on that wall — don't
# assume geometry, derive the gap from the real numbers, then double check it against
# every region (including specials) below.
w_wall_items = [r_int(v) for k, v in regions.items() if k.startswith(("ward_", "mine_", "room_", "corridor_"))
                and layout_gen.assignment.get(k.split("_", 1)[1]) == "W"]
# Also include the CELLS special's own regions (assignment dict only covers ranks).
w_wall_items += [r_int(v) for k, v in regions.items() if k.endswith("_CELLS")]
min_z_used = min(item[1] for item in w_wall_items)
hub_w_x = HUB[0]

# Intake gate sits in the clear band between the hub's NW corner and the first thing
# placed on the west wall (min_z_used), well inside it with margin on both sides.
clear_top = -HUB_HALF
clear_bottom = min_z_used
INTAKE_GATE_Z = int(round((clear_top + clear_bottom) / 2 - (clear_bottom - clear_top) * 0.25))
# ^ bias toward the corner (away from the first mine on this wall) for extra margin, since
# STARTER's east-wall "SKY PRISON" banner (BlockFont, 10 chars wide) needs ~62 blocks of
# clearance along Z and must not creep toward the first mine ward on this wall.

intake_half = max(6, WARD_DEPTH)  # full intake gate width, matches ward-scale proportions
INTAKE_WARD = [hub_w_x - CORRIDOR_LEN - WARD_DEPTH, INTAKE_GATE_Z - intake_half,
               hub_w_x - CORRIDOR_LEN, INTAKE_GATE_Z + intake_half]
INTAKE_CORRIDOR = [hub_w_x - CORRIDOR_LEN, INTAKE_GATE_Z - 3,
                    hub_w_x, INTAKE_GATE_Z + 3]

# STARTER's east wall carries the giant "SKY PRISON" BlockFont banner (10 chars x (5+1)-1 =
# 59 blocks wide), rendered along STARTER's Z dimension — so STARTER's Z-span (its "height")
# must comfortably exceed 59 blocks, not just be "proportionally smaller" than the old 80.
STARTER_GAP = 10
STARTER_W, STARTER_H = 60, 90
STARTER = [INTAKE_WARD[0] - STARTER_GAP - STARTER_W, INTAKE_GATE_Z - STARTER_H // 2,
           INTAKE_WARD[0] - STARTER_GAP, INTAKE_GATE_Z + STARTER_H // 2]

# ---- Verify STARTER/INTAKE_WARD/INTAKE_CORRIDOR don't overlap ANY region (mines,
# wards, corridors, special rooms) nor the hub itself, using the same overlap logic
# tools/layout_gen.py and map_check.py use. ----

def norm(r):
    x1, z1, x2, z2 = r
    return (min(x1, x2), min(z1, z2), max(x1, x2), max(z1, z2))


def overlaps(a, b):
    a = norm(a); b = norm(b)
    return not (a[2] <= b[0] or b[2] <= a[0] or a[3] <= b[1] or b[3] <= a[1])


all_check = {"HUB": hub, **regions}
new_rects = {"STARTER": STARTER, "INTAKE_WARD": INTAKE_WARD, "INTAKE_CORRIDOR": INTAKE_CORRIDOR}
bad = []
for nname, nrect in new_rects.items():
    for ename, erect in all_check.items():
        if ename == "HUB" and nname == "INTAKE_CORRIDOR":
            continue  # INTAKE_CORRIDOR is meant to touch/enter the hub, not a violation
        if overlaps(nrect, erect):
            bad.append((nname, ename))

if bad:
    print("ERROR: new STARTER/INTAKE placement collides with existing regions:")
    for a, b in bad:
        print(" ", a, "<->", b)
    sys.exit(1)
else:
    print(f"OK: STARTER/INTAKE_WARD/INTAKE_CORRIDOR verified clear of all {len(regions)} regions + HUB.")

print(f"HUB_HALF={HUB_HALF}  HUB={HUB}")
print(f"INTAKE_GATE_Z={INTAKE_GATE_Z}  clear band on W wall: [{clear_top:.0f}, {clear_bottom:.0f}]")
print(f"INTAKE_WARD={INTAKE_WARD}")
print(f"INTAKE_CORRIDOR={INTAKE_CORRIDOR}")
print(f"STARTER={STARTER}  size={STARTER_W}x{STARTER_H}")

specials_java = []
for wall, (name, half, depth) in specials_cfg.items():
    ward = r_int(regions[f"ward_{name}"])
    room = r_int(regions[f"room_{name}"])
    specials_java.append(
        f'        new Special("{name}", "{wall}", new int[]{{{ward[0]},{ward[1]},{ward[2]},{ward[3]}}}, '
        f'new int[]{{{room[0]},{room[1]},{room[2]},{room[3]}}}),'
    )
specials_block = "\n".join(specials_java)

MAP_LAYOUT_JAVA = f"""package com.lukeprison.prison;
/** The radial hub's fixed geometry — hub bounds, the player-intake path, and the
 *  four non-mine "special" gates (fishing, crates, yard, cells), each built exactly
 *  like a mine's own gate: a direct corridor off the hub wall, a ward/antechamber,
 *  then its own room. All baked in from the validated layout (tools/layout_gen.py);
 *  WorldBuilder only builds what's here, never recomputes it. */
public class MapLayout {{
    public static final int[] HUB = {{{HUB[0]},{HUB[1]},{HUB[2]},{HUB[3]}}};
    public static final int CORRIDOR_LEN = {CORRIDOR_LEN}, WARD_DEPTH = {WARD_DEPTH};

    // Player intake enters through the hub's west wall, well inside the empty corner
    // buffer — guaranteed clear of every mine/special by construction (verified by
    // tools/gen_java.py against every region before this file is written).
    public static final int INTAKE_GATE_Z = {INTAKE_GATE_Z};
    public static final int[] INTAKE_WARD = {{{INTAKE_WARD[0]},{INTAKE_WARD[1]},{INTAKE_WARD[2]},{INTAKE_WARD[3]}}};
    public static final int[] INTAKE_CORRIDOR = {{{INTAKE_CORRIDOR[0]},{INTAKE_CORRIDOR[1]},{INTAKE_CORRIDOR[2]},{INTAKE_CORRIDOR[3]}}};
    public static final int[] STARTER = {{{STARTER[0]},{STARTER[1]},{STARTER[2]},{STARTER[3]}}};

    public static class Special {{
        public final String name, wall;
        public final int[] ward, room;
        public Special(String name, String wall, int[] ward, int[] room) {{
            this.name = name; this.wall = wall; this.ward = ward; this.room = room;
        }}
    }}
    public static final Special[] SPECIALS = {{
{specials_block}
    }};

    public static Special special(String name) {{
        for (Special s : SPECIALS) if (s.name.equals(name)) return s;
        throw new IllegalArgumentException(name);
    }}
}}
"""

with open(RANK_DATA_PATH, "w") as f:
    f.write(RANK_MINE_DATA_JAVA)
with open(MAP_LAYOUT_PATH, "w") as f:
    f.write(MAP_LAYOUT_JAVA)

print(f"\nWrote {RANK_DATA_PATH}")
print(f"Wrote {MAP_LAYOUT_PATH}")
