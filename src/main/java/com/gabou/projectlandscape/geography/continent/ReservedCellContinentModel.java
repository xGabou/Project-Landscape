/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.geography.continent;

import com.gabou.projectlandscape.config.MacroGeographySettings;

/** Coherent radial regions with independent bounded shoreline detail and explicit marine exclusion. */
public final class ReservedCellContinentModel implements ContinentModel {
    private final MacroSiteField sites;
    private final CoastlineDetail coastline;
    private final MacroGeographySettings settings;
    public ReservedCellContinentModel(MacroSiteField sites,CoastlineDetail coastline,MacroGeographySettings settings) {
        this.sites=sites;this.coastline=coastline;this.settings=settings;
    }
    public double clearance(double x,double z,MacroSiteField.Site site) {
        double scale=settings.continentScaleBlocks();
        double lx=x-site.gridX()*scale,lz=z-site.gridZ()*scale;
        return Math.min(Math.min(lx,scale-lx),Math.min(lz,scale-lz))-settings.minimumMajorOceanWidthBlocks()/2;
    }
    @Override public ContinentSample sample(double x,double z) {
        var site=sites.at(x,z);double dx=x-site.x(),dz=z-site.z(),angle=Math.atan2(dz,dx);
        double radius=site.radius()*(1+0.035*Math.sin(3*angle+site.phase())+0.025*Math.cos(5*angle-site.phase()));
        double profile=Math.min(radius-Math.hypot(dx,dz)+coastline.displacement(x,z),clearance(x,z,site));
        return new ContinentSample(site,profile,Math.max(0,Math.min(1,profile/site.radius())));
    }
}
