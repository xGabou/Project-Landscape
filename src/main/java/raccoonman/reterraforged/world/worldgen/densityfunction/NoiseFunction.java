/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License. See LICENSE. */
package raccoonman.reterraforged.world.worldgen.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import raccoonman.reterraforged.world.worldgen.noise.module.Noise;

public record NoiseFunction(Holder<Noise> noise, int seed) implements MarkerFunction.Mapped {

	@Override
	public double compute(FunctionContext ctx) {
		float value = this.noise.value().compute(ctx.blockX(), ctx.blockZ(), this.seed);
		if (!Float.isFinite(value)) {
			throw new IllegalStateException("Non-finite ReTerraForged density noise "
				+ this.noise.unwrapKey().map(key -> key.location().toString()).orElse("<direct graph>")
				+ " at x=" + ctx.blockX() + ", z=" + ctx.blockZ() + ", compute seed=" + this.seed);
		}
		return value;
	}

	@Override
	public double minValue() {
		return this.noise.value().minValue();
	}

	@Override
	public double maxValue() {
		return this.noise.value().maxValue();
	}
	
	public record Marker(Holder<Noise> noise) implements MarkerFunction {
		public static final Codec<NoiseFunction.Marker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Noise.CODEC.fieldOf("noise").forGetter(NoiseFunction.Marker::noise)
		).apply(instance, NoiseFunction.Marker::new));

		@Override
		public KeyDispatchDataCodec<NoiseFunction.Marker> codec() {
			return new KeyDispatchDataCodec<>(CODEC);
		}

		@Override
		public DensityFunction mapAll(Visitor visitor) {
			DensityFunction self = visitor instanceof Noise.Visitor noiseVisitor ?
				new Marker(Holder.direct(this.noise.value().mapAll(noiseVisitor))) : 
				this;
			return visitor.apply(self);
		}
	}
}
