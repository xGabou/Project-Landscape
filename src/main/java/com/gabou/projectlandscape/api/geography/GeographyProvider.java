/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.api.geography;

/**
 * World/dimension/version/config-scoped canonical finalized geography at integer block X/Z.
 * Deterministic and independent of cache warmth, query order and worker scheduling for a fixed
 * backend. Correctly handles negative coordinates. Results are immutable detached values;
 * mutable cells, pooled storage and tile ownership never escape.
 *
 * A cold query may synchronously generate/join the owning filtered geography tile. It must not
 * generate Minecraft chunks, perform world I/O or use the legacy direct approximation as fallback.
 * Implementations support concurrent callers and fail explicitly after their owning world closes.
 */
@FunctionalInterface
public interface GeographyProvider { GeoSample sample(int x, int z); }
