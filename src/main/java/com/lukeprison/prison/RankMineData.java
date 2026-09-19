package com.lukeprison.prison;
import java.util.LinkedHashMap;
import java.util.Map;
public class RankMineData {
    /** A mine's full rectangle (excavated ore room), and the ward rectangle directly
     *  between it and its hub gate. Both are baked in from the validated radial-hub
     *  layout (tools/layout_gen.py) — WorldBuilder never re-derives these numbers,
     *  it only builds exactly what's here. */
    public static class Def {
        public final String rank, next, filler, common, rare, wall;
        public final int cost, x1,y1,z1,x2,y2,z2, wx1,wz1,wx2,wz2;
        public final double fillerPrice, commonPrice, rarePrice;
        public Def(String rank,int cost,String next,String filler,String common,String rare,
                   double fillerPrice,double commonPrice,double rarePrice,String wall,
                   int x1,int y1,int z1,int x2,int y2,int z2,
                   int wx1,int wz1,int wx2,int wz2){
            this.rank=rank;this.cost=cost;this.next=next;this.filler=filler;this.common=common;this.rare=rare;
            this.fillerPrice=fillerPrice;this.commonPrice=commonPrice;this.rarePrice=rarePrice;this.wall=wall;
            this.x1=x1;this.y1=y1;this.z1=z1;this.x2=x2;this.y2=y2;this.z2=z2;
            this.wx1=wx1;this.wz1=wz1;this.wx2=wx2;this.wz2=wz2;
        }
    }
    public static final Map<String, Def> RANKS = new LinkedHashMap<>();
    static {
        RANKS.put("A", new Def("A",0,"B","STONE","COAL_ORE","IRON_ORE",0.5,3.0,20.0,"N",-625,95,-995,-545,125,-915,-625,-915,-545,-899));
        RANKS.put("B", new Def("B",5000,"C","STONE","COAL_ORE","IRON_ORE",0.95,5.7,38.0,"N",-531,95,-1001,-445,125,-915,-531,-915,-445,-899));
        RANKS.put("C", new Def("C",7800,"D","STONE","COAL_ORE","IRON_ORE",1.4,8.4,56.0,"N",-431,95,-1008,-338,125,-915,-431,-915,-338,-899));
        RANKS.put("D", new Def("D",12000,"E","STONE","COAL_ORE","IRON_ORE",1.85,11.1,74.0,"N",-325,95,-1015,-225,125,-915,-325,-915,-225,-899));
        RANKS.put("E", new Def("E",18600,"F","STONE","IRON_ORE","GOLD_ORE",2.3,13.8,92.0,"N",-211,95,-1022,-104,125,-915,-211,-915,-104,-899));
        RANKS.put("F", new Def("F",28900,"G","STONE","IRON_ORE","GOLD_ORE",2.75,16.5,110.0,"N",104,95,-1029,218,125,-915,104,-915,218,-899));
        RANKS.put("G", new Def("G",44700,"H","STONE","IRON_ORE","GOLD_ORE",3.2,19.2,128.0,"N",232,95,-1035,352,125,-915,232,-915,352,-899));
        RANKS.put("H", new Def("H",69300,"I","STONE","IRON_ORE","GOLD_ORE",3.65,21.9,146.0,"N",365,95,-1042,492,125,-915,365,-915,492,-899));
        RANKS.put("I", new Def("I",90100,"J","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",4.1,24.6,164.0,"N",506,95,-1049,640,125,-915,506,-915,640,-899));
        RANKS.put("J", new Def("J",117200,"K","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",4.55,27.3,182.0,"N",654,95,-1056,795,125,-915,654,-915,795,-899));
        RANKS.put("K", new Def("K",152300,"L","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",5.0,30.0,200.0,"E",915,95,-679,1063,125,-531,899,-679,915,-531));
        RANKS.put("L", new Def("L",198000,"M","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",5.45,32.7,218.0,"E",915,95,-472,1069,125,-318,899,-472,915,-318));
        RANKS.put("M", new Def("M",257400,"N","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",5.9,35.4,236.0,"E",915,95,-260,1076,125,-99,899,-260,915,-99));
        RANKS.put("N", new Def("N",334700,"O","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",6.35,38.1,254.0,"E",915,95,99,1083,125,267,899,99,915,267));
        RANKS.put("O", new Def("O",435100,"P","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",6.8,40.8,272.0,"E",915,95,325,1090,125,500,899,325,915,500));
        RANKS.put("P", new Def("P",565600,"Q","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",7.25,43.5,290.0,"E",915,95,559,1097,125,741,899,559,915,741));
        RANKS.put("Q", new Def("Q",735300,"R","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",7.7,46.2,308.0,"S",-561,95,915,-373,125,1103,-561,899,-373,915));
        RANKS.put("R", new Def("R",955900,"S","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",8.15,48.9,326.0,"S",-319,95,915,-124,125,1110,-319,899,-124,915));
        RANKS.put("S", new Def("S",1242600,"T","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",8.6,51.6,344.0,"S",124,95,915,326,125,1117,124,899,326,915));
        RANKS.put("T", new Def("T",1615400,"U","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",9.05,54.3,362.0,"S",380,95,915,589,125,1124,380,899,589,915));
        RANKS.put("U", new Def("U",2100000,"V","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",9.5,57.0,380.0,"S",643,95,915,859,125,1131,643,899,859,915));
        RANKS.put("V", new Def("V",2730000,"W","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",9.95,59.7,398.0,"W",-1137,95,-565,-915,125,-343,-915,-565,-899,-343));
        RANKS.put("W", new Def("W",3549000,"X","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",10.4,62.4,416.0,"W",-1144,95,-331,-915,125,-102,-915,-331,-899,-102));
        RANKS.put("X", new Def("X",4613700,"Y","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",10.85,65.1,434.0,"W",-1151,95,102,-915,125,338,-915,102,-899,338));
        RANKS.put("Y", new Def("Y",5997900,"Z","REINFORCED_DEEPSLATE","EMERALD_ORE","SCULK",11.3,67.8,452.0,"W",-1158,95,350,-915,125,593,-915,350,-899,593));
        RANKS.put("Z", new Def("Z",7797200,"FREE","REINFORCED_DEEPSLATE","EMERALD_ORE","SCULK",11.75,70.5,470.0,"W",-1165,95,605,-915,125,855,-915,605,-899,855));
        RANKS.put("FREE", new Def("FREE",12476000,"FREE","AIR","AIR","AIR",0,0,0,"N",0,0,0,0,0,0,0,0,0,0));
    }
}
