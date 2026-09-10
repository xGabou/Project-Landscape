/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.biome;

import com.gabou.projectlandscape.generation.version.GenerationVersions;
import com.gabou.projectlandscape.geography.terrain.PaGeographyProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;

/** Context binding is dimension scoped and happens before chunk work is published. */
public final class ClimateBiomeInstallation {
    private ClimateBiomeInstallation(){}
    public static void install(ServerLevel level,ChunkGenerator generator,GeneratorContext context){
        if(context==null||context.generationContext()==null||
                !context.generationContext().manifest().content().versions().equals(GenerationVersions.planned()))return;
        if(!level.dimension().equals(Level.OVERWORLD))throw new IllegalStateException("V1 climate biome resolution supports only the Overworld");
        if(!(generator instanceof ClimateBiomeSourceAccess access))throw new IllegalStateException("Missing V1 ChunkGenerator binding boundary");
        var original=generator.getBiomeSource();
        if(original instanceof ClimateBiomeSource saved){
            if(!saved.manifestFingerprint().equals(context.generationContext().manifest().fingerprint().sha256()))
                throw new IllegalStateException("Saved V1 BiomeSource and generation manifest mismatch; restore matching generation data");
            original=saved.retained();
        }
        var geography=context.canonicalGeography();
        var biomes=level.registryAccess().lookupOrThrow(Registries.BIOME);
        access.atmospheregen$setBiomeSource(new ClimateBiomeSource(original,geography,context.generationContext(),biomes));
    }
}
