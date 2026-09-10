/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.climate;

import com.gabou.projectlandscape.api.geography.*;
import com.gabou.projectlandscape.geography.ocean.CoarseDistanceField;

/** Read-only adapter from the Task 3/4 public geography contracts to climate input. */
public final class ClimateGeographyAdapters {
    private ClimateGeographyAdapters() {}
    public static ClimateGeography of(GeographyProvider geography,MacroGeographyProvider macro,CoarseDistanceField distances) {
        return (x,z)-> {var g=geography.sample(x,z);var m=macro.sampleMacro(x,z);var d=distances.sample(x,z);
            double mountain=g.metrics().mountainInfluence().map(Metric::value).orElse(0.0);
            return new ClimateGeography.Sample(g.seaRelativeElevationBlocks(),d.marineShoreline().valueBlocks(),d.oceanWater().valueBlocks(),mountain,m.waterBody());};
    }
    /** Macro-only diagnostic input; elevation is deliberately a proxy, never final terrain. */
    public static ClimateGeography macroOnly(MacroGeographyProvider macro,CoarseDistanceField distances) {
        return (x,z)-> {var m=macro.sampleMacro(x,z);var d=distances.sample(x,z);
            double elevation=m.land()?Math.max(0,96+m.shorelineProfileBlocks()*0.05):0;
            return new ClimateGeography.Sample(elevation,d.marineShoreline().valueBlocks(),d.oceanWater().valueBlocks(),0,m.waterBody());};
    }
}
