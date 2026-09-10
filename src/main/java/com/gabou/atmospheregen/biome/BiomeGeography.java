/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.biome;

import com.gabou.atmospheregen.api.geography.GeoSample;
import com.gabou.atmospheregen.geography.terrain.PaGeographyProvider;

/** Detached selection context enriched by physical terrain identity without modifying Task 4 output. */
public final class BiomeGeography {
    private BiomeGeography(){}
    public static GeoSample sample(PaGeographyProvider provider,int x,int z){
        var g=provider.sample(x,z);var landform=provider.detailedLandform(x,z);
        if(landform==g.landform())return g;
        return new GeoSample(g.position(),g.elevationBlockY(),g.seaRelativeElevationBlocks(),g.water(),landform,g.metrics(),g.hydrology(),g.legacyTerrainSignals());
    }
}
