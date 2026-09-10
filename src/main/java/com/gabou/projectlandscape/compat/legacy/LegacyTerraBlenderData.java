/* Original Project Atmosphere companion adapter. All Rights Reserved. */
package com.gabou.projectlandscape.compat.legacy;

import com.gabou.projectlandscape.persistence.*;
import com.google.gson.JsonObject;
import terrablender.api.RegionType;
import terrablender.api.Regions;
import terrablender.core.TerraBlender;

/** Optional class, loaded only behind the existing TB gate. Reads metadata; never calls addBiomes. */
final class LegacyTerraBlenderData {
    private LegacyTerraBlenderData() {}
    static GenerationFingerprint capture() {
        var config = TerraBlender.CONFIG;
        if (config == null) throw new IllegalStateException("TerraBlender config was not initialized before world generation manifest");
        JsonObject value = new JsonObject();
        value.addProperty("overworldRegionSize", config.overworldRegionSize);
        value.addProperty("netherRegionSize", config.netherRegionSize);
        value.addProperty("vanillaOverworldRegionWeight", config.vanillaOverworldRegionWeight);
        value.addProperty("vanillaNetherRegionWeight", config.vanillaNetherRegionWeight);
        for (RegionType type : RegionType.values()) {
            JsonObject regions = new JsonObject();
            for (var region : Regions.get(type)) {
                JsonObject descriptor = new JsonObject();
                descriptor.addProperty("weight", region.getWeight());
                // Actual positional index is generation-relevant; outer map order is canonicalized.
                descriptor.addProperty("index", Regions.getIndex(type, region.getName()));
                regions.add(region.getName().toString(), descriptor);
            }
            value.add(type.name(), regions);
        }
        return GenerationFingerprint.of(CanonicalJson.of(value));
    }
}
