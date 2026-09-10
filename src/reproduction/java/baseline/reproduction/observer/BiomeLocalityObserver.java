package baseline.reproduction.observer;
import baseline.reproduction.task6b.LocalityTrace;
import com.gabou.projectlandscape.biome.ClimateBiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=ClimateBiomeSource.class,remap=false)
public class BiomeLocalityObserver {
    @Inject(method="getNoiseBiome",at=@At("HEAD"))
    private void trace(int x,int y,int z,Climate.Sampler sampler,CallbackInfoReturnable<?> ci){LocalityTrace.record("biome_quart",x,y,z);}
}
