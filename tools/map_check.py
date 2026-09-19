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
    return name.startswith(("mine_", "rim_", "ring_", "shaft_"))


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

    # ---- 4. Every mine has a landing standing on its own rim ------------
    for rank, lift in lifts.items():
        pit, rim = lift["pit"], lift["rim"]
        lx, ly, lz = lift["landing"]
        r = rect_norm(rim)
        p = rect_norm(pit)
        if not (r[0] <= lx <= r[2] and r[1] <= lz <= r[3]):
            failures.append(f"mine {rank}: landing {lx},{lz} is outside its rim")
        if p[0] < lx < p[2] and p[1] < lz < p[3]:
            failures.append(f"mine {rank}: landing {lx},{lz} is inside the pit — players would fall in")
        if not (r[0] < p[0] and r[1] < p[1] and r[2] > p[2] and r[3] > p[3]):
            failures.append(f"mine {rank}: rim does not fully enclose the pit")
        if lift["ore_top"] >= ly:
            failures.append(
                f"mine {rank}: rim y{ly} is not above the ore band top y{lift['ore_top']}")

    # ---- 4b. The walk down: ward -> shaft -> ring -> rim ----------------
    #
    # This is the check the old validator had no equivalent of, and the reason
    # every ward was sealed for two builds running: a mine can be perfectly
    # well formed and still have no way in.
    ring_legs = {k: v for k, v in regions.items() if k.startswith("ring_")}
    if len(ring_legs) != 4:
        failures.append(f"ring concourse has {len(ring_legs)} legs, expected 4")

    for name, leg in ring_legs.items():
        joins = sum(1 for other, r in ring_legs.items()
                    if other != name and shared_edge(leg, r) >= MIN_DOOR)
        if joins < 2:
            failures.append(
                f"{name} joins only {joins} other ring leg(s) — the concourse is not a loop")

    def touches_ring(rect):
        return max((shared_edge(rect, leg) for leg in ring_legs.values()), default=0)

    for rank in lifts:
        shaft = regions.get(f"shaft_{rank}")
        ward = regions.get(f"ward_{rank}")
        rim = regions[f"rim_{rank}"]

        if shaft is None:
            failures.append(f"mine {rank}: no shaft tunnel — it can only be reached by lift")
            continue
        # The stair is vertical, so in plan view the shaft must start beneath
        # the ward it descends from.
        under_ward = shared_edge(shaft, ward)
        if under_ward < MIN_DOOR:
            failures.append(
                f"mine {rank}: shaft meets its ward over only {under_ward} blocks "
                f"(need {MIN_DOOR}) — the stair would land in rock")
        on_ring = touches_ring(shaft)
        if on_ring < MIN_DOOR:
            failures.append(
                f"mine {rank}: shaft meets the ring over only {on_ring} blocks (need {MIN_DOOR})")
        rim_on_ring = touches_ring(rim)
        if rim_on_ring < MIN_DOOR:
            failures.append(
                f"mine {rank}: rim meets the ring over only {rim_on_ring} blocks (need {MIN_DOOR})")

    # ---- 4c. Every mine is walkable from every ward --------------------
    under = {k: v for k, v in regions.items() if is_underground(k)}
    start = next((k for k in under if k.startswith("shaft_")), None)
    if start:
        seen, frontier = {start}, [start]
        while frontier:
            cur = frontier.pop()
            for name, rect in under.items():
                if name in seen or name.startswith("mine_"):
                    continue   # the pit itself is a hole, not a walkway
                if shared_edge(under[cur], rect) >= MIN_DOOR:
                    seen.add(name)
                    frontier.append(name)
        for name in under:
            if name.startswith("mine_") or name in seen:
                continue
            failures.append(f"not walkable from the shafts: {name}")

    # ---- 5. Everything is reachable THROUGH THE DOORWAYS ----------------
    #
    # Walking the doorway graph, not raw adjacency. Two regions touching means
    # nothing if no opening is ever cut between them — that is precisely how
    # the wards, the fishing lobby and the grounds all ended up sealed while
    # this check reported a fully connected map.
    doors = layout["doorways"]
    adj = {}
    for d in doors:
        adj.setdefault(d["a"], set()).add(d["b"])
        adj.setdefault(d["b"], set()).add(d["a"])

    surface = [k for k in regions if not is_underground(k)]
    reached, frontier = {"hub_HUB"}, ["hub_HUB"]
    while frontier:
        for nxt in adj.get(frontier.pop(), ()):
            if nxt not in reached:
                reached.add(nxt)
                frontier.append(nxt)

    for name in surface:
        if name not in reached:
            failures.append(f"no doorway route from the hub to {name}")

    # ---- Report ---------------------------------------------------------
    print(f"Hub: {hub[2] - hub[0]} x {hub[3] - hub[1]}   gates: {len(gates)}")
    print(f"Surface regions: {len(surface)}   mine pits: {len(lifts)}")
    print(f"Doorways cut: {len(doors)}")
    print(f"Reachable from hub through them: "
          f"{len([n for n in surface if n in reached])} / {len(surface)}")

    if failures:
        print(f"\nFAIL — {len(failures)} problem(s):")
        for f in failures:
            print(f"  {f}")
        print("\n--- Summary ---")
        print("map check FAILED — see problems above.")
        return 1

    print("\n--- Summary ---")
    print(f"all {len(lifts)} mines reachable on foot from their own ward,")
    print(f"  (stair down, {layout['concourse_in'] - layout['hub_half'] - layout['shaft_near']}-block shaft, "
          f"then the ring concourse)")
    print("every gate bridges hub to ward at full door width,")
    print("every surface area walkable from the hub, no overlaps, no label collisions.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
