/*
 * GENERATED FILE — do not edit by hand.
 * Produced by tools/gen_java.py from tools/layout_gen.py (geometry)
 * and tools/economy.json (rank economics). Re-run `cd tools && python3 gen_java.py`.
 */
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
public class MapLayout {
    public static final int[] HUB = {-68,-68,68,68};
    public static final int HUB_HALF = 68;
    public static final int CORRIDOR_LEN = 6;
    public static final int WARD_DEPTH = 12;
    public static final int GATE_W = 7;

    /** Underground mine level: the ore band, and the rim walkway above it. */
    public static final int MINE_ORE_BOTTOM = 56;
    public static final int MINE_ORE_TOP = 68;
    public static final int MINE_RIM_Y = 70;

    /** One gate in the hub wall. kind is "mine", "room" or "intake". */
    public record Gate(String name, String wall, int centre, String kind, int[] corridor, int[] ward) { }

    public static final Gate[] GATES = {
        new Gate("A", "N", -42, "mine", new int[]{-46,-74,-38,-68}, new int[]{-47,-86,-37,-74}),
        new Gate("B", "N", -30, "mine", new int[]{-34,-74,-26,-68}, new int[]{-35,-86,-25,-74}),
        new Gate("C", "N", -18, "mine", new int[]{-22,-74,-14,-68}, new int[]{-23,-86,-13,-74}),
        new Gate("D", "N", -6, "mine", new int[]{-10,-74,-2,-68}, new int[]{-11,-86,-1,-74}),
        new Gate("E", "N", 6, "mine", new int[]{2,-74,10,-68}, new int[]{1,-86,11,-74}),
        new Gate("F", "N", 18, "mine", new int[]{14,-74,22,-68}, new int[]{13,-86,23,-74}),
        new Gate("G", "N", 30, "mine", new int[]{26,-74,34,-68}, new int[]{25,-86,35,-74}),
        new Gate("FISHING", "N", 42, "room", new int[]{38,-74,46,-68}, new int[]{37,-86,47,-74}),
        new Gate("H", "E", -42, "mine", new int[]{68,-46,74,-38}, new int[]{74,-47,86,-37}),
        new Gate("I", "E", -30, "mine", new int[]{68,-34,74,-26}, new int[]{74,-35,86,-25}),
        new Gate("J", "E", -18, "mine", new int[]{68,-22,74,-14}, new int[]{74,-23,86,-13}),
        new Gate("K", "E", -6, "mine", new int[]{68,-10,74,-2}, new int[]{74,-11,86,-1}),
        new Gate("L", "E", 6, "mine", new int[]{68,2,74,10}, new int[]{74,1,86,11}),
        new Gate("M", "E", 18, "mine", new int[]{68,14,74,22}, new int[]{74,13,86,23}),
        new Gate("N", "E", 30, "mine", new int[]{68,26,74,34}, new int[]{74,25,86,35}),
        new Gate("CRATES", "E", 42, "room", new int[]{68,38,74,46}, new int[]{74,37,86,47}),
        new Gate("O", "S", -42, "mine", new int[]{-46,68,-38,74}, new int[]{-47,74,-37,86}),
        new Gate("P", "S", -30, "mine", new int[]{-34,68,-26,74}, new int[]{-35,74,-25,86}),
        new Gate("Q", "S", -18, "mine", new int[]{-22,68,-14,74}, new int[]{-23,74,-13,86}),
        new Gate("R", "S", -6, "mine", new int[]{-10,68,-2,74}, new int[]{-11,74,-1,86}),
        new Gate("S", "S", 6, "mine", new int[]{2,68,10,74}, new int[]{1,74,11,86}),
        new Gate("T", "S", 18, "mine", new int[]{14,68,22,74}, new int[]{13,74,23,86}),
        new Gate("U", "S", 30, "mine", new int[]{26,68,34,74}, new int[]{25,74,35,86}),
        new Gate("YARD", "S", 42, "room", new int[]{38,68,46,74}, new int[]{37,74,47,86}),
        new Gate("V", "W", -30, "mine", new int[]{-74,-34,-68,-26}, new int[]{-86,-35,-74,-25}),
        new Gate("W", "W", -18, "mine", new int[]{-74,-22,-68,-14}, new int[]{-86,-23,-74,-13}),
        new Gate("X", "W", -6, "mine", new int[]{-74,-10,-68,-2}, new int[]{-86,-11,-74,-1}),
        new Gate("Y", "W", 6, "mine", new int[]{-74,2,-68,10}, new int[]{-86,1,-74,11}),
        new Gate("Z", "W", 18, "mine", new int[]{-74,14,-68,22}, new int[]{-86,13,-74,23}),
        new Gate("INTAKE", "W", 30, "intake", new int[]{-74,26,-68,34}, new int[]{-86,25,-74,35}),
    };

    /** A gate whose ward opens into a room at hub level, rather than onto a lift. */
    public record Room(String name, String wall, int[] ward, int[] room) { }

    public static final Room[] ROOMS = {
        new Room("FISHING", "N", new int[]{37,-86,47,-74}, new int[]{18,-94,66,-86}),
        new Room("CRATES", "E", new int[]{74,37,86,47}, new int[]{86,26,108,58}),
        new Room("YARD", "S", new int[]{37,74,47,86}, new int[]{20,86,64,126}),
    };

    /** The outdoor grounds, clustered around one shared green beyond the fishing lobby. */
    public record Ground(String name, int[] area, int[] link) { }

    public static final int[] GREEN = {8,-154,76,-94};

    public static final Ground[] GROUNDS = {
        new Ground("PONDS", new int[]{-52,-152,4,-96}, new int[]{4,-128,8,-120}),
        new Ground("LOGGING", new int[]{14,-214,70,-158}, new int[]{38,-158,46,-154}),
        new Ground("FARM", new int[]{80,-152,136,-96}, new int[]{76,-128,80,-120}),
    };

    /** Arrival: the starter yard and the walk from it to the intake gate. */
    public static final int[] STARTER = {-168,-15,-98,75};
    public static final int[] STARTER_LINK = {-98,27,-86,33};
    public static final int INTAKE_CENTRE = 30;

    public static Gate gate(String name) {
        for (Gate g : GATES) if (g.name().equals(name)) return g;
        throw new IllegalArgumentException("no gate: " + name);
    }

    public static Room room(String name) {
        for (Room r : ROOMS) if (r.name().equals(name)) return r;
        throw new IllegalArgumentException("no room: " + name);
    }

    public static Ground ground(String name) {
        for (Ground g : GROUNDS) if (g.name().equals(name)) return g;
        throw new IllegalArgumentException("no ground: " + name);
    }
}
