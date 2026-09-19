"""
Single source of truth for Sky Prison's geometry.

Design (v3 — "cage lift" layout)
--------------------------------
The previous two layouts both tried to seat every mine directly against the hub
wall. That is geometrically impossible at a walkable hub size: 26 mines need
roughly 55 blocks of wall frontage each, so ~1430 blocks of perimeter, and a
square hub only offers 4*S — forcing S >= 357 however the mines are staggered.
(Staggering into depth rings does not rescue it, because a 60-wide near-ring
mine sits across the corridor to everything behind it.)

So this layout breaks the link between hub perimeter and mine footprint:

  hub wall gate -> short corridor -> ward (antechamber, holds the mine cage)
                                       |
                                       v  lift
                              mine entrance platform, on the rim of an
                              open pit on the underground mine level

The hub perimeter now only has to carry GATES, not mines. That makes the hub
136x136 (about 24s to sprint across, against 82s for the 462 version) while
mines keep their full 24->60 footprints and can grow later for free.

Mines are open pits: a bedrock-floored, bedrock-walled box with an ore band in
it and open air above, so players drop in and mine downward — the classic
prison mine — rather than tunnelling horizontally into a solid cube.

Everything here is pure geometry with no Minecraft dependency, so it can be
validated (tools/map_check.py) and baked into Java (tools/gen_java.py) without
a server. WorldBuilder.java only ever builds what these numbers say.
"""

RANKS = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")


def mine_width(i):
    """Mine A is 24x24, Mine Z is 60x60, linear in between."""
    return 24 + int(i * (60 - 24) / (len(RANKS) - 1))


WIDTHS = {r: mine_width(i) for i, r in enumerate(RANKS)}
MAX_W = max(WIDTHS.values())

# ---------------------------------------------------------------------------
# Hub and its gates
# ---------------------------------------------------------------------------

HUB_HALF = 68                  # hub interior is 136 x 136, -68..68 on both axes
HUB = (-HUB_HALF, -HUB_HALF, HUB_HALF, HUB_HALF)

CORRIDOR_LEN = 6               # hub wall -> ward
WARD_DEPTH = 12                # ward antechamber depth (holds the cage + signage)
GATE_W = 7                     # clear width of a gate opening
GATE_PITCH = 12                # centre-to-centre spacing of gates along a wall
CORNER_BUFFER = 16             # clear wall length kept at each corner

WALLS = ["N", "E", "S", "W"]

# Which gates sit on which wall. 26 mines + 3 room specials + the intake = 30
# gates, and a 136 wall at pitch 12 with 16-block corners holds 8 per wall.
#
# Cells are NOT here: they are a wing built inside the hub itself, so they need
# no gate and cannot block one.
WALL_GATES = {
    "N": list("ABCDEFG") + ["FISHING"],
    "E": list("HIJKLMN") + ["CRATES"],
    "S": list("OPQRSTU") + ["YARD"],
    "W": list("VWXYZ") + ["INTAKE"],
}

# Non-mine gates that open into a room at hub level, rather than onto a lift.
# name -> (half width along the wall, room depth outward from the ward)
ROOM_SPECIALS = {
    "FISHING": (24, 8),        # a short lobby; the real grounds lie beyond it
    "CRATES": (16, 22),
    "YARD": (22, 40),
}

assignment = {g: w for w, gates in WALL_GATES.items() for g in gates}

# ---------------------------------------------------------------------------
# Underground mine level
# ---------------------------------------------------------------------------

MINE_ORE_BOTTOM = 40           # lowest ore layer
MINE_ORE_TOP = 68              # highest ore layer (29-block ore band)
MINE_RIM_Y = MINE_ORE_TOP + 2  # walkable rim around the pit; the lift lands here
MINE_GRID_COLS = 6             # 26 mines tile into a 6 x 5 grid
MINE_GRID_PITCH = MAX_W + 16   # widest mine plus a clear margin on every side

regions, text_zones, gates, lifts = {}, {}, {}, {}


def _wall_axis_rects(wall, along_lo, along_hi, out_near, out_far):
    """Convert an (along-the-wall, outward-from-the-wall) span into a world rect.

    "Outward" is measured as distance from the hub wall, so every wall uses the
    same positive numbers and no caller ever hand-picks an axis or a sign.
    """
    if wall == "N":
        return (along_lo, HUB[1] - out_far, along_hi, HUB[1] - out_near)
    if wall == "S":
        return (along_lo, HUB[3] + out_near, along_hi, HUB[3] + out_far)
    if wall == "E":
        return (HUB[2] + out_near, along_lo, HUB[2] + out_far, along_hi)
    return (HUB[0] - out_far, along_lo, HUB[0] - out_near, along_hi)


