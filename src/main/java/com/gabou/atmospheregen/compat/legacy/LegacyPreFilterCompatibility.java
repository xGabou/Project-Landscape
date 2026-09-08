/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.compat.legacy;

import com.gabou.atmospheregen.geography.GeographyWorkspace;

/**
 * Explicit V0-only hook: old parameter/classification and coast rules run BEFORE erosion.
 * This is not BaselineClimateProvider and must not be reused as the new climate authority.
 */
@FunctionalInterface
public interface LegacyPreFilterCompatibility<W extends GeographyWorkspace> {
    void apply(W workspace, float x, float z, boolean classifyBiomes);
}
