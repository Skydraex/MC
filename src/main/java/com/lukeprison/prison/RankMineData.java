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
    public static final Map<String, Def> RANKS = new LinkedHashMap<>();
    static {
        RANKS.put("A", new Def("A",0,"B","STONE","COAL_ORE","IRON_ORE",0.5,3.0,20.0,0,95,0,80,125,80));
        RANKS.put("B", new Def("B",5000,"C","STONE","COAL_ORE","IRON_ORE",0.95,5.7,38.0,95,95,0,178,125,83));
        RANKS.put("C", new Def("C",7800,"D","STONE","COAL_ORE","IRON_ORE",1.4,8.4,56.0,193,95,0,279,125,86));
        RANKS.put("D", new Def("D",12000,"E","STONE","COAL_ORE","IRON_ORE",1.85,11.1,74.0,294,95,0,385,125,91));
        RANKS.put("E", new Def("E",18600,"F","STONE","IRON_ORE","GOLD_ORE",2.3,13.8,92.0,400,95,0,496,125,96));
        RANKS.put("F", new Def("F",28900,"G","STONE","IRON_ORE","GOLD_ORE",2.75,16.5,110.0,511,95,0,612,125,101));
        RANKS.put("G", new Def("G",44700,"H","STONE","IRON_ORE","GOLD_ORE",3.2,19.2,128.0,627,95,0,734,125,107));
        RANKS.put("H", new Def("H",69300,"I","STONE","IRON_ORE","GOLD_ORE",3.65,21.9,146.0,749,95,0,861,125,112));
        RANKS.put("I", new Def("I",90100,"J","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",4.1,24.6,164.0,876,95,0,995,125,119));
        RANKS.put("J", new Def("J",117200,"K","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",4.55,27.3,182.0,1010,95,0,1135,125,125));
        RANKS.put("K", new Def("K",152300,"L","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",5.0,30.0,200.0,1150,95,0,1282,125,132));
        RANKS.put("L", new Def("L",198000,"M","DEEPSLATE","GOLD_ORE","REDSTONE_ORE",5.45,32.7,218.0,1297,95,0,1435,125,138));
        RANKS.put("M", new Def("M",257400,"N","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",5.9,35.4,236.0,1450,95,0,1595,125,145));
        RANKS.put("N", new Def("N",334700,"O","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",6.35,38.1,254.0,1610,95,0,1763,125,153));
        RANKS.put("O", new Def("O",435100,"P","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",6.8,40.8,272.0,1778,95,0,1938,125,160));
        RANKS.put("P", new Def("P",565600,"Q","DEEPSLATE","REDSTONE_ORE","LAPIS_ORE",7.25,43.5,290.0,1953,95,0,2121,125,168));
        RANKS.put("Q", new Def("Q",735300,"R","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",7.7,46.2,308.0,2136,95,0,2311,125,175));
        RANKS.put("R", new Def("R",955900,"S","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",8.15,48.9,326.0,2326,95,0,2509,125,183));
        RANKS.put("S", new Def("S",1242600,"T","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",8.6,51.6,344.0,2524,95,0,2715,125,191));
        RANKS.put("T", new Def("T",1615400,"U","DEEPSLATE","LAPIS_ORE","DIAMOND_ORE",9.05,54.3,362.0,2730,95,0,2929,125,199));
        RANKS.put("U", new Def("U",2100000,"V","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",9.5,57.0,380.0,2944,95,0,3151,125,207));
        RANKS.put("V", new Def("V",2730000,"W","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",9.95,59.7,398.0,3166,95,0,3382,125,216));
        RANKS.put("W", new Def("W",3549000,"X","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",10.4,62.4,416.0,3397,95,0,3621,125,224));
        RANKS.put("X", new Def("X",4613700,"Y","DEEPSLATE","DIAMOND_ORE","EMERALD_ORE",10.85,65.1,434.0,3636,95,0,3869,125,233));
        RANKS.put("Y", new Def("Y",5997900,"Z","REINFORCED_DEEPSLATE","EMERALD_ORE","SCULK",11.3,67.8,452.0,3884,95,0,4125,125,241));
        RANKS.put("Z", new Def("Z",7797200,"FREE","REINFORCED_DEEPSLATE","EMERALD_ORE","SCULK",11.75,70.5,470.0,4140,95,0,4390,125,250));
        RANKS.put("FREE", new Def("FREE",12476000,"FREE","AIR","AIR","AIR",0,0,0,0,0,0,0,0,0));
    }
}
