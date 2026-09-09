/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.climate;

import com.gabou.atmospheregen.api.geography.MacroGeographyProvider;

/** Read-only geography input required by the baseline climate model. */
@FunctionalInterface
public interface ClimateGeography {
    Sample sample(int x, int z);

    record Sample(double elevationBlocks, double coastDistanceBlocks, double oceanDistanceBlocks,
            double mountainInfluence, MacroGeographyProvider.MarineClass marineClass) {
        public Sample {
            if (!Double.isFinite(elevationBlocks)) throw new IllegalArgumentException("elevationBlocks must be finite");
            finiteNonnegative("coastDistanceBlocks", coastDistanceBlocks);
            finiteNonnegative("oceanDistanceBlocks", oceanDistanceBlocks);
            if (!Double.isFinite(mountainInfluence) || mountainInfluence < 0 || mountainInfluence > 1)
                throw new IllegalArgumentException("mountainInfluence must be finite in [0,1]");
            if (marineClass == null) throw new NullPointerException("marineClass");
        }
        private static void finiteNonnegative(String name, double value) {
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
    }
}
