package com.gabou.projectlandscape.runtime;

/** Existing server regional atmosphere, observed at gameTick. Intensity has no physical rainfall conversion. */
public record RuntimeClimateSample(long gameTick, double temperatureCelsius, double precipitationIntensity,
        double humidityFraction, double windXMetresPerSecond, double windZMetresPerSecond, double pressureHpa) {
    public RuntimeClimateSample {
        for (double v : new double[]{temperatureCelsius, precipitationIntensity, humidityFraction,
                windXMetresPerSecond, windZMetresPerSecond, pressureHpa}) {
            if (!Double.isFinite(v)) throw new IllegalArgumentException("Non-finite runtime observation");
        }
        if (gameTick < 0 || temperatureCelsius < -273.15 || precipitationIntensity < 0
                || humidityFraction < 0 || humidityFraction > (double)1.2f || pressureHpa <= 0) {
            throw new IllegalArgumentException("Invalid runtime observation units/range");
        }
    }
}
