/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.api.geography;

/** Geography-only topology queries. Does not imply finalized terrain height or hydrology. */
public interface MacroGeographyProvider {
    MacroSample sampleMacro(double x, double z);

    enum MarineClass { LAND, COASTAL_WATER, MAJOR_OCEAN, INLAND_SEA }
    enum IslandClass { NONE, CONTINENTAL_COASTAL, OCEANIC, ARCHIPELAGO }
    record MacroSample(long siteId, double landness, double continentality, MarineClass waterBody,
            long corridorId, boolean inReservedCorridor, IslandClass islandClass, long archipelagoId,
            double shorelineProfileBlocks, double shelfFraction, double marineDepthBlocks) {
        public boolean land() { return waterBody == MarineClass.LAND; }
    }
}
