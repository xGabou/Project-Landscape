/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package raccoonman.reterraforged.mixin;
import com.gabou.atmospheregen.biome.ClimateBiomeSourceAccess;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.*;
@Mixin(ChunkGenerator.class)
abstract class MixinChunkGenerator implements ClimateBiomeSourceAccess {
    @Shadow @Final @Mutable protected BiomeSource biomeSource;
    @Override public void atmospheregen$setBiomeSource(BiomeSource source){this.biomeSource=source;}
}
