/* Original test-only instrumentation. All Rights Reserved. */
package baseline.reproduction.observer;

import baseline.reproduction.Metrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import raccoonman.reterraforged.concurrent.cache.CacheEntry;

@Mixin(value = CacheEntry.class, remap = false)
public class EntryObserver {
    @Shadow private java.util.concurrent.Future<?> task;
    @Inject(method = "get", at = @At("HEAD"))
    private void begin(CallbackInfoReturnable<Object> ci) { Metrics.beginEntry(); if(!this.task.isDone())Metrics.count("entryGetInitiallyPending"); }
    @Inject(method = "get", at = @At("RETURN"))
    private void end(CallbackInfoReturnable<Object> ci) { Metrics.endEntry(); }
}
