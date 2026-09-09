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
