/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.climate;

import com.gabou.atmospheregen.api.climate.*;
import com.gabou.atmospheregen.api.geography.MacroGeographyProvider;
import com.gabou.atmospheregen.config.BaselineClimateConfig;
import com.gabou.atmospheregen.generation.seed.*;

/** Deterministic geography-to-climate equations. It never writes back to geography. */
public final class BaselineClimateModel {
    private record Profile(double moisture,double marineFetch,double orographicRainfall,double shadow,int points,double maximumRise) {}
    private final BaselineClimateConfig.Planned config;
    private final LatitudeModel latitude;
    private final PrevailingWindModel winds;
    private final long temperatureSeed, precipitationSeed;
    public BaselineClimateModel(GenerationSeedService seeds, BaselineClimateConfig.Planned config) {
        this.config=config;latitude=new LatitudeModel(config);winds=new PrevailingWindModel(seeds,config);
        temperatureSeed=seeds.seed(SeedDomain.BASELINE_TEMPERATURE);precipitationSeed=seeds.seed(SeedDomain.BASELINE_PRECIPITATION);
    }
    public ClimateBaseline sample(ClimateGeography geography,int x,int z) { return breakdown(geography,x,z).baseline(); }
    public ClimateBaseline sampleWithWind(ClimateGeography geography,int x,int z,WindDirection wind) { return breakdownWithWind(geography,x,z,wind).baseline(); }
    public Result breakdown(ClimateGeography geography,int x,int z) {
        return breakdownWithWind(geography,x,z,winds.sample(x,z));
    }
    public Result breakdownWithWind(ClimateGeography geography,int x,int z,WindDirection wind) {
        var here=geography.sample(x,z);double lat=latitude.degrees(z),abs=Math.abs(lat);
        double latitudeTemperature=28.0-42.0*Math.pow(abs/90.0,1.15);
        double altitude=Math.max(0,here.elevationBlocks())*config.lapseCelsiusPerBlock();
        double oceanExposure=oceanExposure(here);double oceanTarget=17.0-0.20*abs;
        double oceanModeration=(oceanTarget-latitudeTemperature)*config.oceanInfluence()*oceanExposure;
        double continentality=here.marineClass()==MacroGeographyProvider.MarineClass.LAND
            ? 1.0-Math.exp(-here.coastDistanceBlocks()/6000.0*config.continentalityStrength()) : 0;
        double continentalityContribution=-3.0*continentality;
        long regionalKey=temperatureSeed^((long)Math.floor(x/4096.0)*0x9e3779b97f4a7c15L^((long)Math.floor(z/4096.0)*0xc2b2ae3d27d4eb4fL));
        double regional=(MacroHash.unit(regionalKey)*2.0-1.0)*config.regionalVariationStrength()*2.0;
        double temperature=latitudeTemperature-altitude+oceanModeration+continentalityContribution+regional;
        Profile profile=profile(geography,x,z,wind);
        long moistureKey=precipitationSeed^((long)Math.floor(x/4096.0)*0x9e3779b97f4a7c15L^((long)Math.floor(z/4096.0)*0xc2b2ae3d27d4eb4fL));
        double rainfallVariation=(MacroHash.unit(moistureKey)*2.0-1.0)*config.regionalVariationStrength();
        double rainfall=Math.max(0,(120.0+1250.0*profile.moisture()+420.0*profile.orographicRainfall())*(1.0+rainfallVariation));
        double evaporation=Math.max(0,520.0+Math.max(-5,temperature)*18.0*config.evaporationStrength()
            +continentality*120.0*config.evaporationStrength());
        double moistureIndex=rainfall/(rainfall+evaporation+1.0);
        return new Result(new ClimateBaseline(temperature,rainfall,moistureIndex,evaporation,profile.shadow(),java.util.Optional.of(wind)),
            new ClimateBreakdown(lat,latitudeTemperature,-altitude,oceanModeration,continentalityContribution,regional,
                profile.moisture(),profile.marineFetch(),profile.orographicRainfall(),profile.shadow(),rainfall,evaporation,moistureIndex,wind,profile.points(),profile.maximumRise()));
    }
    private double oceanExposure(ClimateGeography.Sample s) {
        if(s.marineClass()==MacroGeographyProvider.MarineClass.MAJOR_OCEAN)return 1;
        if(s.marineClass()==MacroGeographyProvider.MarineClass.INLAND_SEA)return .60;
        if(s.marineClass()==MacroGeographyProvider.MarineClass.COASTAL_WATER)return .75;
        return Math.exp(-s.oceanDistanceBlocks()/8000.0);
    }
    private Profile profile(ClimateGeography g,int x,int z,WindDirection wind) {
        int points=0;double moisture=0,fetch=0,shadow=0,orographic=0,maxRise=0,previous=Double.NaN;
        double step=config.climateProfileStep(),distance=config.climateProfileDistance();
        for(double d=distance;d>=0;d-=step) {
            int sx=(int)Math.round(x-wind.x()*d),sz=(int)Math.round(z-wind.z()*d);var s=g.sample(sx,sz);points++;
            double source=switch(s.marineClass()) {case MAJOR_OCEAN -> .95;case COASTAL_WATER -> .75;case INLAND_SEA -> .55;case LAND -> 0;};
            if(source>0){moisture+= (source-moisture)*Math.min(.45,step/1800.0);fetch=Math.max(fetch,source);}
            else moisture*=Math.exp(-step/24000.0);
            if(Double.isFinite(previous)) {
                double rise=s.elevationBlocks()-previous;
                if(rise>0 && s.marineClass()==MacroGeographyProvider.MarineClass.LAND) {
                    maxRise=Math.max(maxRise,rise);
                    double loss=1-Math.exp(-rise/220.0*config.orographicStrength());
                    double precip=moisture*loss;moisture=Math.max(0,moisture-precip);orographic+=precip;
                    shadow=Math.min(1,shadow+precip);
                } else shadow*=Math.exp(-step/config.rainShadowRecoveryDistance());
            }
            previous=s.elevationBlocks();
        }
        return new Profile(Math.min(1,moisture),fetch,Math.min(1,orographic),Math.min(1,shadow),points,maxRise);
    }
    public record Result(ClimateBaseline baseline,ClimateBreakdown breakdown) {}
}
