/* Original development harness. All Rights Reserved. */
package baseline.reproduction.biome;

import baseline.reproduction.Evidence;
import com.gabou.atmospheregen.biome.*;
import com.gabou.atmospheregen.climate.*;
import com.gabou.atmospheregen.geography.terrain.PaGeographyProvider;
import com.gabou.atmospheregen.geography.ocean.CoarseDistanceField;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import java.util.*;

public final class Task6RuntimeChecks {
    private Task6RuntimeChecks(){}
    public static String run(ServerLevel level,Evidence out,boolean reopened)throws Exception{
        var source=level.getChunkSource().getGenerator().getBiomeSource();
        if(!(source instanceof ClimateBiomeSource v1))throw new AssertionError("V1 source not installed");
        var context=((RTFRandomState)(Object)level.getChunkSource().randomState()).generatorContext();
        var geography=new PaGeographyProvider(context);
        var exact=ClimateGeographyAdapters.of(geography,geography.macroProvider(),new CoarseDistanceField(geography.macroProvider()));
        var reuse=new CanonicalClimateGeography(geography);
        int checked=0;
        int sx=level.getSharedSpawnPos().getX(),sz=level.getSharedSpawnPos().getZ();
        for(int[] p:new int[][]{{sx,sz},{sx+4,sz},{sx,sz+4},{-129,-129},{-128,-128},{-1,-1},{0,0},{127,127},{128,128}}){
            if(!exact.sample(p[0],p[1]).equals(reuse.sample(p[0],p[1])))throw new AssertionError("Detached canonical surface mismatch "+Arrays.toString(p));
            checked++;
        }
        out.row("task6_surface_cache","checked",checked,"exact",true,"stats",reuse.cacheStats());
        var model=new BaselineClimateModel(context.generationContext().seeds(),context.generationContext().manifest().content().baselineClimate().planned().orElseThrow());
        long start=System.nanoTime();var reference=model.sample(exact,sx,sz);long referenceNs=System.nanoTime()-start;
        start=System.nanoTime();var cached=v1.sampleClimate(sx,sz);long cachedNs=System.nanoTime()-start;
        if(!reference.equals(cached))throw new AssertionError("Canonical climate changed by reuse");
        out.row("task6_climate_exact","reopened",reopened,"referenceNs",referenceNs,"cachedNs",cachedNs,
            "temperatureC",cached.meanTemperatureCelsius(),"rainfallMm",cached.annualRainfallMm(),
            "evaporationMm",cached.potentialEvaporationMm(),"rainShadow",cached.rainShadow(),"exact",true);
        List<String> winners=new ArrayList<>();
        var sampler=level.getChunkSource().randomState().sampler();
        var biomes=level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        int caveCases=0;
        for(var cave:List.of(net.minecraft.world.level.biome.Biomes.LUSH_CAVES,net.minecraft.world.level.biome.Biomes.DRIPSTONE_CAVES,net.minecraft.world.level.biome.Biomes.DEEP_DARK)){
            var holder=biomes.getOrThrow(cave);
            BiomeSource fixed=new BiomeSource(){
                @Override protected com.mojang.serialization.Codec<? extends BiomeSource> codec(){throw new UnsupportedOperationException("Test fixture is not serialized");}
                @Override protected java.util.stream.Stream<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> collectPossibleBiomes(){return java.util.stream.Stream.of(holder);}
                @Override public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getNoiseBiome(int x,int y,int z,net.minecraft.world.level.biome.Climate.Sampler ignored){return holder;}
            };
            var composed=new ClimateBiomeSource(fixed,geography,context.generationContext(),biomes);
            int qx=Math.floorDiv(sx,4),qz=Math.floorDiv(sz,4);
            int below=Math.floorDiv((int)Math.floor(geography.sample(qx*4,qz*4).elevationBlockY())-16,4);
            if(!composed.getNoiseBiome(qx,below,qz,sampler).is(cave))throw new AssertionError("Lost retained cave family "+cave);
            if(composed.getNoiseBiome(qx,level.getMaxBuildHeight()>>2,qz,sampler).is(cave))throw new AssertionError("Cave fixture leaked to surface "+cave);
            if(!composed.possibleBiomes().contains(holder))throw new AssertionError("Cave holder missing from possible biomes");
            caveCases++;
        }
        out.row("task6_cave_composition","reopened",reopened,"controlledRetainedFamilies",caveCases,"surfaceLeakage",0,
                "scope","actual V1 source composed with fixed cave-holder fixtures; not a natural cave frequency survey");
        start=System.nanoTime();
        for(int z=sz>>2;z<(sz>>2)+4;z++)for(int x=sx>>2;x<(sx>>2)+4;x++)
            winners.add(v1.getNoiseBiome(x,level.getMaxBuildHeight()>>2,z,sampler).unwrapKey().orElseThrow().location().toString());
        out.row("task6_biome_queries","reopened",reopened,"count",winners.size(),"elapsedNs",System.nanoTime()-start,"surfaceCache",v1.surfaceCacheStats(),"climateCache",v1.climateCacheStats());
        var ops=RegistryOps.create(JsonOps.INSTANCE,level.registryAccess());
        var encoded=BiomeSource.CODEC.encodeStart(ops,v1).getOrThrow(false,s->{});
        var decoded=BiomeSource.CODEC.parse(ops,encoded).getOrThrow(false,s->{});
        if(!(decoded instanceof ClimateBiomeSource copy)||!copy.manifestFingerprint().equals(v1.manifestFingerprint()))throw new AssertionError("Source codec manifest roundtrip");
        String digest=Evidence.hash(winners);
        out.row("task6_runtime","reopened",reopened,"biomeDigest",digest,"sourceCodecRoundtrip",true,"source",encoded,"spawn",List.of(sx,sz),"surfaceCache",v1.surfaceCacheStats());
        out.flush();
        return digest;
    }
}
