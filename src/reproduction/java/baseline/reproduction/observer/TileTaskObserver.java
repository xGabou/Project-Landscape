/* Original Task 1C developer-only counter. All Rights Reserved. */
package baseline.reproduction.observer;
import baseline.reproduction.Metrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.generation.TileGenerator;
@Mixin(value=TileGenerator.class,remap=false)
public class TileTaskObserver {
    @Inject(method="generate",at=@At("HEAD"))
    private void count(int x,int z,CallbackInfoReturnable<?> callback) { Metrics.count("tileGenerationCalls"); }
}
