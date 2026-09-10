/* Original development integration assertions. All Rights Reserved. */
package baseline.reproduction.biome;

import baseline.reproduction.Evidence;
import com.gabou.projectlandscape.biome.*;
import com.gabou.projectlandscape.geography.terrain.PaGeographyProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.Heightmap;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import java.util.*;

final class Task6IntegrationChecks {
    static void run(ServerLevel level,ClimateBiomeSource source,PaGeographyProvider geography,Evidence out)throws Exception{
        var state=(RTFRandomState)(Object)level.getChunkSource().randomState();
        var generation=state.generatorContext().generationContext();
        var resolver=new ClimateBiomeResolver(new VanillaBiomeCatalog(),generation.seeds(),generation.manifest().content().biomeResolver().planned().orElseThrow());
        var sampler=level.getChunkSource().randomState().sampler();
        int sx=level.getSharedSpawnPos().getX(),sz=level.getSharedSpawnPos().getZ();
        // Exact-key climate cache has no spatial cell. 4096 tests the climate regional field.
        int[][] boundaries={{0,0},{-16,0},{16,0},{-128,0},{128,0},{4096,0},{16384,0},{sx>>2<<2,sz>>2<<2},{-28672,-12288}};
        for(var boundary:boundaries){
            String previous=null;double pt=0,pr=0;
            for(int dx:new int[]{-4,0,4}){
                int x=boundary[0]+dx,z=boundary[1];
                if(QuartPos.toBlock(QuartPos.fromBlock(x))!=x)throw new AssertionError("Quart roundtrip");
                long start=System.nanoTime();var g=BiomeGeography.sample(geography,x,z);long geoNs=System.nanoTime()-start;
                start=System.nanoTime();var c=source.sampleClimate(x,z);long climateNs=System.nanoTime()-start;
                start=System.nanoTime();var expected=resolver.select(g,c);long resolverNs=System.nanoTime()-start;
                start=System.nanoTime();var actual=source.getNoiseBiome(QuartPos.fromBlock(x),level.getMaxBuildHeight()>>2,QuartPos.fromBlock(z),sampler);long sourceNs=System.nanoTime()-start;
                if(!actual.unwrapKey().orElseThrow().location().equals(expected))throw new AssertionError("Surface resolver authority at "+x+","+z);
                boolean marine=Set.of("DEEP_OCEAN","SHALLOW_OCEAN","INLAND_SEA").contains(g.water().name());
                var traits=resolver.explain(g,c).winner().descriptor().traits();
                if(marine!=traits.contains(BiomeTrait.MARINE))throw new AssertionError("Marine/land authority");
                if(g.water().name().equals("COAST")&&!traits.contains(BiomeTrait.COASTAL))throw new AssertionError("Shore authority");
                out.row("quart_seams","boundaryBlockX",boundary[0],"x",x,"z",z,"winner",expected.toString(),"directEqualsHolder",true,
                    "water",g.water().name(),"landform",g.landform().name(),"temperatureC",c.meanTemperatureCelsius(),"rainfallMm",c.annualRainfallMm(),
                    "deltaTemperature",previous==null?null:c.meanTemperatureCelsius()-pt,"deltaRainfall",previous==null?null:c.annualRainfallMm()-pr,
                    "winnerChanged",previous!=null&&!previous.equals(expected.toString()),"geographyNs",geoNs,"climateNs",climateNs,"resolverNs",resolverNs,"warmFullSourceNs",sourceNs);
                previous=expected.toString();pt=c.meanTemperatureCelsius();pr=c.annualRainfallMm();
            }
            out.flush();
        }
        var site=state.generatorContext().generator.getHeightmap().paBridge().macro().sites().site(0,0);
        var physical=new LinkedHashMap<String,int[]>();
        for(int dz=-4;dz<=4;dz++)for(int dx=-4;dx<=4;dx++){
            int x=((int)site.x()>>2<<2)+dx*512,z=((int)site.z()>>2<<2)+dz*512;
            var g=BiomeGeography.sample(geography,x,z);
            physical.putIfAbsent(g.water().name(),new int[]{x,z});
        }
        for(var entry:physical.entrySet()){
            int x=entry.getValue()[0],z=entry.getValue()[1];var g=BiomeGeography.sample(geography,x,z);var c=source.sampleClimate(x,z);
            var r=resolver.explain(g,c);var holder=source.getNoiseBiome(x>>2,level.getMaxBuildHeight()>>2,z>>2,sampler);
            if(!holder.unwrapKey().orElseThrow().location().equals(r.winner().key()))throw new AssertionError("Physical surface winner");
            out.row("runtime_physical_samples","seed",Long.toString(level.getSeed()),"x",x,"z",z,"water",g.water().name(),"landform",g.landform().name(),
                "elevationBlockY",g.elevationBlockY(),"temperatureC",c.meanTemperatureCelsius(),"rainfallMm",c.annualRainfallMm(),"evaporationMm",c.potentialEvaporationMm(),
                "ratio",BiomeScorer.aridity(c),"winner",r.winner().key().toString(),"winnerScore",r.winner().score(),"directEqualsHolder",true,
                "secondCandidate",r.second().map(s->s.key().toString()).orElse(null),"margin",r.second().map(s->r.winner().score()-s.score()).orElse(1.0));
            out.flush();
        }
        out.row("runtime_physical_search","sampledPoints",81,"waterClassesFound",physical.keySet(),"scope","bounded canonical site patch; absent wetland/coast/river classes are not proven unreachable");
        var biomes=level.registryAccess().registryOrThrow(Registries.BIOME);
        for(var key:List.of(net.minecraft.world.level.biome.Biomes.PLAINS,net.minecraft.world.level.biome.Biomes.FOREST)){
            var features=biomes.getHolderOrThrow(key).value().getGenerationSettings().features().stream().flatMap(s->s.stream()).toList();
            if(features.stream().noneMatch(h->h.unwrapKey().orElseThrow().location().getPath().contains("trees")))throw new AssertionError("Missing ordinary vegetation");
            if(features.stream().anyMatch(h->!h.isBound()||!h.value().feature().isBound()))throw new AssertionError("Unresolved feature");
            out.row("runtime_features","biome",key.location().toString(),"ordinaryTreeFeatures",true,"allHoldersBound",true);
        }
        for(int dx:new int[]{0,256}){
            int x=sx+dx,z=sz;
            boolean loadedBefore=level.getChunkSource().getChunkNow(x>>4,z>>4)!=null;
            long start=System.nanoTime();var chunk=level.getChunk(x>>4,z>>4);long elapsed=System.nanoTime()-start;
            if(chunk.getStatus()!=net.minecraft.world.level.chunk.ChunkStatus.FULL)throw new AssertionError("Not FULL");
            var blocks=new TreeMap<String,Integer>();int vegetation=0,caves=0;
            for(int bz=0;bz<16;bz++)for(int bx=0;bx<16;bx++){
                int wx=(x>>4<<4)+bx,wz=(z>>4<<4)+bz,y=level.getHeight(Heightmap.Types.WORLD_SURFACE,wx,wz)-1;
                var block=level.getBlockState(new BlockPos(wx,y,wz));
                if(block.isAir())throw new AssertionError("Air at declared world surface");
                blocks.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString(),1,Integer::sum);
                if(block.is(net.minecraft.tags.BlockTags.LEAVES)||block.is(net.minecraft.tags.BlockTags.LOGS))vegetation++;
            }
            long populationStart=System.nanoTime();int queries=0,retainedCaveCases=0;
            for(int qz=z>>2;qz<(z>>2)+4;qz++)for(int qx=x>>2;qx<(x>>2)+4;qx++)for(int qy=-16;qy<32;qy++){
                var holder=source.getNoiseBiome(qx,qy,qz,sampler);
                if(!holder.isBound())throw new AssertionError("Unresolved biome");
                var retained=source.retained().getNoiseBiome(qx,qy,qz,sampler);
                boolean retainedCave=retained.is(net.minecraft.world.level.biome.Biomes.LUSH_CAVES)||retained.is(net.minecraft.world.level.biome.Biomes.DRIPSTONE_CAVES)||retained.is(net.minecraft.world.level.biome.Biomes.DEEP_DARK);
                if(retainedCave&&qy*4<geography.sample(qx*4,qz*4).elevationBlockY()-8){
                    if(!holder.equals(retained))throw new AssertionError("Natural cave delegation suppressed");
                    retainedCaveCases++;
                }
                if(holder.is(net.minecraft.world.level.biome.Biomes.LUSH_CAVES)||holder.is(net.minecraft.world.level.biome.Biomes.DRIPSTONE_CAVES)||holder.is(net.minecraft.world.level.biome.Biomes.DEEP_DARK))caves++;
                queries++;
            }
            out.row("runtime_full_chunks","chunkX",x>>4,"chunkZ",z>>4,"status",chunk.getStatus().toString(),"loadedBefore",loadedBefore,"acquisitionNs",elapsed,
                "surfaceBlocks",blocks,"vegetationSurfaceColumns",vegetation,"structureStarts",chunk.getAllStarts().size(),
                "quartQueries",queries,"quartPopulationQueryNs",System.nanoTime()-populationStart,"naturalCaveHolders",caves,"retainedCaveEqualityChecks",retainedCaveCases,
                "scope","two bounded FULL chunks; cached spawn and offset acquisition, not paired fresh-generation benchmark; no structure or cave frequency claim");
            out.flush();
        }
        out.row("terrablender_v1","present",net.minecraftforge.fml.ModList.get().isLoaded("terrablender"),"surfaceAuthority","ClimateBiomeResolver",
            "surfaceEqualityChecks",boundaries.length*3,"passed",true);
        out.flush();
    }
}
