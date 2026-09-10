/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.geography.ocean;

import com.gabou.projectlandscape.geography.continent.*;
import com.gabou.projectlandscape.config.MacroGeographySettings;
import com.gabou.projectlandscape.generation.seed.*;

/** Marine topology, intentionally independent of legacy thresholds and climate. */
public final class OceanModel {
    private final long seed;
    private final MacroSiteField sites;
    private final MacroGeographySettings settings;
    public OceanModel(GenerationSeedService seeds,MacroSiteField sites,MacroGeographySettings settings) {
        seed=seeds.seed(SeedDomain.OCEAN);this.sites=sites;this.settings=settings;
    }
    /** Negative inside a declared enclosed marine basin; not a hydrological freshwater lake. */
    public double inlandSeaProfile(double x,double z,MacroSiteField.Site site) {
        long h=MacroSiteField.hash(seed,site.gridX(),site.gridZ());
        if(MacroSiteField.unit(h)>=settings.inlandSeaFrequency())return Double.MAX_VALUE;
        double angle=MacroSiteField.unit(MacroSiteField.mix(h))*Math.PI*2;
        double offset=site.radius()*0.25,radius=site.radius()*0.16;
        return Math.hypot(x-site.x()-Math.cos(angle)*offset,z-site.z()-Math.sin(angle)*offset)-radius;
    }
    /** Axis 0: east boundary, axis 1: south boundary. Normal points from first site to second. */
    public MajorOceanCorridor corridor(int gx,int gz,int axis) {
        var first=sites.site(gx,gz);var second=sites.site(gx+(axis==0?1:0),gz+(axis==1?1:0));
        double scale=settings.continentScaleBlocks();
        long id=MacroSiteField.mix(seed^first.id()^Long.rotateLeft(second.id(),23)^axis);
        return new MajorOceanCorridor(id,first.id(),second.id(),(gx+(axis==0?1:0.5))*scale,
            (gz+(axis==1?1:0.5))*scale,axis==0?1:0,axis==1?1:0,
            scale*0.12,settings.minimumMajorOceanWidthBlocks());
    }
    public MajorOceanCorridor nearestCorridor(double x,double z,MacroSiteField.Site site) {
        double scale=settings.continentScaleBlocks(),lx=x-site.gridX()*scale,lz=z-site.gridZ()*scale;
        double dx=Math.min(lx,scale-lx),dz=Math.min(lz,scale-lz);
        return dx<dz?corridor(site.gridX()-(lx<scale/2?1:0),site.gridZ(),0)
            :corridor(site.gridX(),site.gridZ()-(lz<scale/2?1:0),1);
    }
}
