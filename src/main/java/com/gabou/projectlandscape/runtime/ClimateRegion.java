package com.gabou.projectlandscape.runtime;

/** Dimension identity belongs to the owning world service and persisted header. */
public record ClimateRegion(int x, int z) {
    public static ClimateRegion at(int x, int z, int size) {
        if (size <= 0) throw new IllegalArgumentException("Region size");
        return new ClimateRegion(Math.floorDiv(x, size), Math.floorDiv(z, size));
    }
    public long key() { return ((long) x << 32) ^ (z & 0xffffffffL); }
}
