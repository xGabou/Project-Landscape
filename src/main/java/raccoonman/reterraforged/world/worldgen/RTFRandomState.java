/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * See LICENSE for the applicable copyright and permission notice. */
package raccoonman.reterraforged.world.worldgen;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import raccoonman.reterraforged.data.worldgen.preset.settings.Preset;
import raccoonman.reterraforged.world.worldgen.noise.module.Noise;

public interface RTFRandomState {
	void initialize(RegistryAccess registries);
	void initialize(RegistryAccess registries, String dimension);

	boolean requiresGeneratorContext();
	/** True only when the active router was decoded from retained ReTerraForged data. */
	boolean usesLegacyData();
	String contextDescription();

	default GeneratorContext requireGeneratorContext(String operation) {
		GeneratorContext context = this.generatorContext();
		if (context == null) {
			throw new IllegalStateException("Cannot " + operation + " for ReTerraForged " + this.contextDescription()
					+ ": missing generation context; initialize RandomState with the required RTF preset before sampling");
		}
		if (context.cache != null && context.cache.isClosed()) {
			throw new IllegalStateException("Cannot " + operation + " for ReTerraForged " + this.contextDescription()
					+ ": generation context has been shut down");
		}
		return context;
	}
	
	@Nullable
	Preset preset();

	@Nullable
	GeneratorContext generatorContext();
	
	DensityFunction wrap(DensityFunction function);

	Noise seed(Noise noise);
}
