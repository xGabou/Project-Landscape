/* Original offline performance harness. All Rights Reserved. */
package baseline.reproduction.performance;

import com.gabou.atmospheregen.biome.CanonicalClimateGeography;
import com.gabou.atmospheregen.climate.PaBaselineClimateProvider;
import com.gabou.atmospheregen.config.*;
import com.gabou.atmospheregen.generation.seed.NamedSeedService;
import com.gabou.atmospheregen.generation.version.GenerationVersions;
import com.gabou.atmospheregen.geography.terrain.*;
import com.google.gson.GsonBuilder;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import raccoonman.reterraforged.data.worldgen.preset.PresetNoiseData;
import raccoonman.reterraforged.data.worldgen.preset.settings.Presets;
import raccoonman.reterraforged.registries.RTFRegistries;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import com.gabou.atmospheregen.climate.BaselineClimateModel;
import jdk.jfr.Recording;
import jdk.jfr.Configuration;

/** Uses the production preset registry bootstrap and filtered tile pipeline without a client. */
public final class WorldgenBenchmark {
    private static volatile Object sink;
    private static final BaselineClimateConfig.Planned CONFIG=new BaselineClimateConfig.Planned(100000,0,.0065,.5,1,4096,.85,12000,24000,256,1,.10);
    private static final com.google.gson.Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]);
        if(Files.exists(out))throw new IllegalArgumentException("Refusing existing evidence: "+out);
        Files.createDirectories(out);
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            var preset=Presets.makeLegacyDefault();
            var registries=new RegistrySetBuilder().add(RTFRegistries.NOISE,c->PresetNoiseData.bootstrap(preset,c))
                    .build(RegistryAccess.EMPTY);
            var context=GeneratorContext.makeCached(preset,registries.lookupOrThrow(RTFRegistries.NOISE),8675309,3,6,false);
            var seeds=new NamedSeedService(8675309,new ResourceLocation("minecraft","overworld"),GenerationVersions.planned());
            PaGeographyInstallation.install(context,seeds,MacroGeographySettings.defaults(),6,false);
            if(System.getProperty("task6b.mode","acquisition").equals("ownership")) {
                try {OwnershipChecks.run(context,out);} finally {context.cache.close();}
                return;
            }
            if(System.getProperty("task6b.mode","acquisition").equals("erosion-experiment")) {
                try {ErosionExperiment.run(context,out);} finally {context.cache.close();}
                return;
            }
            try(var recording=new Recording(Configuration.getConfiguration("profile"))) {
                recording.enable("jdk.JavaMonitorEnter").withThreshold(java.time.Duration.ofMillis(1));
                recording.start();
                var geography=new PaGeographyProvider(context);
                var surface=new CanonicalClimateGeography(geography);
                var climate=new PaBaselineClimateProvider(surface,seeds,CONFIG);
                var rows=new ArrayList<Object>();
                rows.add(measure("macro",10000,()->sink=geography.macroProvider().sampleMacro(0,0)));
                rows.add(measure("exact_geography_cold",1,()->sink=geography.sample(0,0)));
                rows.add(measure("exact_geography_warm",1000,()->sink=geography.sample(0,0)));
                rows.add(measure("surface_extraction",10,()->sink=geography.snapshotSurfaceTile(0,0)));
                rows.add(measure("climate_cold",1,()->sink=climate.sample(0,0)));
                rows.add(measure("climate_warm",10000,()->sink=climate.sample(0,0)));
                var model=new BaselineClimateModel(seeds,CONFIG);
                rows.add(measure("temperature_warm",10000,()->sink=model.temperatureOnly(surface,0,0)));
                write(out,"microbenchmarks.json",rows);
                for(var scenario:scenarios().entrySet()) {
                    System.out.println("TASK6B scenario="+scenario.getKey());
                    var trace=CacheReplay.trace(scenario.getValue(),seeds,CONFIG);
                    write(out,scenario.getKey()+"_trace.json",trace);
                    write(out,scenario.getKey()+"_strategies.json",CacheReplay.compare(trace));
                    // Each scenario starts with fresh context/cache ownership, as a cold region workload.
                    var fresh=GeneratorContext.makeCached(preset,registries.lookupOrThrow(RTFRegistries.NOISE),8675309,3,6,false);
                    PaGeographyInstallation.install(fresh,seeds,MacroGeographySettings.defaults(),6,false);
                    try {
                        var g=new PaGeographyProvider(fresh);var s=new CanonicalClimateGeography(g);
                        var c=new PaBaselineClimateProvider(s,seeds,CONFIG);var outputs=new ArrayList<Object>();var measurements=new ArrayList<Object>();
                        for(int pass=0;pass<2;pass++){
                            int index=0;
                            for(var p:scenario.getValue()){
                                measurements.add(measure("pass"+pass+"_query"+index++,1,()->{sink=c.sample(p.x(),p.z());}));
                                var value=(com.gabou.atmospheregen.api.climate.ClimateBaseline)sink;
                                outputs.add(Map.of("x",p.x(),"z",p.z(),"rawDoubleBits",List.of(
                                        Double.doubleToRawLongBits(value.meanTemperatureCelsius()),Double.doubleToRawLongBits(value.annualRainfallMm()),
                                        Double.doubleToRawLongBits(value.ecologicalMoistureIndex()),Double.doubleToRawLongBits(value.potentialEvaporationMm()),
                                        Double.doubleToRawLongBits(value.rainShadow()),Double.doubleToRawLongBits(value.prevailingWind().orElseThrow().x()),
                                        Double.doubleToRawLongBits(value.prevailingWind().orElseThrow().z()))));
                            }
                        }
                        write(out,scenario.getKey()+".json",Map.of("measurements",measurements,"surfaceCache",s.cacheStats(),"climateCache",c.cacheStats(),
                                "outputDigest",hash(JSON.toJson(outputs)),"queriesPerPass",scenario.getValue().size(),"outputs",outputs));
                    } finally {fresh.cache.close();}
                }
                recording.stop();Path jfr=out.resolve("profile.jfr");recording.dump(jfr);
                write(out,"jfr_summary.json",ProfileSummary.read(jfr));
                write(out,"completion.json",Map.of("status","OFFLINE_ACQUISITION_COMPLETE_NOT_FULL_ACCEPTANCE",
                        "java",System.getProperty("java.runtime.version"),"workers",Runtime.getRuntime().availableProcessors(),
                        "seed",8675309,"config",CONFIG,"baselineHead","a3140a5d7113137563d275cbaea05a8e94f3b23e",
                        "limitations",List.of("one cold and one warm pass; repeats required","no FULL chunks in this harness","microbenchmarks include startup; not steady-state acceptance")));
            } finally {context.cache.close();}
        } finally {raccoonman.reterraforged.concurrent.ThreadPools.WORLD_GEN.shutdownNow();}
    }
    private static Map<String,Object> measure(String name,int count,Runnable action){
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        var os=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
        long processCpu=os.getProcessCpuTime();
        long id=Thread.currentThread().getId(),allocated=bean.getThreadAllocatedBytes(id),cpu=bean.getCurrentThreadCpuTime(),start=System.nanoTime();
        for(int i=0;i<count;i++)action.run();
        return Map.of("name",name,"count",count,"wallNanos",System.nanoTime()-start,
                "processCpuNanos",os.getProcessCpuTime()-processCpu,"callingThreadCpuNanos",bean.getCurrentThreadCpuTime()-cpu,"callingThreadAllocatedBytes",bean.getThreadAllocatedBytes(id)-allocated);
    }
    static LinkedHashMap<String,List<CacheReplay.Point>> scenarios(){
        var scenarios=new LinkedHashMap<String,List<CacheReplay.Point>>();
        var sequential=new ArrayList<CacheReplay.Point>();var spawn=new ArrayList<CacheReplay.Point>();
        var scattered=new ArrayList<CacheReplay.Point>();var interleaved=new ArrayList<CacheReplay.Point>();
        var heavy=new ArrayList<CacheReplay.Point>();
        for(int i=0;i<16;i++){
            sequential.add(new CacheReplay.Point(-28672+i*16,-12288));
            spawn.add(new CacheReplay.Point(-28672+(i%4)*16,-12288+(i/4)*16));
            scattered.add(new CacheReplay.Point(-28672+i*16384,-12288+i*8192));
            interleaved.add(new CacheReplay.Point(-28672+(i%4)*65536+(i/4)*16,-12288+(i%4)*32768));
        }
        for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)heavy.add(new CacheReplay.Point(-28672+x*4,-12288+z*4));
        scenarios.put("sequential_generation",sequential);scenarios.put("spawn_expansion",spawn);scenarios.put("scattered_generation",scattered);
        scenarios.put("interleaved_regions",interleaved);scenarios.put("climate_heavy",heavy);return scenarios;
    }
    static void write(Path out,String name,Object value)throws Exception {Files.writeString(out.resolve(name),JSON.toJson(value),StandardOpenOption.CREATE_NEW);}
    static String hash(String value)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
}
