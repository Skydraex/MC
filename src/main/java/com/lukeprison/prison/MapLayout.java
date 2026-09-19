package com.lukeprison.prison;
/** The radial hub's fixed geometry — hub bounds, the player-intake path, and the
 *  four non-mine "special" gates (fishing, crates, yard, cells), each built exactly
 *  like a mine's own gate: a direct corridor off the hub wall, a ward/antechamber,
 *  then its own room. All baked in from the validated layout (tools/layout_gen.py);
 *  WorldBuilder only builds what's here, never recomputes it. */
public class MapLayout {
    public static final int[] HUB = {-885,-885,885,885};
    public static final int CORRIDOR_LEN = 14, WARD_DEPTH = 16;

    // Player intake enters through the hub's west wall, well inside the empty corner
    // buffer — guaranteed clear of every mine/special by construction.
    public static final int INTAKE_GATE_Z = -795;
    public static final int[] INTAKE_WARD = {-915,-803,-899,-787};
    public static final int[] INTAKE_CORRIDOR = {-899,-798,-885,-792};
    public static final int[] STARTER = {-1135,-835,-935,-755};

    public static class Special {
        public final String name, wall;
        public final int[] ward, room;
        public Special(String name, String wall, int[] ward, int[] room) {
            this.name = name; this.wall = wall; this.ward = ward; this.room = room;
        }
    }
    public static final Special[] SPECIALS = {
        new Special("FISHING", "N", new int[]{-90,-915,90,-899}, new int[]{-90,-1075,90,-915}),
        new Special("CRATES", "E", new int[]{899,-40,915,40}, new int[]{915,-40,965,40}),
        new Special("YARD", "S", new int[]{-70,899,70,915}, new int[]{-70,915,70,1005}),
        new Special("CELLS", "W", new int[]{-915,-90,-899,90}, new int[]{-1115,-90,-915,90}),
    };

    public static Special special(String name) {
        for (Special s : SPECIALS) if (s.name.equals(name)) return s;
        throw new IllegalArgumentException(name);
    }
}
