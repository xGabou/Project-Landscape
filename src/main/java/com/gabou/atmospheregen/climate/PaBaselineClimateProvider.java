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
    private final LatitudeModel latitude;
    private final com.gabou.atmospheregen.runtime.BaselineContextStore runtimeContext = new com.gabou.atmospheregen.runtime.BaselineContextStore();
    private final com.gabou.atmospheregen.generation.cache.ExactCache<ClimateBaseline> cache=
            new com.gabou.atmospheregen.generation.cache.ExactCache<>(CAPACITY);
    public PaBaselineClimateProvider(ClimateGeography geography,GenerationSeedService seeds,BaselineClimateConfig.Planned config) {
        this.geography=Objects.requireNonNull(geography);model=new BaselineClimateModel(Objects.requireNonNull(seeds),Objects.requireNonNull(config));
        latitude = new LatitudeModel(config);
    }
    @Override public ClimateBaseline sample(int x,int z) {
        long key=((long)x<<32)^(z&0xffffffffL);
        return cache.get(key,ignored->{
            if (!com.gabou.atmospheregen.runtime.BaselineContextStore.isRepresentative(x,z)) return model.sample(geography,x,z);
            ClimateGeography.Sample[] local = new ClimateGeography.Sample[1];
            ClimateBaseline result = model.sample((px,pz)->{
                var value=geography.sample(px,pz);
                if (local[0]==null && px==x && pz==z) local[0]=value;
                return value;
            },x,z);
            runtimeContext.record(new com.gabou.atmospheregen.runtime.BaselineContextStore.Context(x,z,latitude.degrees(z),result,local[0]));
            return result;
        });
    }
    public com.gabou.atmospheregen.runtime.BaselineContextStore runtimeContext(){return runtimeContext;}
    public BaselineClimateModel.Result explain(int x,int z) { return model.breakdown(geography,x,z); }
    public Map<String,Long> cacheStats(){return cache.stats();}
    public void clear(){cache.clear();runtimeContext.clear();}
    public void close(){cache.close();runtimeContext.close();}
}
