/* Original Project Landscape compatibility implementation. All Rights Reserved. */
package com.gabou.projectlandscape.compat.projectatmosphere;

import com.gabou.projectlandscape.api.atmosphere.AtmosphereForecastSample;
import com.gabou.projectlandscape.api.climate.ClimateBaseline;
import com.gabou.projectlandscape.api.climate.WindDirection;
import com.gabou.projectlandscape.api.geography.*;
import com.gabou.projectlandscape.biome.ClimateBiomeResolver;
import com.gabou.projectlandscape.biome.VanillaBiomeCatalog;
import com.gabou.projectlandscape.climate.BaselineClimateModel;
import com.gabou.projectlandscape.climate.ClimateGeography;
import com.gabou.projectlandscape.generation.context.WorldGenerationContext;
import com.gabou.projectlandscape.geography.terrain.PaTerrainBridge;
import java.util.Optional;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;

/**
 * Forecast classification with deliberately macro-only inputs. This class must not obtain a
 * PaGeographyProvider or TileCache: PA calls it before normal chunk traffic exists.
 */
public final class LandscapeForecastService {
    private final PaTerrainBridge macroTerrain;
    private final BaselineClimateModel climate;
    private final ClimateBiomeResolver resolver;
    private final double seaLevel;

    public LandscapeForecastService(GeneratorContext context, WorldGenerationContext generation) {
        this.macroTerrain = context.generator.getHeightmap().paBridge();
        if (this.macroTerrain == null) {
            throw new IllegalArgumentException("Landscape forecast service requires V1 macro terrain");
        }
        this.seaLevel = context.levels.waterLevel;
        this.climate = new BaselineClimateModel(generation.seeds(),
                generation.manifest().content().baselineClimate().planned().orElseThrow());
        this.resolver = new ClimateBiomeResolver(new VanillaBiomeCatalog(), generation.seeds(),
                generation.manifest().content().biomeResolver().planned().orElseThrow());
    }

    public AtmosphereForecastSample sample(int x, int z) {
        var macro = this.macroTerrain.macro().sampleMacro(x, z);
        ClimateGeography.Sample climatePoint = climatePoint(macro);
        double temperature = this.climate.temperatureOnly((ignoredX, ignoredZ) -> climatePoint, x, z);
        double rainfall = forecastRainfall(macro);
        ClimateBaseline baseline = this.climate.deriveEvaporationMoisture(temperature, rainfall,
                macro.land() ? macro.continentality() : 0.0D, new WindDirection(1.0D, 0.0D));
        return new AtmosphereForecastSample(this.resolver.select(geography(x, z, macro), baseline));
    }

    private ClimateGeography.Sample climatePoint(MacroGeographyProvider.MacroSample macro) {
        double coast = Math.abs(macro.shorelineProfileBlocks());
        double elevation = macro.land()
                ? this.seaLevel + Math.min(48.0D, Math.max(2.0D, coast / 8.0D))
                : this.seaLevel - Math.max(0.0D, macro.marineDepthBlocks());
        double ocean = macro.waterBody() == MacroGeographyProvider.MarineClass.MAJOR_OCEAN ? 0.0D : coast;
        return new ClimateGeography.Sample(elevation, coast, ocean, 0.0D, macro.waterBody());
    }

    private static double forecastRainfall(MacroGeographyProvider.MacroSample macro) {
        double marineInfluence = macro.land() ? Math.exp(-Math.abs(macro.shorelineProfileBlocks()) / 9000.0D) : 1.0D;
        return 280.0D + 1050.0D * marineInfluence + (macro.land() ? 140.0D * (1.0D - macro.continentality()) : 0.0D);
    }

    private GeoSample geography(int x, int z, MacroGeographyProvider.MacroSample macro) {
        WaterCategory water = water(macro);
        Landform form = macro.land() ? Landform.PLAINS : Landform.OCEAN;
        double elevation = climatePoint(macro).elevationBlocks();
        GeographyMetrics metrics = new GeographyMetrics(
                Optional.of(new Metric(macro.continentality(), Metric.Quality.MODELLED, 1.0D)),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        return new GeoSample(new BlockPosition(x, z), elevation, elevation - this.seaLevel, water, form,
                metrics, new HydrologySample(water, false, false, false, Optional.empty(), Optional.empty()));
    }

    private static WaterCategory water(MacroGeographyProvider.MacroSample macro) {
        return switch (macro.waterBody()) {
            case LAND -> WaterCategory.LAND;
            case INLAND_SEA -> WaterCategory.INLAND_SEA;
            case COASTAL_WATER -> WaterCategory.SHALLOW_OCEAN;
            case MAJOR_OCEAN -> macro.shelfFraction() > 0.5D ? WaterCategory.SHALLOW_OCEAN : WaterCategory.DEEP_OCEAN;
        };
    }
}
