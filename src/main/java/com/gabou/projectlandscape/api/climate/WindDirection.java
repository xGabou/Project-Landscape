/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.api.climate;

/** Unit horizontal vector pointing towards downwind (+X east, +Z south). Not wind speed. */
public record WindDirection(double x, double z) {
    public WindDirection {
        if (!Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x * x + z * z - 1.0) > 1.0e-12)
            throw new IllegalArgumentException("Prevailing wind must be a finite unit vector");
    }
}
