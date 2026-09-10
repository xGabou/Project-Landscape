/* Original Project Landscape compatibility API. All Rights Reserved. */
package com.gabou.projectlandscape.api.atmosphere;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Immutable forecast-estimation result. It is not authority for Minecraft biome placement. */
public record AtmosphereForecastSample(ResourceLocation biomeId) {
    public AtmosphereForecastSample {
        Objects.requireNonNull(biomeId);
    }
}
