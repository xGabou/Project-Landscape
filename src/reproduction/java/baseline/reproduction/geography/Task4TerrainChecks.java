/* Original Task 4 verification harness. All Rights Reserved. Not distributed. */
package baseline.reproduction.geography;

import baseline.reproduction.Evidence;
import com.gabou.projectlandscape.config.MacroGeographySettings;
import com.gabou.projectlandscape.generation.seed.*;
import com.gabou.projectlandscape.generation.version.*;
import com.gabou.projectlandscape.geography.terrain.PaGeographyInstallation;
import net.minecraft.server.level.ServerLevel;
import raccoonman.reterraforged.registries.RTFRegistries;
import raccoonman.reterraforged.world.worldgen.*;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import java.util.*;

public final class Task4TerrainChecks {
    public static final GenerationVersions VERSION=new GenerationVersions(1,GeographyAlgorithmVersion.PA_GEOGRAPHY_V1,
        BaselineClimateAlgorithmVersion.LEGACY_RTF_HINTS_V0,BiomeResolverAlgorithmVersion.LEGACY_MULTINOISE_V0);
    public static void run(ServerLevel level,Evidence out)throws Exception {
        var state=(RTFRandomState)(Object)level.getChunkSource().randomState();
        var noises=level.registryAccess().lookupOrThrow(RTFRegistries.NOISE);
        for(long seed:new long[]{8675309,4303642605L,42}) {
            var c=GeneratorContext.makeCached(state.preset(),noises,(int)seed,3,6,false);
            var seeds=new NamedSeedService(seed,level.dimension().location(),VERSION);
            PaGeographyInstallation.install(c,seeds,MacroGeographySettings.defaults(),6,false);
            try {
                var bridge=c.generator.getHeightmap().paBridge();int checked=0;
                List<int[]> points=new ArrayList<>();
                for(int gx=-1;gx<=0;gx++)for(int gz=-1;gz<=0;gz++) {
                    var site=bridge.macro().sites().site(gx,gz);
                    points.add(new int[]{(int)site.x(),(int)site.z()});points.add(new int[]{(int)(site.x()+site.radius()),(int)site.z()});
                    for(var mouth:bridge.rivers().mouths((int)site.x(),(int)site.z())) {
                        var marine=bridge.macro().sampleMacro(mouth.x(),mouth.z());
                        if(marine.land())throw new AssertionError("River mouth on land");
                        points.add(new int[]{(int)mouth.x(),(int)mouth.z()});
                        out.row("hydrology_validation","seed",Long.toString(seed),"mouth",mouth,"targetAgreement",true);
                    }
                }
                points.add(new int[]{-1,-1});points.add(new int[]{0,0});points.add(new int[]{1,1});
                List<Object> first=new ArrayList<>();
                for(int[] point:points) {
                    int x=point[0],z=point[1];Cell cell=new Cell();c.lookup.sampleFilteredTile(cell,x,z);
                    var macro=bridge.macro().sampleMacro(x,z);
                    if(!Float.isFinite(cell.height)||cell.height< -1||cell.height>2)throw new AssertionError("Invalid terrain elevation");
                    if(!macro.land()&&(cell.terrain.isRiver()||cell.terrain.isLake()||cell.terrain.isWetland()||cell.height>c.levels.water))throw new AssertionError("Marine classification disagrees");
                    first.add(Evidence.cell(cell,c.generator.getHeightmap()));checked++;
                }
                for(int i=points.size()-1;i>=0;i--) {
                    var point=points.get(i);Cell cell=new Cell();c.lookup.sampleFilteredTile(cell,point[0],point[1]);
                    if(!first.get(i).equals(Evidence.cell(cell,c.generator.getHeightmap())))throw new AssertionError("Warm/order canonical disagreement");
                }
                out.row("v1_terrain","seed",Long.toString(seed),"canonicalChecks",checked,"reverseWarmExact",true,"digest",Evidence.hash(first),"riverCacheEntries",bridge.rivers().retainedEntries());
                var provider=new com.gabou.projectlandscape.geography.terrain.PaGeographyProvider(c);
                for(int i=0;i<Math.min(4,points.size());i++) {
                    var point=points.get(i);var sample=provider.sampleDetailed(point[0],point[1]);
                    out.row("v1_debug_samples","seed",Long.toString(seed),"version","PA_GEOGRAPHY_V1","x",point[0],"z",point[1],
                        "macro",sample.macro(),"distances",sample.distances(),"elevation",sample.geography().elevationBlockY(),"terrain",sample.geography().landform(),
                        "river",sample.geography().hydrology().river(),"lake",sample.geography().hydrology().lake(),"wetland",sample.geography().hydrology().wetland());
                }
            }finally{c.cache.close();}
        }
        benchmark(level,out);out.flush();
    }
    private static void benchmark(ServerLevel level,Evidence out) {
        var state=(RTFRandomState)(Object)level.getChunkSource().randomState();var noises=level.registryAccess().lookupOrThrow(RTFRegistries.NOISE);
        int[][] tiles={{0,0},{64,64},{112,64},{64,112},{-64,-64},{-112,-64},{32,48},{48,32}};
        for(boolean v1:new boolean[]{false,true}) {
            var c=GeneratorContext.makeCached(state.preset(),noises,8675309,3,6,false);
            if(v1)PaGeographyInstallation.install(c,new NamedSeedService(8675309,level.dimension().location(),VERSION),MacroGeographySettings.defaults(),6,false);
            try {
                for(int repetition=-6;repetition<10;repetition++) {
                    long start=System.nanoTime();
                    for(var pos:tiles){var tile=c.generator.generate(pos[0],pos[1]).join();tile.close();}
                    if(repetition>=0)out.row("task4_performance","version",v1?"PA_GEOGRAPHY_V1":"LEGACY_RTF_V0","operation","filtered_tile_batch",
                        "repetition",repetition,"tiles",tiles.length,"elapsedNs",System.nanoTime()-start);
                }
                if(v1) {
                    var provider=new com.gabou.projectlandscape.geography.terrain.PaGeographyProvider(c);
                    for(int i=0;i<8;i++)provider.sample(i*32,0);
                    for(int repetition=0;repetition<5;repetition++) {
                        double sum=0;long start=System.nanoTime();
                        for(int i=0;i<100000;i++)sum+=c.generator.getHeightmap().paBridge().macro().sampleMacro(i%16384,i/16384).landness();
                        out.row("task4_performance","version","PA_GEOGRAPHY_V1","operation","macro_sample","queries",100000,"elapsedNs",System.nanoTime()-start,"checksum",sum);
                        start=System.nanoTime();
                        for(int i=0;i<100000;i++)sum+=provider.sample(i%128,0).elevationBlockY();
                        out.row("task4_performance","version","PA_GEOGRAPHY_V1","operation","canonical_warm_sample","queries",100000,"elapsedNs",System.nanoTime()-start,"checksum",sum);
                    }
                }
            }finally{c.cache.close();}
        }
    }
}
