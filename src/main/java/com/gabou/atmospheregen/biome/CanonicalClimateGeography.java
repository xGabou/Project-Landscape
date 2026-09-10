/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.biome;

import com.gabou.atmospheregen.climate.ClimateGeography;
import com.gabou.atmospheregen.geography.terrain.PaGeographyProvider;
import com.gabou.atmospheregen.geography.ocean.CoarseDistanceField;
import java.util.*;

/** Exact detached canonical elevations amortized across nearby profile queries. No interpolation. */
public final class CanonicalClimateGeography implements ClimateGeography {
    private static final int CAPACITY=1024;
    private final PaGeographyProvider geography;
    private final CoarseDistanceField distances;
    private final com.gabou.atmospheregen.generation.cache.ExactCache<PaGeographyProvider.SurfaceTile> cache=
            new com.gabou.atmospheregen.generation.cache.ExactCache<>(CAPACITY);
    public CanonicalClimateGeography(PaGeographyProvider geography){this.geography=Objects.requireNonNull(geography);distances=geography.distanceField();geography.onClose(cache::close);}
    @Override public Sample sample(int x,int z){
        var macro=geography.macroProvider().sampleMacro(x,z);
        if(!macro.land()){
            double elevation=geography.marineSeaRelativeElevation(macro);var d=distances.sample(x,z);
            return new Sample(elevation,d.marineShoreline().valueBlocks(),d.oceanWater().valueBlocks(),0,macro.waterBody());
        }
        var tile=tile(x,z);var d=distances.sample(x,z);
        return new Sample(tile.seaRelativeElevation(x,z),d.marineShoreline().valueBlocks(),d.oceanWater().valueBlocks(),tile.mountainInfluence(x,z),macro.waterBody());
    }
    private PaGeographyProvider.SurfaceTile tile(int x,int z){
        int tx=geography.surfaceTileCoordinate(x),tz=geography.surfaceTileCoordinate(z);
        long key=((long)tx<<32)^(tz&0xffffffffL);
        return cache.get(key,ignored->geography.snapshotSurfaceTile(x,z));
    }
    public Map<String,Long> cacheStats(){var stats=new HashMap<>(cache.stats());stats.put("retainedArrayBytes",stats.get("entries")*128L*128*Float.BYTES*2);return Map.copyOf(stats);}
    public void clear(){cache.clear();}
}
