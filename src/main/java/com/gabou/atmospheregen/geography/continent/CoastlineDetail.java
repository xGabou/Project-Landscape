/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.geography.continent;

import com.gabou.atmospheregen.generation.seed.*;

/** Bounded fine shoreline displacement; maximum absolute displacement is amplitude blocks. */
public final class CoastlineDetail {
    private final double phase,amplitude;
    public CoastlineDetail(GenerationSeedService seeds,double amplitude) {
        phase=MacroSiteField.unit(seeds.seed(SeedDomain.COASTLINE_DETAIL))*Math.PI*2;this.amplitude=amplitude;
    }
    public double displacement(double x,double z) {
        return amplitude*(0.6*Math.sin(x/337+phase)*Math.sin(z/431-phase)+0.4*Math.sin(x/113-z/173+phase));
    }
}
