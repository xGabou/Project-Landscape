/* Original test-only instrumentation. All Rights Reserved. */
package baseline.reproduction.observer;

import baseline.reproduction.Metrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "raccoonman.reterraforged.world.worldgen.densityfunction.tile.TileCache$Entry", remap = false)
public class TileReleaseObserver {
    @Inject(method = "drop", at = @At("RETURN"))
    private void drop(CallbackInfoReturnable<Boolean> ci) {
        if(ci.getReturnValue()) Metrics.count("thresholdTileReleases");
    }
}
