package com.lukeprison.prison;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.Map;

/**
 * A 5x7 bitmap font rendered in blocks, for the giant letters prison servers put above mine
 * gates and across facades. Each glyph is 7 rows of 5 characters; '#' is a block.
 */
public class BlockFont {

    public enum Axis { POS_X, NEG_X, POS_Z, NEG_Z }

    private static final Map<Character, String[]> GLYPHS = Map.ofEntries(
            Map.entry('A', new String[]{" ### ", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"}),
            Map.entry('B', new String[]{"#### ", "#   #", "#   #", "#### ", "#   #", "#   #", "#### "}),
            Map.entry('C', new String[]{" ####", "#    ", "#    ", "#    ", "#    ", "#    ", " ####"}),
            Map.entry('D', new String[]{"#### ", "#   #", "#   #", "#   #", "#   #", "#   #", "#### "}),
            Map.entry('E', new String[]{"#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####"}),
            Map.entry('F', new String[]{"#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#    "}),
            Map.entry('G', new String[]{" ####", "#    ", "#    ", "#  ##", "#   #", "#   #", " ####"}),
            Map.entry('H', new String[]{"#   #", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"}),
            Map.entry('I', new String[]{"#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "#####"}),
            Map.entry('J', new String[]{"  ###", "   # ", "   # ", "   # ", "   # ", "#  # ", " ##  "}),
            Map.entry('K', new String[]{"#   #", "#  # ", "# #  ", "##   ", "# #  ", "#  # ", "#   #"}),
            Map.entry('L', new String[]{"#    ", "#    ", "#    ", "#    ", "#    ", "#    ", "#####"}),
            Map.entry('M', new String[]{"#   #", "## ##", "# # #", "#   #", "#   #", "#   #", "#   #"}),
            Map.entry('N', new String[]{"#   #", "##  #", "# # #", "#  ##", "#   #", "#   #", "#   #"}),
            Map.entry('O', new String[]{" ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "}),
            Map.entry('P', new String[]{"#### ", "#   #", "#   #", "#### ", "#    ", "#    ", "#    "}),
            Map.entry('Q', new String[]{" ### ", "#   #", "#   #", "#   #", "# # #", "#  # ", " ## #"}),
            Map.entry('R', new String[]{"#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #"}),
            Map.entry('S', new String[]{" ####", "#    ", "#    ", " ### ", "    #", "    #", "#### "}),
            Map.entry('T', new String[]{"#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  "}),
            Map.entry('U', new String[]{"#   #", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "}),
            Map.entry('V', new String[]{"#   #", "#   #", "#   #", "#   #", "#   #", " # # ", "  #  "}),
            Map.entry('W', new String[]{"#   #", "#   #", "#   #", "#   #", "# # #", "## ##", "#   #"}),
            Map.entry('X', new String[]{"#   #", "#   #", " # # ", "  #  ", " # # ", "#   #", "#   #"}),
            Map.entry('Y', new String[]{"#   #", "#   #", " # # ", "  #  ", "  #  ", "  #  ", "  #  "}),
            Map.entry('Z', new String[]{"#####", "    #", "   # ", "  #  ", " #   ", "#    ", "#####"}),
            Map.entry(' ', new String[]{"     ", "     ", "     ", "     ", "     ", "     ", "     "})
    );

    public static final int GLYPH_W = 5, GLYPH_H = 7, GAP = 1;

    /** Total width in blocks of a rendered string. */
    public static int width(String text) {
        return text.length() * (GLYPH_W + GAP) - GAP;
    }

    /**
     * Renders text on a vertical plane. (x, y, z) is the top-left corner of the first glyph.
     * The axis says which way successive letters advance; the plane is perpendicular to the
     * unused horizontal axis. Reading direction is chosen so the text is legible to a viewer
     * standing on the "outside" face:
     *   POS_X — read facing north;  NEG_X — read facing south;
     *   POS_Z — read facing east;   NEG_Z — read facing west.
     *
     * For the NEG_X/NEG_Z axes both the letter-advance step and the in-glyph column both
     * subtract from the world coordinate, which compounds into a full mirror image (right
     * letter order, but each glyph — and the string as a whole — flipped, exactly like text
     * seen in a mirror). The column is reflected here to cancel that out.
     */
    public static void write(World world, String text, int x, int y, int z, Axis axis, Material mat) {
        int cursor = 0;
        for (char ch : text.toUpperCase().toCharArray()) {
            String[] glyph = GLYPHS.getOrDefault(ch, GLYPHS.get(' '));
            for (int row = 0; row < GLYPH_H; row++) {
                for (int col = 0; col < GLYPH_W; col++) {
                    if (glyph[row].charAt(col) != '#') continue;
                    int along = cursor + col;
                    int alongMirrored = cursor + (GLYPH_W - 1 - col);
                    int by = y - row;
                    switch (axis) {
                        case POS_X -> world.getBlockAt(x + along, by, z).setType(mat, false);
                        case NEG_X -> world.getBlockAt(x - alongMirrored, by, z).setType(mat, false);
                        case POS_Z -> world.getBlockAt(x, by, z + along).setType(mat, false);
                        case NEG_Z -> world.getBlockAt(x, by, z - alongMirrored).setType(mat, false);
                    }
                }
            }
            cursor += GLYPH_W + GAP;
        }
    }
}
