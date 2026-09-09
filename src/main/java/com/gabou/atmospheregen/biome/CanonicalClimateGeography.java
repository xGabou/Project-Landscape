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
    private final Map<Long,PaGeographyProvider.SurfaceTile> cache=new LinkedHashMap<>(CAPACITY,.75f,true);
    private long hits,misses;
    public CanonicalClimateGeography(PaGeographyProvider geography){this.geography=Objects.requireNonNull(geography);distances=geography.distanceField();geography.onClose(this::clear);}
    @Override public Sample sample(int x,int z){
        var tile=tile(x,z);var macro=geography.macroProvider().sampleMacro(x,z);var d=distances.sample(x,z);
        return new Sample(tile.seaRelativeElevation(x,z),d.marineShoreline().valueBlocks(),d.oceanWater().valueBlocks(),tile.mountainInfluence(x,z),macro.waterBody());
    }
    private synchronized PaGeographyProvider.SurfaceTile tile(int x,int z){
        int tx=geography.surfaceTileCoordinate(x),tz=geography.surfaceTileCoordinate(z);
        long key=((long)tx<<32)^(tz&0xffffffffL);var tile=cache.get(key);
        if(tile!=null){hits++;return tile;}
        misses++;tile=geography.snapshotSurfaceTile(x,z);
        if(cache.size()>=CAPACITY)cache.remove(cache.keySet().iterator().next());cache.put(key,tile);return tile;
    }
    public synchronized Map<String,Long> cacheStats(){return Map.of("entries",(long)cache.size(),"capacity",(long)CAPACITY,"hits",hits,"misses",misses,"retainedArrayBytes",cache.values().stream().mapToLong(PaGeographyProvider.SurfaceTile::retainedArrayBytes).sum());}
    public synchronized void clear(){cache.clear();}
}
