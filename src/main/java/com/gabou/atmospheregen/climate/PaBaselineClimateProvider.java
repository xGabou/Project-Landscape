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
    private final com.gabou.atmospheregen.generation.cache.ExactCache<ClimateBaseline> cache=
            new com.gabou.atmospheregen.generation.cache.ExactCache<>(CAPACITY);
    public PaBaselineClimateProvider(ClimateGeography geography,GenerationSeedService seeds,BaselineClimateConfig.Planned config) {
        this.geography=Objects.requireNonNull(geography);model=new BaselineClimateModel(Objects.requireNonNull(seeds),Objects.requireNonNull(config));
    }
    @Override public ClimateBaseline sample(int x,int z) { long key=((long)x<<32)^(z&0xffffffffL);return cache.get(key,ignored->model.sample(geography,x,z)); }
    public BaselineClimateModel.Result explain(int x,int z) { return model.breakdown(geography,x,z); }
    public Map<String,Long> cacheStats(){return cache.stats();}
    public void clear(){cache.clear();}
    public void close(){cache.close();}
}
