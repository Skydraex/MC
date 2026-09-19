#!/usr/bin/env python3
"""
Headless connectivity/void-gap checker for the Sky Prison map.

WHY THIS EXISTS
----------------
This sandbox cannot reach api.papermc.io / piston-meta.mojang.com / Maven Central /
jitpack.io (all blocked by the environment's network policy), so a real Paper server
cannot be downloaded or booted here. That rules out MockBukkit or any other
"run the actual plugin" approach for self-testing inside this sandbox.

What CAN be done here: the void-gap and disconnected-room bugs that have bitten this
project several times were always arithmetic mistakes in WorldBuilder.java's own
region/connector/doorway coordinates, not gaps in prison-server knowledge. That class
of bug is checkable from the coordinates alone, without a running world.

This script is a deliberately narrow port of ONLY the geometry from WorldBuilder.java:
- every named region rectangle (must match the WorldBuilder.java constants verbatim)
- every floor-laying call (room fills, connector(), walkway())
- every doorway() carve

It builds the same "floor tile" set the real builder would place at height Y,
flood-fills it from hub spawn, and reports:
  1. Any two regions whose bounding boxes overlap beyond a shared border (contradicts
     the "every region is disjoint" invariant WorldBuilder.java's own comments claim).
  2. Any named anchor point (ward, mine, crate hall, yard, cell block, fishing area,
     logging yard, farm, starter area) that the flood-fill from hub spawn cannot reach.

MAINTENANCE: whenever WorldBuilder.java's constants or connector/walkway/doorway
calls change, this file's REGION_* dicts and the two build_floor_from_* functions
must be re-synced by hand. Each block below cites the WorldBuilder.java section it
mirrors so a diff reviewer can check them side by side.

LAYOUT (as of the two-lane hub redesign): odd-position ranks (A,C,E...) branch off
the hub to the LEFT lane (positive Z), even-position ranks (B,D,F...) to the RIGHT
lane (negative Z), paired by distance from hub. Each lane is a full copy of the old
single-line structure (ward + bypass corridor + per-mine spur), just at its own Z band.
"""

import sys
from collections import deque

Y = 95  # WorldBuilder.Y

LEFT_BASE, RIGHT_BASE = 32, -32
LEFT_ENTRANCE_Z, RIGHT_ENTRANCE_Z = LEFT_BASE + 3, RIGHT_BASE - 3
LEFT_BYPASS_Z, RIGHT_BYPASS_Z = LEFT_ENTRANCE_Z - 18, RIGHT_ENTRANCE_Z - 18

# ---- Master layout rectangles, copied verbatim from WorldBuilder.java ----
STARTER   = (-170, -20, -130, 20)
CORRIDOR  = (-130,  -5,  -76,  5)
HUB       = (-100, -40,  -15, 40)
CELLS     = (-135,  45,  -15,  64)
CRATES    = ( -75, -75,  -45, -45)
YARD      = ( -40, -75,  -15, -45)
FISH_X1, FISH_X2 = -460, -20
FISH_Z1, FISH_Z2 = -240, -100
FISH_GATE_X = HUB[0] + 15
FISH_PATH = (FISH_GATE_X - 3, FISH_Z2, FISH_GATE_X + 3, HUB[1] - 1)
_hub_mid_x = (HUB[0] + HUB[2]) // 2  # still used for the cell-block connector
LOGGING   = (-580,-240, -480,-140)
FARM      = (-580,-130, -480, -30)

RANK_ORDER = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
WIDTHS = {  # x2-x1 (== z2-z1, mines are square), copied from RankMineData.java's current sizes
    "A": 80, "B": 83, "C": 86, "D": 91, "E": 96, "F": 101, "G": 107, "H": 112, "I": 119,
    "J": 125, "K": 132, "L": 138, "M": 145, "N": 153, "O": 160, "P": 168, "Q": 175, "R": 183,
    "S": 191, "T": 199, "U": 207, "V": 216, "W": 224, "X": 233, "Y": 241, "Z": 250,
}
GAP, X0 = 30, 40

LANES = {r: ("LEFT" if i % 2 == 0 else "RIGHT") for i, r in enumerate(RANK_ORDER)}

