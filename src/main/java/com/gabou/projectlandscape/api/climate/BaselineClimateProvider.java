/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.api.climate;

/**
 * Immutable long-term expected climate at integer block X/Z for a fixed generation context.
 * Coordinate-based rather than isolated-GeoSample-only: a future implementation may own a bounded
 * GeographyProvider neighborhood sampler for orographic barriers. It must not depend on runtime
 * weather, mutable global state, cache warmth or Minecraft chunk generation.
 * There is no physical baseline climate implementation in Task 2.
 */
@FunctionalInterface
public interface BaselineClimateProvider { ClimateBaseline sample(int x, int z); }
