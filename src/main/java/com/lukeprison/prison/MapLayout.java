package com.lukeprison.prison;
/** The radial hub's fixed geometry — hub bounds, the player-intake path, and the
 *  four non-mine "special" gates (fishing, crates, yard, cells), each built exactly
 *  like a mine's own gate: a direct corridor off the hub wall, a ward/antechamber,
 *  then its own room. All baked in from the validated layout (tools/layout_gen.py);
 *  WorldBuilder only builds what's here, never recomputes it. */
public class MapLayout {
    public static final int[] HUB = {-231,-231,231,231};
    public static final int CORRIDOR_LEN = 6, WARD_DEPTH = 8;

    // Player intake enters through the hub's west wall, well inside the empty corner
    // buffer — guaranteed clear of every mine/special by construction (verified by
    // tools/gen_java.py against every region before this file is written).
    public static final int INTAKE_GATE_Z = -209;
    public static final int[] INTAKE_WARD = {-245,-217,-237,-201};
    public static final int[] INTAKE_CORRIDOR = {-237,-212,-231,-206};
    public static final int[] STARTER = {-315,-254,-255,-164};

    public static class Special {
        public final String name, wall;
        public final int[] ward, room;
        public Special(String name, String wall, int[] ward, int[] room) {
            this.name = name; this.wall = wall; this.ward = ward; this.room = room;
        }
    }
    public static final Special[] SPECIALS = {
        new Special("FISHING", "N", new int[]{-25,-245,25,-237}, new int[]{-25,-290,25,-245}),
        new Special("CRATES", "E", new int[]{237,-12,245,12}, new int[]{245,-12,260,12}),
        new Special("YARD", "S", new int[]{-20,237,20,245}, new int[]{-20,245,20,270}),
        new Special("CELLS", "W", new int[]{-245,-25,-237,25}, new int[]{-300,-25,-245,25}),
    };

    public static Special special(String name) {
        for (Special s : SPECIALS) if (s.name.equals(name)) return s;
        throw new IllegalArgumentException(name);
    }
}
