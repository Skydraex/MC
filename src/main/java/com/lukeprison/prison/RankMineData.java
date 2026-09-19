/*
 * GENERATED FILE — do not edit by hand.
 * Produced by tools/gen_java.py from tools/layout_gen.py (geometry)
 * and tools/economy.json (rank economics). Re-run `cd tools && python3 gen_java.py`.
 */
package com.lukeprison.prison;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every rank: its economics, and the geometry of its mine.
 *
 * A mine is an open PIT on the underground mine level — a bedrock box with an
 * ore band in it and open air above, so players drop in and mine downward
 * rather than tunnelling into a solid cube. {@code rim} is the walkway that
 * wraps the pit, and {@code landing} is where that mine's cage lift puts you
 * down: always on the rim, never over the hole.
 */
public class RankMineData {

    public static class Def {
        public final String rank, next, filler, common, rare, wall;
        public final int cost, gateCentre, oreBottom, oreTop;
        public final double fillerPrice, commonPrice, rarePrice;
        /** {x1,z1,x2,z2} of the ore area. */
        public final int[] pit;
        /** {x1,z1,x2,z2} of the walkway wrapping the pit. */
        public final int[] rim;
        /** {x,y,z} you arrive on, on foot or by cage: always solid rim, never the hole. */
        public final int[] landing;
        /** {x1,z1,x2,z2} of the walk-down shaft from this rank's ward to the ring. */
        public final int[] shaft;

        public Def(String rank, int cost, String next, String filler, String common, String rare,
                   double fillerPrice, double commonPrice, double rarePrice,
                   String wall, int gateCentre,
                   int[] pit, int[] rim, int[] shaft, int[] landing, int oreBottom, int oreTop) {
            this.rank = rank; this.cost = cost; this.next = next;
            this.filler = filler; this.common = common; this.rare = rare;
            this.fillerPrice = fillerPrice; this.commonPrice = commonPrice; this.rarePrice = rarePrice;
            this.wall = wall; this.gateCentre = gateCentre;
            this.pit = pit; this.rim = rim; this.shaft = shaft; this.landing = landing;
            this.oreBottom = oreBottom; this.oreTop = oreTop;
        }

        /** FREE has no mine of its own — it unlocks the open world instead. */
        public boolean hasMine() { return pit != null; }

        /** The ward this rank's gate opens into, on the hub wall. */
        public MapLayout.Gate gate() { return MapLayout.gate(rank); }
    }

    public static final Map<String, Def> RANKS = new LinkedHashMap<>();

