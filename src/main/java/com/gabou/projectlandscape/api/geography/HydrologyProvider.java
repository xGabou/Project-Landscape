/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.api.geography;

/** Uses the same world-scoped, canonical, detached and cache-independent contract as GeographyProvider. */
@FunctionalInterface
public interface HydrologyProvider { HydrologySample sample(int x, int z); }
