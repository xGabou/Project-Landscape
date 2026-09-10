/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.api.biome;

import com.gabou.projectlandscape.api.geography.GeoSample;
import com.gabou.projectlandscape.api.climate.ClimateBaseline;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** Future initial-biome authority; no implementation or Minecraft BiomeSource replacement in Task 2. */
@FunctionalInterface
public interface ClimateBiomeResolver {
    Holder<Biome> resolve(GeoSample geography, ClimateBaseline climate, BiomeResolutionContext context);
}