def gate_positions(wall):
    """Centre coordinate, along the wall, of each gate on that wall."""
    n = len(WALL_GATES[wall])
    span = (n - 1) * GATE_PITCH
    start = -span / 2
    return [start + i * GATE_PITCH for i in range(n)]


def place_wall(wall):
    for name, centre in zip(WALL_GATES[wall], gate_positions(wall)):
        half = GATE_W / 2
        # Corridor: hub wall out to the ward.
        regions[f"corridor_{name}"] = _wall_axis_rects(
            wall, centre - half, centre + half, 0, CORRIDOR_LEN)

        # Ward: the antechamber every gate opens into.
        ward_half = GATE_PITCH / 2 - 1
        regions[f"ward_{name}"] = _wall_axis_rects(
            wall, centre - ward_half, centre + ward_half,
            CORRIDOR_LEN, CORRIDOR_LEN + WARD_DEPTH)

        # Nameplate zone: sits on the hub-facing side of the gate, INSIDE the
        # hub, so you can read where a gate goes before you walk through it.
        text_zones[f"label_{name}"] = _wall_axis_rects(
            wall, centre - ward_half, centre + ward_half, -3, -1)

        gates[name] = {
            "wall": wall,
            "centre": centre,
            "width": GATE_W,
            "kind": "mine" if name in WIDTHS else ("intake" if name == "INTAKE" else "room"),
        }

        if name in ROOM_SPECIALS:
            sp_half, depth = ROOM_SPECIALS[name]
            near = CORRIDOR_LEN + WARD_DEPTH
            regions[f"room_{name}"] = _wall_axis_rects(
                wall, centre - sp_half, centre + sp_half, near, near + depth)


for _w in WALLS:
    place_wall(_w)

# ---------------------------------------------------------------------------
# The intake path: starter yard -> intake ward -> hub, on the W wall's own gate
# ---------------------------------------------------------------------------

_intake_centre = gates["INTAKE"]["centre"]
STARTER_W, STARTER_H = 70, 90
_starter_near = CORRIDOR_LEN + WARD_DEPTH + 12
STARTER = _wall_axis_rects("W", _intake_centre - STARTER_H / 2, _intake_centre + STARTER_H / 2,
                           _starter_near, _starter_near + STARTER_W)
regions["room_STARTER"] = STARTER
regions["corridor_STARTERLINK"] = _wall_axis_rects(
    "W", _intake_centre - 3, _intake_centre + 3,
    CORRIDOR_LEN + WARD_DEPTH, _starter_near)

# ---------------------------------------------------------------------------
# The grounds: fishing, logging and farm clustered around one shared green,
# reached through the FISHING gate's lobby on the north wall.
# ---------------------------------------------------------------------------

_fish_centre = gates["FISHING"]["centre"]
_grounds_near = CORRIDOR_LEN + WARD_DEPTH + ROOM_SPECIALS["FISHING"][1]
GREEN_HALF, GREEN_DEPTH = 34, 60          # the shared central green
GROUNDS = {}


def _place_grounds():
    """Fishing, logging and farm sit around a shared green rather than in a line.

    The green is directly beyond the fishing lobby; the three zones ring it on
    its west, north and east sides, so every one of them is a few seconds from
    the others and none is behind another.
    """
    near = _grounds_near
    regions["room_GREEN"] = _wall_axis_rects(
        "N", _fish_centre - GREEN_HALF, _fish_centre + GREEN_HALF, near, near + GREEN_DEPTH)

    zone_w, zone_d = 56, 56
    far = near + GREEN_DEPTH
    specs = {
        # name: (along-centre offset from the green, near depth, far depth)
        "PONDS":   (-GREEN_HALF - zone_w / 2 - 4, near + 2, near + 2 + zone_d),
        "LOGGING": (0, far + 4, far + 4 + zone_d),
        "FARM":    (GREEN_HALF + zone_w / 2 + 4, near + 2, near + 2 + zone_d),
    }
    for name, (along_off, d_near, d_far) in specs.items():
        centre = _fish_centre + along_off
        half = zone_w / 2
        r = _wall_axis_rects("N", centre - half, centre + half, d_near, d_far)
        regions[f"room_{name}"] = r
        GROUNDS[name] = r
        # Each zone opens onto the green with its own short link, spanning ONLY
        # the gap between the two — never reaching into either, or it registers
        # as an overlap (and in-world would carve a corridor through the zone).
        if name == "LOGGING":
            link = _wall_axis_rects("N", centre - 4, centre + 4, far, far + 4)
        else:
            if along_off < 0:                       # zone sits west of the green
                a_lo, a_hi = centre + half, _fish_centre - GREEN_HALF
            else:                                   # zone sits east of the green
                a_lo, a_hi = _fish_centre + GREEN_HALF, centre - half
            link = _wall_axis_rects("N", a_lo, a_hi,
                                    near + GREEN_DEPTH / 2 - 4, near + GREEN_DEPTH / 2 + 4)
        regions[f"corridor_{name}LINK"] = link


