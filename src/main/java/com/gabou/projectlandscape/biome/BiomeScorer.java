/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.biome;

import com.gabou.projectlandscape.api.climate.ClimateBaseline;
import com.gabou.projectlandscape.api.geography.GeoSample;

/** Continuous Model B score; acceptable climate envelopes are hard eligibility constraints. */
public final class BiomeScorer {
    private BiomeScorer() {}

    public static double aridity(ClimateBaseline climate) {
        return climate.annualRainfallMm() / Math.max(1.0, climate.potentialEvaporationMm());
    }

    public static double score(BiomeDescriptor descriptor, GeoSample geography, ClimateBaseline climate) {
        double ratio = aridity(climate);
        if (!descriptor.temperature().allowed(climate.meanTemperatureCelsius())
                || !descriptor.rainfall().allowed(climate.annualRainfallMm())
                || !descriptor.aridity().allowed(ratio)) return Double.NEGATIVE_INFINITY;
        return .34 * descriptor.temperature().score(climate.meanTemperatureCelsius())
                + .28 * descriptor.rainfall().score(climate.annualRainfallMm())
                + .28 * descriptor.aridity().score(ratio)
                + .10 * descriptor.elevation().score(geography.seaRelativeElevationBlocks());
    }
}
