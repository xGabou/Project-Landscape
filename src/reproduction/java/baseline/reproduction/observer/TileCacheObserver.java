/* Original test-only instrumentation. All Rights Reserved. */
package baseline.reproduction.observer;

import baseline.reproduction.Metrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.Tile;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.TileCache;

@Mixin(value = TileCache.class, remap = false)
public class TileCacheObserver {
    @Inject(method = "provideIfPresent", at = @At("RETURN"))
    private void present(int x, int z, CallbackInfoReturnable<Tile> ci) {
        Metrics.count(ci.getReturnValue() == null ? "presentMiss" : "presentHit");
    }
    @Inject(method = "provide", at = @At("HEAD"))
    private void provide(int x, int z, CallbackInfoReturnable<Tile> ci) { Metrics.count("provideCalls"); }
    @Inject(method = "queue", at = @At("HEAD"))
    private void queue(int x, int z, CallbackInfo ci) { Metrics.count("queueCalls"); }
    @Inject(method = "drop", at = @At("HEAD"))
    private void drop(int x, int z, CallbackInfo ci) { Metrics.count("dropCalls"); }
}
