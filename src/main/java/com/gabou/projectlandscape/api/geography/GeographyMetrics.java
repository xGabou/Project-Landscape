/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.api.geography;

import java.util.Objects;
import java.util.Optional;

/**
 * Continuous geographic information, when the backend can truthfully calculate it.
 * Continentality and mountain influence are normalized [0,1] indices; coast/ocean distance and
 * local relief are nonnegative blocks; slope is radians [0,pi/2]; latitude is degrees [-90,90].
 * Unknown ridge position/direction, basin identity and other future metrics are not fabricated here.
 */
public record GeographyMetrics(Optional<Metric> continentality, Optional<Metric> coastDistance,
        Optional<Metric> oceanDistance, Optional<Metric> slope, Optional<Metric> localRelief,
        Optional<Metric> mountainInfluence, Optional<Metric> latitude) {
    public GeographyMetrics {
        range(continentality, 0, 1); range(coastDistance, 0, Double.MAX_VALUE);
        range(oceanDistance, 0, Double.MAX_VALUE); range(slope, 0, Math.PI / 2);
        range(localRelief, 0, Double.MAX_VALUE); range(mountainInfluence, 0, 1); range(latitude, -90, 90);
    }
    private static void range(Optional<Metric> metric, double min, double max) {
        Objects.requireNonNull(metric);
        metric.ifPresent(m -> { if (m.value() < min || m.value() > max) throw new IllegalArgumentException("Geography metric outside [" + min + ", " + max + "]"); });
    }
    public static GeographyMetrics unknown() {
        return new GeographyMetrics(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty());
    }
}
