import json

RANKS = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")

def mine_width(i):
    return 24 + int(i * (60 - 24) / (len(RANKS) - 1))

WIDTHS = {r: mine_width(i) for i, r in enumerate(RANKS)}
MAX_W = max(WIDTHS.values())

CORRIDOR_LEN = 6
WARD_DEPTH = 8
GATE_MARGIN = 4
CORNER_BUFFER = MAX_W / 2 + CORRIDOR_LEN + WARD_DEPTH + 8
WALLS = ["N", "E", "S", "W"]

# One non-mine "special" gate reserved on each wall's centre, same corridor+room idea,
# each its own fixed-size direct feature (not a mine, but built the same principled way).
# Half-widths/depths rescaled proportionally to the new, much smaller mine_width() range.
SPECIALS = {
    "N": ("FISHING", 25, 45),
    "E": ("CRATES", 12, 15),
    "S": ("YARD", 20, 25),
    "W": ("CELLS", 25, 55),
}

items = [WIDTHS[r] + GATE_MARGIN for r in RANKS]

def feasible(cap, k):
    groups, cur = 1, 0
    for it in items:
        if cur + it > cap:
            groups += 1
            cur = it
            if groups > k:
                return False
        else:
            cur += it
    return True

lo, hi = max(items), sum(items)
while lo < hi:
    mid = (lo + hi) // 2
    if feasible(mid, 4):
        hi = mid
    else:
        lo = mid + 1
cap = lo

groups_ranks = []
cur_group, cur_sum = [], 0
for r, it in zip(RANKS, items):
    if cur_sum + it > cap and cur_group:
        groups_ranks.append(cur_group)
        cur_group, cur_sum = [], 0
    cur_group.append(r)
    cur_sum += it
groups_ranks.append(cur_group)
while len(groups_ranks) < 4:
    biggest = max(range(len(groups_ranks)), key=lambda i: sum(WIDTHS[r] + GATE_MARGIN for r in groups_ranks[i]))
    g = groups_ranks[biggest]
    half = len(g) // 2
    groups_ranks[biggest:biggest+1] = [g[:half], g[half:]]

groups = {w: g for w, g in zip(WALLS, groups_ranks)}
assignment = {r: w for w, g in groups.items() for r in g}

def usable_needed(w):
    ranks_here = groups[w]
    widths = [WIDTHS[r] for r in ranks_here]
    needed = sum(widths) + GATE_MARGIN * (len(widths) - 1 if widths else 0)
    special_half = SPECIALS[w][1]
    needed += 2 * special_half + 2 * GATE_MARGIN
    return needed

required_wall_len = max(usable_needed(w) + 2 * CORNER_BUFFER for w in WALLS)
HUB_HALF = required_wall_len / 2
HUB = (-HUB_HALF, -HUB_HALF, HUB_HALF, HUB_HALF)

regions, text_zones, gates, special_gates = {}, {}, {}, {}

def rects_for(wall, gate_start, w, kind, rank=None):
    if wall == "N":
        hub_z = HUB[1]
        gx1, gx2 = gate_start, gate_start + w
        ward = (gx1, hub_z - CORRIDOR_LEN - WARD_DEPTH, gx2, hub_z - CORRIDOR_LEN)
        corridor = (gate_start + w/2 - 2, hub_z - CORRIDOR_LEN, gate_start + w/2 + 2, hub_z)
        far = (gx1, ward[1] - w, gx2, ward[1]) if kind == "mine" else (gx1, ward[1] - SPECIALS[wall][2], gx2, ward[1])
        text = (gx1, ward[1] - 1, gx2, ward[1] + 1)
    elif wall == "S":
        hub_z = HUB[3]
        gx1, gx2 = gate_start, gate_start + w
        ward = (gx1, hub_z + CORRIDOR_LEN, gx2, hub_z + CORRIDOR_LEN + WARD_DEPTH)
        corridor = (gate_start + w/2 - 2, hub_z, gate_start + w/2 + 2, hub_z + CORRIDOR_LEN)
        far = (gx1, ward[3], gx2, ward[3] + w) if kind == "mine" else (gx1, ward[3], gx2, ward[3] + SPECIALS[wall][2])
        text = (gx1, ward[3] - 1, gx2, ward[3] + 1)
    elif wall == "E":
        hub_x = HUB[2]
        gz1, gz2 = gate_start, gate_start + w
        ward = (hub_x + CORRIDOR_LEN, gz1, hub_x + CORRIDOR_LEN + WARD_DEPTH, gz2)
        corridor = (hub_x, gate_start + w/2 - 2, hub_x + CORRIDOR_LEN, gate_start + w/2 + 2)
        far = (ward[2], gz1, ward[2] + w, gz2) if kind == "mine" else (ward[2], gz1, ward[2] + SPECIALS[wall][2], gz2)
        text = (ward[0] - 1, gz1, ward[0] + 1, gz2)
    else:
        hub_x = HUB[0]
        gz1, gz2 = gate_start, gate_start + w
        ward = (hub_x - CORRIDOR_LEN - WARD_DEPTH, gz1, hub_x - CORRIDOR_LEN, gz2)
        corridor = (hub_x - CORRIDOR_LEN, gate_start + w/2 - 2, hub_x, gate_start + w/2 + 2)
        far = (ward[0] - w, gz1, ward[0], gz2) if kind == "mine" else (ward[0] - SPECIALS[wall][2], gz1, ward[0], gz2)
        text = (ward[2] - 1, gz1, ward[2] + 1, gz2)
    return ward, corridor, far, text

