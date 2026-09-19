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
        /** {x,y,z} the cage lift sets players down on. */
        public final int[] landing;

        public Def(String rank, int cost, String next, String filler, String common, String rare,
                   double fillerPrice, double commonPrice, double rarePrice,
                   String wall, int gateCentre,
                   int[] pit, int[] rim, int[] landing, int oreBottom, int oreTop) {
            this.rank = rank; this.cost = cost; this.next = next;
            this.filler = filler; this.common = common; this.rare = rare;
            this.fillerPrice = fillerPrice; this.commonPrice = commonPrice; this.rarePrice = rarePrice;
            this.wall = wall; this.gateCentre = gateCentre;
            this.pit = pit; this.rim = rim; this.landing = landing;
            this.oreBottom = oreBottom; this.oreTop = oreTop;
        }

        /** FREE has no mine of its own — it unlocks the open world instead. */
        public boolean hasMine() { return pit != null; }

        /** The ward this rank's gate opens into, on the hub wall. */
        public MapLayout.Gate gate() { return MapLayout.gate(rank); }
    }

    public static final Map<String, Def> RANKS = new LinkedHashMap<>();

    static {
        RANKS.put("A", new Def("A",0,"B","STONE","COAL_ORE","IRON_ORE",0.5,3.0,20.0,"N",-42,new int[]{-202,-164,-178,-140},new int[]{-206,-168,-174,-136},new int[]{-190,70,-166},40,68));
        RANKS.put("B", new Def("B",5000,"C","STONE","COAL_ORE","IRON_ORE",0.95,5.7,38.0,"N",-30,new int[]{-126,-164,-102,-140},new int[]{-130,-168,-98,-136},new int[]{-114,70,-166},40,68));
        RANKS.put("C", new Def("C",7800,"D","STONE","COAL_ORE","IRON_ORE",1.4,8.4,56.0,"N",-18,new int[]{-51,-165,-25,-139},new int[]{-55,-169,-21,-135},new int[]{-38,70,-167},40,68));
        RANKS.put("D", new Def("D",12000,"E","STONE","COAL_ORE","IRON_ORE",1.85,11.1,74.0,"N",-6,new int[]{24,-166,52,-138},new int[]{20,-170,56,-134},new int[]{38,70,-168},40,68));
        RANKS.put("E", new Def("E",18600,"F","STONE","IRON_ORE","GOLD_ORE",2.3,13.8,92.0,"N",6,new int[]{100,-166,128,-138},new int[]{96,-170,132,-134},new int[]{114,70,-168},40,68));
        RANKS.put("F", new Def("F",28900,"G","STONE","IRON_ORE","GOLD_ORE",2.75,16.5,110.0,"N",18,new int[]{174,-168,206,-136},new int[]{170,-172,210,-132},new int[]{190,70,-170},40,68));
        RANKS.put("G", new Def("G",44700,"H","STONE","IRON_ORE","GOLD_ORE",3.2,19.2,128.0,"N",30,new int[]{-206,-92,-174,-60},new int[]{-210,-96,-170,-56},new int[]{-190,70,-94},40,68));
        RANKS.put("H", new Def("H",69300,"I","STONE","IRON_ORE","GOLD_ORE",3.65,21.9,146.0,"E",-42,new int[]{-131,-93,-97,-59},new int[]{-135,-97,-93,-55},new int[]{-114,70,-95},40,68));
        RANKS.put("I", new Def("I",90100,"J","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",4.1,24.6,164.0,"E",-30,new int[]{-56,-94,-20,-58},new int[]{-60,-98,-16,-54},new int[]{-38,70,-96},40,68));
        RANKS.put("J", new Def("J",117200,"K","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",4.55,27.3,182.0,"E",-18,new int[]{20,-94,56,-58},new int[]{16,-98,60,-54},new int[]{38,70,-96},40,68));
        RANKS.put("K", new Def("K",152300,"L","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",5.0,30.0,200.0,"E",-6,new int[]{95,-95,133,-57},new int[]{91,-99,137,-53},new int[]{114,70,-97},40,68));
        RANKS.put("L", new Def("L",198000,"M","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",5.45,32.7,218.0,"E",6,new int[]{170,-96,210,-56},new int[]{166,-100,214,-52},new int[]{190,70,-98},40,68));
        RANKS.put("M", new Def("M",257400,"N","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",5.9,35.4,236.0,"E",18,new int[]{-210,-20,-170,20},new int[]{-214,-24,-166,24},new int[]{-190,70,-22},40,68));
        RANKS.put("N", new Def("N",334700,"O","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",6.35,38.1,254.0,"E",30,new int[]{-135,-21,-93,21},new int[]{-139,-25,-89,25},new int[]{-114,70,-23},40,68));
        RANKS.put("O", new Def("O",435100,"P","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",6.8,40.8,272.0,"S",-42,new int[]{-60,-22,-16,22},new int[]{-64,-26,-12,26},new int[]{-38,70,-24},40,68));
        RANKS.put("P", new Def("P",565600,"Q","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",7.25,43.5,290.0,"S",-30,new int[]{16,-22,60,22},new int[]{12,-26,64,26},new int[]{38,70,-24},40,68));
        RANKS.put("Q", new Def("Q",735300,"R","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",7.7,46.2,308.0,"S",-18,new int[]{90,-24,138,24},new int[]{86,-28,142,28},new int[]{114,70,-26},40,68));
        RANKS.put("R", new Def("R",955900,"S","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",8.15,48.9,326.0,"S",-6,new int[]{166,-24,214,24},new int[]{162,-28,218,28},new int[]{190,70,-26},40,68));
        RANKS.put("S", new Def("S",1242600,"T","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",8.6,51.6,344.0,"S",6,new int[]{-214,52,-166,100},new int[]{-218,48,-162,104},new int[]{-190,70,50},40,68));
        RANKS.put("T", new Def("T",1615400,"U","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",9.05,54.3,362.0,"S",18,new int[]{-140,50,-88,102},new int[]{-144,46,-84,106},new int[]{-114,70,48},40,68));
        RANKS.put("U", new Def("U",2100000,"V","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",9.5,57.0,380.0,"S",30,new int[]{-64,50,-12,102},new int[]{-68,46,-8,106},new int[]{-38,70,48},40,68));
        RANKS.put("V", new Def("V",2730000,"W","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",9.95,59.7,398.0,"W",-30,new int[]{11,49,65,103},new int[]{7,45,69,107},new int[]{38,70,47},40,68));
        RANKS.put("W", new Def("W",3549000,"X","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",10.4,62.4,416.0,"W",-18,new int[]{86,48,142,104},new int[]{82,44,146,108},new int[]{114,70,46},40,68));
        RANKS.put("X", new Def("X",4613700,"Y","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",10.85,65.1,434.0,"W",-6,new int[]{162,48,218,104},new int[]{158,44,222,108},new int[]{190,70,46},40,68));
        RANKS.put("Y", new Def("Y",5997900,"Z","REINFORCED_DEEPSLATE","EMERALD_ORE","SCULK",11.3,67.8,452.0,"W",6,new int[]{-219,123,-161,181},new int[]{-223,119,-157,185},new int[]{-190,70,121},40,68));
        RANKS.put("Z", new Def("Z",7797200,"FREE","REINFORCED_DEEPSLATE","EMERALD_ORE","SCULK",11.75,70.5,470.0,"W",18,new int[]{-144,122,-84,182},new int[]{-148,118,-80,186},new int[]{-114,70,120},40,68));
        RANKS.put("FREE", new Def("FREE",12476000,"FREE","AIR","AIR","AIR",0.0,0.0,0.0,"W",0,null,null,null,0,0));
    }
}
