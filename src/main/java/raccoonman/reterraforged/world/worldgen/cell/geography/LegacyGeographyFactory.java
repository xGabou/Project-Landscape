/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License. See LICENSE. */
package raccoonman.reterraforged.world.worldgen.cell.geography;

import raccoonman.reterraforged.world.worldgen.cell.heightmap.*;

import net.minecraft.core.HolderGetter;
import raccoonman.reterraforged.data.worldgen.preset.PresetNoiseData;
import raccoonman.reterraforged.data.worldgen.preset.PresetTerrainTypeNoise;
import raccoonman.reterraforged.data.worldgen.preset.settings.Preset;
import raccoonman.reterraforged.data.worldgen.preset.settings.TerrainSettings;
import raccoonman.reterraforged.data.worldgen.preset.settings.WorldSettings;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.cell.CellPopulator;
import raccoonman.reterraforged.world.worldgen.cell.climate.Climate;
import raccoonman.reterraforged.world.worldgen.cell.continent.Continent;
import raccoonman.reterraforged.world.worldgen.cell.continent.ContinentLerper2;
import raccoonman.reterraforged.world.worldgen.cell.continent.ContinentLerper3;
import raccoonman.reterraforged.world.worldgen.cell.terrain.Blender;
import raccoonman.reterraforged.world.worldgen.cell.terrain.Populators;
import raccoonman.reterraforged.world.worldgen.cell.terrain.provider.TerrainProvider;
import raccoonman.reterraforged.world.worldgen.cell.terrain.region.RegionLerper;
import raccoonman.reterraforged.world.worldgen.cell.terrain.region.RegionModule;
import raccoonman.reterraforged.world.worldgen.cell.terrain.region.RegionSelector;
import raccoonman.reterraforged.world.worldgen.noise.function.DistanceFunction;
import raccoonman.reterraforged.world.worldgen.noise.function.EdgeFunction;
import raccoonman.reterraforged.world.worldgen.noise.function.Interpolation;
import raccoonman.reterraforged.world.worldgen.noise.module.Noise;
import raccoonman.reterraforged.world.worldgen.noise.module.Noises;
import raccoonman.reterraforged.world.worldgen.util.Seed;

/** Frozen V0 construction order and sequential int seeds. Not a factory for PA_GEOGRAPHY_V1. */
public final class LegacyGeographyFactory {
    private LegacyGeographyFactory() {}
	public static Heightmap make(GeneratorContext context) {
    	HolderGetter<Noise> noiseLookup = context.noiseLookup;
    	
        Preset preset = context.preset;
        WorldSettings world = context.preset.world();
        ControlPoints controlPoints = ControlPoints.make(world.controlPoints);

        TerrainSettings terrainSettings = preset.terrain();
        TerrainSettings.General general = terrainSettings.general;
        float globalVerticalScale = general.globalVerticalScale;
        
        Seed regionWarp = context.seed.offset(8934);
        int regionWarpScale = 400;
        int regionWarpStrength = 200;
        
        RegionConfig regionConfig = new RegionConfig(
        	context.seed.root() + 789124, 
        	general.terrainRegionSize, 
        	Noises.simplex(regionWarp.next(), regionWarpScale, 1),
        	Noises.simplex(regionWarp.next(), regionWarpScale, 1), 
        	regionWarpStrength
        );
        Levels levels = context.levels;
        float terrainFrequency = 1.0F / terrainSettings.general.globalHorizontalScale;
        CellPopulator region = new RegionModule(regionConfig);

        Seed mountainSeed = context.seed.offset(general.terrainSeedOffset);
        Noise mountainShape = Noises.worleyEdge(mountainSeed.next(), 1000, EdgeFunction.DISTANCE_2_ADD, DistanceFunction.EUCLIDEAN);
        mountainShape = Noises.warpPerlin(mountainShape, mountainSeed.next(), 333, 2, 250.0F);
        mountainShape = Noises.curve(mountainShape, Interpolation.CURVE3);
        mountainShape = Noises.clamp(mountainShape, 0.0F, 0.9F);
        mountainShape = Noises.map(mountainShape, 0.0F, 1.0F);

        Noise ground = PresetNoiseData.getNoise(noiseLookup, PresetTerrainTypeNoise.GROUND);
        
        CellPopulator terrainRegions = new RegionSelector(TerrainProvider.generateTerrain(context.seed, terrainSettings, regionConfig, levels, noiseLookup));
        CellPopulator terrainRegionBorders = Populators.makeBorder(context.seed, ground, terrainSettings.plains, terrainSettings.steppe, globalVerticalScale);
        CellPopulator terrainBlend = new RegionLerper(terrainRegionBorders, terrainRegions);
        CellPopulator mountains = Populators.makeMountainChain(mountainSeed, ground, terrainSettings.mountains, globalVerticalScale, general.fancyMountains);
        Continent continent = world.continent.continentType.create(context.seed, context);
        Climate climate = Climate.make(continent, context);
        CellPopulator land = new Blender(mountainShape, terrainBlend, mountains, 0.3F, 0.8F, 0.575F);
        
        CellPopulator deepOcean = Populators.makeDeepOcean(context.seed.next(), levels.water);
        CellPopulator shallowOcean = Populators.makeShallowOcean(context.levels);
        CellPopulator coast = Populators.makeCoast(context.levels);
        
        CellPopulator oceans = new ContinentLerper3(deepOcean, shallowOcean, coast, controlPoints.deepOcean(), controlPoints.shallowOcean(), controlPoints.coast());
        CellPopulator terrain = new ContinentLerper2(oceans, land, controlPoints.shallowOcean(), controlPoints.inland());

        Noise beachNoise = Noises.perlin2(context.seed.next(), 20, 1);
        beachNoise = Noises.mul(beachNoise, context.levels.scale(5));
        return new Heightmap(terrain, region, continent, climate, levels, controlPoints, terrainFrequency, beachNoise);
	}
	
}