def place_wall(wall):
    ranks_here = groups[wall]
    widths = [WIDTHS[r] for r in ranks_here]
    sp_name, sp_half, sp_depth = SPECIALS[wall]
    sp_w = 2 * sp_half
    needed = sum(widths) + GATE_MARGIN * len(widths) + sp_w
    avail = 2 * HUB_HALF - 2 * CORNER_BUFFER
    slack = max(0, avail - needed)
    n_gaps = len(widths)  # gaps between mines + one side of special = distribute slack
    extra_margin = slack / max(1, n_gaps)

    half = len(ranks_here) // 2
    left_group, right_group = ranks_here[:half], ranks_here[half:]
    left_w = sum(WIDTHS[r] for r in left_group) + (GATE_MARGIN + extra_margin) * len(left_group)
    c = -sp_half - left_w
    positions = {}
    for r in left_group:
        positions[r] = c
        c += WIDTHS[r] + GATE_MARGIN + extra_margin
    c = sp_half + GATE_MARGIN + extra_margin
    for r in right_group:
        positions[r] = c
        c += WIDTHS[r] + GATE_MARGIN + extra_margin

    for r in ranks_here:
        w = WIDTHS[r]
        ward, corridor, mine, text = rects_for(wall, positions[r], w, "mine")
        regions[f"ward_{r}"] = ward
        regions[f"corridor_{r}"] = corridor
        regions[f"mine_{r}"] = mine
        text_zones[f"label_{r}"] = text
        gates[r] = {"wall": wall, "gate_center": positions[r] + w/2, "width": w}

    ward, corridor, room, text = rects_for(wall, -sp_half, sp_w, "special")
    regions[f"ward_{sp_name}"] = ward
    regions[f"corridor_{sp_name}"] = corridor
    regions[f"room_{sp_name}"] = room
    text_zones[f"label_{sp_name}"] = text
    special_gates[sp_name] = {"wall": wall, "gate_center": 0.0, "width": sp_w}

for w in WALLS:
    place_wall(w)

def norm(r):
    x1, z1, x2, z2 = r
    return (min(x1, x2), min(z1, z2), max(x1, x2), max(z1, z2))

def overlaps(a, b):
    a = norm(a); b = norm(b)
    return not (a[2] <= b[0] or b[2] <= a[0] or a[3] <= b[1] or b[3] <= a[1])

names = list(regions.keys())
bad = []
for i in range(len(names)):
    for j in range(i + 1, len(names)):
        a, b = names[i], names[j]
        ra = a.split("_", 1)[1]; rb = b.split("_", 1)[1]
        if ra == rb:
            continue
        if overlaps(regions[a], regions[b]):
            bad.append((a, b))

print(f"HUB_HALF={HUB_HALF:.0f}")
for w in WALLS:
    print(" ", w, groups[w], SPECIALS[w][0])
print(f"Overlap violations: {len(bad)}")
for b in bad[:30]:
    print("  OVERLAP:", b)

tnames = list(text_zones.keys())
tbad = [(a, b) for i, a in enumerate(tnames) for b in tnames[i+1:] if overlaps(text_zones[a], text_zones[b])]
print(f"Text zone overlaps: {len(tbad)}")
for b in tbad:
    print("  TEXT OVERLAP:", b)

far = max(max(abs(v[0]), abs(v[1]), abs(v[2]), abs(v[3])) for k, v in regions.items())
print("Farthest extent:", far, " hub_half:", HUB_HALF)

def get_layout():
    """Returns the full validated layout as plain dicts — the single source of truth
    for both RankMineData.java/MapLayout.java (generated from this) and map_check.py
    (which re-validates against this exact function, so the two can never drift)."""
    return {
        "hub": list(HUB), "regions": {k: list(v) for k, v in regions.items()},
        "gates": gates, "special_gates": special_gates, "assignment": assignment,
        "widths": WIDTHS, "hub_half": HUB_HALF, "specials": SPECIALS,
        "overlap_violations": bad, "text_zone_violations": tbad,
    }

if __name__ == "__main__":
    import json
    json.dump(get_layout(), open("layout.json", "w"), indent=1)
    print("wrote tools/layout.json")
