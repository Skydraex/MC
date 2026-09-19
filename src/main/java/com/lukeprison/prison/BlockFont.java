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
     * Renders text on a vertical plane. (x, y, z) is the top-left corner of the first glyph AS
     * THE READER SEES IT, and rows advance DOWNWARD from y.
     *
     * The axis is the direction the text runs in world coordinates, and both the letter order
     * and each glyph's own columns advance that way. Pick the axis from where the reader stands:
     * text must run toward the reader's right.
     *
     *   viewer facing north (-Z) -> their right is +X -> POS_X, start at the lowest  X
     *   viewer facing south (+Z) -> their right is -X -> NEG_X, start at the highest X
     *   viewer facing east  (+X) -> their right is +Z -> POS_Z, start at the lowest  Z
     *   viewer facing west  (-X) -> their right is -Z -> NEG_Z, start at the highest Z
     *
     * The previous version advanced the letter order negatively for NEG_X/NEG_Z but still laid
     * each glyph's columns positively, in an attempt to "cancel a mirror". The result was that
     * north- and east-facing walls read correctly while south- and west-facing walls came out
     * with every letter mirrored, and no axis could render them properly. Columns now follow the
     * axis, so all four walls read the same way round.
     */
    public static void write(World world, String text, int x, int y, int z, Axis axis, Material mat) {
        int cursor = 0;
        for (char ch : text.toUpperCase().toCharArray()) {
            String[] glyph = GLYPHS.getOrDefault(ch, GLYPHS.get(' '));
            for (int row = 0; row < GLYPH_H; row++) {
                for (int col = 0; col < GLYPH_W; col++) {
                    if (glyph[row].charAt(col) != '#') continue;
                    int along = cursor + col;
                    int by = y - row;
                    switch (axis) {
                        case POS_X -> world.getBlockAt(x + along, by, z).setType(mat, false);
                        case NEG_X -> world.getBlockAt(x - along, by, z).setType(mat, false);
                        case POS_Z -> world.getBlockAt(x, by, z + along).setType(mat, false);
                        case NEG_Z -> world.getBlockAt(x, by, z - along).setType(mat, false);
                    }
                }
            }
            cursor += GLYPH_W + GAP;
        }
    }
}
