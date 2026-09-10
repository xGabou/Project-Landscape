/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.biome;
import com.gabou.atmospheregen.api.biome.BiomeResolutionContext;
import com.gabou.atmospheregen.persistence.GenerationFingerprint;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import java.util.*;
public interface BiomeCatalog {
    Collection<BiomeDescriptor> descriptors();
    default Holder<Biome> holder(net.minecraft.resources.ResourceLocation key,BiomeResolutionContext context){return context.biomes().getOrThrow(ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,key));}
    GenerationFingerprint fingerprint();
}
