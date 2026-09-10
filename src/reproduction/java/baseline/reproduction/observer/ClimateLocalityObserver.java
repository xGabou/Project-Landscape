package baseline.reproduction.observer;
import baseline.reproduction.task6b.LocalityTrace;
import com.gabou.projectlandscape.biome.CanonicalClimateGeography;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=CanonicalClimateGeography.class,remap=false)
public class ClimateLocalityObserver {
    @Inject(method="sample",at=@At("HEAD"))
    private void trace(int x,int z,CallbackInfoReturnable<?> ci){LocalityTrace.record("profile_sample",x,0,z);}
}
