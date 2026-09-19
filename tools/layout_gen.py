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
    """Mine A is 24x24, Mine Z is 44x44, linear in between.

    These used to run to 60. Every extra block of mine width is eight blocks of
    ring circumference (four sides, two mines deep is not an option), and the
    ring's radius is what a player walks every single trip. 44 is still four
    times mine A's area and far more ore than a reset cycle consumes.
    """
    return 24 + int(i * (44 - 24) / (len(RANKS) - 1))


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

# A 13-layer ore band. The previous 29-layer band was both deeper than any
# prison mine needs between resets and, multiplied across 26 pits, a large part
# of why the first-boot build was enormous.
# The mine level sits low on purpose. The pit ceiling used to be 14 blocks above the
# rim, which is enough for lamps and a guard rail and nothing else — no terrain, no
# trees, no structures. Dropping the band gives each mine a 30-block cavern to be
# decorated in, while still leaving solid rock between its ceiling and the surface.
MINE_ORE_BOTTOM = 30           # lowest ore layer
MINE_ORE_TOP = 42              # highest ore layer
MINE_RIM_Y = MINE_ORE_TOP + 2  # walkable rim around the pit; the lift lands here

# The mines are reached ON FOOT, which is what decides where they can sit.
#
# Each mine ward holds a spiral stair down to the mine level. At the bottom, a
# shaft tunnel runs straight out from under the ward to a ring concourse, and
# every mine opens off that ring. So the walk is: gate -> ward -> down the
# stair -> out the shaft -> onto the ring -> into your mine. The cage lift
# still works and goes straight there, for players who would rather not walk.
#
# The ring's radius is forced by arithmetic, not taste: a wall's mines have to
# fit along that side of the ring, and the widest wall's run sets the size.
RIM_W = 4                      # walkway wrapping each pit
MINE_GAP = 4                   # dividing wall between neighbouring mines
CONCOURSE_IN = 174             # ring concourse, inner edge, measured from origin
CONCOURSE_W = 9                # wide enough to read as a thoroughfare, not a tunnel
CONCOURSE_OUT = CONCOURSE_IN + CONCOURSE_W
SHAFT_W = 7                    # clear width of a ward's shaft tunnel
SHAFT_NEAR = 8                 # the shaft starts under the ward, where the stair lands

regions, text_zones, gates, lifts = {}, {}, {}, {}
ring = {}


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


# On which walls does "increasing along-axis" run RIGHT-to-LEFT for a player
# standing in the hub looking out at that wall?
#
# Facing north, east is on your right, so +x reads left to right.
# Facing south, east is on your LEFT, so +x reads right to left — and mine O
# ended up on the right-hand side with U on the left. Same on the west wall.
READS_BACKWARDS = {"N": False, "E": False, "S": True, "W": True}


def gate_positions(wall):
    """Centre coordinate, along the wall, of each gate, in listed order.

    Listed order is READING order, so A..G and O..U both run left to right for
    the player looking at them.
    """
    n = len(WALL_GATES[wall])
    span = (n - 1) * GATE_PITCH
    start = -span / 2
    pos = [start + i * GATE_PITCH for i in range(n)]
    return list(reversed(pos)) if READS_BACKWARDS[wall] else pos


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
# Mines: open pits on the underground level, ringing a walkable concourse.
#
# A mine belongs to the wall its gate is on, and sits on that side of the ring
# in gate order, so "A to G are north, H to N are east" holds underground too.
# Walking out of gate D and down its stair puts you on the ring directly
# opposite mine D.
# ---------------------------------------------------------------------------

RING_NEAR = CONCOURSE_IN - HUB_HALF     # ring, as a distance out from the hub wall
RING_FAR = CONCOURSE_OUT - HUB_HALF


def _place_ring():
    """The concourse: a square ring at mine level joining every mine.

    The north and south legs run the full width; the east and west legs stop
    short of them, so the four legs abut at the corners without overlapping.
    """
    for wall in WALLS:
        limit = CONCOURSE_OUT if wall in ("N", "S") else CONCOURSE_IN
        r = _wall_axis_rects(wall, -limit, limit, RING_NEAR, RING_FAR)
        regions[f"ring_{wall}"] = r
        ring[wall] = r