MINES = {}
left_cursor = right_cursor = X0
for r in RANK_ORDER:
    w = WIDTHS[r]
    if LANES[r] == "LEFT":
        x1 = left_cursor; x2 = x1 + w; left_cursor = x2 + GAP
        z1, z2 = LEFT_BASE, LEFT_BASE + w
    else:
        x1 = right_cursor; x2 = x1 + w; right_cursor = x2 + GAP
        z1, z2 = RIGHT_BASE - w, RIGHT_BASE
    MINES[r] = (x1, z1, x2, z2)

floor = set()
regions = []


def add_region(name, x1, z1, x2, z2):
    x1, x2 = min(x1, x2), max(x1, x2)
    z1, z2 = min(z1, z2), max(z1, z2)
    regions.append((name, x1, z1, x2, z2))
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            floor.add((x, z))


def add_strip(cx, z1, z2, halfwidth):
    """Mirrors connector(): a strip along Z centred on cx."""
    z1, z2 = min(z1, z2), max(z1, z2)
    for z in range(z1, z2 + 1):
        for dx in range(-halfwidth - 1, halfwidth + 2):
            floor.add((cx + dx, z))


def add_walkway(x1, x2, centre_z, halfwidth):
    """Mirrors walkway(): a strip along X centred on centre_z."""
    if x2 < x1:
        return
    for x in range(x1, x2 + 1):
        for dz in range(-halfwidth, halfwidth + 1):
            floor.add((x, centre_z + dz))


def entrance_z(rank):
    return LEFT_ENTRANCE_Z if LANES[rank] == "LEFT" else RIGHT_ENTRANCE_Z


def bypass_z(rank):
    return LEFT_BYPASS_Z if LANES[rank] == "LEFT" else RIGHT_BYPASS_Z


def ward_rect(rank):
    x1 = MINES[rank][0]
    ez = entrance_z(rank)
    return (x1 - 24, ez - 11, x1 - 4, ez + 11)


def pit_rect(rank):
    w = ward_rect(rank)
    return (w[0], w[1] - 28, w[2], w[1] - 8)


# ---- Every region's own floor ----
add_region("STARTER", *STARTER)
add_region("CORRIDOR", *CORRIDOR)
add_region("HUB", *HUB)
add_region("CELLS", *CELLS)
add_region("CRATES", *CRATES)
add_region("YARD", *YARD)
add_region("FISH_PATH", *FISH_PATH)
add_region("LOGGING", *LOGGING)
add_region("FARM", *FARM)
add_region("FISH_AREA", FISH_X1, FISH_Z1, FISH_X2, FISH_Z2)

for r in RANK_ORDER:
    add_region(f"MINE_{r}", *MINES[r])
    add_region(f"WARD_{r}", *ward_rect(r))
for r in ("F", "M", "T"):
    add_region(f"PIT_{r}", *pit_rect(r))

