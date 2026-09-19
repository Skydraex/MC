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
        RANKS.put("A", new Def("A",0,"B","STONE","COAL_ORE","IRON_ORE",0.5,3.0,20.0,"N",-148,95,-269,-124,125,-245,-148,-245,-124,-237));
        RANKS.put("B", new Def("B",5000,"C","STONE","COAL_ORE","IRON_ORE",0.95,5.7,38.0,"N",-119,95,-270,-94,125,-245,-119,-245,-94,-237));
        RANKS.put("C", new Def("C",7800,"D","STONE","COAL_ORE","IRON_ORE",1.4,8.4,56.0,"N",-89,95,-271,-63,125,-245,-89,-245,-63,-237));
        RANKS.put("D", new Def("D",12000,"E","STONE","COAL_ORE","IRON_ORE",1.85,11.1,74.0,"N",-58,95,-273,-30,125,-245,-58,-245,-30,-237));
        RANKS.put("E", new Def("E",18600,"F","STONE","IRON_ORE","GOLD_ORE",2.3,13.8,92.0,"N",30,95,-274,59,125,-245,30,-245,59,-237));
        RANKS.put("F", new Def("F",28900,"G","STONE","IRON_ORE","GOLD_ORE",2.75,16.5,110.0,"N",64,95,-276,95,125,-245,64,-245,95,-237));
        RANKS.put("G", new Def("G",44700,"H","STONE","IRON_ORE","GOLD_ORE",3.2,19.2,128.0,"N",100,95,-277,132,125,-245,100,-245,132,-237));
        RANKS.put("H", new Def("H",69300,"I","STONE","IRON_ORE","GOLD_ORE",3.65,21.9,146.0,"N",137,95,-279,171,125,-245,137,-245,171,-237));
        RANKS.put("I", new Def("I",90100,"J","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",4.1,24.6,164.0,"N",175,95,-280,210,125,-245,175,-245,210,-237));
        RANKS.put("J", new Def("J",117200,"K","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",4.55,27.3,182.0,"E",245,95,-146,281,125,-110,237,-146,245,-110));
        RANKS.put("K", new Def("K",152300,"L","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",5.0,30.0,200.0,"E",245,95,-103,283,125,-65,237,-103,245,-65));
        RANKS.put("L", new Def("L",198000,"M","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",5.45,32.7,218.0,"E",245,95,-58,284,125,-19,237,-58,245,-19));
        RANKS.put("M", new Def("M",257400,"N","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",5.9,35.4,236.0,"E",245,95,19,286,125,60,237,19,245,60));
        RANKS.put("N", new Def("N",334700,"O","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",6.35,38.1,254.0,"E",245,95,67,287,125,109,237,67,245,109));
        RANKS.put("O", new Def("O",435100,"P","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",6.8,40.8,272.0,"E",245,95,116,289,125,160,237,116,245,160));
        RANKS.put("P", new Def("P",565600,"Q","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",7.25,43.5,290.0,"E",245,95,167,290,125,212,237,167,245,212));
        RANKS.put("Q", new Def("Q",735300,"R","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",7.7,46.2,308.0,"S",-143,95,245,-96,125,292,-143,237,-96,245));
        RANKS.put("R", new Def("R",955900,"S","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",8.15,48.9,326.0,"S",-82,95,245,-34,125,293,-82,237,-34,245));
        RANKS.put("S", new Def("S",1242600,"T","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",8.6,51.6,344.0,"S",34,95,245,83,125,294,34,237,83,245));
        RANKS.put("T", new Def("T",1615400,"U","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",9.05,54.3,362.0,"S",97,95,245,148,125,296,97,237,148,245));
        RANKS.put("U", new Def("U",2100000,"V","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",9.5,57.0,380.0,"S",163,95,245,215,125,297,163,237,215,245));
        RANKS.put("V", new Def("V",2730000,"W","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",9.95,59.7,398.0,"W",-299,95,-144,-245,125,-90,-245,-144,-237,-90));
        RANKS.put("W", new Def("W",3549000,"X","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",10.4,62.4,416.0,"W",-300,95,-85,-245,125,-30,-245,-85,-237,-30));
        RANKS.put("X", new Def("X",4613700,"Y","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",10.85,65.1,434.0,"W",-302,95,30,-245,125,87,-245,30,-237,87));
        RANKS.put("Y", new Def("Y",5997900,"Z","REINFORCED_DEEPSLATE","EMERALD_ORE","SCULK",11.3,67.8,452.0,"W",-303,95,92,-245,125,150,-245,92,-237,150));
        RANKS.put("Z", new Def("Z",7797200,"FREE","REINFORCED_DEEPSLATE","EMERALD_ORE","SCULK",11.75,70.5,470.0,"W",-305,95,154,-245,125,214,-245,154,-237,214));
        RANKS.put("FREE", new Def("FREE",12476000,"FREE","AIR","AIR","AIR",0,0,0,"N",0,0,0,0,0,0,0,0,0,0));
    }
}
