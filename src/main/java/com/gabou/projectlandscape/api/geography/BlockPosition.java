/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.api.geography;

/** Integer block coordinates, including negative coordinates; no chunk/quart conversion implied. */
public record BlockPosition(int x, int z) {
    public long spatialKey() { return ((long) x << 32) | (z & 0xffffffffL); }
}
