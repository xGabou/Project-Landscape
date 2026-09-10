package baseline.reproduction.observer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Integration fixture bounds spatial setup only. PA still generates and evolves real weather. */
@Pseudo
@Mixin(targets="net.Gabou.projectatmosphere.manager.ForecastGenerator",remap=false)
public abstract class PaForecastRadius {
    @ModifyArg(method="generateForecastForRegion",at=@At(value="INVOKE",
            target="Lnet/Gabou/projectatmosphere/manager/ForecastGenerator;sampleAround(Lnet/minecraft/core/BlockPos;Lnet/minecraft/server/level/ServerLevel;I)Ljava/util/Map;"),index=2)
    private static int phase2a$boundedSetup(int original) {
        return Boolean.getBoolean("phase2a.runtime") ? 128 : original;
    }
    @ModifyArg(method="sampleRegionCell",at=@At(value="INVOKE",
            target="Lnet/Gabou/projectatmosphere/manager/ForecastGenerator;sampleSquare(Lnet/minecraft/core/BlockPos;Lnet/minecraft/server/level/ServerLevel;I)Ljava/util/List;"),index=2)
    private static int phase2a$boundedCellSetup(int original) {
        return Boolean.getBoolean("phase2a.runtime") ? 128 : original;
    }
}
