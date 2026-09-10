package baseline.reproduction.observer;
import baseline.reproduction.task6b.LocalityTrace;
import com.gabou.projectlandscape.geography.terrain.PaGeographyProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=PaGeographyProvider.class,remap=false)
public class SurfaceLocalityObserver {
    @Inject(method="snapshotSurfaceTile",at=@At("HEAD"))
    private void trace(int x,int z,CallbackInfoReturnable<?> ci){LocalityTrace.record("surface_load",x,0,z);}
}
