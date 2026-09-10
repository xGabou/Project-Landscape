/* Original developer-only Task 1C harness. All Rights Reserved.
 * No production API, algorithm identity, or generation-version implementation. */
package baseline.reproduction;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import raccoonman.reterraforged.registries.RTFRegistries;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkStatus;
import raccoonman.reterraforged.concurrent.ThreadPools;
import raccoonman.reterraforged.concurrent.pool.ArrayPool;
import raccoonman.reterraforged.data.worldgen.preset.settings.Preset;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.WorldLookup;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.Tile;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.TileCache;
import raccoonman.reterraforged.world.worldgen.noise.module.Noise;

/** Timers never include serialization, digesting, fixture discovery, or disk I/O. */
public final class LegacyBaselineSuite {
    static final String COMPARATOR="cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee";
    static final String CANONICAL="LEGACY_FILTERED_CANONICAL", DIRECT="LEGACY_DIRECT_APPROXIMATE";
    static final long[] SEEDS={8675309L,4303642605L,42L,-987654321L,0L,Long.MAX_VALUE,-Long.MAX_VALUE};
    private final ServerLevel level;
    private final Evidence out;
    private final Preset preset;
    private final HolderGetter<Noise> noises;
    private final List<GeneratorContext> owned=new ArrayList<>();
    private final Set<Long> workerIds=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private long lastWorkerAllocatedBytes;
    private static volatile float sink;
    public LegacyBaselineSuite(ServerLevel level, Evidence out) {
        this.level=level;this.out=out;
        this.preset=((RTFRandomState)(Object)level.getChunkSource().randomState()).preset();
        this.noises=level.registryAccess().lookupOrThrow(RTFRegistries.NOISE);
    }
    /** Offline comparator uses identical preset/noise bootstrap; holder checks remain runtime-only. */
    public LegacyBaselineSuite(Preset preset, HolderGetter<Noise> noises, Evidence out) {
        this.level=null;this.preset=preset;this.noises=noises;this.out=out;
    }
    private GeneratorContext context(long seed) {
        var c=GeneratorContext.makeUncached(preset,noises,(int)seed,3,1,6);
        c.cache=new TileCache(3,false,c.generator);c.lookup=new WorldLookup(c);owned.add(c);return c;
    }
    public void run(String mode,int worldIndex) throws Exception {
        try {
            identity(mode,worldIndex);
            if(mode.equals("golden")) golden();
            if(mode.startsWith("golden")) scheduling();
            if(mode.equals("benchmark")) {if(worldIndex==0) pointsAndTiles();chunks(worldIndex);}
            if(mode.equals("allocation")) allocationProbe();
            if(mode.equals("variance")) variance(worldIndex);
        } finally {for(var c:owned)c.cache.close();out.flush();}
    }
    private void identity(String mode,int worldIndex) throws Exception {
        Object effective=effective(preset);
        out.row("identity","schemaVersion",1,"fixtureVersion",1,"backend","LEGACY_RTF_V0","comparator",COMPARATOR,
            "mode",mode,"worldIndex",worldIndex,"configurationFingerprint",Evidence.hash(effective),"effectivePreset",effective,
            "presetCodec",Preset.DIRECT_CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,preset).getOrThrow(false,s->{}),
            "seedSuite",Arrays.stream(SEEDS).mapToObj(Long::toString).toList(),"tileExponent",3,"borderChunks",1,"batchCount",6,
            "workers",ThreadPools.availableProcessors(),"standaloneTestContextQueue",false,"terraBlender",net.minecraftforge.fml.ModList.get().isLoaded("terrablender"),
            "floatPolicy","exact signed int raw IEEE754 bits; stable terrain and enum names",
            "directSemantics",DIRECT,"canonicalSemantics",CANONICAL);
        out.row("environment","java",System.getProperty("java.runtime.version"),"vm",System.getProperty("java.vm.name"),
            "os",System.getProperty("os.name"),"osVersion",System.getProperty("os.version"),"arch",System.getProperty("os.arch"),
            "processors",Runtime.getRuntime().availableProcessors(),"maxHeapBytes",Runtime.getRuntime().maxMemory(),
            "jvmArguments",ManagementFactory.getRuntimeMXBean().getInputArguments(),"warmupWorlds",1,"measuredWorlds",3,
            "warmupTileRepetitions",1,"measuredTileRepetitions",3,"observerOverhead","existing test-only cache LongAdder/timer observers active; not subtracted");
    }
    // Include fields omitted by the legacy codec, with stable field ordering.
    private static Object effective(Object value) throws Exception {
        if(value==null||value instanceof String||value instanceof Number||value instanceof Boolean)return value;
        if(value instanceof Enum<?> e)return e.name();
        if(value instanceof Map<?,?> map) {Map<String,Object> sorted=new TreeMap<>();for(var entry:map.entrySet())sorted.put(entry.getKey().toString(),effective(entry.getValue()));return sorted;}
        if(value instanceof Iterable<?> list) {List<Object> values=new ArrayList<>();for(var entry:list)values.add(effective(entry));return values;}
        Map<String,Object> result=new TreeMap<>();
        for(var f:value.getClass().getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&!f.isSynthetic()) {
            f.setAccessible(true);result.put(f.getName(),effective(f.get(value)));
        }
        return result;
    }
    private Map<String,Object> sample(GeneratorContext c,int x,int z,boolean exact) {
        Cell cell=new Cell();
        if(exact)c.lookup.applyCell(cell,x,z,true,true);else c.lookup.sampleDirectApproximate(cell,x,z,true);
        return Evidence.cell(cell,c.generator.getHeightmap());
    }
    private void golden() throws Exception {
        var fixtureFile=java.nio.file.Path.of(System.getProperty("task1c.fixtureFile"));
        var manifest=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(fixtureFile)).getAsJsonObject();
        if(!manifest.get("configurationFingerprint").getAsString().equals(Evidence.hash(effective(preset))))throw new IllegalStateException("Frozen fixture configuration mismatch");
        List<ReproductionSuite.Point> collisionPoints=null;
        Map<String,Map<String,Object>> first=new TreeMap<>();
        for(long seed:SEEDS) {
            var c=context(seed);List<ReproductionSuite.Point> points=new ArrayList<>();
            for(var row:manifest.getAsJsonArray("seedFixtures"))if(row.getAsJsonObject().get("seed").getAsLong()==seed) {
                for(var point:row.getAsJsonObject().getAsJsonArray("points"))points.add(Evidence.JSON.fromJson(point,ReproductionSuite.Point.class));
                out.row("fixtures","seed",Long.toString(seed),"points",points,"discovery","Frozen fixture_manifest.json v1; no rediscovery");
            }
            if(points.size()!=85)throw new IllegalStateException("Missing fixed seed coordinates: "+seed);
            if(collisionPoints==null)collisionPoints=points;
            int ordinal=0;
            for(var p:points) {
                var raw=sample(c,p.x(),p.z(),false);var filtered=sample(c,p.x(),p.z(),true);
                out.row("canonical_geography","seed",Long.toString(seed),"ordinal",ordinal,"point",p,"semantics",CANONICAL,"fields",filtered);
                out.row("legacy_direct_samples","seed",Long.toString(seed),"ordinal",ordinal,"point",p,"semantics",DIRECT,"fields",raw);
                out.row("cached_direct_comparison","seed",Long.toString(seed),"ordinal",ordinal++,"point",p,"differentFields",Evidence.different(raw,filtered));
                var hints=new TreeMap<String,Object>();filtered.forEach((k,v)->{if(k.startsWith("hint_")||Set.of("biome","biomeRegionId","temperature","moisture","terrain").contains(k))hints.put(k,v);});
                out.row("biome_hints","seed",Long.toString(seed),"point",p,"semantics",CANONICAL,"fields",hints);
                var terrain=c.cache.provide(p.x()>>7,p.z()>>7).lookup(p.x(),p.z()).terrain;
                out.row("terrain_traits","seed",Long.toString(seed),"point",p,"terrain",terrain.getName(),"category",terrain.getCategory().name(),
                    "river",terrain.isRiver(),"lake",terrain.isLake(),"wetland",terrain.isWetland(),"mountain",terrain.isMountain(),
                    "deepOcean",terrain.isDeepOcean(),"shallowOcean",terrain.isShallowOcean(),"semantics",CANONICAL);
            }
            // Collision is legacy-specific; never activate it for a future backend.
            if(seed==SEEDS[0]||seed==SEEDS[1])for(int i=0;i<collisionPoints.size();i++) {
                var p=collisionPoints.get(i);var fields=sample(c,p.x(),p.z(),true);
                if(seed==SEEDS[0])first.put(""+i,fields);else out.row("seed_collision","backend","LEGACY_RTF_V0","seedA",Long.toString(SEEDS[0]),"seedB",Long.toString(seed),
                    "ordinal",i,"point",p,"semantics",CANONICAL,"a",first.get(""+i),"b",fields,"differentFields",Evidence.different(first.get(""+i),fields),"futureBackendAssertion","inactive until Task 2");
            }
            c.cache.close();
        }
        // Real Minecraft holder resolution, with the live world's full seed and loaded registries.
        if(level==null)return;
        var source=level.getChunkSource().getGenerator().getBiomeSource();var rs=level.getChunkSource().randomState();
        for(var p:collisionPoints)out.row("minecraft_biomes","seed",Long.toString(level.getSeed()),"point",p,"quartY",20,
            "semantics","legacy MultiNoiseBiomeSource query; not a filtered-geography resolver","biome",source.getNoiseBiome(p.x()>>2,20,p.z()>>2,rs.sampler()).unwrapKey().orElseThrow().location().toString());
    }
    private static String digest(Tile tile) {
        List<Object> cells=new ArrayList<>();tile.iterate((c,x,z)->cells.add(Evidence.cell(c,null)));return Evidence.hash(cells);
    }
    private void scheduling() {
        int[][] grid={{0,0},{0,-1},{1,0},{0,1},{-1,0}};
        for(long seed:SEEDS)for(String order:List.of("center_first","reverse","parallel")) {
            var c=context(seed);List<int[]> positions=new ArrayList<>(List.of(grid));if(order.equals("reverse"))Collections.reverse(positions);
            List<CompletableFuture<Tile>> futures=new ArrayList<>();
            for(int[] p:positions){var f=c.generator.generate(p[0],p[1]);futures.add(f);if(!order.equals("parallel"))f.join();}
            for(int i=0;i<positions.size();i++) {
                Tile t=futures.get(i).join();int[] p=positions.get(i);
                out.row("tile_digests","seed",Long.toString(seed),"tileX",p[0],"tileZ",p[1],"workers",ThreadPools.availableProcessors(),"order",order,
                    "semantics",CANONICAL,"coreCells",16384,"sha256",digest(t));
                // New Task 3 capture has its own digest; NEVER change the frozen legacy field digest.
                List<Object> mountains=new ArrayList<>();
                t.iterate((cell,x,z)->mountains.add(new int[]{Float.floatToRawIntBits(cell.mountainChainSelector()),
                    Float.floatToRawIntBits(cell.mountainChainContribution()),Float.floatToRawIntBits(cell.regionalMountainContribution())}));
                out.row("mountain_tile_digests","seed",Long.toString(seed),"tileX",p[0],"tileZ",p[1],"workers",ThreadPools.availableProcessors(),
                    "order",order,"semantics","captured pre-carving population weights transported with finalized tile","sha256",Evidence.hash(mountains));
                t.close();
            }
            c.cache.close();
        }
    }
    private static Map<Long,Long> allocations() {
        Map<Long,Long> result=new HashMap<>();
        if(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean b&&b.isThreadAllocatedMemorySupported()) {
            if(!b.isThreadAllocatedMemoryEnabled())b.setThreadAllocatedMemoryEnabled(true);
            long[] ids=b.getAllThreadIds(),values=b.getThreadAllocatedBytes(ids);
            for(int i=0;i<ids.length;i++)if(values[i]>=0)result.put(ids[i],values[i]);
        }
        return result;
    }
    private long allocatedDelta(Map<Long,Long> before) {
        var after=allocations();long sum=0;
        lastWorkerAllocatedBytes=0;
        for(var e:after.entrySet()) {
            long delta=Math.max(0,e.getValue()-before.getOrDefault(e.getKey(),0L));sum+=delta;
            if(workerIds.contains(e.getKey())||e.getKey()==Thread.currentThread().getId())lastWorkerAllocatedBytes+=delta;
        }
        return sum;
    }
    private void allocationProbe() throws Exception {
        // Discover the existing executor's workers, never replace its factory/pool.
        int count=ThreadPools.availableProcessors();var ready=new java.util.concurrent.CountDownLatch(count);var release=new java.util.concurrent.CountDownLatch(1);
        for(int i=0;i<count;i++)ThreadPools.WORLD_GEN.submit(()->{workerIds.add(Thread.currentThread().getId());ready.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        try {if(!ready.await(30,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Worker discovery timeout");} finally {release.countDown();}
        var c=context(SEEDS[0]);
        for(int repeat=-1;repeat<3;repeat++) {
            var before=allocations();long start=System.nanoTime();var tile=c.generator.generate(80,-80).join();long ns=System.nanoTime()-start;long process=allocatedDelta(before);
            out.row("allocation_probe","repeat",repeat,"warmup",repeat<0,"seed",Long.toString(SEEDS[0]),"tileX",80,"tileZ",-80,"elapsedNs",ns,
                "rtfWorkerCount",workerIds.size(),"rtfWorkersAndCallerAllocatedBytes",lastWorkerAllocatedBytes,"processThreadAllocatedBytes",process,
                "scope","ThreadMXBean worker+caller deltas; includes observer/measurement allocations and any other RTF tasks on that executor; excludes renderer and Minecraft chunk workers");tile.close();
        }
        pools(c,"allocation probe completed");
    }
    private void measurement(String operation,int repeat,int index,long ns,long allocated,Map<String,Long> before,Object... extra) {
        Map<String,Long> delta=new TreeMap<>();Metrics.snapshot().forEach((k,v)->delta.put(k,v-before.getOrDefault(k,0L)));
        out.row("benchmark_runs","operation",operation,"repeat",repeat,"index",index,"warmup",repeat<0,"elapsedNs",ns,
            "processThreadAllocatedBytes",allocated,"allocationScope","all live JVM threads, includes client/background work; not retained memory",
            "cacheObserverDelta",delta,"details",extra);
    }
    private void pools(GeneratorContext c,String phase) throws Exception {
        int entries=((raccoonman.reterraforged.concurrent.cache.map.LongMap<?>)Evidence.field(Evidence.field(c.cache,"cache"),"map")).size();
        out.row("cache_metrics","phase",phase,"cellPool",((ArrayPool<?>)Evidence.field(c.generator,"cellPool")).statistics(),
            "chunkPool",((ArrayPool<?>)Evidence.field(c.generator,"chunkPool")).statistics(),"heapUsedBytes",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),
            "cachedEntries",entries,"publishedCellCountUpperBound",entries*25600L,
            "memoryScope","whole JVM instantaneous used heap, not retained cache bytes; pending entries may not yet own published cells");
    }
    private void pointsAndTiles() throws Exception {
        var c=context(SEEDS[0]);Cell cell=new Cell();
        for(int repeat=-3;repeat<5;repeat++)for(String operation:List.of("raw_heightmap_point","direct_approximate_point","canonical_warm_point","cached_opportunistic_point","warm_tile_lookup")) {
            c.cache.provide(0,0);c.cache.provide(-1,-1);
            // Batched throughput measurements plus individual latency, outside serialization.
            final int queries=65536;
            long[] times=new long[4096];var metrics=Metrics.snapshot();var alloc=allocations();long start=System.nanoTime();float consume=0;
            for(int i=0;i<queries;i++) {
                int x=(i*37&127),z=(i*71&127);long t=System.nanoTime();cell.reset();
                if(operation.equals("raw_heightmap_point"))c.generator.getHeightmap().apply(cell,x,z,true);
                else if(operation.equals("direct_approximate_point"))c.lookup.sampleDirectApproximate(cell,x,z,true);
                else if(operation.equals("cached_opportunistic_point"))c.lookup.applyCell(cell,x,z,false,true);
                else if(operation.equals("warm_tile_lookup"))cell.copyFrom(c.cache.provide(x>>7,z>>7).lookup(x,z));
                else c.lookup.applyCell(cell,x,z,true,true);
                long elapsed=System.nanoTime()-t;if((i&15)==0)times[i>>4]=elapsed;consume+=cell.height;
            }
            long ns=System.nanoTime()-start,bytes=allocatedDelta(alloc);sink=consume;
            measurement(operation,repeat,0,ns,bytes,metrics,"queries",queries,"latencySampling","every 16th query","xFormula","i*37 & 127","zFormula","i*71 & 127");
            out.row("query_latencies","operation",operation,"repeat",repeat,"warmup",repeat<0,"nanoseconds",times);
        }
        for(String operation:List.of("filtered_tile","cold_cache_tile","cold_exact_point"))for(int repeat=-1;repeat<3;repeat++) {
            var fresh=context(SEEDS[0]);
            for(int i=0;i<5;i++) {
                int x=80+i,z=-80;var metrics=Metrics.snapshot();var alloc=allocations();long start=System.nanoTime();Tile tile=null;
                if(operation.equals("filtered_tile"))tile=fresh.generator.generate(x,z).join();
                else if(operation.equals("cold_cache_tile"))tile=fresh.cache.provide(x,z);
                else fresh.lookup.applyCell(cell,x<<7,z<<7,true,true);
                long ns=System.nanoTime()-start,bytes=allocatedDelta(alloc);
                measurement(operation,repeat,i,ns,bytes,metrics,"tileX",x,"tileZ",z,"queue",false);
                if(tile!=null)sink=tile.lookup(x<<7,z<<7).height;
                if(tile!=null&&operation.equals("filtered_tile"))tile.close();
            }
            pools(fresh,operation+" repeat "+repeat+" before shutdown");fresh.cache.close();pools(fresh,operation+" repeat "+repeat+" after shutdown");
        }
        // Explicit warm tile lookup and actual eviction/reload counters, outside point timings.
        var before=Metrics.snapshot();for(int i=0;i<64;i++)c.cache.drop(0,0);
        boolean evicted=c.cache.provideIfPresent(0,0)==null;c.cache.provide(0,0);
        out.row("cache_lifecycle","evictedAfter64Drops",evicted,"before",before,"after",Metrics.snapshot());
    }
    private void chunks(int worldIndex) throws Exception {
        // Identical fresh worlds: first discarded as warmup; then three measured worlds.
        int repeat=worldIndex-1;
        for(String workload:List.of("cold_exploration","sequential_adjacent","scattered","warmed_neighbor_terrain")) {
            int base=switch(workload){case "cold_exploration"->1024;case "sequential_adjacent"->2048;case "scattered"->3072;default->4096;};
            var context=((RTFRandomState)(Object)level.getChunkSource().randomState()).generatorContext();
            if(workload.equals("warmed_neighbor_terrain"))context.cache.provideAtChunk(base,base);
            long[] latency=new long[8];int preexisting=0;var metrics=Metrics.snapshot();var alloc=allocations();long start=System.nanoTime();
            for(int i=0;i<8;i++) {
                int x=base+(workload.equals("scattered")?i*64:i),z=base+(workload.equals("cold_exploration")?i:0);
                if(level.getChunkSource().hasChunk(x,z))preexisting++;
                long t=System.nanoTime();var chunk=level.getChunk(x,z);latency[i]=System.nanoTime()-t;
                if(chunk.getStatus()!=ChunkStatus.FULL)throw new IllegalStateException("Expected FULL");
            }
            long ns=System.nanoTime()-start,bytes=allocatedDelta(alloc);
            measurement("FULL_"+workload,repeat,0,ns,bytes,metrics,"chunks",8,"baseChunk",base,"preexistingFull",preexisting);
            out.row("chunk_latencies","operation","FULL_"+workload,"repeat",repeat,"warmup",repeat<0,"nanoseconds",latency);
            pools(context,"FULL_"+workload+" repeat "+repeat);
        }
    }
    private void variance(int worldIndex) throws Exception {
        int[][] positions={{0,0},{-129,-129},{127,127},{128,128},{-4032,-4096},{-2688,-4096},{-64,-4096},{3392,-3072}};
        for(int[] p:positions)for(var requested:List.of(ChunkStatus.NOISE,ChunkStatus.SURFACE,ChunkStatus.CARVERS,ChunkStatus.FEATURES,ChunkStatus.FULL)) {
            var chunk=level.getChunkSource().getChunk(p[0]>>4,p[1]>>4,requested,true);
            List<String> blocks=new ArrayList<>();
            for(int z=0;z<16;z++)for(int x=0;x<16;x++)for(int y=level.getMinBuildHeight();y<level.getMaxBuildHeight();y++)
                blocks.add(chunk.getBlockState(new BlockPos((p[0]>>4<<4)+x,y,(p[1]>>4<<4)+z)).toString());
            String file="variance_blocks/"+worldIndex+"_"+(p[0]>>4)+"_"+(p[1]>>4)+"_"+requested.toString().replace(':','_')+".json.gz";
            out.snapshot(file,blocks);
            out.row("variance_stages","repeat",worldIndex,"seed",Long.toString(level.getSeed()),"chunkX",p[0]>>4,"chunkZ",p[1]>>4,
                "requestedStatus",requested.toString(),"actualStatus",chunk.getStatus().toString(),"snapshot",file,"sha256",Evidence.hash(blocks),
                "minY",level.getMinBuildHeight(),"maxY",level.getMaxBuildHeight(),"order","z,x,y; y fastest",
                "limitation","neighbor prerequisites and already generated spawn chunks may have advanced beyond requested status; not feature attribution");
            if(requested==ChunkStatus.FULL) {
                var context=((RTFRandomState)(Object)level.getChunkSource().randomState()).generatorContext();
                List<String> biomes=new ArrayList<>();
                for(int z=0;z<4;z++)for(int x=0;x<4;x++)for(int y=level.getMinBuildHeight()>>2;y<level.getMaxBuildHeight()>>2;y++)
                    biomes.add(chunk.getNoiseBiome((p[0]>>4<<2)+x,y,(p[1]>>4<<2)+z).unwrapKey().orElseThrow().location().toString());
                out.row("variance_geography","repeat",worldIndex,"seed",Long.toString(level.getSeed()),"chunkX",p[0]>>4,"chunkZ",p[1]>>4,
                    "exact",sample(context,p[0],p[1],true),"storedBiomeSHA256",Evidence.hash(biomes));
            }
        }
    }
}
