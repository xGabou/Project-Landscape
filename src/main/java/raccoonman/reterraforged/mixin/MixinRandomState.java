/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * See LICENSE for the applicable copyright and permission notice. */
package raccoonman.reterraforged.mixin;

import raccoonman.reterraforged.RTFCommon;
import raccoonman.reterraforged.registries.RTFRegistries;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction.NoiseHolder;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import raccoonman.reterraforged.concurrent.ThreadPools;
import raccoonman.reterraforged.config.PerformanceConfig;
import raccoonman.reterraforged.data.worldgen.preset.settings.Preset;
import raccoonman.reterraforged.tags.RTFDensityFunctionTags;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.densityfunction.CellSampler;
import raccoonman.reterraforged.world.worldgen.densityfunction.NoiseFunction;
import raccoonman.reterraforged.world.worldgen.noise.module.Noise;
import raccoonman.reterraforged.world.worldgen.noise.module.Noises;
import raccoonman.reterraforged.world.worldgen.terrablender.TBClimateSampler;
import raccoonman.reterraforged.world.worldgen.terrablender.TBCompat;

@Mixin(RandomState.class)
@Implements(@Interface(iface = RTFRandomState.class, prefix = "reterraforged$RTFRandomState$"))
class MixinRandomState {
	private DensityFunction.Visitor densityFunctionWrapper;
	@Shadow
	@Final
	private Climate.Sampler sampler;
	@Shadow
	@Final
    private SurfaceSystem surfaceSystem;
	
	@Deprecated
	private boolean hasContext;
	@Nullable
	private GeneratorContext generatorContext;
	@Nullable
	private Preset preset;
	
	private long seed;
	private String rtfDimension;
	private boolean legacyData;
	
	@Redirect(
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"
		),
		method = "<init>",
		require = 1
	)
	private NoiseRouter RandomState(NoiseRouter router, DensityFunction.Visitor visitor, NoiseGeneratorSettings noiseGeneratorSettings, HolderGetter<NormalNoise.NoiseParameters> params, final long seed) {
		this.seed = seed;
		this.densityFunctionWrapper = new DensityFunction.Visitor() {
			
			@Override
			public DensityFunction apply(DensityFunction function) {
				if(function instanceof NoiseFunction.Marker marker) {
					return new NoiseFunction(marker.noise(), (int) seed);
				}
				if(function instanceof CellSampler.Marker marker) {
					MixinRandomState.this.hasContext |= true;
					String operation = "sample density field " + marker.field();
					return new CellSampler(() -> ((RTFRandomState)(Object)MixinRandomState.this).requireGeneratorContext(operation).lookup, marker.field());
				}
				return visitor.apply(function);
			}

			@Override
			public NoiseHolder visitNoise(NoiseHolder noiseHolder) {
	            return visitor.visitNoise(noiseHolder);
	        }
		};
		return router.mapAll(this.densityFunctionWrapper);
	}

	public void reterraforged$RTFRandomState$initialize(RegistryAccess registries) {
		RegistryLookup<Preset> presets = registries.lookup(RTFRegistries.LEGACY_PRESET).orElse(null);
		ResourceKey<net.minecraft.core.Registry<Noise>> noiseKey = RTFRegistries.LEGACY_NOISE;
		ResourceKey<Preset> presetKey = RTFRegistries.createLegacyKey(RTFRegistries.LEGACY_PRESET, "preset");
		this.legacyData = presets != null && presets.get(presetKey).isPresent();
		// An old exported datapack deliberately wins over the bundled default. Its router
		// was decoded from legacy data and must keep its legacy manifest/backend identity.
		if (!this.legacyData) {
			presets = registries.lookup(RTFRegistries.PRESET).orElse(null);
			noiseKey = RTFRegistries.NOISE;
			presetKey = Preset.KEY;
		}
		if (presets == null || presets.get(presetKey).isEmpty()) {
			if (this.hasContext) {
				throw new IllegalStateException("Cannot initialize ReTerraForged " + this.reterraforged$RTFRandomState$contextDescription()
						+ ": density router requires generation context but the RTF preset is missing");
			}
			// Do not claim a vanilla/foreign router merely by wrapping unused RTF tags.
			return;
		}
		RegistryLookup<Noise> noises = registries.lookupOrThrow(noiseKey);
		final ResourceKey<Preset> activePresetKey = presetKey;
		RegistryLookup<DensityFunction> functions = registries.lookupOrThrow(Registries.DENSITY_FUNCTION);

		functions.get(RTFDensityFunctionTags.ADDITIONAL_NOISE_ROUTER_FUNCTIONS).ifPresent((set) -> {
			set.forEach((function) -> function.value().mapAll(this.densityFunctionWrapper));
		});
		
		if((Object) this.sampler instanceof TBClimateSampler tbClimateSampler && TBCompat.isEnabled()) {
			functions.get(TBCompat.uniquenessKey()).ifPresent((uniqueness) -> {
				tbClimateSampler.setUniqueness(uniqueness.value().mapAll(this.densityFunctionWrapper));
			});
		}
		
		presets.get(activePresetKey).ifPresentOrElse((presetHolder) -> {
			this.preset = presetHolder.value();

			if(this.hasContext) {
				PerformanceConfig config = PerformanceConfig.read(PerformanceConfig.DEFAULT_FILE_PATH)
					.resultOrPartial(RTFCommon.LOGGER::error)
					.orElseGet(PerformanceConfig::makeDefault);
				GeneratorContext replacement = GeneratorContext.makeCached(this.preset, noises, (int) this.seed, config.tileSize(), config.batchCount(), ThreadPools.availableProcessors() > 4);
				if (this.generatorContext != null && this.generatorContext.cache != null) this.generatorContext.cache.close();
				this.generatorContext = replacement;
			}
		}, () -> { throw new IllegalStateException("Required RTF preset disappeared during initialization"); });
	}

	public void reterraforged$RTFRandomState$initialize(RegistryAccess registries, String dimension) {
		if (this.rtfDimension != null && !this.rtfDimension.equals(dimension)) {
			throw new IllegalStateException("Cannot rebind ReTerraForged RandomState from dimension " + this.rtfDimension + " to " + dimension);
		}
		this.rtfDimension = dimension;
		this.reterraforged$RTFRandomState$initialize(registries);
	}

	public boolean reterraforged$RTFRandomState$requiresGeneratorContext() { return this.hasContext; }

	public boolean reterraforged$RTFRandomState$usesLegacyData() { return this.legacyData; }

	public String reterraforged$RTFRandomState$contextDescription() {
		return "dimension=" + (this.rtfDimension == null ? "<unbound; ChunkMap initialization not completed>" : this.rtfDimension) + ", seed=" + this.seed;
	}
	
	@Nullable
	public Preset reterraforged$RTFRandomState$preset() {
		return this.preset;
	}
	
	@Nullable
	public GeneratorContext reterraforged$RTFRandomState$generatorContext() {
		return this.generatorContext;
	}

	@Nullable
	public DensityFunction reterraforged$RTFRandomState$wrap(DensityFunction function) {
		return function.mapAll(this.densityFunctionWrapper);
	}

	public Noise reterraforged$RTFRandomState$seed(Noise noise) {
		return Noises.shiftSeed(noise, (int) this.seed);
	}
}