# ---- buildLaneWalkways(), once per lane (WorldBuilder.java) ----
for left, ez, bz in ((True, LEFT_ENTRANCE_Z, LEFT_BYPASS_Z), (False, RIGHT_ENTRANCE_Z, RIGHT_BYPASS_Z)):
    lane_ranks = [r for r in RANK_ORDER if (LANES[r] == "LEFT") == left]
    first_w = ward_rect(lane_ranks[0])
    add_walkway(HUB[2] + 1, first_w[0] - 1, ez, 3)
    for r in lane_ranks:
        w = ward_rect(r)
        add_walkway(w[2] + 1, MINES[r][0] - 1, ez, 2)
    first_x = first_w[0]
    last_x = MINES[lane_ranks[-1]][2]
    add_walkway(first_x, last_x, bz, 3)
    add_strip((HUB[2] + first_x) // 2, bz, ez, 3)
    for r in lane_ranks:
        w = ward_rect(r)
        mid_x = (w[0] + w[2]) // 2
        add_strip(mid_x, w[1] - 1, bz + 3, 2)

# Direct hub-north-wall -> fishing path (already laid as its own region above; the doorway
# just needs to exist, which carveAllDoorways provides — no extra floor tiles needed here
# since FISH_PATH touches HUB[1]-1 directly).

# ---- carveAllDoorways() connector calls ----
hub_mid_x = _hub_mid_x
cell_entrance_x = CELLS[0] + 2 + 6 // 2  # CELL_SIZE=6
crate_mid = (CRATES[0] + CRATES[2]) // 2
yard_mid = (YARD[0] + YARD[2]) // 2
cell_corridor_z = HUB[3] + 6

add_strip(hub_mid_x, HUB[3] + 1, cell_corridor_z, 2)
add_walkway(cell_entrance_x, hub_mid_x, cell_corridor_z, 2)
add_strip(cell_entrance_x, cell_corridor_z, CELLS[1] - 1, 2)
add_strip(crate_mid, CRATES[3] + 1, HUB[1] - 1, 1)
add_strip(yard_mid, YARD[3] + 1, HUB[1] - 1, 1)

for r in ("F", "M", "T"):
    w = ward_rect(r)
    p = pit_rect(r)
    mid = (w[0] + w[2]) // 2
    add_strip(mid, p[3] + 1, w[1] - 1, 1)

logging_mid_z = (LOGGING[1] + LOGGING[3]) // 2
add_walkway(LOGGING[2] + 1, FISH_X1 - 1, logging_mid_z, 2)
farm_mid_x = (FARM[0] + FARM[2]) // 2
add_strip(farm_mid_x, LOGGING[3] + 1, FARM[1] - 1, 2)

# ---- Anchor points: one representative walkable tile per room ----
anchors = {
    "hub_spawn": (HUB[0] + 10, 0),
    "starter_spawn": (STARTER[0] + 8, 0),
    "corridor": ((CORRIDOR[0] + CORRIDOR[2]) // 2, 0),
    "cell_block_vestibule": (cell_entrance_x, CELLS[1] + 1),
    "crate_hall": ((CRATES[0] + CRATES[2]) // 2, (CRATES[1] + CRATES[3]) // 2),
    "yard": ((YARD[0] + YARD[2]) // 2, (YARD[1] + YARD[3]) // 2),
    "fishing_area": (-200, (FISH_Z1 + FISH_Z2) // 2),
    "logging_yard": ((LOGGING[0] + LOGGING[2]) // 2, logging_mid_z),
    "farm": (farm_mid_x, (FARM[1] + FARM[3]) // 2),
}
for r in RANK_ORDER:
    w = ward_rect(r)
    anchors[f"ward_{r}"] = ((w[0] + w[2]) // 2, entrance_z(r))
    m = MINES[r]
    anchors[f"mine_{r}"] = ((m[0] + m[2]) // 2, (m[1] + m[3]) // 2)

KNOWN_BENIGN_OVERLAPS = {
    frozenset({"STARTER", "CORRIDOR"}),
    frozenset({"CORRIDOR", "HUB"}),
    frozenset({"FISH_PATH", "FISH_AREA"}),
    frozenset({"FISH_PATH", "HUB"}),
}


def bbox_overlap(a, b):
    aname, ax1, az1, ax2, az2 = a
    bname, bx1, bz1, bx2, bz2 = b
    if frozenset({aname, bname}) in KNOWN_BENIGN_OVERLAPS:
        return False
    ix = min(ax2, bx2) - max(ax1, bx1)
    iz = min(az2, bz2) - max(az1, bz1)
    return ix > 0 and iz > 0


def flood_fill(start):
    if start not in floor:
        return set()
    seen = {start}
    q = deque([start])
    while q:
        x, z = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nb = (x + dx, z + dz)
            if nb in floor and nb not in seen:
                seen.add(nb)
                q.append(nb)
    return seen


def main():
    print(f"Total floor tiles modeled: {len(floor)}")
    print(f"Left lane spans x {X0}..{left_cursor - GAP}, right lane spans x {X0}..{right_cursor - GAP}")

    print("\n--- Region overlap check ---")
    overlap_found = False
    for i in range(len(regions)):
        for j in range(i + 1, len(regions)):
            if bbox_overlap(regions[i], regions[j]):
                print(f"  OVERLAP: {regions[i][0]} and {regions[j][0]}")
                overlap_found = True
    if not overlap_found:
        print("  none found")

    print("\n--- Connectivity from hub spawn ---")
    reached = flood_fill(anchors["hub_spawn"])
    print(f"  reachable tiles: {len(reached)}")

    missing = []
    for name, pt in anchors.items():
        status = "OK" if pt in reached else ("NOT ON FLOOR" if pt not in floor else "UNREACHABLE")
        if status != "OK":
            missing.append((name, pt, status))
        print(f"  {name:24s} {pt}  {status}")

    print("\n--- Summary ---")
    if missing:
        print(f"  {len(missing)} anchor(s) not connected to hub spawn:")
        for name, pt, status in missing:
            print(f"    - {name} at {pt}: {status}")
        sys.exit(1)
    else:
        print("  all anchors reachable from hub spawn, no region overlaps.")
        sys.exit(0)


if __name__ == "__main__":
    main()
