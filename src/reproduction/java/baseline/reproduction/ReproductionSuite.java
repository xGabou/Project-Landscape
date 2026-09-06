/* Original Task 1A reproduction/measurement harness. All Rights Reserved.
 * Calls inherited implementation; never shipped in the production jar. */
package baseline.reproduction;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.*;
import raccoonman.reterraforged.RTFCommon;
import raccoonman.reterraforged.concurrent.*;
import raccoonman.reterraforged.concurrent.cache.*;
import raccoonman.reterraforged.concurrent.pool.*;
import raccoonman.reterraforged.data.worldgen.preset.settings.Preset;
import raccoonman.reterraforged.registries.RTFRegistries;
import raccoonman.reterraforged.world.worldgen.*;
import raccoonman.reterraforged.world.worldgen.cell.*;
import raccoonman.reterraforged.world.worldgen.cell.terrain.*;
import raccoonman.reterraforged.world.worldgen.densityfunction.*;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.*;
import raccoonman.reterraforged.world.worldgen.noise.module.Noise;
import raccoonman.reterraforged.world.worldgen.noise.module.Noises;
import raccoonman.reterraforged.world.worldgen.util.PosUtil;

public final class ReproductionSuite {
    public record Point(String label, int x, int z) {}
    private final Evidence out;
    private final Preset preset;
    private final HolderGetter<Noise> noises;
    private final long[] seeds;
    private final int repeats = Integer.getInteger("task1a.repeats", 3);
    private final List<GeneratorContext> owned = new ArrayList<>();
    public ReproductionSuite(ServerLevel level, Evidence out, long[] seeds) {
        this.out = out; this.seeds = seeds;
        this.preset = ((RTFRandomState)(Object)level.getChunkSource().randomState()).preset();
        this.noises = level.registryAccess().lookupOrThrow(RTFRegistries.NOISE);
    }
    private GeneratorContext context(long seed, int size, int border, int batch) {
        var c = GeneratorContext.makeUncached(preset, noises, (int)seed, size, border, batch);
        c.cache = new TileCache(size, false, c.generator);
        c.lookup = new raccoonman.reterraforged.world.worldgen.cell.heightmap.WorldLookup(c);
        owned.add(c);
        out.row("contexts", "id", "isolated-"+owned.size(), "requestedLongSeed", seed, "effectiveIntSeed", (int)seed,
            "registrySource", "live minecraft:overworld", "size", size,"border",border,"batch",batch,"queue",false);
        return c;
    }
    private GeneratorContext context(long seed) { return context(seed, 3, 1, 6); }
    private Map<String,Object> raw(GeneratorContext c, Point p) {
        long start = System.nanoTime(), allocation = allocated();
        Cell cell = new Cell(); c.generator.getHeightmap().apply(cell, p.x, p.z, true);
        out.row("measurements", "operation", "raw_point", "x", p.x, "z", p.z, "elapsedNs", System.nanoTime()-start, "callerAllocatedBytes", delta(allocation));
        return Evidence.cell(cell, c.generator.getHeightmap());
    }
    private Map<String,Object> lookup(GeneratorContext c, Point p, boolean load, boolean climate) {
        Cell cell = new Cell(); long allocation = allocated(), start = System.nanoTime();
        boolean cached = c.lookup.applyCell(cell, p.x, p.z, load, climate);
        out.row("measurements", "operation", load ? "exact_tile_query" : "cache_or_direct_query", "cachedResult", cached,
            "x", p.x, "z", p.z, "elapsedNs", System.nanoTime()-start, "callerAllocatedBytes", delta(allocation), "workerAllocationsIncluded", false);
        return Evidence.cell(cell, c.generator.getHeightmap());
    }
    private static long allocated() {
        var bean = ManagementFactory.getThreadMXBean();
        return bean instanceof com.sun.management.ThreadMXBean b && b.isThreadAllocatedMemorySupported() ? b.getThreadAllocatedBytes(Thread.currentThread().getId()) : -1;
    }
    private static long delta(long before) { return before < 0 ? -1 : allocated()-before; }
    private void evict(GeneratorContext c, Point p) { for(int i=0;i<64;i++) c.cache.drop(p.x>>7,p.z>>7); }
    public void run(ServerLevel level, boolean full) throws Exception {
        out.row("environment", "baseline", "e9dd8841a1b4a95e4bb2b23e084d40abbc1bea70", "java", System.getProperty("java.runtime.version"),
            "processors", Runtime.getRuntime().availableProcessors(), "worldgenWorkers", ThreadPools.availableProcessors(), "repeats", repeats,
            "seedSuite", seeds, "dimension", level.dimension().location().toString(), "preset", "loaded Legacy Default",
            "tileSize", 3, "tileBorderChunks", 1, "batchCount", 6, "fieldEncoding", "float raw IEEE754 signed int bits; stable terrain/enum names; no golden values");
        out.row("preset", "value", Preset.DIRECT_CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,preset).getOrThrow(false,RTFCommon.LOGGER::error));
        try {
            if(full) {
                sampling(); crossWorld(); noiseOwnership(level); boundsAndLifetime(); resources(); missingContext(level);
            }
            scheduling();
        } finally {
            // Test-owned contexts only: stop their maintenance timers. Do not reset production caches.
            for(var c:owned) ((Cache<?>)Evidence.field(c.cache,"cache")).close();
            out.row("closed_cache_ownership", "closedTestContexts", owned.size(), "globalManagerStillContainsFirstClosedCache",
                !owned.isEmpty() && ((List<?>)Evidence.field(CacheManager.class,"CACHES")).contains(Evidence.field(owned.get(0).cache,"cache")));
            out.flush();
        }
    }
    private List<Point> suite(GeneratorContext c, long seed) {
        List<Point> points = new ArrayList<>();
        int[] axes = {-129,-128,-127,-17,-16,-15,-5,-4,-3,-1,0,1,3,4,5,15,16,17,63,64,65,126,127,128,129};
        for(int n:axes) {points.add(new Point("diagonal_boundary",n,n)); points.add(new Point("x_boundary",n,0)); points.add(new Point("z_boundary",0,n));}
        for (String token : System.getProperty("task1a.points", "").split(",")) if (!token.isBlank()) {
            String[] pair=token.split(":");points.add(new Point("requested",Integer.parseInt(pair[0]),Integer.parseInt(pair[1])));
        }
        Map<String,Point> locations = new TreeMap<>(); Cell prior = null;
        for(int z=-4096;z<=4096 && locations.size()<10;z+=64) for(int x=-4096;x<=4096 && locations.size()<10;x+=64) {
            if(x==-4096)prior=null;
            Cell cell=new Cell(); c.generator.getHeightmap().apply(cell,x,z,true);
            String type=cell.terrain.getName();
            String category=cell.terrain.isDeepOcean()?"deep_ocean":cell.terrain.isShallowOcean()?"shallow_ocean":cell.terrain.isRiver()?"river":cell.terrain.isMountain()?"mountain":
                cell.terrain==TerrainType.COAST||cell.terrain==TerrainType.BEACH?"coast":type.contains("plateau")?"plateau":type.contains("flats")?"plains":"";
            if(!category.isEmpty()) locations.putIfAbsent(category,new Point(category,x,z));
            if(cell.terrain.isRiver() && cell.continentEdge < c.generator.getHeightmap().controlPoints().inland()) locations.putIfAbsent("river_mouth_proxy",new Point("river_mouth_proxy",x,z));
            if(prior!=null && Float.floatToRawIntBits(prior.terrainRegionId)!=Float.floatToRawIntBits(cell.terrainRegionId)) locations.putIfAbsent("region_transition",new Point("region_transition",x,z));
            if(prior!=null && prior.terrain.isMountain()!=cell.terrain.isMountain()) locations.putIfAbsent("mountain_edge",new Point("mountain_edge",x,z));
            prior=cell;
        }
        points.addAll(locations.values());
        out.row("fixtures","seed",seed,"points",points,"discovery","row-major -4096..4096 step64; first category match; mouth=river below inland continent threshold, approximate not solved mouth; edges are adjacent scan changes","categoriesFound",locations.keySet());
        return points;
    }
    private void sampling() {
        List<Point> collisionPoints = null;
        Map<Long,List<Map<String,Object>>> collisions=new LinkedHashMap<>();
        for(long seed:seeds) {
            RTFCommon.LOGGER.info("TASK1A sampling seed={}",seed);
            var c=context(seed); List<Point> points=suite(c,seed);
            if(collisionPoints==null)collisionPoints=points;
            List<Map<String,Object>> seedSamples=new ArrayList<>();
            for(Point p:collisionPoints) seedSamples.add(raw(c,p));
            collisions.put(seed,seedSamples);
            // Group cache phases by tile: exact same order for every comparison, no accidental warmth.
            Map<Long,List<Point>> groups=new TreeMap<>();
            for(Point p:points) groups.computeIfAbsent(PosUtil.pack(p.x>>7,p.z>>7),k->new ArrayList<>()).add(p);
            for(var group:groups.values()) {
                Map<Point,Map<String,Object>> direct=new LinkedHashMap<>();
                for(Point p:group) direct.put(p,lookup(c,p,false,true));
                Point first=group.get(0); c.cache.provide(first.x>>7,first.z>>7);
                Map<Point,Map<String,Object>> filtered=new LinkedHashMap<>();
                for(Point p:group) {
                    var b=lookup(c,p,false,true); var warm=lookup(c,p,false,true); filtered.put(p,b);
                    out.row("cached_uncached","seed",seed,"point",p,"direct",direct.get(p),"filtered",b,"directVsFiltered",Evidence.different(direct.get(p),b),"filteredVsWarm",Evidence.different(b,warm));
                }
                evict(c,first);
                for(Point p:group) {var d=lookup(c,p,false,true); out.row("eviction_reload","seed",seed,"point",p,"afterDrop",d,"directVsAfterDrop",Evidence.different(direct.get(p),d));}
                c.cache.provide(first.x>>7,first.z>>7);
                for(Point p:group) {var r=lookup(c,p,false,true);out.row("eviction_reload","seed",seed,"point",p,"filteredVsRegenerated",Evidence.different(filtered.get(p),r));}
                evict(c,first);
            }
        }
        for(int a=0;a<seeds.length;a++) for(int b=a+1;b<seeds.length;b++) for(int i=0;i<collisionPoints.size();i++) {
            var left=collisions.get(seeds[a]).get(i);var right=collisions.get(seeds[b]).get(i);
            out.row("seed_collisions","seedA",seeds[a],"seedB",seeds[b],"sameLow32",(int)seeds[a]==(int)seeds[b],"point",collisionPoints.get(i),"a",left,"b",right,"differentFields",Evidence.different(left,right));
        }
    }
    private Map<String,Object> density(GeneratorContext c, Point p) {
        Map<String,Object> result=new TreeMap<>();
        for(var f:CellSampler.Field.values())result.put(f.name(),Double.doubleToRawLongBits(new CellSampler(()->c.lookup,f).compute(new DensityFunction.SinglePointContext(p.x,80,p.z))));
        return result;
    }
    private void crossSequence(String label, GeneratorContext a, GeneratorContext b) {
        Point p=new Point("P",127,127), other=new Point("invalidate_by_position",128,129);
        density(a,other);var aa=density(a,p);var contaminated=density(b,p);var again=density(a,p);
        density(b,other);var bb=density(b,p);
        out.row("cross_world_cache","sequence",label,"thread",Thread.currentThread().getName(),"point",p,"a",aa,"bAfterA",contaminated,"aAgain",again,"bAfterPositionChange",bb,
            "bContaminationFields",Evidence.different(contaminated,bb),"aToBFields",Evidence.different(aa,contaminated));
    }
    private void crossWorld() throws Exception {
        var a=context(seeds[0]);var b=context(42);
        crossSequence("A-B-A",a,b);crossSequence("B-A-B",b,a);
        ExecutorService one=Executors.newSingleThreadExecutor();ExecutorService two=Executors.newSingleThreadExecutor();
        try {
            one.submit(()->{crossSequence("worker1 A-B-A",a,b);}).get();
            var fresh=two.submit(()->density(b,new Point("P",127,127))).get();
            out.row("cross_world_cache","sequence","fresh other worker B","values",fresh);
        }finally{one.shutdown();two.shutdown();}
        var cache=new CellSampler.Cache2d();Point p=new Point("mode_key",125,-127);
        var noClimate=Evidence.cell(cache.getAndUpdate(a.lookup,p.x,p.z,false),a.generator.getHeightmap());
        var withClimate=Evidence.cell(cache.getAndUpdate(a.lookup,p.x,p.z,true),a.generator.getHeightmap());
        var fresh=Evidence.cell(new CellSampler.Cache2d().getAndUpdate(a.lookup,p.x,p.z,true),a.generator.getHeightmap());
        out.row("cross_world_cache","sequence","same context false-true climate","noClimate",noClimate,"thenClimate",withClimate,"freshClimate",fresh,"differentFields",Evidence.different(withClimate,fresh));
        var warming=context(seeds[0]);Point warmPoint=new Point("same_position_cache_transition",3,3);
        density(warming,new Point("invalidate",4,4));var before=density(warming,warmPoint);
        warming.cache.provide(0,0);var stale=density(warming,warmPoint);
        density(warming,new Point("invalidate",4,4));var refreshed=density(warming,warmPoint);
        out.row("cross_world_cache","sequence","same context tile becomes warm","beforeTile",before,"afterTileSamePosition",stale,"afterPositionChange",refreshed,
            "beforeVsStale",Evidence.different(before,stale),"staleVsRefreshed",Evidence.different(stale,refreshed));
    }
    private void noiseOwnership(ServerLevel level) {
        Noise n=Noises.simplex(0,100,3); Noise cache=Noises.cache2d(n);
        for(int seed:new int[]{123,456,123}) out.row("noise_cache","case","same instance P changing compute seed","seed",seed,"cached",Float.floatToRawIntBits(cache.compute(17,29,seed)),"uncached",Float.floatToRawIntBits(n.compute(17,29,seed)),"newGraph",Float.floatToRawIntBits(Noises.cache2d(n).compute(17,29,seed)));
        var registry=level.registryAccess().registryOrThrow(RTFRegistries.NOISE);
        registry.holders().forEach(holder->{
            AtomicInteger cacheNodes=new AtomicInteger();
            Noise shared=holder.value().mapAll(node->{if(node instanceof raccoonman.reterraforged.world.worldgen.noise.module.Cache2d)cacheNodes.incrementAndGet();return node;});
            Holder<Noise> copiedHolder=Holder.direct(shared);
            for(int[] pos:new int[][]{{17,29},{100,200},{-2048,3072}}) {
            double a=new NoiseFunction(copiedHolder,123).compute(new DensityFunction.SinglePointContext(pos[0],80,pos[1]));
            double b=new NoiseFunction(copiedHolder,456).compute(new DensityFunction.SinglePointContext(pos[0],80,pos[1]));
            double fresh=shared.mapAll(node->node).compute(pos[0],pos[1],456);
            out.row("noise_cache","case","registered graph copied then shared across NoiseFunctions","key",holder.key().location().toString(),"x",pos[0],"z",pos[1],"cacheNodes",cacheNodes.get(),"a",Double.doubleToRawLongBits(a),"bAfterA",Double.doubleToRawLongBits(b),"bFreshGraph",Double.doubleToRawLongBits(fresh),"contaminated",Double.isFinite(b)&&Double.isFinite(fresh)&&Double.doubleToRawLongBits(b)!=Double.doubleToRawLongBits(fresh));
            // Non-finite registry noise results are not evidence of seed-cache contamination.
            out.row("noise_cache_bits", "key", holder.key().location().toString(),"x",pos[0],"z",pos[1],"cacheNodes",cacheNodes.get(),"finite",Double.isFinite(b)&&Double.isFinite(fresh),"differentBits",Double.doubleToRawLongBits(b)!=Double.doubleToRawLongBits(fresh));
            }
        });
    }
    private void boundsAndLifetime() throws Exception {
        var c=context(seeds[0]);var tile=c.cache.provide(0,0);int total=tile.getBlockSize().total();
        int[][] coords={{-1,1},{total,0},{total,1},{0,-1},{1,total},{-1,-1},{total,total},{0,0},{total-1,total-1},{total*2,0},{-total,1}};
        for(int[] q:coords) {
            int index=tile.getBlockSize().indexOf(q[0],q[1]);Cell got=tile.getCellRaw(q[0],q[1]);
            out.row("tile_bounds","x",q[0],"z",q[1],"total",total,"index",index,"axisInBounds",q[0]>=0&&q[0]<total&&q[1]>=0&&q[1]<total,"absent",got.isAbsent(),"aliasedX",got.isAbsent()?null:Math.floorMod(index,total),"aliasedZ",got.isAbsent()?null:index/total);
        }
        Cell ref=tile.lookup(0,0);Tile.Chunk reader=tile.getChunkReader(0,0);var before=Evidence.cell(ref,null);
        Object entry=((Cache<?>)Evidence.field(c.cache,"cache")).get(PosUtil.pack(0,0));
        for(int i=0;i<63;i++)c.cache.drop(0,0);
        out.row("lifecycle","case","63 drops","stillPublished",c.cache.provideIfPresent(0,0)==tile,"referenceUnchanged",before.equals(Evidence.cell(ref,null)));
        ExecutorService retainedReader=Executors.newSingleThreadExecutor();CountDownLatch mayRead=new CountDownLatch(1);
        Future<Map<String,Object>> later=retainedReader.submit(()->{mayRead.await();return Evidence.cell(reader.getCell(0,0),null);});
        c.cache.drop(0,0);var after=Evidence.cell(ref,null);var replacement=c.generator.generate(2,0).join();
        mayRead.countDown();
        try{out.row("lifecycle","case","worker retained reader across release","changed",!before.equals(later.get()),"observed",later.get());}finally{retainedReader.shutdown();}
        out.row("lifecycle","case","64 drops then pool reuse","before",before,"afterRelease",after,"afterReuse",Evidence.cell(ref,null),"sameArray",tile.getBacking()==replacement.getBacking(),"readerStillReferencesRecycledCell",reader.getCell(0,0)==ref,"lazyEntryStillReturns",((CacheEntry<?>)entry).get()!=null);
        var replacementBefore=Evidence.cell(replacement.lookup(256,0),null);tile.close();
        out.row("lifecycle","case","stale Tile.close after pool reborrow","replacementChanged",!replacementBefore.equals(Evidence.cell(replacement.lookup(256,0),null)));
        replacement.close();
        var expiry=context(seeds[0]);var retained=expiry.cache.provide(0,0);Cache<?> cache=(Cache<?>)Evidence.field(expiry.cache,"cache");
        Object e=cache.get(0);Evidence.set(e,"timestamp",0L);cache.poll();
        out.row("lifecycle","case","TTL poll forced timestamp only","removed",expiry.cache.provideIfPresent(0,0)==null,"resourceStillOpen",((Resource<?>)Evidence.field(retained,"cacheResource")).isOpen(),"poolItems",((List<?>)Evidence.field(Evidence.field(expiry.generator,"cellPool"),"pool")).size());
        retained.close();
        var fail=context(seeds[0]);Object saved=Evidence.field(fail.generator,"filters");Evidence.set(fail.generator,"filters",null);
        try{fail.generator.generate(0,0).join();}catch(Throwable ex){out.row("lifecycle","case","test-only forced filter failure after allocation","error",Evidence.failure(ex),"returnedCellArrays",((List<?>)Evidence.field(Evidence.field(fail.generator,"cellPool"),"pool")).size());}
        finally{Evidence.set(fail.generator,"filters",saved);}
        out.row("lifecycle","case","global cache manager ownership","registeredCaches",((List<?>)Evidence.field(CacheManager.class,"CACHES")).size(),"retainsTestCache",((List<?>)Evidence.field(CacheManager.class,"CACHES")).contains(cache));
    }
    private void resources() throws Exception {
        for(int depth:new int[]{1,2,8,64}) {
            List<Resource<Cell>> resources=new ArrayList<>();Set<Cell> identities=Collections.newSetFromMap(new IdentityHashMap<>());
            boolean unique=true,restored=true;
            try{for(int i=0;i<depth;i++){var r=Cell.getResource();resources.add(r);Cell c=r.get();unique&=identities.add(c);c.height=i+1;}throw new IllegalArgumentException("test exception");}
            catch(IllegalArgumentException expected){}finally{for(int i=resources.size()-1;i>=0;i--){restored&=resources.get(i).get().height==i+1;resources.get(i).close();}}
            out.row("cell_resources","depth",depth,"unique",unique,"outerValuesPreserved",restored,"threadLocalClosed",!Cell.LOCAL.get().isOpen());
        }
        var pool=new ThreadLocalPool<Cell>(4,Cell::new,Cell::reset);var r=pool.get();var reserved=pool.get();r.close();r.close();var a=pool.get();var b=pool.get();
        out.row("cell_resources","case","isolated fallback pool double close","subsequentBorrowAliases",a.get()==b.get());
        ExecutorService exec=Executors.newFixedThreadPool(4);try{
            var barrier=new CyclicBarrier(4);List<Future<Cell>> futures=new ArrayList<>();
            for(int i=0;i<4;i++) futures.add(exec.submit(()->{try(var cell=Cell.getResource()){Cell value=cell.get();barrier.await();return value;}}));
            Set<Cell> set=Collections.newSetFromMap(new IdentityHashMap<>());for(var f:futures)set.add(f.get());out.row("cell_resources","case","concurrent normal resources","distinctCells",set.size());
        }finally{exec.shutdown();}
    }
    private void missingContext(ServerLevel level) throws Exception {
        var uncached=GeneratorContext.makeUncached(preset,noises,(int)seeds[0],3,1,6);
        try{uncached.lookup.applyCell(new Cell(),0,0,true);}catch(Throwable ex){out.row("missing_context","case","makeUncached public lookup","error",Evidence.failure(ex));}
        var settings=level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        RandomState uninitialized=RandomState.create(settings,level.registryAccess().lookupOrThrow(Registries.NOISE),seeds[0]);
        try{uninitialized.sampler().sample(100,20,200);out.row("missing_context","case","RandomState before initialize","result","returned");}catch(Throwable ex){out.row("missing_context","case","RandomState before initialize","error",Evidence.failure(ex));}
        ((RTFRandomState)(Object)uninitialized).initialize(level.registryAccess());
        out.row("missing_context","case","same state after initialize","contextPresent",((RTFRandomState)(Object)uninitialized).generatorContext()!=null);
        var emptyPresets=new MappedRegistry<Preset>(RTFRegistries.PRESET,com.mojang.serialization.Lifecycle.stable()).freeze();
        List<Registry<?>> values=new ArrayList<>();level.registryAccess().registries().forEach(entry->values.add(entry.key().equals(RTFRegistries.PRESET)?emptyPresets:entry.value()));
        var missing=RandomState.create(settings,level.registryAccess().lookupOrThrow(Registries.NOISE),seeds[0]);
        ((RTFRandomState)(Object)missing).initialize(new RegistryAccess.ImmutableRegistryAccess(values));
        try{missing.sampler().sample(137,20,249);out.row("missing_context","case","initialized with empty preset registry","result","returned");}
        catch(Throwable ex){out.row("missing_context","case","initialized with empty preset registry","error",Evidence.failure(ex));}
        for(var rule:level.registryAccess().registryOrThrow(RTFRegistries.STRUCTURE_RULE))out.row("missing_context","case","actual structure rule with missing context","result",rule.test(missing,BlockPos.ZERO));
        for(var dimension:level.getServer().getAllLevels())out.row("missing_context","case","actual dimension","dimension",dimension.dimension().location().toString(),"presetPresent",((RTFRandomState)(Object)dimension.getChunkSource().randomState()).preset()!=null,"contextPresent",((RTFRandomState)(Object)dimension.getChunkSource().randomState()).generatorContext()!=null);
        var c=context(seeds[0]);((Cache<?>)Evidence.field(c.cache,"cache")).close();
        out.row("missing_context","case","cache closed then query","result",lookup(c,new Point("after close",1,1),true,true));
    }
    private Map<String,Object> tileDigest(Tile tile) {
        List<Object> cells=new ArrayList<>();tile.iterate((c,x,z)->cells.add(Evidence.cell(c,null)));
        Map<String,Object> result=new TreeMap<>();result.put("hash",Evidence.hash(cells));result.put("cells",cells.size());return result;
    }
    private void scheduling() {
        int[][] grid={{0,0},{0,-1},{1,0},{0,1},{-1,0}};
        for(int repeat=0;repeat<repeats;repeat++) for(String order:List.of("center_first","reverse","north_first","south_first","east_first","west_first","clockwise","parallel")) {
            var c=context(seeds[0]);List<int[]> positions=new ArrayList<>(List.of(grid));
            if(order.equals("reverse"))Collections.reverse(positions);
            int first=switch(order){case "north_first"->1;case "east_first"->2;case "south_first"->3;case "west_first"->4;case "clockwise"->1;default->0;};
            if(first>0)Collections.rotate(positions,-first);
            List<CompletableFuture<Tile>> futures=new ArrayList<>();long start=System.nanoTime();
            for(int[] p:positions){var f=c.generator.generate(p[0],p[1]);futures.add(f);if(!order.equals("parallel"))f.join();}
            for(int i=0;i<futures.size();i++){Tile tile=futures.get(i).join();out.row("generation_order","repeat",repeat,"order",order,"tileX",positions.get(i)[0],"tileZ",positions.get(i)[1],"workers",ThreadPools.availableProcessors(),"digest",tileDigest(tile));tile.close();}
            out.row("measurements","operation","five_filtered_tiles_with_hashing","order",order,"elapsedNs",System.nanoTime()-start,"note","measurement plumbing only; includes canonicalization/hashing, not benchmark baseline");
        }
        List<Point> points=List.of(new Point("center",64,64),new Point("edge",127,64),new Point("corner",127,127),new Point("negative_edge",-129,-64));
        var haloContext=context(seeds[0]);Tile left=haloContext.generator.generate(0,0).join(),right=haloContext.generator.generate(1,0).join();
        for(int x=120;x<=135;x++)for(int z:new int[]{0,1,64,126,127}){
            var l=Evidence.cell(left.getCellRaw(x-left.getBlockX()+16,z-left.getBlockZ()+16),haloContext.generator.getHeightmap());
            var r=Evidence.cell(right.getCellRaw(x-right.getBlockX()+16,z-right.getBlockZ()+16),haloContext.generator.getHeightmap());
            out.row("tile_overlap","x",x,"z",z,"left",l,"right",r,"differentFields",Evidence.different(l,r));
        }
        left.close();right.close();
        Map<Point,Map<String,Object>> baseline=new HashMap<>();
        for(int[] config:new int[][]{{3,1,6},{3,1,1},{3,1,3},{3,1,12},{2,1,6},{4,1,6},{3,2,6},{3,0,6}}) {
            var c=context(seeds[0],config[0],config[1],config[2]);Map<Long,Tile> tiles=new HashMap<>();
            for(Point p:points){long k=PosUtil.pack(p.x>>(4+config[0]),p.z>>(4+config[0]));Tile t=tiles.computeIfAbsent(k,key->c.generator.generate(p.x>>(4+config[0]),p.z>>(4+config[0])).join());
                var sample=Evidence.cell(t.lookup(p.x,p.z),c.generator.getHeightmap());baseline.putIfAbsent(p,sample);
                out.row("tile_geometry","size",config[0],"border",config[1],"batch",config[2],"point",p,"sample",sample,"differentFromBaseline",Evidence.different(baseline.get(p),sample));}
            tiles.values().forEach(Tile::close);
        }
    }
}