    static {
        RANKS.put("A", new Def("A",0,"B","STONE","COAL_ORE","IRON_ORE",0.5,3.0,20.0,"N",-42,new int[]{-127,-211,-103,-187},new int[]{-131,-215,-99,-183},new int[]{-46,-174,-38,-76},new int[]{-115,44,-185},30,42));
        RANKS.put("B", new Def("B",5000,"C","STONE","COAL_ORE","IRON_ORE",0.95,5.7,38.0,"N",-30,new int[]{-91,-211,-67,-187},new int[]{-95,-215,-63,-183},new int[]{-34,-174,-26,-76},new int[]{-79,44,-185},30,42));
        RANKS.put("C", new Def("C",7800,"D","STONE","COAL_ORE","IRON_ORE",1.4,8.4,56.0,"N",-18,new int[]{-55,-212,-30,-187},new int[]{-59,-216,-26,-183},new int[]{-22,-174,-14,-76},new int[]{-42,44,-185},30,42));
        RANKS.put("D", new Def("D",12000,"E","STONE","COAL_ORE","IRON_ORE",1.85,11.1,74.0,"N",-6,new int[]{-18,-213,8,-187},new int[]{-22,-217,12,-183},new int[]{-10,-174,-2,-76},new int[]{-5,44,-185},30,42));
        RANKS.put("E", new Def("E",18600,"F","STONE","IRON_ORE","GOLD_ORE",2.3,13.8,92.0,"N",6,new int[]{20,-214,47,-187},new int[]{16,-218,51,-183},new int[]{2,-174,10,-76},new int[]{34,44,-185},30,42));
        RANKS.put("F", new Def("F",28900,"G","STONE","IRON_ORE","GOLD_ORE",2.75,16.5,110.0,"N",18,new int[]{59,-215,87,-187},new int[]{55,-219,91,-183},new int[]{14,-174,22,-76},new int[]{73,44,-185},30,42));
        RANKS.put("G", new Def("G",44700,"H","STONE","IRON_ORE","GOLD_ORE",3.2,19.2,128.0,"N",30,new int[]{99,-215,127,-187},new int[]{95,-219,131,-183},new int[]{26,-174,34,-76},new int[]{113,44,-185},30,42));
        RANKS.put("H", new Def("H",69300,"I","STONE","IRON_ORE","GOLD_ORE",3.65,21.9,146.0,"E",-42,new int[]{187,-146,216,-118},new int[]{183,-150,220,-114},new int[]{76,-46,174,-38},new int[]{185,44,-132},30,42));
        RANKS.put("I", new Def("I",90100,"J","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",4.1,24.6,164.0,"E",-30,new int[]{187,-106,217,-76},new int[]{183,-110,221,-72},new int[]{76,-34,174,-26},new int[]{185,44,-90},30,42));
        RANKS.put("J", new Def("J",117200,"K","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",4.55,27.3,182.0,"E",-18,new int[]{187,-64,218,-32},new int[]{183,-68,222,-28},new int[]{76,-22,174,-14},new int[]{185,44,-48},30,42));
        RANKS.put("K", new Def("K",152300,"L","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",5.0,30.0,200.0,"E",-6,new int[]{187,-20,219,12},new int[]{183,-24,223,16},new int[]{76,-10,174,-2},new int[]{185,44,-4},30,42));
        RANKS.put("L", new Def("L",198000,"M","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",5.45,32.7,218.0,"E",6,new int[]{187,24,219,56},new int[]{183,20,223,60},new int[]{76,2,174,10},new int[]{185,44,40},30,42));
        RANKS.put("M", new Def("M",257400,"N","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",5.9,35.4,236.0,"E",18,new int[]{187,68,220,100},new int[]{183,64,224,104},new int[]{76,14,174,22},new int[]{185,44,84},30,42));
        RANKS.put("N", new Def("N",334700,"O","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",6.35,38.1,254.0,"E",30,new int[]{187,112,221,146},new int[]{183,108,225,150},new int[]{76,26,174,34},new int[]{185,44,130},30,42));
        RANKS.put("O", new Def("O",435100,"P","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",6.8,40.8,272.0,"S",42,new int[]{132,187,166,222},new int[]{128,183,170,226},new int[]{38,76,46,174},new int[]{149,44,185},30,42));
        RANKS.put("P", new Def("P",565600,"Q","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",7.25,43.5,290.0,"S",30,new int[]{84,187,120,223},new int[]{80,183,124,227},new int[]{26,76,34,174},new int[]{102,44,185},30,42));
        RANKS.put("Q", new Def("Q",735300,"R","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",7.7,46.2,308.0,"S",18,new int[]{36,187,72,223},new int[]{32,183,76,227},new int[]{14,76,22,174},new int[]{54,44,185},30,42));
        RANKS.put("R", new Def("R",955900,"S","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",8.15,48.9,326.0,"S",6,new int[]{-14,187,24,224},new int[]{-18,183,28,228},new int[]{2,76,10,174},new int[]{5,44,185},30,42));
        RANKS.put("S", new Def("S",1242600,"T","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",8.6,51.6,344.0,"S",-6,new int[]{-64,187,-26,225},new int[]{-68,183,-22,229},new int[]{-10,76,-2,174},new int[]{-44,44,185},30,42));
        RANKS.put("T", new Def("T",1615400,"U","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",9.05,54.3,362.0,"S",-18,new int[]{-114,187,-76,226},new int[]{-118,183,-72,230},new int[]{-22,76,-14,174},new int[]{-95,44,185},30,42));
        RANKS.put("U", new Def("U",2100000,"V","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",9.5,57.0,380.0,"S",-30,new int[]{-166,187,-126,227},new int[]{-170,183,-122,231},new int[]{-34,76,-26,174},new int[]{-146,44,185},30,42));
        RANKS.put("V", new Def("V",2730000,"W","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",9.95,59.7,398.0,"W",30,new int[]{-227,89,-187,129},new int[]{-231,85,-183,133},new int[]{-174,26,-76,34},new int[]{-185,44,109},30,42));
        RANKS.put("W", new Def("W",3549000,"X","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",10.4,62.4,416.0,"W",18,new int[]{-228,36,-187,77},new int[]{-232,32,-183,81},new int[]{-174,14,-76,22},new int[]{-185,44,56},30,42));
        RANKS.put("X", new Def("X",4613700,"Y","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",10.85,65.1,434.0,"W",6,new int[]{-229,-18,-187,24},new int[]{-233,-22,-183,28},new int[]{-174,2,-76,10},new int[]{-185,44,3},30,42));
        RANKS.put("Y", new Def("Y",5997900,"Z","REINFORCED_DEEPSLATE","EMERALD_ORE","SCULK",11.3,67.8,452.0,"W",-6,new int[]{-230,-73,-187,-30},new int[]{-234,-77,-183,-26},new int[]{-174,-10,-76,-2},new int[]{-185,44,-52},30,42));
        RANKS.put("Z", new Def("Z",7797200,"FREE","REINFORCED_DEEPSLATE","EMERALD_ORE","SCULK",11.75,70.5,470.0,"W",-18,new int[]{-231,-129,-187,-85},new int[]{-235,-133,-183,-81},new int[]{-174,-22,-76,-14},new int[]{-185,44,-107},30,42));
        RANKS.put("FREE", new Def("FREE",12476000,"FREE","AIR","AIR","AIR",0.0,0.0,0.0,"W",0,null,null,null,null,0,0));
    }
}
