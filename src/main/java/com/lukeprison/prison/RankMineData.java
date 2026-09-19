package com.lukeprison.prison;
import java.util.LinkedHashMap;
import java.util.Map;
public class RankMineData {
    public static class Def {
        public final String rank, next, filler, common, rare;
        public final int cost, x1,y1,z1,x2,y2,z2;
        public final double fillerPrice, commonPrice, rarePrice;
        public Def(String rank,int cost,String next,String filler,String common,String rare,
                   double fillerPrice,double commonPrice,double rarePrice,
                   int x1,int y1,int z1,int x2,int y2,int z2){
            this.rank=rank;this.cost=cost;this.next=next;this.filler=filler;this.common=common;this.rare=rare;
            this.fillerPrice=fillerPrice;this.commonPrice=commonPrice;this.rarePrice=rarePrice;
            this.x1=x1;this.y1=y1;this.z1=z1;this.x2=x2;this.y2=y2;this.z2=z2;
        }
    }
    /** rank -> LEFT/RIGHT lane off the hub. A,C,E... = left; B,D,F... = right, paired by distance from hub. */
    public static final Map<String, String> LANES = new LinkedHashMap<>();
    public static final Map<String, Def> RANKS = new LinkedHashMap<>();
    static {
        LANES.put("A", "LEFT");
        LANES.put("B", "RIGHT");
        LANES.put("C", "LEFT");
        LANES.put("D", "RIGHT");
        LANES.put("E", "LEFT");
        LANES.put("F", "RIGHT");
        LANES.put("G", "LEFT");
        LANES.put("H", "RIGHT");
        LANES.put("I", "LEFT");
        LANES.put("J", "RIGHT");
        LANES.put("K", "LEFT");
        LANES.put("L", "RIGHT");
        LANES.put("M", "LEFT");
        LANES.put("N", "RIGHT");
        LANES.put("O", "LEFT");
        LANES.put("P", "RIGHT");
        LANES.put("Q", "LEFT");
        LANES.put("R", "RIGHT");
        LANES.put("S", "LEFT");
        LANES.put("T", "RIGHT");
        LANES.put("U", "LEFT");
        LANES.put("V", "RIGHT");
        LANES.put("W", "LEFT");
        LANES.put("X", "RIGHT");
        LANES.put("Y", "LEFT");
        LANES.put("Z", "RIGHT");
        LANES.put("FREE", "LEFT");
        RANKS.put("A", new Def("A",0,"B","STONE","COAL_ORE","IRON_ORE",0.5,3.0,20.0,40,95,40,120,125,120));
        RANKS.put("B", new Def("B",5000,"C","STONE","COAL_ORE","IRON_ORE",0.95,5.7,38.0,40,95,-123,123,125,-40));
        RANKS.put("C", new Def("C",7800,"D","STONE","COAL_ORE","IRON_ORE",1.4,8.4,56.0,150,95,40,236,125,126));
        RANKS.put("D", new Def("D",12000,"E","STONE","COAL_ORE","IRON_ORE",1.85,11.1,74.0,153,95,-131,244,125,-40));
        RANKS.put("E", new Def("E",18600,"F","STONE","IRON_ORE","GOLD_ORE",2.3,13.8,92.0,266,95,40,362,125,136));
        RANKS.put("F", new Def("F",28900,"G","STONE","IRON_ORE","GOLD_ORE",2.75,16.5,110.0,274,95,-141,375,125,-40));
        RANKS.put("G", new Def("G",44700,"H","STONE","IRON_ORE","GOLD_ORE",3.2,19.2,128.0,392,95,40,499,125,147));
        RANKS.put("H", new Def("H",69300,"I","STONE","IRON_ORE","GOLD_ORE",3.65,21.9,146.0,405,95,-152,517,125,-40));
        RANKS.put("I", new Def("I",90100,"J","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",4.1,24.6,164.0,529,95,40,648,125,159));
        RANKS.put("J", new Def("J",117200,"K","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",4.55,27.3,182.0,547,95,-165,672,125,-40));
        RANKS.put("K", new Def("K",152300,"L","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",5.0,30.0,200.0,678,95,40,810,125,172));
        RANKS.put("L", new Def("L",198000,"M","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",5.45,32.7,218.0,702,95,-178,840,125,-40));
        RANKS.put("M", new Def("M",257400,"N","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",5.9,35.4,236.0,840,95,40,985,125,185));
        RANKS.put("N", new Def("N",334700,"O","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",6.35,38.1,254.0,870,95,-193,1023,125,-40));
        RANKS.put("O", new Def("O",435100,"P","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",6.8,40.8,272.0,1015,95,40,1175,125,200));
        RANKS.put("P", new Def("P",565600,"Q","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",7.25,43.5,290.0,1053,95,-208,1221,125,-40));
        RANKS.put("Q", new Def("Q",735300,"R","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",7.7,46.2,308.0,1205,95,40,1380,125,215));
        RANKS.put("R", new Def("R",955900,"S","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",8.15,48.9,326.0,1251,95,-223,1434,125,-40));
        RANKS.put("S", new Def("S",1242600,"T","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",8.6,51.6,344.0,1410,95,40,1601,125,231));
        RANKS.put("T", new Def("T",1615400,"U","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",9.05,54.3,362.0,1464,95,-239,1663,125,-40));
        RANKS.put("U", new Def("U",2100000,"V","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",9.5,57.0,380.0,1631,95,40,1838,125,247));
        RANKS.put("V", new Def("V",2730000,"W","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",9.95,59.7,398.0,1693,95,-256,1909,125,-40));
        RANKS.put("W", new Def("W",3549000,"X","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",10.4,62.4,416.0,1868,95,40,2092,125,264));
        RANKS.put("X", new Def("X",4613700,"Y","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",10.85,65.1,434.0,1939,95,-273,2172,125,-40));
        RANKS.put("Y", new Def("Y",5997900,"Z","REINFORCED_DEEPSLATE","EMERALD_ORE","SCULK",11.3,67.8,452.0,2122,95,40,2363,125,281));
        RANKS.put("Z", new Def("Z",7797200,"FREE","REINFORCED_DEEPSLATE","EMERALD_ORE","SCULK",11.75,70.5,470.0,2202,95,-290,2452,125,-40));
        RANKS.put("FREE", new Def("FREE",12476000,"FREE","AIR","AIR","AIR",0,0,0,0,0,0,0,0,0));
    }
}
