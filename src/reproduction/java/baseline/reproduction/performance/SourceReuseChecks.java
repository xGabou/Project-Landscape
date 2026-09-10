/* Original exact surface reuse regression and microbenchmark. All Rights Reserved. */
package baseline.reproduction.performance;

import com.gabou.atmospheregen.biome.*;
import com.gabou.atmospheregen.generation.context.WorldGenerationContext;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import net.minecraft.core.*;
import net.minecraft.world.level.biome.*;
import java.nio.file.Path;
import java.util.*;

final class SourceReuseChecks {
    static List<ClimateBiomeSource> run(GeneratorContext context,WorldGenerationContext generation,Path out)throws Exception {
        var biomes=net.minecraft.data.registries.VanillaRegistries.createLookup().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var g=context.canonicalGeography();var service=context.canonicalClimate(generation);
        var resolver=new ClimateBiomeResolver(new VanillaBiomeCatalog(),generation.seeds(),generation.manifest().content().biomeResolver().planned().orElseThrow());
        var sources=new ArrayList<ClimateBiomeSource>();int caveChecks=0;
        for(var cave:List.of(Biomes.LUSH_CAVES,Biomes.DRIPSTONE_CAVES,Biomes.DEEP_DARK)){
            var source=new ClimateBiomeSource(new FixedBiomeSource(biomes.getOrThrow(cave)),g,generation,biomes);sources.add(source);
            source.getNoiseBiome(0,100,0,null);
            for(int y:new int[]{-100,-99,-98}){if(source.getNoiseBiome(0,y,0,null)!=biomes.getOrThrow(cave))throw new AssertionError("Winner overrides cave");caveChecks++;}
        }
        var source=new ClimateBiomeSource(new FixedBiomeSource(biomes.getOrThrow(Biomes.PLAINS)),g,generation,biomes);sources.add(source);
        var winners=new ArrayList<String>();
        for(int[] p:new int[][]{{0,0},{-1,-1},{-32,-32},{32,32},{-7168,-3072}}){
            int x=QuartPos.toBlock(p[0]),z=QuartPos.toBlock(p[1]);var geo=BiomeGeography.sample(g,x,z);
            var expected=resolver.select(geo,service.provider().sample(x,z));
            for(int y:new int[]{-100,0,24,100}){
                var holder=source.getNoiseBiome(p[0],y,p[1],null);
                if(holder!=biomes.getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,expected)))throw new AssertionError("2D winner changed");
                winners.add(holder.unwrapKey().orElseThrow().location().toString());
            }
        }
        var measurements=new ArrayList<Object>();
        for(int pass=-2;pass<7;pass++){
            long start=System.nanoTime();
            for(int i=0;i<10000;i++){
                var geo=BiomeGeography.sample(g,0,0);
                var expected=GeographicBiomeRules.temperatureOnlyOverride(geo,service.model().temperatureOnly(service.geography(),0,0));
                if(expected==null)throw new AssertionError("Marine fixture");
            }
            long referenceNs=System.nanoTime()-start;start=System.nanoTime();
            for(int i=0;i<10000;i++)source.getNoiseBiome(0,100,0,null);
            measurements.add(Map.of("pass",pass,"warmup",pass<0,"queries",10000,"referenceSurfacePathNanos",referenceNs,"cachedSourceNanos",System.nanoTime()-start));
        }
        WorldgenBenchmark.write(out,"source_reuse.json",Map.of("caveDelegationChecks",caveChecks,"directHolderChecks",winners.size(),"holderReferenceIdentity",true,"winners",winners,
                "measurements",measurements,"surfaceGeography",source.surfaceGeographyCacheStats(),"surfaceWinners",source.surfaceWinnerCacheStats(),
                "scope","real registry holders and canonical terrain; reference repeats original surface physical path; full chunk effects measured separately"));
        return sources;
    }
}
