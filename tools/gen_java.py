#!/usr/bin/env python3
"""
Bakes tools/layout_gen.py's validated geometry into the plugin's two data
classes, MapLayout.java and RankMineData.java.

Those two files are GENERATED — never hand-edit them. WorldBuilder.java builds
exactly what they say and never recomputes geometry, so the Python layout, the
validator and the in-game world can't drift apart.

Rank economics (cost, next, ore mix, sell prices) live in tools/economy.json,
not in the Java. The previous version scraped them back out of the generated
RankMineData.java with a regex, which silently broke the moment the Def
signature changed — which is exactly what this rewrite does.

Run: `cd tools && python3 gen_java.py`
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, HERE)

from layout_gen import get_layout, RANKS  # noqa: E402

RANK_DATA_PATH = os.path.join(REPO, "src/main/java/com/lukeprison/prison/RankMineData.java")
MAP_LAYOUT_PATH = os.path.join(REPO, "src/main/java/com/lukeprison/prison/MapLayout.java")
ECONOMY_PATH = os.path.join(HERE, "economy.json")

GENERATED_BANNER = """/*
 * GENERATED FILE — do not edit by hand.
 * Produced by tools/gen_java.py from tools/layout_gen.py (geometry)
 * and tools/economy.json (rank economics). Re-run `cd tools && python3 gen_java.py`.
 */"""


def ints(rect):
    return [int(round(v)) for v in rect]


def arr(rect):
    return "{" + ",".join(str(v) for v in ints(rect)) + "}"


def main():
    layout = get_layout()
    if layout["overlap_violations"] or layout["text_zone_violations"]:
        print("ERROR: layout_gen reports violations — refusing to generate Java from a bad layout")
        return 1

    econ = json.load(open(ECONOMY_PATH))
    missing = [r for r in RANKS + ["FREE"] if r not in econ]
    if missing:
        print(f"ERROR: tools/economy.json is missing ranks: {missing}")
        return 1

    regions = layout["regions"]
    gates = layout["gates"]
    lifts = layout["lifts"]

    # ---------------------------------------------------------------- MapLayout

    gate_lines = []
    for name, g in gates.items():
        gate_lines.append(
            f'        new Gate("{name}", "{g["wall"]}", {int(round(g["centre"]))}, "{g["kind"]}", '
            f'new int[]{arr(regions[f"corridor_{name}"])}, new int[]{arr(regions[f"ward_{name}"])}),')

    room_lines = []
    for name in layout["room_specials"]:
        room_lines.append(
            f'        new Room("{name}", "{gates[name]["wall"]}", '
            f'new int[]{arr(regions[f"ward_{name}"])}, new int[]{arr(regions[f"room_{name}"])}),')

    grounds_lines = []
    for name in ("PONDS", "LOGGING", "FARM"):
        grounds_lines.append(
            f'        new Ground("{name}", new int[]{arr(regions[f"room_{name}"])}, '
            f'new int[]{arr(regions[f"corridor_{name}LINK"])}),')

    map_layout = f"""{GENERATED_BANNER}
package com.lukeprison.prison;

/**
 * The prison's fixed geometry.
 *
 * The hub is a compact square whose perimeter carries GATES ONLY. Each gate
 * opens through a short corridor into a ward; a mine's ward holds a cage lift
 * down to its pit, while a room gate's ward opens straight into its room.
 * Mine footprints therefore cost the hub no perimeter at all, which is what
 * keeps it walkable.
 */
public class MapLayout {{
    public static final int[] HUB = {arr(layout["hub"])};
    public static final int HUB_HALF = {layout["hub_half"]};
    public static final int CORRIDOR_LEN = {layout["corridor_len"]};
    public static final int WARD_DEPTH = {layout["ward_depth"]};
    public static final int GATE_W = {layout["gate_w"]};

    /** Underground mine level: the ore band, and the rim walkway above it. */
    public static final int MINE_ORE_BOTTOM = {layout["mine_ore_bottom"]};
    public static final int MINE_ORE_TOP = {layout["mine_ore_top"]};
    public static final int MINE_RIM_Y = {layout["mine_rim_y"]};

    /** One gate in the hub wall. kind is "mine", "room" or "intake". */
    public record Gate(String name, String wall, int centre, String kind, int[] corridor, int[] ward) {{ }}

    public static final Gate[] GATES = {{
{chr(10).join(gate_lines)}
    }};

    /** A gate whose ward opens into a room at hub level, rather than onto a lift. */
    public record Room(String name, String wall, int[] ward, int[] room) {{ }}

    public static final Room[] ROOMS = {{
{chr(10).join(room_lines)}
    }};

    /** The outdoor grounds, clustered around one shared green beyond the fishing lobby. */
    public record Ground(String name, int[] area, int[] link) {{ }}

