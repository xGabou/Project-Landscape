/* Original development cache experiment. All Rights Reserved. */
package baseline.reproduction.performance;

import com.gabou.atmospheregen.climate.*;
import com.gabou.atmospheregen.config.BaselineClimateConfig;
import com.gabou.atmospheregen.generation.seed.NamedSeedService;
import java.util.*;

/** Coordinates are obtained by executing the unchanged model. No simulated climate is reported. */
final class CacheReplay {
    record Point(int x,int z) { long tile(){return ((long)(x>>7)<<32)^((z>>7)&0xffffffffL);} }
    record Access(int query,Point point) {}
    static List<Access> trace(List<Point> queries,NamedSeedService seeds,BaselineClimateConfig.Planned config){
        var trace=new ArrayList<Access>();var model=new BaselineClimateModel(seeds,config);
        for(int i=0;i<queries.size();i++){
            int query=i;var p=queries.get(i);
            model.sample((x,z)->{trace.add(new Access(query,new Point(x,z)));return new ClimateGeography.Sample(0,0,0,0,
                    com.gabou.atmospheregen.api.geography.MacroGeographyProvider.MarineClass.MAJOR_OCEAN);},p.x,p.z);
        }
        return trace;
    }
    static Map<String,Object> compare(List<Access> trace){
        var result=new LinkedHashMap<String,Object>();
        result.put("A",replay(trace,1024,0));
        result.put("B",replay(trace,128,65536));
        var sorted=new ArrayList<>(trace);sorted.sort(Comparator.comparingLong(a->a.point.tile()));
        result.put("C",replay(sorted,128,0));
        // Offline whole-corpus tile-major lower bound, NOT a production streaming claim.
        result.put("D",replay(sorted,128,65536));
        result.put("scope","A/B streaming LRU replay; C/D whole-corpus tile-major extraction lower bounds; load counts, not timings");
        return result;
    }
    static Map<String,Object> replay(List<Access> trace,int tileCapacity,int sampleCapacity){
        var tiles=new LinkedHashMap<Long,Integer>(16,.75f,true);
        var samples=new LinkedHashMap<Point,Boolean>(16,.75f,true);
        var last=new HashMap<Long,Integer>();var previousKeys=new ArrayList<Long>();var reused=new HashSet<Long>();
        var hitCounts=new HashMap<Long,Integer>();var lifetimes=new ArrayList<Integer>();var reuseDistances=new ArrayList<Integer>();
        long hits=0,misses=0,evictions=0,sampleHits=0,evictedUnused=0;
        for(int i=0;i<trace.size();i++){
            Point p=trace.get(i).point;long key=p.tile();
            if(sampleCapacity>0&&samples.get(p)!=null){sampleHits++;continue;}
            if(sampleCapacity>0){if(samples.size()>=sampleCapacity)samples.remove(samples.keySet().iterator().next());samples.put(p,true);}
            Integer prev=last.put(key,i);
            if(prev!=null){reused.add(key);reuseDistances.add(new HashSet<>(previousKeys.subList(prev,previousKeys.size())).size());}
            // Index in the tile-access stream (sample-cache hits do not touch tile LRU).
            last.put(key,previousKeys.size());previousKeys.add(key);
            if(tiles.get(key)!=null){hits++;hitCounts.merge(key,1,Integer::sum);continue;}
            misses++;
            if(tiles.size()>=tileCapacity){long victim=tiles.keySet().iterator().next();int born=tiles.remove(victim);evictions++;
                lifetimes.add(i-born);if(hitCounts.getOrDefault(victim,0)==0)evictedUnused++;hitCounts.remove(victim);}
            tiles.put(key,i);hitCounts.put(key,0);
        }
        Collections.sort(reuseDistances);
        var r=new LinkedHashMap<String,Object>();r.put("requests",trace.size());r.put("tileHits",hits);r.put("tileMisses",misses);
        r.put("evictions",evictions);r.put("sampleHits",sampleHits);r.put("uniqueTilesReused",reused.size());r.put("evictedBeforeReuse",evictedUnused);
        r.put("medianReuseDistance",quantile(reuseDistances,.5));r.put("p95ReuseDistance",quantile(reuseDistances,.95));
        r.put("averageEvictedLifetimeRequests",lifetimes.stream().mapToInt(i->i).average().orElse(0));
        r.put("tileCapacity",tileCapacity);r.put("sampleCapacity",sampleCapacity);r.put("maximumSurfaceArrayBytes",tileCapacity*131072L);
        return r;
    }
    private static int quantile(List<Integer> sorted,double p){return sorted.isEmpty()?0:sorted.get((int)Math.ceil(p*sorted.size())-1);}
}
