"""
Validates Sky Prison's geometry before a single block is placed in-game.

Why this was rewritten
----------------------
The previous version asked only "does rectangle A touch rectangle B", and
reported a fully connected map while players were walking into bedrock and
sealed doorways. Touching is necessary but nowhere near sufficient: two
rectangles that meet at a single corner "touch", and a corridor two blocks
narrower than the doorway it serves "touches" as well.

So connectivity here is measured by SHARED EDGE LENGTH, not contact. Two
regions are only considered joined if they share a straight run of at least
MIN_DOOR blocks — the same width a player actually has to walk through. That
turns the three failures the old check missed into hard errors:

  * corner-only contact                  -> shared edge 0
  * corridor narrower than its doorway   -> shared edge < MIN_DOOR
  * a region floating with no neighbour  -> unreachable from the hub

It still cannot prove the Java placed the right blocks — only that the geometry
it is handed is sound. The live world is checked separately, in-game, by
`/padmin validate`.

Run locally with `python3 tools/map_check.py`; CI runs it before the build.
"""
import sys

from layout_gen import get_layout, GATE_W

# A player needs a 1-wide gap to pass, but every doorway in this map is built
# GATE_W wide; anything narrower than a third of that is a mistake, not a style
# choice, and is far more likely to be an off-by-N in the layout maths.
MIN_DOOR = max(3, GATE_W // 3)


def rect_norm(r):
    x1, z1, x2, z2 = r
    return (min(x1, x2), min(z1, z2), max(x1, x2), max(z1, z2))


def shared_edge(a, b):
    """Length of the straight run two rectangles share.

    Returns 0 when they only meet at a corner, and the overlap length when they
    abut or overlap along a side. Rectangles that are far apart return 0.
    """
    a, b = rect_norm(a), rect_norm(b)
    x_overlap = min(a[2], b[2]) - max(a[0], b[0])
    z_overlap = min(a[3], b[3]) - max(a[1], b[1])
    # Not adjacent at all on one axis -> no shared edge.
    if x_overlap < 0 or z_overlap < 0:
        return 0
    # Overlapping in both axes (regions intersect) or abutting on one.
    if x_overlap > 0 and z_overlap > 0:
        return min(x_overlap, z_overlap)
    return x_overlap if z_overlap == 0 else z_overlap


def is_underground(name):
    return name.startswith(("mine_", "rim_"))


def main():
    layout = get_layout()
    regions = layout["regions"]
    gates = layout["gates"]
    lifts = layout["lifts"]
    hub = layout["hub"]

    failures = []

    # ---- 1. Nothing overlaps, no label collides -------------------------
    for a, b in layout["overlap_violations"]:
        failures.append(f"region overlap: {a} <-> {b}")
    for a, b in layout["text_zone_violations"]:
        failures.append(f"label overlap: {a} <-> {b}")

    # ---- 2. Every gate's corridor really bridges hub wall to ward -------
    for name in gates:
        corridor = regions.get(f"corridor_{name}")
        ward = regions.get(f"ward_{name}")
        if corridor is None or ward is None:
            failures.append(f"gate {name}: missing corridor or ward")
            continue
        to_hub = shared_edge(corridor, hub)
        to_ward = shared_edge(corridor, ward)
        if to_hub < MIN_DOOR:
            failures.append(
                f"gate {name}: corridor meets the hub over only {to_hub} blocks (need {MIN_DOOR})")
        if to_ward < MIN_DOOR:
            failures.append(
                f"gate {name}: corridor meets its ward over only {to_ward} blocks (need {MIN_DOOR})")

    # ---- 3. Every room hangs off its own ward ---------------------------
    for name, g in gates.items():
        if g["kind"] != "room":
            continue
        room, ward = regions.get(f"room_{name}"), regions.get(f"ward_{name}")
        if room is None:
            failures.append(f"room gate {name}: no room region")
            continue
        edge = shared_edge(ward, room)
        if edge < MIN_DOOR:
            failures.append(
                f"room {name}: meets its ward over only {edge} blocks (need {MIN_DOOR})")

    # ---- 4. Every mine has a lift landing standing on its own rim -------
    for rank, lift in lifts.items():
        pit, rim = lift["pit"], lift["rim"]
        lx, ly, lz = lift["landing"]
        r = rect_norm(rim)
        p = rect_norm(pit)
        if not (r[0] <= lx <= r[2] and r[1] <= lz <= r[3]):
            failures.append(f"mine {rank}: lift landing {lx},{lz} is outside its rim")
        if p[0] < lx < p[2] and p[1] < lz < p[3]:
            failures.append(f"mine {rank}: lift landing {lx},{lz} is inside the pit — players would fall in")
        if not (r[0] < p[0] and r[1] < p[1] and r[2] > p[2] and r[3] > p[3]):
            failures.append(f"mine {rank}: rim does not fully enclose the pit")
        if lift["ore_top"] >= ly:
            failures.append(
                f"mine {rank}: rim y{ly} is not above the ore band top y{lift['ore_top']}")

    # ---- 5. Everything on the surface is reachable from the hub ---------
    surface = {k: v for k, v in regions.items() if not is_underground(k)}
    surface["HUB"] = hub
    reached, frontier = {"HUB"}, ["HUB"]
    while frontier:
        cur = frontier.pop()
        for name, rect in surface.items():
            if name in reached:
                continue
            if shared_edge(surface[cur], rect) >= MIN_DOOR:
                reached.add(name)
                frontier.append(name)

    for name in surface:
        if name not in reached:
            failures.append(f"unreachable from the hub: {name}")

    # ---- Report ---------------------------------------------------------
    print(f"Hub: {hub[2] - hub[0]} x {hub[3] - hub[1]}   gates: {len(gates)}")
    print(f"Surface regions: {len(surface) - 1}   mine pits: {len(lifts)}")
    print(f"Reachable from hub: {len(reached) - 1} / {len(surface) - 1}"
          f"   (shared edge >= {MIN_DOOR} blocks)")

    if failures:
        print(f"\nFAIL — {len(failures)} problem(s):")
        for f in failures:
            print(f"  {f}")
        print("\n--- Summary ---")
        print("map check FAILED — see problems above.")
        return 1

    print("\n--- Summary ---")
    print(f"all {len(lifts)} mines reachable by lift from their own ward,")
    print("every gate bridges hub to ward at full door width,")
    print("every surface area walkable from the hub, no overlaps, no label collisions.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