_place_grounds()

# ---------------------------------------------------------------------------
# Mines: open pits on the underground level, one per rank, on a tidy grid.
# Horizontal position does not affect walking time, because every mine is
# reached by its ward's cage lift, so the grid is purely about not overlapping.
# ---------------------------------------------------------------------------


def _place_mines():
    for i, rank in enumerate(RANKS):
        col, row = i % MINE_GRID_COLS, i // MINE_GRID_COLS
        cx = (col - (MINE_GRID_COLS - 1) / 2) * MINE_GRID_PITCH
        cz = (row - 2) * MINE_GRID_PITCH
        half = WIDTHS[rank] / 2
        pit = (cx - half, cz - half, cx + half, cz + half)
        regions[f"mine_{rank}"] = pit

        # The rim walkway wraps the pit; the lift lands on its north edge.
        rim = (pit[0] - 4, pit[1] - 4, pit[2] + 4, pit[3] + 4)
        regions[f"rim_{rank}"] = rim
        lifts[rank] = {
            "ward": f"ward_{rank}",
            "landing": (cx, MINE_RIM_Y, pit[1] - 2),
            "pit": pit,
            "rim": rim,
            "ore_bottom": MINE_ORE_BOTTOM,
            "ore_top": MINE_ORE_TOP,
        }


_place_mines()

# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------


def norm(r):
    x1, z1, x2, z2 = r
    return (min(x1, x2), min(z1, z2), max(x1, x2), max(z1, z2))


def overlaps(a, b):
    a, b = norm(a), norm(b)
    return not (a[2] <= b[0] or b[2] <= a[0] or a[3] <= b[1] or b[3] <= a[1])


def _same_feature(a, b):
    """Two regions belonging to the same gate/room are allowed to touch."""
    return a.split("_", 1)[1] == b.split("_", 1)[1]


def _is_underground(name):
    return name.startswith(("mine_", "rim_"))


def _find_overlaps():
    names = list(regions.keys())
    bad = []
    for i, a in enumerate(names):
        for b in names[i + 1:]:
            if _same_feature(a, b):
                continue
            # The mine level is a different Y band entirely, so a mine rect may
            # share x/z with a surface rect without any real collision.
            if _is_underground(a) != _is_underground(b):
                continue
            if overlaps(regions[a], regions[b]):
                bad.append((a, b))
    return bad


def _find_text_overlaps():
    names = list(text_zones.keys())
    return [(a, b) for i, a in enumerate(names) for b in names[i + 1:]
            if overlaps(text_zones[a], text_zones[b])]


bad = _find_overlaps()
tbad = _find_text_overlaps()


def get_layout():
    """The whole validated layout as plain dicts — consumed by gen_java.py
    (which bakes it into Java) and map_check.py (which re-validates it), so the
    two can never drift apart."""
    return {
        "hub": list(HUB),
        "hub_half": HUB_HALF,
        "regions": {k: list(v) for k, v in regions.items()},
        "text_zones": {k: list(v) for k, v in text_zones.items()},
        "gates": gates,
        "lifts": lifts,
        "assignment": assignment,
        "widths": WIDTHS,
        "wall_gates": WALL_GATES,
        "room_specials": ROOM_SPECIALS,
        "grounds": {k: list(v) for k, v in GROUNDS.items()},
        "starter": list(STARTER),
        "corridor_len": CORRIDOR_LEN,
        "ward_depth": WARD_DEPTH,
        "gate_w": GATE_W,
        "mine_ore_bottom": MINE_ORE_BOTTOM,
        "mine_ore_top": MINE_ORE_TOP,
        "mine_rim_y": MINE_RIM_Y,
        "overlap_violations": bad,
        "text_zone_violations": tbad,
    }


if __name__ == "__main__":
    print(f"HUB_HALF={HUB_HALF}  (interior {HUB_HALF * 2} x {HUB_HALF * 2})")
    for w in WALLS:
        print(f"  {w}: {WALL_GATES[w]}")
    print(f"Surface regions: {sum(1 for k in regions if not _is_underground(k))}")
    print(f"Mine level: {len(RANKS)} pits, ore y{MINE_ORE_BOTTOM}-{MINE_ORE_TOP}, rim y{MINE_RIM_Y}")
    print(f"Overlap violations: {len(bad)}")
    for b in bad[:20]:
        print("  OVERLAP:", b)
    print(f"Text zone overlaps: {len(tbad)}")
    for b in tbad[:20]:
        print("  TEXT OVERLAP:", b)
