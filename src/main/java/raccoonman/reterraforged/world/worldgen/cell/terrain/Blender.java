/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License. See LICENSE. */
package raccoonman.reterraforged.world.worldgen.cell.terrain;

import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.CellPopulator;
import raccoonman.reterraforged.world.worldgen.noise.NoiseUtil;
import raccoonman.reterraforged.world.worldgen.noise.function.Interpolation;
import raccoonman.reterraforged.world.worldgen.noise.module.Noise;

public class Blender implements CellPopulator {
	private Noise control;
	private CellPopulator lower;
	private CellPopulator upper;
	private float blendLower;
	private float blendUpper;
	private float blendRange;
	private float midpoint;

	public Blender(Noise control, CellPopulator lower, CellPopulator upper, float min, float max, float split) {
		this.control = control;
		this.lower = lower;
		this.upper = upper;
		this.blendLower = min;
		this.blendUpper = max;
		this.blendRange = this.blendUpper - this.blendLower;
		this.midpoint = this.blendLower + this.blendRange * split;
	}

	@Override
	public void apply(Cell cell, float x, float y) {
		float select = this.control.compute(x, y, 0);
		// This legacy Blender's sole production use blends regional land with the mountain chain.
		cell.mountainChainSelector(select);
		if (select < this.blendLower) {
			this.lower.apply(cell, x, y);
			cell.mountainChainContribution(0.0F);
			return;
		}
		if (select > this.blendUpper) {
			this.upper.apply(cell, x, y);
			cell.mountainChainContribution(1.0F);
			return;
		}
		float alpha = Interpolation.LINEAR.apply((select - this.blendLower) / this.blendRange);
		this.lower.apply(cell, x, y);
		float lowerHeight = cell.height;
		float lowerErosion = cell.erosion;
		float lowerWeirdness = cell.weirdness;
		Terrain lowerType = cell.terrain;
		float lowerRegionalMountain = cell.regionalMountainContribution();
		this.upper.apply(cell, x, y);
		float upperHeight = cell.height;
		float upperErosion = cell.erosion;
		float upperWeirdness = cell.weirdness;
		cell.height = NoiseUtil.lerp(lowerHeight, upperHeight, alpha);
		cell.erosion = NoiseUtil.lerp(lowerErosion, upperErosion, alpha);
		cell.weirdness = NoiseUtil.lerp(lowerWeirdness, upperWeirdness, alpha);
		cell.mountainChainContribution(alpha);
		cell.regionalMountainContribution(lowerRegionalMountain * (1.0F - alpha));
		if (select < this.midpoint) {
			cell.terrain = lowerType;
		}
	}
}
