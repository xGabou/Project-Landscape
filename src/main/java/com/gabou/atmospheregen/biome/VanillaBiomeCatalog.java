/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.biome;

import com.gabou.atmospheregen.persistence.*;
import com.google.gson.JsonPrimitive;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import java.util.*;

/** Built-in vanilla Overworld descriptors; future datapack descriptors use the same abstraction. */
public final class VanillaBiomeCatalog implements BiomeCatalog {
    private final List<BiomeDescriptor> descriptors; private final GenerationFingerprint fingerprint;
    public VanillaBiomeCatalog(){List<BiomeDescriptor> d=new ArrayList<>();
        add(d,BiomeIds.DESERT,20,40,0,260,0,0.35,0,1800,BiomeTrait.DRYLAND);add(d,BiomeIds.BADLANDS,18,40,0,420,0,0.45,0,2200,BiomeTrait.DRYLAND);add(d,BiomeIds.WOODED_BADLANDS,16,34,180,700,0,0.7,0,2200,BiomeTrait.DRYLAND,BiomeTrait.FOREST);
        add(d,BiomeIds.SAVANNA,18,50,180,1200,.2,1.1,0,1800,BiomeTrait.GRASSLAND);add(d,BiomeIds.PLAINS,-5,32,0,1400,0,1.8,0,1600,BiomeTrait.GRASSLAND);add(d,BiomeIds.FOREST,5,28,650,2200,.6,2.4,0,1700,BiomeTrait.FOREST);add(d,BiomeIds.BIRCH_FOREST,2,22,700,2200,.65,2.4,0,1700,BiomeTrait.FOREST);add(d,BiomeIds.DARK_FOREST,4,25,1000,2600,.8,2.8,0,1700,BiomeTrait.FOREST);
        add(d,BiomeIds.JUNGLE,20,38,1200,3500,1,4,0,1400,BiomeTrait.TROPICAL_FOREST);add(d,BiomeIds.SPARSE_JUNGLE,18,35,800,2400,.8,3,0,1500,BiomeTrait.TROPICAL_FOREST);add(d,BiomeIds.BAMBOO_JUNGLE,20,38,1500,4000,1.2,4,0,1300,BiomeTrait.TROPICAL_FOREST);
        add(d,BiomeIds.TAIGA,-10,18,500,2400,.55,6,0,1500,BiomeTrait.BOREAL_FOREST);add(d,BiomeIds.OLD_GROWTH_SPRUCE_TAIGA,-8,15,600,1900,.6,2.5,0,1600,BiomeTrait.BOREAL_FOREST);add(d,BiomeIds.SNOWY_TAIGA,-20,8,350,3000,.45,8,0,1500,BiomeTrait.BOREAL_FOREST,BiomeTrait.SNOW);
        add(d,BiomeIds.SNOWY_PLAINS,-60,3,0,5000,0,20,0,1500,BiomeTrait.SNOW);add(d,BiomeIds.ICE_SPIKES,-30,-5,100,900,.2,2,0,1800,BiomeTrait.SNOW);add(d,BiomeIds.MEADOW,-2,20,500,2200,.5,2.5,350,1800,BiomeTrait.GRASSLAND);add(d,BiomeIds.GROVE,-12,8,500,2200,.55,2.6,700,2600,BiomeTrait.BOREAL_FOREST,BiomeTrait.SNOW);
        add(d,BiomeIds.SWAMP,8,28,1100,3200,.9,3.2,-20,500,BiomeTrait.WETLAND);add(d,BiomeIds.MANGROVE_SWAMP,20,35,1500,4000,1.1,4,-20,300,BiomeTrait.WETLAND,BiomeTrait.COASTAL);
        add(d,BiomeIds.STONY_PEAKS,-20,18,300,2200,.3,2.5,900,4000,BiomeTrait.ALPINE);add(d,BiomeIds.JAGGED_PEAKS,-30,5,200,1800,.25,2.3,1400,5000,BiomeTrait.ALPINE,BiomeTrait.SNOW);add(d,BiomeIds.FROZEN_PEAKS,-40,0,100,1600,.2,2,1400,5000,BiomeTrait.ALPINE,BiomeTrait.SNOW);add(d,BiomeIds.SNOWY_SLOPES,-25,8,250,1900,.25,2.4,800,3500,BiomeTrait.ALPINE,BiomeTrait.SNOW);
        add(d,BiomeIds.RIVER,-40,40,0,4000,0,5,-100,4000,BiomeTrait.RIVER);add(d,BiomeIds.FROZEN_RIVER,-40,0,0,2500,0,4,-100,4000,BiomeTrait.RIVER,BiomeTrait.SNOW);add(d,BiomeIds.BEACH,-10,35,0,4000,0,5,-20,200,BiomeTrait.COASTAL);add(d,BiomeIds.SNOWY_BEACH,-30,3,0,2500,0,4,-20,200,BiomeTrait.COASTAL,BiomeTrait.SNOW);add(d,BiomeIds.STONY_SHORE,-20,35,0,4000,0,5,-20,300,BiomeTrait.COASTAL);
        add(d,BiomeIds.OCEAN,-40,40,0,4000,0,5,-10000,0,BiomeTrait.MARINE);add(d,BiomeIds.DEEP_OCEAN,-40,40,0,4000,0,5,-10000,0,BiomeTrait.MARINE);add(d,BiomeIds.COLD_OCEAN,-40,8,0,3000,0,4,-10000,0,BiomeTrait.MARINE);add(d,BiomeIds.DEEP_COLD_OCEAN,-40,8,0,3000,0,4,-10000,0,BiomeTrait.MARINE);add(d,BiomeIds.LUKEWARM_OCEAN,8,25,0,3500,.2,4,-10000,0,BiomeTrait.MARINE);add(d,BiomeIds.DEEP_LUKEWARM_OCEAN,8,25,0,3500,.2,4,-10000,0,BiomeTrait.MARINE);add(d,BiomeIds.FROZEN_OCEAN,-40,-1,0,2200,0,3,-10000,0,BiomeTrait.MARINE,BiomeTrait.SNOW);add(d,BiomeIds.DEEP_FROZEN_OCEAN,-40,-1,0,2200,0,3,-10000,0,BiomeTrait.MARINE,BiomeTrait.SNOW);
        add(d,BiomeIds.WARM_OCEAN,24,50,0,10000,0,100,-10000,0,BiomeTrait.MARINE);
        tune(d,BiomeIds.PLAINS,new BiomeDescriptor.Range(-5,8,24,32),new BiomeDescriptor.Range(0,250,700,1500),new BiomeDescriptor.Range(0,.25,.85,1.8));
        tune(d,BiomeIds.FOREST,new BiomeDescriptor.Range(2,8,21,28),new BiomeDescriptor.Range(400,850,1600,2600),new BiomeDescriptor.Range(.45,.8,2.2,4));
        tune(d,BiomeIds.SAVANNA,new BiomeDescriptor.Range(18,22,35,50),new BiomeDescriptor.Range(100,200,700,1400),new BiomeDescriptor.Range(.15,.3,.8,1.3));
        descriptors=d.stream().sorted(Comparator.comparing(x->x.key().toString())).toList();
        var canonical=new com.google.gson.JsonArray();
        for(var descriptor:descriptors)canonical.add(BiomeDescriptor.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,descriptor).getOrThrow(false,message->{}));
        fingerprint=GenerationFingerprint.of(CanonicalJson.of(canonical));
    }
    private static void tune(List<BiomeDescriptor> descriptors,net.minecraft.resources.ResourceLocation id,BiomeDescriptor.Range temperature,BiomeDescriptor.Range rainfall,BiomeDescriptor.Range aridity){
        for(int i=0;i<descriptors.size();i++){var d=descriptors.get(i);if(d.key().equals(id)){descriptors.set(i,new BiomeDescriptor(id,temperature,rainfall,aridity,d.elevation(),d.traits(),d.priority()));return;}}
        throw new IllegalArgumentException("Unknown catalog tuning target "+id);
    }
    private static void add(List<BiomeDescriptor>d,net.minecraft.resources.ResourceLocation k,double t0,double t1,double r0,double r1,double a0,double a1,double e0,double e1,BiomeTrait... traits){var assigned=EnumSet.noneOf(BiomeTrait.class);assigned.addAll(Arrays.asList(traits));if(k.equals(BiomeIds.BADLANDS)||k.equals(BiomeIds.WOODED_BADLANDS))assigned.add(BiomeTrait.PLATEAU);if(k.equals(BiomeIds.MEADOW)||k.equals(BiomeIds.GROVE))assigned.add(BiomeTrait.HIGHLAND);d.add(new BiomeDescriptor(k,new BiomeDescriptor.Range(t0,t0+(t1-t0)*.25,t1-(t1-t0)*.25,t1),new BiomeDescriptor.Range(r0,r0+(r1-r0)*.25,r1-(r1-r0)*.25,r1),new BiomeDescriptor.Range(a0,a0+(a1-a0)*.25,a1-(a1-a0)*.25,a1),new BiomeDescriptor.Range(e0,e0+(e1-e0)*.25,e1-(e1-e0)*.25,e1),assigned,10));}
    public Collection<BiomeDescriptor> descriptors(){return descriptors;}public GenerationFingerprint fingerprint(){return fingerprint;}
}
