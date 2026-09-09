/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.climate;

import com.gabou.atmospheregen.api.climate.*;
import com.gabou.atmospheregen.config.BaselineClimateConfig;
import com.gabou.atmospheregen.generation.seed.GenerationSeedService;
import java.util.*;

/** World-scoped V1 baseline climate provider with bounded, detached result caching. */
public final class PaBaselineClimateProvider implements BaselineClimateProvider {
    private static final int CAPACITY=4096;
    private final ClimateGeography geography;private final BaselineClimateModel model;
    private final Map<Long,ClimateBaseline> cache=new LinkedHashMap<>(CAPACITY,.75f,true);private long hits,misses;
    public PaBaselineClimateProvider(ClimateGeography geography,GenerationSeedService seeds,BaselineClimateConfig.Planned config) {
        this.geography=Objects.requireNonNull(geography);model=new BaselineClimateModel(Objects.requireNonNull(seeds),Objects.requireNonNull(config));
    }
    @Override public synchronized ClimateBaseline sample(int x,int z) { long key=((long)x<<32)^(z&0xffffffffL);var v=cache.get(key);if(v!=null){hits++;return v;}misses++;v=model.sample(geography,x,z);if(cache.size()>=CAPACITY)cache.remove(cache.keySet().iterator().next());cache.put(key,v);return v; }
    public synchronized BaselineClimateModel.Result explain(int x,int z) { return model.breakdown(geography,x,z); }
    public synchronized Map<String,Long> cacheStats(){return Map.of("entries",(long)cache.size(),"capacity",(long)CAPACITY,"hits",hits,"misses",misses);}
    public synchronized void clear(){cache.clear();}
    public synchronized void close(){cache.clear();}
}
