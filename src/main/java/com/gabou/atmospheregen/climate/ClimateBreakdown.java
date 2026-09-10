/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.climate;

import com.gabou.atmospheregen.api.climate.WindDirection;

/** Explainable intermediate values for diagnostics; not a biome or weather contract. */
public record ClimateBreakdown(double latitudeDegrees,double latitudeTemperatureCelsius,double altitudeContributionCelsius,
        double oceanModerationCelsius,double continentalityContributionCelsius,double regionalTemperatureCelsius,
        double initialMoisture,double marineFetch,double orographicRainfall,double rainShadow,
        double finalRainfall,double potentialEvaporation,double moistureIndex,WindDirection prevailingWind,
        int sampledProfilePoints,double maximumBarrierRiseBlocks) {}
