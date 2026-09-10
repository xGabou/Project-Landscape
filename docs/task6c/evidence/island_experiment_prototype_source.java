/* Original bounded read-mostly cache experiment. All Rights Reserved. */
package baseline.reproduction.performance;

import com.gabou.atmospheregen.geography.continent.*;
import com.gabou.atmospheregen.config.MacroGeographySettings;
import com.gabou.atmospheregen.generation.seed.*;
import com.gabou.atmospheregen.generation.version.GenerationVersions;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.nio.file.*;
import java.lang.management.ManagementFactory;

final class IslandExperiment {
    private interface Access { List<IslandModel.Island> get(MacroSiteField.Site site); }
    private record Entry(long id,List<IslandModel.Island> values) {}
    /** Experimental front cache; bounded 64 slots plus unchanged 256-entry backing cache. */
    private static final class Candidate implements Access {
        final IslandModel model; final AtomicReferenceArray<Entry> front=new AtomicReferenceArray<>(64);
        final LongAdder fastHits=new LongAdder();
        Candidate(IslandModel model){this.model=model;}
        public List<IslandModel.Island> get(MacroSiteField.Site site){
            int slot=(int)MacroSiteField.mix(site.id())&63;Entry e=front.get(slot);
            if(e!=null&&e.id==site.id()){fastHits.increment();return e.values;}
            synchronized(this){
                e=front.get(slot);if(e!=null&&e.id==site.id()){fastHits.increment();return e.values;}
                var value=model.islands(site);front.set(slot,new Entry(site.id(),value));return value;
            }
        }
        synchronized void clear(){for(int i=0;i<64;i++)front.set(i,null);model.clear();}
    }
    static void run(Path out)throws Exception {
        var seeds=new NamedSeedService(8675309,new ResourceLocation("minecraft","overworld"),GenerationVersions.planned());
        var settings=MacroGeographySettings.defaults();var field=new MacroSiteField(seeds,settings);
        var known=new HashMap<Long,List<IslandModel.Island>>();var reference=new PaMacroGeography(seeds,settings).islands();
        var hot=new ArrayList<MacroSiteField.Site>();
        for(int i=0;i<16;i++)hot.add(field.site(i%4-2,i/4-2));
        var trace=new ArrayList<MacroSiteField.Site>();
        var path=Path.of("../../docs/task6b/evidence/runtime-runs/optimized-extended/task6b_locality_trace.json.gz");
        try(var reader=new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(Files.newInputStream(path)))){
            var events=com.google.gson.JsonParser.parseReader(reader).getAsJsonArray().get(0).getAsJsonObject().getAsJsonArray("events");
            for(var item:events){var e=item.getAsJsonObject();if(e.get("kind").getAsString().equals("profile_sample"))trace.add(field.at(e.get("x").getAsInt(),e.get("z").getAsInt()));}
        }
        for(var site:hot)known.put(site.id(),reference.islands(site));
        for(var site:trace)known.computeIfAbsent(site.id(),k->reference.islands(site));
        var cold=new ArrayList<MacroSiteField.Site>();
        for(int i=0;i<4096;i++){var s=field.site(i%64-32,i/64-32);cold.add(s);known.put(s.id(),reference.islands(s));}
        var bean=ManagementFactory.getThreadMXBean();bean.setThreadContentionMonitoringEnabled(true);
        var rows=new ArrayList<Object>();
        try(var recording=new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"))){
            recording.enable("jdk.JavaMonitorEnter").withThreshold(java.time.Duration.ofMillis(1));recording.start();
            for(int workers:new int[]{1,8,24,48})for(String workload:List.of("hot","cold","mixed95_5","trace"))for(int pass=-1;pass<3;pass++) {
                for(int order=0;order<2;order++) {
                    boolean candidate=((pass+order)&1)==0;
                    var model=new PaMacroGeography(seeds,settings).islands();var c=new Candidate(model);Access access=candidate?c:model::islands;
                    if(!workload.equals("cold")){for(var s:hot)access.get(s);for(var s:trace)access.get(s);}
                    int count=workload.equals("cold")?4096:workload.equals("trace")?trace.size():100000;
                    var pool=Executors.newFixedThreadPool(workers);var ready=new CountDownLatch(workers);var start=new CountDownLatch(1);
                    var futures=new ArrayList<Future<long[]>>();long beforeCpu=((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime();
                    for(int w=0;w<workers;w++){final int worker=w;futures.add(pool.submit(()->{
                        long id=Thread.currentThread().getId();ready.countDown();start.await();
                        for(int i=worker;i<count;i+=workers){var s=workload.equals("cold")?cold.get(i):workload.equals("trace")?trace.get(i):workload.equals("mixed95_5")&&i%20==0?cold.get((i/20)%cold.size()):hot.get(i%hot.size());
                            if(!access.get(s).equals(known.get(s.id())))throw new AssertionError("Island bits changed");
                        }
                        var info=bean.getThreadInfo(id);return new long[]{info.getBlockedCount(),info.getBlockedTime()};
                    }));}
                    ready.await();long begin=System.nanoTime();start.countDown();long blocked=0,blockedMs=0;
                    for(var future:futures){var result=future.get();blocked+=result[0];blockedMs+=result[1];}
                    long nanos=System.nanoTime()-begin;pool.shutdown();pool.awaitTermination(1,TimeUnit.MINUTES);
                    long cpu=((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime()-beforeCpu;
                    rows.add(Map.of("workers",workers,"workload",workload,"pass",pass,"candidate",candidate,"queries",count,"wallNanos",nanos,"cpuNanos",cpu,"blockedCount",blocked,"blockedThreadMillis",blockedMs,"frontHits",c.fastHits.sum()));
                    c.clear();for(int i=0;i<64;i++)if(c.front.get(i)!=null)throw new AssertionError("Clear retained front");
                }
            }
            recording.stop();recording.dump(out.resolve("island_experiment.jfr"));
        }
        WorldgenBenchmark.write(out,"island_model_experiment.json",Map.of("exact",true,"rows",rows,"traceCoordinates",trace.size(),"distinctReferenceSites",known.size(),"frontCapacity",64,"backingCapacity",256,
            "memory","64 atomic slot references + at most 64 immutable entry records and island lists additional to bounded 256-entry backing LRU; no thread-local or static world state",
            "scope","Isolated original versus 64-slot read front; identical immutable island lists incl coordinates/radii/kind/cluster ID; trace replays owning sites of actual profile coordinates, not an assertion that every profile coordinate called IslandModel",
            "coordination","Misses retain serialized construction; same-key duplicate computation prevented by recheck. No pending map; single-flight/capacity wait counters are not applicable. Hot reads bypass the monitor; cold/mixed included."));
    }
}
