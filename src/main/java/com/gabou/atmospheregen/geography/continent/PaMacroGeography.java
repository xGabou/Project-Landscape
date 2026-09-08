/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.geography.continent;

import com.gabou.atmospheregen.api.geography.MacroGeographyProvider;
import com.gabou.atmospheregen.config.MacroGeographySettings;
import com.gabou.atmospheregen.generation.seed.GenerationSeedService;
import com.gabou.atmospheregen.geography.ocean.*;

/** Production V1 macro provider. No terrain, climate, biome, registry or world I/O dependencies. */
public final class PaMacroGeography implements MacroGeographyProvider {
    private final MacroGeographySettings settings;
    private final MacroSiteField sites;
    private final ReservedCellContinentModel continents;
    private final IslandModel islands;
    private final OceanModel oceans;
    private final ShelfModel shelves;
    public PaMacroGeography(GenerationSeedService seeds,MacroGeographySettings settings) {
        this.settings=settings;sites=new MacroSiteField(seeds,settings);
        continents=new ReservedCellContinentModel(sites,new CoastlineDetail(seeds,settings.coastlineDetailBlocks()),settings);
        islands=new IslandModel(seeds,settings,continents);oceans=new OceanModel(seeds,sites,settings);shelves=new ShelfModel(seeds,settings);
    }
    @Override public MacroSample sampleMacro(double x,double z) {
        if(!Double.isFinite(x)||!Double.isFinite(z)||Math.abs(x)>30000000||Math.abs(z)>30000000)
            throw new IllegalArgumentException("V1 macro coordinates must be finite and within ±30000000 blocks");
        var continent=continents.sample(x,z);var site=continent.site();
        double profile=continent.shorelineProfileBlocks(),basin=oceans.inlandSeaProfile(x,z,site);
        boolean inland=basin<0;
        if(basin<profile)profile=basin;
        IslandClass kind=IslandClass.NONE;long cluster=0;
        if(!inland&&profile<0) {
            var island=islands.sample(x,z,site);
            if(island.profile()>profile){profile=island.profile();if(profile>0){kind=island.kind();cluster=island.clusterId();}}
        }
        boolean reserved=continents.clearance(x,z,site)<=0;
        var corridor=oceans.nearestCorridor(x,z,site);
        var shelf=shelves.sample(-profile,x,z);
        MarineClass marine=profile>0?MarineClass.LAND:inland?MarineClass.INLAND_SEA:
            reserved?MarineClass.MAJOR_OCEAN:MarineClass.COASTAL_WATER;
        return new MacroSample(site.id(),Math.tanh(profile/512),Math.max(0,Math.min(1,profile/site.radius())),
            marine,corridor.id(),reserved,kind,cluster,profile,shelf.fraction(),shelf.depthBlocks());
    }
    public MacroGeographySettings settings(){return settings;}
    public MacroSiteField sites(){return sites;}
    public IslandModel islands(){return islands;}
    public OceanModel oceans(){return oceans;}
}
