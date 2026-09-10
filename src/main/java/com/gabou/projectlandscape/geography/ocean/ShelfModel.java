/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.geography.ocean;

import com.gabou.projectlandscape.config.MacroGeographySettings;
import com.gabou.projectlandscape.generation.seed.*;
import com.gabou.projectlandscape.geography.continent.MacroSiteField;

/** Continuous shallow shelf, smooth shelf break, and deep bathymetry using a shoreline profile. */
public final class ShelfModel {
    public record Shelf(double fraction,double depthBlocks) {}
    private final MacroGeographySettings settings;
    private final double phase;
    public ShelfModel(GenerationSeedService seeds,MacroGeographySettings settings) {
        this.settings=settings;phase=MacroSiteField.unit(seeds.seed(SeedDomain.SHELF_BATHYMETRY))*Math.PI*2;
    }
    public Shelf sample(double marineProfile,double x,double z) {
        if(marineProfile<=0)return new Shelf(0,0);
        double width=settings.shelfWidthBlocks(),u=Math.min(1,marineProfile/width);
        double shelf=settings.shelfDepthBlocks()*u;
        double v=Math.max(0,Math.min(1,(marineProfile-width)/width));v=v*v*(3-2*v);
        double deep=52+8*Math.sin(x/701+phase)*Math.cos(z/907-phase);
        return new Shelf(1-v,(shelf+(deep-settings.shelfDepthBlocks())*v)*settings.marineDepthInfluence());
    }
}
