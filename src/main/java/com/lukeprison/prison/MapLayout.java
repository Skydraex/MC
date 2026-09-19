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
        new Gate("O", "S", 42, "mine", new int[]{38,68,46,74}, new int[]{37,74,47,86}),
        new Gate("P", "S", 30, "mine", new int[]{26,68,34,74}, new int[]{25,74,35,86}),
        new Gate("Q", "S", 18, "mine", new int[]{14,68,22,74}, new int[]{13,74,23,86}),
        new Gate("R", "S", 6, "mine", new int[]{2,68,10,74}, new int[]{1,74,11,86}),
        new Gate("S", "S", -6, "mine", new int[]{-10,68,-2,74}, new int[]{-11,74,-1,86}),
        new Gate("T", "S", -18, "mine", new int[]{-22,68,-14,74}, new int[]{-23,74,-13,86}),
        new Gate("U", "S", -30, "mine", new int[]{-34,68,-26,74}, new int[]{-35,74,-25,86}),
        new Gate("YARD", "S", -42, "room", new int[]{-46,68,-38,74}, new int[]{-47,74,-37,86}),
        new Gate("V", "W", 30, "mine", new int[]{-74,26,-68,34}, new int[]{-86,25,-74,35}),
        new Gate("W", "W", 18, "mine", new int[]{-74,14,-68,22}, new int[]{-86,13,-74,23}),
        new Gate("X", "W", 6, "mine", new int[]{-74,2,-68,10}, new int[]{-86,1,-74,11}),
        new Gate("Y", "W", -6, "mine", new int[]{-74,-10,-68,-2}, new int[]{-86,-11,-74,-1}),
        new Gate("Z", "W", -18, "mine", new int[]{-74,-22,-68,-14}, new int[]{-86,-23,-74,-13}),
        new Gate("INTAKE", "W", -30, "intake", new int[]{-74,-34,-68,-26}, new int[]{-86,-35,-74,-25}),
    };

    /** A gate whose ward opens into a room at hub level, rather than onto a lift. */
    public record Room(String name, String wall, int[] ward, int[] room) { }

    public static final Room[] ROOMS = {
        new Room("FISHING", "N", new int[]{37,-86,47,-74}, new int[]{18,-94,66,-86}),
        new Room("CRATES", "E", new int[]{74,37,86,47}, new int[]{86,26,108,58}),
        new Room("YARD", "S", new int[]{-47,74,-37,86}, new int[]{-64,86,-20,126}),
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
    public static final int[] STARTER = {-168,-75,-98,15};
    public static final int[] STARTER_LINK = {-98,-33,-86,-27};
    public static final int INTAKE_CENTRE = -30;

    /** The underground ring concourse: four legs, joined at the corners. */
    public static final int[][] RING = {
        new int[]{-183,-183,183,-174},
        new int[]{174,-174,183,174},
        new int[]{-183,174,183,183},
        new int[]{-183,-174,-174,174},
    };

    public static final int CONCOURSE_IN = 174;
    public static final int CONCOURSE_OUT = 183;
    public static final int SHAFT_W = 7;
    public static final int RIM_W = 4;

    /**
     * Every opening that has to exist between two areas, derived from the validated layout
     * rather than carved by hand wherever a wall happened to be built.
     *
     * Three separate rounds of "I cannot get in" — all thirty wards, then the fishing
     * lobby, then the green and all three grounds — were one missing doorway each. A
     * builder that forgets one seals the area behind it, and nothing catches that. These
     * are cut in a single pass after everything is built, so adding a region brings its
     * doors with it.
     *
     * level is "HUB" for the surface and "MINE" for the underground.
     */
    public record Doorway(String a, String b, int[] rect, String level) { }

    public static final Doorway[] DOORWAYS = {
        new Doorway("corridor_A", "hub_HUB", new int[]{-45,-70,-39,-66}, "HUB"),
        new Doorway("corridor_A", "ward_A", new int[]{-45,-76,-39,-72}, "HUB"),
        new Doorway("corridor_B", "hub_HUB", new int[]{-33,-70,-27,-66}, "HUB"),
        new Doorway("corridor_B", "ward_B", new int[]{-33,-76,-27,-72}, "HUB"),
        new Doorway("corridor_C", "hub_HUB", new int[]{-21,-70,-15,-66}, "HUB"),
        new Doorway("corridor_C", "ward_C", new int[]{-21,-76,-15,-72}, "HUB"),
        new Doorway("corridor_CRATES", "hub_HUB", new int[]{66,39,70,45}, "HUB"),
        new Doorway("corridor_CRATES", "ward_CRATES", new int[]{72,39,76,45}, "HUB"),
        new Doorway("corridor_D", "hub_HUB", new int[]{-9,-70,-3,-66}, "HUB"),
        new Doorway("corridor_D", "ward_D", new int[]{-9,-76,-3,-72}, "HUB"),
        new Doorway("corridor_E", "hub_HUB", new int[]{3,-70,9,-66}, "HUB"),
        new Doorway("corridor_E", "ward_E", new int[]{3,-76,9,-72}, "HUB"),
        new Doorway("corridor_F", "hub_HUB", new int[]{15,-70,21,-66}, "HUB"),
        new Doorway("corridor_F", "ward_F", new int[]{15,-76,21,-72}, "HUB"),
        new Doorway("corridor_FARMLINK", "room_FARM", new int[]{78,-128,82,-120}, "HUB"),
        new Doorway("corridor_FARMLINK", "room_GREEN", new int[]{74,-128,78,-120}, "HUB"),
        new Doorway("corridor_FISHING", "hub_HUB", new int[]{39,-70,45,-66}, "HUB"),
        new Doorway("corridor_FISHING", "ward_FISHING", new int[]{39,-76,45,-72}, "HUB"),
        new Doorway("corridor_G", "hub_HUB", new int[]{27,-70,33,-66}, "HUB"),
        new Doorway("corridor_G", "ward_G", new int[]{27,-76,33,-72}, "HUB"),
        new Doorway("corridor_H", "hub_HUB", new int[]{66,-45,70,-39}, "HUB"),
        new Doorway("corridor_H", "ward_H", new int[]{72,-45,76,-39}, "HUB"),
        new Doorway("corridor_I", "hub_HUB", new int[]{66,-33,70,-27}, "HUB"),
        new Doorway("corridor_I", "ward_I", new int[]{72,-33,76,-27}, "HUB"),
        new Doorway("corridor_INTAKE", "hub_HUB", new int[]{-70,-33,-66,-27}, "HUB"),
        new Doorway("corridor_INTAKE", "ward_INTAKE", new int[]{-76,-33,-72,-27}, "HUB"),
        new Doorway("corridor_J", "hub_HUB", new int[]{66,-21,70,-15}, "HUB"),
        new Doorway("corridor_J", "ward_J", new int[]{72,-21,76,-15}, "HUB"),
        new Doorway("corridor_K", "hub_HUB", new int[]{66,-9,70,-3}, "HUB"),
        new Doorway("corridor_K", "ward_K", new int[]{72,-9,76,-3}, "HUB"),
        new Doorway("corridor_L", "hub_HUB", new int[]{66,3,70,9}, "HUB"),
        new Doorway("corridor_L", "ward_L", new int[]{72,3,76,9}, "HUB"),
        new Doorway("corridor_LOGGINGLINK", "room_GREEN", new int[]{38,-156,46,-152}, "HUB"),
        new Doorway("corridor_LOGGINGLINK", "room_LOGGING", new int[]{38,-160,46,-156}, "HUB"),
        new Doorway("corridor_M", "hub_HUB", new int[]{66,15,70,21}, "HUB"),
        new Doorway("corridor_M", "ward_M", new int[]{72,15,76,21}, "HUB"),
        new Doorway("corridor_N", "hub_HUB", new int[]{66,27,70,33}, "HUB"),
        new Doorway("corridor_N", "ward_N", new int[]{72,27,76,33}, "HUB"),
        new Doorway("corridor_O", "hub_HUB", new int[]{39,66,45,70}, "HUB"),
        new Doorway("corridor_O", "ward_O", new int[]{39,72,45,76}, "HUB"),
        new Doorway("corridor_P", "hub_HUB", new int[]{27,66,33,70}, "HUB"),
        new Doorway("corridor_P", "ward_P", new int[]{27,72,33,76}, "HUB"),
        new Doorway("corridor_PONDSLINK", "room_GREEN", new int[]{6,-128,10,-120}, "HUB"),
        new Doorway("corridor_PONDSLINK", "room_PONDS", new int[]{2,-128,6,-120}, "HUB"),
        new Doorway("corridor_Q", "hub_HUB", new int[]{15,66,21,70}, "HUB"),
        new Doorway("corridor_Q", "ward_Q", new int[]{15,72,21,76}, "HUB"),
        new Doorway("corridor_R", "hub_HUB", new int[]{3,66,9,70}, "HUB"),
        new Doorway("corridor_R", "ward_R", new int[]{3,72,9,76}, "HUB"),
        new Doorway("corridor_S", "hub_HUB", new int[]{-9,66,-3,70}, "HUB"),
        new Doorway("corridor_S", "ward_S", new int[]{-9,72,-3,76}, "HUB"),
        new Doorway("corridor_STARTERLINK", "room_STARTER", new int[]{-100,-32,-96,-28}, "HUB"),
        new Doorway("corridor_STARTERLINK", "ward_INTAKE", new int[]{-88,-32,-84,-28}, "HUB"),
        new Doorway("corridor_T", "hub_HUB", new int[]{-21,66,-15,70}, "HUB"),
        new Doorway("corridor_T", "ward_T", new int[]{-21,72,-15,76}, "HUB"),
        new Doorway("corridor_U", "hub_HUB", new int[]{-33,66,-27,70}, "HUB"),
        new Doorway("corridor_U", "ward_U", new int[]{-33,72,-27,76}, "HUB"),
        new Doorway("corridor_V", "hub_HUB", new int[]{-70,27,-66,33}, "HUB"),
        new Doorway("corridor_V", "ward_V", new int[]{-76,27,-72,33}, "HUB"),
        new Doorway("corridor_W", "hub_HUB", new int[]{-70,15,-66,21}, "HUB"),
        new Doorway("corridor_W", "ward_W", new int[]{-76,15,-72,21}, "HUB"),
        new Doorway("corridor_X", "hub_HUB", new int[]{-70,3,-66,9}, "HUB"),
        new Doorway("corridor_X", "ward_X", new int[]{-76,3,-72,9}, "HUB"),
        new Doorway("corridor_Y", "hub_HUB", new int[]{-70,-9,-66,-3}, "HUB"),
        new Doorway("corridor_Y", "ward_Y", new int[]{-76,-9,-72,-3}, "HUB"),
        new Doorway("corridor_YARD", "hub_HUB", new int[]{-45,66,-39,70}, "HUB"),
        new Doorway("corridor_YARD", "ward_YARD", new int[]{-45,72,-39,76}, "HUB"),
        new Doorway("corridor_Z", "hub_HUB", new int[]{-70,-21,-66,-15}, "HUB"),
        new Doorway("corridor_Z", "ward_Z", new int[]{-76,-21,-72,-15}, "HUB"),
        new Doorway("rim_A", "ring_N", new int[]{-118,-185,-112,-181}, "MINE"),
        new Doorway("rim_B", "ring_N", new int[]{-82,-185,-76,-181}, "MINE"),
        new Doorway("rim_C", "ring_N", new int[]{-46,-185,-39,-181}, "MINE"),
        new Doorway("rim_D", "ring_N", new int[]{-8,-185,-2,-181}, "MINE"),
        new Doorway("rim_E", "ring_N", new int[]{30,-185,37,-181}, "MINE"),
        new Doorway("rim_F", "ring_N", new int[]{70,-185,76,-181}, "MINE"),
        new Doorway("rim_G", "ring_N", new int[]{110,-185,116,-181}, "MINE"),
        new Doorway("rim_H", "ring_E", new int[]{181,-136,185,-128}, "MINE"),
        new Doorway("rim_I", "ring_E", new int[]{181,-94,185,-87}, "MINE"),
        new Doorway("rim_J", "ring_E", new int[]{181,-52,185,-44}, "MINE"),
        new Doorway("rim_K", "ring_E", new int[]{181,-8,185,-1}, "MINE"),
        new Doorway("rim_L", "ring_E", new int[]{181,36,185,43}, "MINE"),
        new Doorway("rim_M", "ring_E", new int[]{181,80,185,88}, "MINE"),
        new Doorway("rim_N", "ring_E", new int[]{181,126,185,133}, "MINE"),
        new Doorway("rim_O", "ring_S", new int[]{146,181,152,185}, "MINE"),
        new Doorway("rim_P", "ring_S", new int[]{98,181,105,185}, "MINE"),
        new Doorway("rim_Q", "ring_S", new int[]{50,181,57,185}, "MINE"),
        new Doorway("rim_R", "ring_S", new int[]{2,181,8,185}, "MINE"),
        new Doorway("rim_S", "ring_S", new int[]{-48,181,-41,185}, "MINE"),
        new Doorway("rim_T", "ring_S", new int[]{-98,181,-92,185}, "MINE"),
        new Doorway("rim_U", "ring_S", new int[]{-150,181,-143,185}, "MINE"),
        new Doorway("rim_V", "ring_W", new int[]{-185,106,-181,112}, "MINE"),
        new Doorway("rim_W", "ring_W", new int[]{-185,53,-181,60}, "MINE"),
        new Doorway("rim_X", "ring_W", new int[]{-185,0,-181,6}, "MINE"),
        new Doorway("rim_Y", "ring_W", new int[]{-185,-55,-181,-48}, "MINE"),
        new Doorway("rim_Z", "ring_W", new int[]{-185,-110,-181,-104}, "MINE"),
        new Doorway("ring_E", "ring_N", new int[]{175,-176,182,-172}, "MINE"),
        new Doorway("ring_E", "ring_S", new int[]{175,172,182,176}, "MINE"),
        new Doorway("ring_E", "shaft_H", new int[]{172,-45,176,-39}, "MINE"),
        new Doorway("ring_E", "shaft_I", new int[]{172,-33,176,-27}, "MINE"),
        new Doorway("ring_E", "shaft_J", new int[]{172,-21,176,-15}, "MINE"),
        new Doorway("ring_E", "shaft_K", new int[]{172,-9,176,-3}, "MINE"),
        new Doorway("ring_E", "shaft_L", new int[]{172,3,176,9}, "MINE"),
        new Doorway("ring_E", "shaft_M", new int[]{172,15,176,21}, "MINE"),
        new Doorway("ring_E", "shaft_N", new int[]{172,27,176,33}, "MINE"),
        new Doorway("ring_N", "ring_W", new int[]{-182,-176,-175,-172}, "MINE"),
        new Doorway("ring_N", "shaft_A", new int[]{-45,-176,-39,-172}, "MINE"),
        new Doorway("ring_N", "shaft_B", new int[]{-33,-176,-27,-172}, "MINE"),
        new Doorway("ring_N", "shaft_C", new int[]{-21,-176,-15,-172}, "MINE"),
        new Doorway("ring_N", "shaft_D", new int[]{-9,-176,-3,-172}, "MINE"),
        new Doorway("ring_N", "shaft_E", new int[]{3,-176,9,-172}, "MINE"),
        new Doorway("ring_N", "shaft_F", new int[]{15,-176,21,-172}, "MINE"),
        new Doorway("ring_N", "shaft_G", new int[]{27,-176,33,-172}, "MINE"),
        new Doorway("ring_S", "ring_W", new int[]{-182,172,-175,176}, "MINE"),
        new Doorway("ring_S", "shaft_O", new int[]{39,172,45,176}, "MINE"),
        new Doorway("ring_S", "shaft_P", new int[]{27,172,33,176}, "MINE"),
        new Doorway("ring_S", "shaft_Q", new int[]{15,172,21,176}, "MINE"),
        new Doorway("ring_S", "shaft_R", new int[]{3,172,9,176}, "MINE"),
        new Doorway("ring_S", "shaft_S", new int[]{-9,172,-3,176}, "MINE"),
        new Doorway("ring_S", "shaft_T", new int[]{-21,172,-15,176}, "MINE"),
        new Doorway("ring_S", "shaft_U", new int[]{-33,172,-27,176}, "MINE"),
        new Doorway("ring_W", "shaft_V", new int[]{-176,27,-172,33}, "MINE"),
        new Doorway("ring_W", "shaft_W", new int[]{-176,15,-172,21}, "MINE"),
        new Doorway("ring_W", "shaft_X", new int[]{-176,3,-172,9}, "MINE"),
        new Doorway("ring_W", "shaft_Y", new int[]{-176,-9,-172,-3}, "MINE"),
        new Doorway("ring_W", "shaft_Z", new int[]{-176,-21,-172,-15}, "MINE"),
        new Doorway("room_CRATES", "ward_CRATES", new int[]{84,38,88,46}, "HUB"),
        new Doorway("room_FISHING", "room_GREEN", new int[]{38,-96,46,-92}, "HUB"),
        new Doorway("room_FISHING", "ward_FISHING", new int[]{38,-88,46,-84}, "HUB"),
        new Doorway("room_YARD", "ward_YARD", new int[]{-46,84,-38,88}, "HUB"),
    };

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
