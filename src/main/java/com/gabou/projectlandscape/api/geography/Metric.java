/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.api.geography;

/** An available metric with stated spatial support. Absence is Optional.empty(), never a zero sentinel. */
public record Metric(double value, Quality quality, double resolutionBlocks) {
    public enum Quality { MODELLED, LEGACY_HEURISTIC }
    public Metric {
        if (!Double.isFinite(value) || quality == null || !Double.isFinite(resolutionBlocks) || resolutionBlocks <= 0)
            throw new IllegalArgumentException("Metric requires a finite value, quality and positive finite resolution");
    }
}
