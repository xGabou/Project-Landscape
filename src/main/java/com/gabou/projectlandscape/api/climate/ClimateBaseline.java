/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.api.climate;

import java.util.Objects;
import java.util.Optional;

/**
 * Long-term model expectations, never live weather. Temperature is Celsius; rainfall and potential
 * evaporation are mm/year. Ecological moisture index and rain shadow are [0,1] model indices,
 * NOT physical relative humidity. Missing wind means unavailable, not zero speed/direction.
 * Legacy RTF temperature/moisture parameters cannot be converted to these units by assignment.
 */
public record ClimateBaseline(double meanTemperatureCelsius, double annualRainfallMm,
        double ecologicalMoistureIndex, double potentialEvaporationMm, double rainShadow,
        Optional<WindDirection> prevailingWind) {
    public ClimateBaseline {
        finiteRange(meanTemperatureCelsius, -273.15, Double.MAX_VALUE);
        finiteRange(annualRainfallMm, 0, Double.MAX_VALUE); finiteRange(ecologicalMoistureIndex, 0, 1);
        finiteRange(potentialEvaporationMm, 0, Double.MAX_VALUE); finiteRange(rainShadow, 0, 1);
        Objects.requireNonNull(prevailingWind);
    }
    private static void finiteRange(double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid baseline climate quantity");
    }
}
