/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.geography.continent;

import com.gabou.atmospheregen.api.geography.MacroGeographyProvider.IslandClass;
import com.gabou.atmospheregen.config.MacroGeographySettings;
import com.gabou.atmospheregen.generation.seed.*;
import java.util.*;
import com.gabou.atmospheregen.geography.terrain.TerrainMetrics.Stage;

/** Explicit independent coastal/oceanic islands and bounded clusters. Immutable cached geometry. */
public final class IslandModel {
    public record Island(double x,double z,double radius,IslandClass kind,long clusterId) {}
    public record Sample(double profile,IslandClass kind,long clusterId) {}
    private final long islandSeed,clusterSeed;
    private final MacroGeographySettings settings;
    private final ReservedCellContinentModel continent;
    private final Map<Long,List<Island>> cache=new LinkedHashMap<>(256,0.75f,true);
    private long hits,misses;
    public IslandModel(GenerationSeedService seeds,MacroGeographySettings settings,ReservedCellContinentModel continent) {
        islandSeed=seeds.seed(SeedDomain.ISLANDS);clusterSeed=seeds.seed(SeedDomain.ARCHIPELAGOS);
        this.settings=settings;this.continent=continent;
    }
    public List<Island> islands(MacroSiteField.Site site) {
        long waiting=Stage.ISLAND_WAIT.start();
        synchronized(this) {
            Stage.ISLAND_WAIT.end(waiting);
            long working=Stage.ISLAND_WORK.start();
            try {return islandsLocked(site);} finally {Stage.ISLAND_WORK.end(working);}
        }
    }
    private List<Island> islandsLocked(MacroSiteField.Site site) {
        List<Island> found=cache.get(site.id());if(found!=null){hits++;return found;}misses++;
        List<Island> result=new ArrayList<>();
        long h=MacroSiteField.hash(islandSeed,site.gridX(),site.gridZ());
        if(MacroSiteField.unit(h)<settings.islandFrequency()) {
            double angle=MacroSiteField.unit(MacroSiteField.mix(h))*Math.PI*2;
            double radius=80+120*MacroSiteField.unit(MacroSiteField.mix(h+1));
            double d=site.radius()*1.09+radius+180;
            add(result,site,site.x()+Math.cos(angle)*d,site.z()+Math.sin(angle)*d,radius,IslandClass.CONTINENTAL_COASTAL,0);
        }
        double scale=settings.continentScaleBlocks();
        for(int corner=0;corner<4;corner++) {
            long k=MacroSiteField.mix(h+corner+11);
            double x=(site.gridX()+((corner&1)==0?0.12:0.88))*scale;
            double z=(site.gridZ()+((corner&2)==0?0.12:0.88))*scale;
            if(MacroSiteField.unit(k)<settings.islandFrequency())
                add(result,site,x,z,100+180*MacroSiteField.unit(MacroSiteField.mix(k)),IslandClass.OCEANIC,0);
        }
        long cluster=MacroSiteField.hash(clusterSeed,site.gridX(),site.gridZ());
        if(MacroSiteField.unit(cluster)<settings.archipelagoFrequency()) {
            int corner=(int)(cluster&3);double cx=(site.gridX()+((corner&1)==0?0.18:0.82))*scale;
            double cz=(site.gridZ()+((corner&2)==0?0.18:0.82))*scale;
            int count=4+(int)((cluster>>>8)&3);
            for(int i=0;i<count;i++) {
                long k=MacroSiteField.mix(cluster+i);double angle=2*Math.PI*i/count;
                double d=450+100*MacroSiteField.unit(k);
                add(result,site,cx+Math.cos(angle)*d,cz+Math.sin(angle)*d,
                    85+65*MacroSiteField.unit(MacroSiteField.mix(k)),IslandClass.ARCHIPELAGO,cluster);
            }
        }
        List<Island> immutable=List.copyOf(result);
        if(cache.size()>=256)cache.remove(cache.keySet().iterator().next());cache.put(site.id(),immutable);return immutable;
    }
    private void add(List<Island> result,MacroSiteField.Site site,double x,double z,double r,IslandClass kind,long cluster) {
        // Full disc plus clearance is excluded from every reserved marine strip.
        double shelfClearance=Math.max(64,2*settings.shelfWidthBlocks()-settings.minimumMajorOceanWidthBlocks()/2);
        if(continent.clearance(x,z,site)<=r+shelfClearance)return;
        // Validate the full bounding ring against the real mainland predicate, with a gap.
        for(int i=0;i<32;i++) {
            double a=i*Math.PI/16;
            if(continent.sample(x+Math.cos(a)*(r+96),z+Math.sin(a)*(r+96)).shorelineProfileBlocks()>-128)return;
        }
        for(var existing:result)if(Math.hypot(x-existing.x,z-existing.z)<r+existing.radius+96)return;
        result.add(new Island(x,z,r,kind,cluster));
    }
    public Sample sample(double x,double z,MacroSiteField.Site site) {
        double best=-Double.MAX_VALUE;IslandClass kind=IslandClass.NONE;long cluster=0;
        for(Island island:islands(site)) {
            double profile=island.radius-Math.hypot(x-island.x,z-island.z);
            if(profile>best){best=profile;kind=island.kind;cluster=island.clusterId;}
        }
        return new Sample(best,kind,cluster);
    }
    public synchronized Map<String,Long> cacheStats(){return Map.of("entries",(long)cache.size(),"capacity",256L,"hits",hits,"misses",misses);}
    public synchronized void clear(){cache.clear();}
}
