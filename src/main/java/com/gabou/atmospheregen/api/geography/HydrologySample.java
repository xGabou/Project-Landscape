/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.api.geography;

import java.util.Objects;
import java.util.Optional;

/**
 * Surface-water geography. Valley influence is a dimensionless [0,1] heuristic, NOT river distance
 * or flow. Optional segment IDs are backend-namespaced stable IDs, never object identities.
 * No claim of discharge, groundwater, drainage basin or flow direction is made.
 */
public record HydrologySample(WaterCategory water, boolean river, boolean lake, boolean wetland,
        Optional<Metric> riverValleyInfluence, Optional<String> segmentIdentity) {
    public HydrologySample {
        Objects.requireNonNull(water); Objects.requireNonNull(riverValleyInfluence); Objects.requireNonNull(segmentIdentity);
        riverValleyInfluence.ifPresent(m -> { if (m.value() < 0 || m.value() > 1) throw new IllegalArgumentException("River valley influence outside [0,1]"); });
        segmentIdentity.ifPresent(id -> { if (id.isBlank()) throw new IllegalArgumentException("Empty segment identity is not an unknown value"); });
    }
}
