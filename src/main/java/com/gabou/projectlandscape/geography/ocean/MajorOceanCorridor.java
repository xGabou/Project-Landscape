/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.geography.ocean;

/** Declared before shoreline sampling; IDs identify neighboring macro sites, not connected landmasses. */
public record MajorOceanCorridor(long id, long firstSiteId, long secondSiteId,
        double centerX, double centerZ, double normalX, double normalZ,
        double halfLengthBlocks, double requiredWidthBlocks) {}
