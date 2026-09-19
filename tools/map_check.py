"""
Validates the Sky Prison map before it's ever built in-game.

This does NOT re-derive the layout with its own copy of the geometry (that's exactly
how the earlier two-lane version quietly drifted out of sync with WorldBuilder.java).
Instead it imports tools/layout_gen.py directly — the one function that also generates
RankMineData.java and MapLayout.java — and checks the actual thing that will be built:

  1. No two regions (mine, ward, corridor, special room) overlap.
  2. No two text label zones overlap.
  3. Every mine and every special (fishing/crates/yard/cells) is reachable from the
     hub's centre by flood-filling through touching regions only (proves every gate's
     corridor really does connect hub -> ward -> mine, with nothing missing or gapped).

Run locally with `python3 tools/map_check.py`. CI runs this before the Maven build.
"""
import sys
from layout_gen import get_layout


def rect_norm(r):
    x1, z1, x2, z2 = r
    return (min(x1, x2), min(z1, z2), max(x1, x2), max(z1, z2))


def touching_or_overlapping(a, b, pad=1):
    a = rect_norm(a)
    b = rect_norm(b)
    return not (a[2] + pad <= b[0] or b[2] + pad <= a[0] or a[3] + pad <= b[1] or b[3] + pad <= a[1])


def main():
    layout = get_layout()
    regions = layout["regions"]
    hub = layout["hub"]

    ok = True

    if layout["overlap_violations"]:
        ok = False
        print(f"FAIL: {len(layout['overlap_violations'])} region overlap(s):")
        for a, b in layout["overlap_violations"]:
            print(f"  {a}  <->  {b}")

    if layout["text_zone_violations"]:
        ok = False
        print(f"FAIL: {len(layout['text_zone_violations'])} text label overlap(s):")
        for a, b in layout["text_zone_violations"]:
            print(f"  {a}  <->  {b}")

    # Reachability: BFS over "touches the hub, or touches something that touches the
    # hub" using only real built regions (hub, wards, corridors, mines/rooms).
    all_regions = {"HUB": hub, **regions}
    reached = {"HUB"}
    frontier = ["HUB"]
    while frontier:
        cur = frontier.pop()
        for name, rect in all_regions.items():
            if name in reached:
                continue
            if touching_or_overlapping(all_regions[cur], rect):
                reached.add(name)
                frontier.append(name)

    required_anchors = [f"mine_{r}" for r in "ABCDEFGHIJKLMNOPQRSTUVWXYZ"]
    required_anchors += [f"room_{name}" for name in ["FISHING", "CRATES", "YARD", "CELLS"]]
    unreachable = [a for a in required_anchors if a not in reached]
    if unreachable:
        ok = False
        print(f"FAIL: {len(unreachable)} anchor(s) not reachable from the hub:")
        for a in unreachable:
            print(f"  {a}")

    print(f"\nTotal regions: {len(regions)}  (hub half-size: {layout['hub_half']:.0f})")
    print(f"Reachable regions: {len(reached)} / {len(all_regions) + 1}")

    if ok:
        print("\n--- Summary ---")
        print("all 26 mines + fishing/crates/yard/cells reachable directly from the hub,")
        print("no region overlaps, no text-label overlaps.")
        return 0
    else:
        print("\n--- Summary ---")
        print("map check FAILED — see violations above.")
        return 1


if __name__ == "__main__":
    sys.exit(main())
