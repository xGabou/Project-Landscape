/* Original headless ownership/disposal regression. All Rights Reserved. */
package baseline.reproduction.performance;

import com.gabou.atmospheregen.config.*;
import com.gabou.atmospheregen.persistence.*;
import com.gabou.atmospheregen.generation.context.WorldGenerationContext;
import com.gabou.atmospheregen.generation.version.GenerationVersions;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import java.nio.file.Path;
import java.util.*;

final class OwnershipChecks {
    static void run(GeneratorContext context,Path out)throws Exception {
        var macro=MacroGeographySettings.defaults();
        var config=new BaselineClimateConfig.Planned(100000,0,.0065,.5,1,4096,.85,12000,24000,256,1,.10);
        var content=new ManifestContent(GenerationVersions.planned(),8675309,net.minecraft.world.level.Level.OVERWORLD.location(),
                new WorldGeographyConfig(Optional.empty(),Optional.of(new PlannedGeographySettings(macro.continentScaleBlocks(),macro.minimumMajorOceanWidthBlocks(),1200,1000,.5,Optional.of(macro)))),
                new BaselineClimateConfig(Optional.of(config)),new BiomeResolverConfig(Optional.of(new BiomeResolverConfig.Planned(64,1))),
                Map.of("offline-ownership-fixture-only",GenerationFingerprint.bytes(new byte[]{1})),Optional.of(new com.gabou.atmospheregen.biome.VanillaBiomeCatalog().fingerprint()));
        var generation=new WorldGenerationContext(GenerationManifest.create(content),net.minecraft.world.level.Level.OVERWORLD,context.lookup.samplingIdentity());
        context.bindGenerationContext(generation);
        var g=context.canonicalGeography();var service=context.canonicalClimate(generation);
        if(g!=context.canonicalGeography()||service!=context.canonicalClimate(generation)||service!=g.climateService(generation))throw new AssertionError("Duplicated production service");
        var baseline=service.provider().sample(0,0);
        var reference=new com.gabou.atmospheregen.climate.BaselineClimateModel(generation.seeds(),config)
                .sample(com.gabou.atmospheregen.climate.ClimateGeographyAdapters.of(g,g.macroProvider(),g.distanceField()),0,0);
        if(!baseline.equals(reference))throw new AssertionError("Service changes climate");
        var sources=System.getProperty("task6b.sourceChecks","false").equals("true")?SourceReuseChecks.run(context,generation,out):List.<com.gabou.atmospheregen.biome.ClimateBiomeSource>of();
        var before=Map.of("surface",service.geography().cacheStats(),"climate",service.provider().cacheStats(),"distance",g.distanceCacheStats());
        var islands=context.generator.getHeightmap().paBridge().macro().islands();var islandBefore=islands.cacheStats();
        context.cache.close();
        if(islands.cacheStats().get("frontEntries")!=0||islands.cacheStats().get("entries")!=0)throw new AssertionError("Island cache retained after disposal");
        try{islands.islands(context.generator.getHeightmap().paBridge().macro().sites().at(0,0));throw new AssertionError("Island query after disposal");}catch(IllegalStateException expected){}
        WorldgenBenchmark.write(out,"island_disposal.json",Map.of("before",islandBefore,"after",islands.cacheStats(),"postCloseQueriesRejected",true));
        for(var source:sources){
            if(source.surfaceWinnerCacheStats().get("entries")!=0||source.surfaceGeographyCacheStats().get("entries")!=0)throw new AssertionError("Retained source cache after disposal");
            try{source.getNoiseBiome(0,100,0,null);throw new AssertionError("Source query after disposal");}catch(IllegalStateException expected){}
        }
        if(service.geography().cacheStats().get("entries")!=0||service.provider().cacheStats().get("entries")!=0||g.distanceCacheStats().get("entries")!=0)throw new AssertionError("Retained cache payload after dispose");
        try{service.provider().sample(0,0);throw new AssertionError("Climate after dispose");}catch(IllegalStateException expected){}
        try{context.canonicalClimate(generation);throw new AssertionError("Recreated service after dispose");}catch(IllegalStateException expected){}
        WorldgenBenchmark.write(out,"provider_instance_counts.json",Map.of("geographyProviders",1,"climateProviders",1,"surfaceCaches",1,"distanceFields",1,
                "identityChecks",true,"scope","actual production context/service construction through headless Forge, diagnostic reference shares provider"));
        WorldgenBenchmark.write(out,"disposal.json",Map.of("exactClimate",true,"before",before,"after",Map.of("surface",service.geography().cacheStats(),"climate",service.provider().cacheStats(),"distance",g.distanceCacheStats()),
                "postCloseQueriesRejected",true,"scope","payload cleared even while test retains service/context references; no GC reachability claim"));
    }
}
