/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.biome;

import com.gabou.atmospheregen.api.climate.ClimateBaseline;
import com.gabou.atmospheregen.api.geography.*;
import net.minecraft.resources.ResourceLocation;

/** Geography constrains the available ecological family before climate ranking. */
public final class GeographicBiomeRules {
    private GeographicBiomeRules() {}
    public static boolean eligible(BiomeDescriptor d,GeoSample g) {
        if(d.traits().contains(BiomeTrait.PLATEAU))return g.landform()==Landform.PLATEAU;
        if(d.traits().contains(BiomeTrait.HIGHLAND))
            return g.landform()==Landform.HILLS||g.landform()==Landform.MOUNTAIN;
        return true;
    }
    public static ResourceLocation override(GeoSample g,ClimateBaseline c) {
        double t=c.meanTemperatureCelsius();
        if(g.water()==WaterCategory.RIVER||g.water()==WaterCategory.LAKE)
            return t<=0?BiomeIds.FROZEN_RIVER:BiomeIds.RIVER;
        if(g.water()==WaterCategory.DEEP_OCEAN||g.water()==WaterCategory.SHALLOW_OCEAN||g.water()==WaterCategory.INLAND_SEA)
            return ocean(t,g.water()==WaterCategory.DEEP_OCEAN);
        double mountain=g.metrics().mountainInfluence().map(Metric::value).orElse(0.0);
        if(g.water()==WaterCategory.COAST){
            if(mountain>.5)return BiomeIds.STONY_SHORE;
            return t<=0?BiomeIds.SNOWY_BEACH:BiomeIds.BEACH;
        }
        if(g.water()==WaterCategory.WETLAND&&t>0&&c.annualRainfallMm()>=900&&BiomeScorer.aridity(c)>=.9)
            return t>=20&&g.landform()==Landform.COAST?BiomeIds.MANGROVE_SWAMP:BiomeIds.SWAMP;
        if(g.landform()==Landform.MOUNTAIN||mountain>.55){
            if(t<=-5)return BiomeIds.FROZEN_PEAKS;
            if(t<0&&mountain>.8)return BiomeIds.JAGGED_PEAKS;
            if(t<3)return BiomeIds.SNOWY_SLOPES;
            return BiomeIds.STONY_PEAKS;
        }
        return null;
    }
    private static ResourceLocation ocean(double t,boolean deep){
        if(t<=-1)return deep?BiomeIds.DEEP_FROZEN_OCEAN:BiomeIds.FROZEN_OCEAN;
        if(t<8)return deep?BiomeIds.DEEP_COLD_OCEAN:BiomeIds.COLD_OCEAN;
        if(t>=24&&!deep)return BiomeIds.WARM_OCEAN;
        if(t>20)return deep?BiomeIds.DEEP_LUKEWARM_OCEAN:BiomeIds.LUKEWARM_OCEAN;
        return deep?BiomeIds.DEEP_OCEAN:BiomeIds.OCEAN;
    }
}
