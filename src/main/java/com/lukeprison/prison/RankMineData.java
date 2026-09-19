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
        /** {x1,z1,x2,z2} of the landscaped chamber around the rim. */
        public final int[] plot;
        /** {x1,z1,x2,z2} of the walk-down shaft from this rank's ward to the ring. */
        public final int[] shaft;

        public Def(String rank, int cost, String next, String filler, String common, String rare,
                   double fillerPrice, double commonPrice, double rarePrice,
                   String wall, int gateCentre,
                   int[] pit, int[] rim, int[] plot, int[] shaft, int[] landing,
                   int oreBottom, int oreTop) {
            this.rank = rank; this.cost = cost; this.next = next;
            this.filler = filler; this.common = common; this.rare = rare;
            this.fillerPrice = fillerPrice; this.commonPrice = commonPrice; this.rarePrice = rarePrice;
            this.wall = wall; this.gateCentre = gateCentre;
            this.pit = pit; this.rim = rim; this.plot = plot;
            this.shaft = shaft; this.landing = landing;
            this.oreBottom = oreBottom; this.oreTop = oreTop;
        }

        /** FREE has no mine of its own — it unlocks the open world instead. */
        public boolean hasMine() { return pit != null; }

        /** The ward this rank's gate opens into, on the hub wall. */
        public MapLayout.Gate gate() { return MapLayout.gate(rank); }
    }

    public static final Map<String, Def> RANKS = new LinkedHashMap<>();

    static {
        RANKS.put("A", new Def("A",0,"B","STONE","COAL_ORE","IRON_ORE",0.5,3.0,20.0,"N",-42,new int[]{-175,-273,-151,-249},new int[]{-179,-277,-147,-245},new int[]{-187,-285,-139,-245},new int[]{-46,-236,-38,-76},new int[]{-163,44,-247},30,42));
        RANKS.put("B", new Def("B",5000,"C","STONE","COAL_ORE","IRON_ORE",0.95,5.7,38.0,"N",-30,new int[]{-123,-273,-99,-249},new int[]{-127,-277,-95,-245},new int[]{-135,-285,-87,-245},new int[]{-34,-236,-26,-76},new int[]{-111,44,-247},30,42));
        RANKS.put("C", new Def("C",7800,"D","STONE","COAL_ORE","IRON_ORE",1.4,8.4,56.0,"N",-18,new int[]{-71,-274,-46,-249},new int[]{-75,-278,-42,-245},new int[]{-83,-286,-34,-245},new int[]{-22,-236,-14,-76},new int[]{-58,44,-247},30,42));
        RANKS.put("D", new Def("D",12000,"E","STONE","COAL_ORE","IRON_ORE",1.85,11.1,74.0,"N",-6,new int[]{-18,-275,8,-249},new int[]{-22,-279,12,-245},new int[]{-30,-287,20,-245},new int[]{-10,-236,-2,-76},new int[]{-5,44,-247},30,42));
        RANKS.put("E", new Def("E",18600,"F","STONE","IRON_ORE","GOLD_ORE",2.3,13.8,92.0,"N",6,new int[]{36,-276,63,-249},new int[]{32,-280,67,-245},new int[]{24,-288,75,-245},new int[]{2,-236,10,-76},new int[]{50,44,-247},30,42));
        RANKS.put("F", new Def("F",28900,"G","STONE","IRON_ORE","GOLD_ORE",2.75,16.5,110.0,"N",18,new int[]{91,-277,119,-249},new int[]{87,-281,123,-245},new int[]{79,-289,131,-245},new int[]{14,-236,22,-76},new int[]{105,44,-247},30,42));
        RANKS.put("G", new Def("G",44700,"H","STONE","IRON_ORE","GOLD_ORE",3.2,19.2,128.0,"N",30,new int[]{147,-277,175,-249},new int[]{143,-281,179,-245},new int[]{135,-289,187,-245},new int[]{26,-236,34,-76},new int[]{161,44,-247},30,42));
        RANKS.put("H", new Def("H",69300,"I","STONE","IRON_ORE","GOLD_ORE",3.65,21.9,146.0,"E",-42,new int[]{249,-194,278,-166},new int[]{245,-198,282,-162},new int[]{245,-206,290,-154},new int[]{76,-46,236,-38},new int[]{247,44,-180},30,42));
        RANKS.put("I", new Def("I",90100,"J","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",4.1,24.6,164.0,"E",-30,new int[]{249,-138,279,-108},new int[]{245,-142,283,-104},new int[]{245,-150,291,-96},new int[]{76,-34,236,-26},new int[]{247,44,-122},30,42));
        RANKS.put("J", new Def("J",117200,"K","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",4.55,27.3,182.0,"E",-18,new int[]{249,-80,280,-48},new int[]{245,-84,284,-44},new int[]{245,-92,292,-36},new int[]{76,-22,236,-14},new int[]{247,44,-64},30,42));
        RANKS.put("K", new Def("K",152300,"L","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",5.0,30.0,200.0,"E",-6,new int[]{249,-20,281,12},new int[]{245,-24,285,16},new int[]{245,-32,293,24},new int[]{76,-10,236,-2},new int[]{247,44,-4},30,42));
        RANKS.put("L", new Def("L",198000,"M","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",5.45,32.7,218.0,"E",6,new int[]{249,40,281,72},new int[]{245,36,285,76},new int[]{245,28,293,84},new int[]{76,2,236,10},new int[]{247,44,56},30,42));
        RANKS.put("M", new Def("M",257400,"N","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",5.9,35.4,236.0,"E",18,new int[]{249,100,282,132},new int[]{245,96,286,136},new int[]{245,88,294,144},new int[]{76,14,236,22},new int[]{247,44,116},30,42));
        RANKS.put("N", new Def("N",334700,"O","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",6.35,38.1,254.0,"E",30,new int[]{249,160,283,194},new int[]{245,156,287,198},new int[]{245,148,295,206},new int[]{76,26,236,34},new int[]{247,44,178},30,42));
        RANKS.put("O", new Def("O",435100,"P","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",6.8,40.8,272.0,"S",42,new int[]{180,249,214,284},new int[]{176,245,218,288},new int[]{168,245,226,296},new int[]{38,76,46,236},new int[]{197,44,247},30,42));
        RANKS.put("P", new Def("P",565600,"Q","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",7.25,43.5,290.0,"S",30,new int[]{116,249,152,285},new int[]{112,245,156,289},new int[]{104,245,164,297},new int[]{26,76,34,236},new int[]{134,44,247},30,42));
        RANKS.put("Q", new Def("Q",735300,"R","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",7.7,46.2,308.0,"S",18,new int[]{52,249,88,285},new int[]{48,245,92,289},new int[]{40,245,100,297},new int[]{14,76,22,236},new int[]{70,44,247},30,42));
        RANKS.put("R", new Def("R",955900,"S","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",8.15,48.9,326.0,"S",6,new int[]{-14,249,24,286},new int[]{-18,245,28,290},new int[]{-26,245,36,298},new int[]{2,76,10,236},new int[]{5,44,247},30,42));
        RANKS.put("S", new Def("S",1242600,"T","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",8.6,51.6,344.0,"S",-6,new int[]{-80,249,-42,287},new int[]{-84,245,-38,291},new int[]{-92,245,-30,299},new int[]{-10,76,-2,236},new int[]{-60,44,247},30,42));
        RANKS.put("T", new Def("T",1615400,"U","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",9.05,54.3,362.0,"S",-18,new int[]{-146,249,-108,288},new int[]{-150,245,-104,292},new int[]{-158,245,-96,300},new int[]{-22,76,-14,236},new int[]{-127,44,247},30,42));
        RANKS.put("U", new Def("U",2100000,"V","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",9.5,57.0,380.0,"S",-30,new int[]{-214,249,-174,289},new int[]{-218,245,-170,293},new int[]{-226,245,-162,301},new int[]{-34,76,-26,236},new int[]{-194,44,247},30,42));
        RANKS.put("V", new Def("V",2730000,"W","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",9.95,59.7,398.0,"W",30,new int[]{-289,121,-249,161},new int[]{-293,117,-245,165},new int[]{-301,109,-245,173},new int[]{-236,26,-76,34},new int[]{-247,44,141},30,42));
        RANKS.put("W", new Def("W",3549000,"X","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",10.4,62.4,416.0,"W",18,new int[]{-290,52,-249,93},new int[]{-294,48,-245,97},new int[]{-302,40,-245,105},new int[]{-236,14,-76,22},new int[]{-247,44,72},30,42));
        RANKS.put("X", new Def("X",4613700,"Y","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",10.85,65.1,434.0,"W",6,new int[]{-291,-18,-249,24},new int[]{-295,-22,-245,28},new int[]{-303,-30,-245,36},new int[]{-236,2,-76,10},new int[]{-247,44,3},30,42));
        RANKS.put("Y", new Def("Y",5997900,"Z","REINFORCED_DEEPSLATE","EMERALD_ORE","SCULK",11.3,67.8,452.0,"W",-6,new int[]{-292,-89,-249,-46},new int[]{-296,-93,-245,-42},new int[]{-304,-101,-245,-34},new int[]{-236,-10,-76,-2},new int[]{-247,44,-68},30,42));
        RANKS.put("Z", new Def("Z",7797200,"FREE","REINFORCED_DEEPSLATE","EMERALD_ORE","SCULK",11.75,70.5,470.0,"W",-18,new int[]{-293,-161,-249,-117},new int[]{-297,-165,-245,-113},new int[]{-305,-173,-245,-105},new int[]{-236,-22,-76,-14},new int[]{-247,44,-139},30,42));
        RANKS.put("FREE", new Def("FREE",12476000,"FREE","AIR","AIR","AIR",0.0,0.0,0.0,"W",0,null,null,null,null,null,0,0));
    }
}
