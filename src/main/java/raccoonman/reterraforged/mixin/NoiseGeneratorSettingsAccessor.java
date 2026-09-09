/* Original Project Atmosphere companion integration. All Rights Reserved. */
package raccoonman.reterraforged.mixin;

import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Definition-only read: TerraBlender's accessor adds a late runtime wrapper. */
@Mixin(NoiseGeneratorSettings.class)
public interface NoiseGeneratorSettingsAccessor {
    @Accessor("surfaceRule") SurfaceRules.RuleSource atmospheregen$storedSurfaceRule();
}