def _mine_footprint(rank):
    """Along-the-ring space one mine consumes: its pit, its rim, its party wall."""
    return WIDTHS[rank] + 2 * RIM_W + MINE_GAP


def _place_mines():
    for wall in WALLS:
        # Laid in along-axis order, not listed order, so each mine sits
        # directly out from its own gate even on the walls that read backwards.
        on_wall = sorted((g for g in WALL_GATES[wall] if g in WIDTHS),
                         key=lambda g: gates[g]["centre"])
        foots = [_mine_footprint(r) for r in on_wall]
        cursor = -sum(foots) / 2
        for rank, foot in zip(on_wall, foots):
            centre = cursor + foot / 2
            cursor += foot
            w = WIDTHS[rank]

            rim = _wall_axis_rects(wall, centre - (w / 2 + RIM_W), centre + (w / 2 + RIM_W),
                                   RING_FAR, RING_FAR + w + 2 * RIM_W)
            pit = _wall_axis_rects(wall, centre - w / 2, centre + w / 2,
                                   RING_FAR + RIM_W, RING_FAR + RIM_W + w)
            regions[f"mine_{rank}"] = pit
            regions[f"rim_{rank}"] = rim

            # Where you arrive, on foot or by cage: the rim strip facing the
            # ring, two blocks out, centred on the mine. Always over solid rim,
            # never over the hole.
            entry = _wall_axis_rects(wall, centre - 0.5, centre + 0.5,
                                     RING_FAR + 1, RING_FAR + 3)
            landing = ((entry[0] + entry[2]) / 2, MINE_RIM_Y, (entry[1] + entry[3]) / 2)

            # The shaft tunnel: from under the ward out to the ring, at mine
            # level, on the GATE's centreline rather than the mine's.
            regions[f"shaft_{rank}"] = _wall_axis_rects(
                wall, gates[rank]["centre"] - SHAFT_W / 2, gates[rank]["centre"] + SHAFT_W / 2,
                SHAFT_NEAR, RING_NEAR)

            lifts[rank] = {
                "ward": f"ward_{rank}",
                "landing": landing,
                "pit": pit,
                "rim": rim,
                "mine_centre": centre,
                "ore_bottom": MINE_ORE_BOTTOM,
                "ore_top": MINE_ORE_TOP,
            }


_place_ring()
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
    return name.startswith(("mine_", "rim_", "ring_", "shaft_"))


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


# ---------------------------------------------------------------------------
# Doorways
#
# Every wall this map builds used to have its opening carved by hand, by
# whichever method happened to build that wall, with a hand-picked edge and
# width. Miss one and the area behind it is sealed — which is how all thirty
# wards, the fishing lobby, the green's perimeter and all three grounds ended
# up walled off in three separate rounds of the same bug.
#
# So openings are derived here instead, from the geometry that is already
# validated, and WorldBuilder cuts every one of them after it has finished
# building. Add a region and its doors come with it.
# ---------------------------------------------------------------------------