    public static final int[] GREEN = {arr(regions["room_GREEN"])};

    public static final Ground[] GROUNDS = {{
{chr(10).join(grounds_lines)}
    }};

    /** Arrival: the starter yard and the walk from it to the intake gate. */
    public static final int[] STARTER = {arr(layout["starter"])};
    public static final int[] STARTER_LINK = {arr(regions["corridor_STARTERLINK"])};
    public static final int INTAKE_CENTRE = {int(round(gates["INTAKE"]["centre"]))};

    public static Gate gate(String name) {{
        for (Gate g : GATES) if (g.name().equals(name)) return g;
        throw new IllegalArgumentException("no gate: " + name);
    }}

    public static Room room(String name) {{
        for (Room r : ROOMS) if (r.name().equals(name)) return r;
        throw new IllegalArgumentException("no room: " + name);
    }}

    public static Ground ground(String name) {{
        for (Ground g : GROUNDS) if (g.name().equals(name)) return g;
        throw new IllegalArgumentException("no ground: " + name);
    }}
}}
"""

    # ------------------------------------------------------------ RankMineData

    def def_line(rank):
        e = econ[rank]
        common = (f'"{rank}",{e["cost"]},"{e["next"]}","{e["filler"]}","{e["common"]}",'
                  f'"{e["rare"]}",{e["fillerPrice"]},{e["commonPrice"]},{e["rarePrice"]}')
        if rank == "FREE":
            return (f'        RANKS.put("FREE", new Def({common},"W",0,'
                    f'null,null,null,0,0));')
        lift = lifts[rank]
        g = gates[rank]
        lx, ly, lz = lift["landing"]
        return (f'        RANKS.put("{rank}", new Def({common},'
                f'"{g["wall"]}",{int(round(g["centre"]))},'
                f'new int[]{arr(lift["pit"])},new int[]{arr(lift["rim"])},'
                f'new int[]{{{int(round(lx))},{int(round(ly))},{int(round(lz))}}},'
                f'{lift["ore_bottom"]},{lift["ore_top"]}));')

    rank_lines = "\n".join(def_line(r) for r in RANKS + ["FREE"])

    rank_data = f"""{GENERATED_BANNER}
package com.lukeprison.prison;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every rank: its economics, and the geometry of its mine.
 *
 * A mine is an open PIT on the underground mine level — a bedrock box with an
 * ore band in it and open air above, so players drop in and mine downward
 * rather than tunnelling into a solid cube. {{@code rim}} is the walkway that
 * wraps the pit, and {{@code landing}} is where that mine's cage lift puts you
 * down: always on the rim, never over the hole.
 */
public class RankMineData {{

    public static class Def {{
        public final String rank, next, filler, common, rare, wall;
        public final int cost, gateCentre, oreBottom, oreTop;
        public final double fillerPrice, commonPrice, rarePrice;
        /** {{x1,z1,x2,z2}} of the ore area. */
        public final int[] pit;
        /** {{x1,z1,x2,z2}} of the walkway wrapping the pit. */
        public final int[] rim;
        /** {{x,y,z}} the cage lift sets players down on. */
        public final int[] landing;

        public Def(String rank, int cost, String next, String filler, String common, String rare,
                   double fillerPrice, double commonPrice, double rarePrice,
                   String wall, int gateCentre,
                   int[] pit, int[] rim, int[] landing, int oreBottom, int oreTop) {{
            this.rank = rank; this.cost = cost; this.next = next;
            this.filler = filler; this.common = common; this.rare = rare;
            this.fillerPrice = fillerPrice; this.commonPrice = commonPrice; this.rarePrice = rarePrice;
            this.wall = wall; this.gateCentre = gateCentre;
            this.pit = pit; this.rim = rim; this.landing = landing;
            this.oreBottom = oreBottom; this.oreTop = oreTop;
        }}

        /** FREE has no mine of its own — it unlocks the open world instead. */
        public boolean hasMine() {{ return pit != null; }}

        /** The ward this rank's gate opens into, on the hub wall. */
        public MapLayout.Gate gate() {{ return MapLayout.gate(rank); }}
    }}

    public static final Map<String, Def> RANKS = new LinkedHashMap<>();

    static {{
{rank_lines}
    }}
}}
"""

    with open(MAP_LAYOUT_PATH, "w") as f:
        f.write(map_layout)
    with open(RANK_DATA_PATH, "w") as f:
        f.write(rank_data)

    print(f"hub {layout['hub'][2] - layout['hub'][0]}x{layout['hub'][3] - layout['hub'][1]}, "
          f"{len(gates)} gates, {len(lifts)} mine pits")
    print(f"wrote {os.path.relpath(MAP_LAYOUT_PATH, REPO)}")
    print(f"wrote {os.path.relpath(RANK_DATA_PATH, REPO)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