MIN_DOOR = max(3, GATE_W // 3)
DOOR_MAX = GATE_W


def _door_between(a, b):
    """The box to cut where two abutting rectangles meet, or None."""
    a, b = norm(a), norm(b)
    xo_lo, xo_hi = max(a[0], b[0]), min(a[2], b[2])
    zo_lo, zo_hi = max(a[1], b[1]), min(a[3], b[3])
    xo, zo = xo_hi - xo_lo, zo_hi - zo_lo
    if xo < 0 or zo < 0:
        return None          # nowhere near each other
    if xo > 0 and zo > 0:
        return None          # already open to each other; no wall between them
    if zo == 0 and xo >= MIN_DOOR:
        c, half = (xo_lo + xo_hi) / 2, min(DOOR_MAX, xo - 1) / 2
        return (c - half, zo_lo - 2, c + half, zo_lo + 2)
    if xo == 0 and zo >= MIN_DOOR:
        c, half = (zo_lo + zo_hi) / 2, min(DOOR_MAX, zo - 1) / 2
        return (xo_lo - 2, c - half, xo_lo + 2, c + half)
    return None


# Pairs that must be joined but do not share a feature name.
_EXTRA_LINKS = {
    ("room_FISHING", "room_GREEN"),
    ("room_GREEN", "corridor_PONDSLINK"),
    ("room_GREEN", "corridor_LOGGINGLINK"),
    ("room_GREEN", "corridor_FARMLINK"),
    ("corridor_PONDSLINK", "room_PONDS"),
    ("corridor_LOGGINGLINK", "room_LOGGING"),
    ("corridor_FARMLINK", "room_FARM"),
    ("corridor_STARTERLINK", "room_STARTER"),
    ("corridor_STARTERLINK", "ward_INTAKE"),
}


def _should_connect(a, b):
    ta, na = a.split("_", 1)
    tb, nb = b.split("_", 1)
    if na == nb:
        return True                                   # corridor -> ward -> room
    if "hub" in (ta, tb):
        return {ta, tb} == {"hub", "corridor"}        # the hub joins its corridors
    if {ta, tb} <= {"ring", "rim", "shaft"}:
        return True                                   # the whole mine level
    return (a, b) in _EXTRA_LINKS or (b, a) in _EXTRA_LINKS


def _compute_doorways():
    allr = dict(regions)
    allr["hub_HUB"] = HUB
    names = sorted(allr)
    out = []
    for i, a in enumerate(names):
        for b in names[i + 1:]:
            if a.startswith("mine_") or b.startswith("mine_"):
                continue                              # the pit is a hole, not a room
            if _is_underground(a) != _is_underground(b):
                continue
            if not _should_connect(a, b):
                continue
            d = _door_between(allr[a], allr[b])
            if d:
                out.append({"a": a, "b": b, "rect": list(d),
                            "level": "MINE" if _is_underground(a) else "HUB"})
    return out


doorways = _compute_doorways()


def _compound_bounds(margin=18):
    """A rectangle enclosing everything walkable on the surface.

    The prison is meant to stand in open country you can SEE but not reach. With
    nothing to stop you, the countryside generator's ground runs right up to the
    build and you can simply walk away across it.
    """
    xs, zs = [], []
    for name, r in regions.items():
        if _is_underground(name):
            continue
        n = norm(r)
        xs += [n[0], n[2]]
        zs += [n[1], n[3]]
    return (min(xs) - margin, min(zs) - margin, max(xs) + margin, max(zs) + margin)


COMPOUND = _compound_bounds()


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
        "ring": {k: list(v) for k, v in ring.items()},
        "concourse_in": CONCOURSE_IN,
        "concourse_out": CONCOURSE_OUT,
        "shaft_w": SHAFT_W,
        "shaft_near": SHAFT_NEAR,
        "rim_w": RIM_W,
        "doorways": doorways,
        "compound": list(COMPOUND),
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
    print(f"Ring concourse: radius {CONCOURSE_IN}-{CONCOURSE_OUT}, "
          f"shaft walk {CONCOURSE_IN - HUB_HALF - SHAFT_NEAR} blocks from each ward")
    for w in WALLS:
        on_wall = [g for g in WALL_GATES[w] if g in WIDTHS]
        span = sum(_mine_footprint(r) for r in on_wall)
        print(f"  {w}: {len(on_wall)} mines, ring run {span} "
              f"(half {span / 2:.0f} must be <= {CONCOURSE_OUT})")
    print(f"Doorways: {len(doorways)}")
    print(f"Compound bounds: {[int(v) for v in COMPOUND]} "
          f"({int(COMPOUND[2] - COMPOUND[0])} x {int(COMPOUND[3] - COMPOUND[1])})")
    print(f"Overlap violations: {len(bad)}")
    for b in bad[:20]:
        print("  OVERLAP:", b)
    print(f"Text zone overlaps: {len(tbad)}")
    for b in tbad[:20]:
        print("  TEXT OVERLAP:", b)
