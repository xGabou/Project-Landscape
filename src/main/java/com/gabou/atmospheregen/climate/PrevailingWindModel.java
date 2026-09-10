/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.climate;

import com.gabou.atmospheregen.api.climate.WindDirection;
import com.gabou.atmospheregen.config.BaselineClimateConfig;
import com.gabou.atmospheregen.generation.seed.*;

/** Broad circulation bands. Direction is downwind; this is not runtime weather. */
public final class PrevailingWindModel {
    private final LatitudeModel latitude;
    private final long seed;
    private final double regionalScale, variation;
    public PrevailingWindModel(GenerationSeedService seeds, BaselineClimateConfig.Planned config) {
        latitude=new LatitudeModel(config);seed=seeds.seed(SeedDomain.BASELINE_WIND);regionalScale=config.windBandScaleBlocks();variation=config.regionalVariationStrength();
    }
    public WindDirection sample(int x,int z) { return sample((double)x,(double)z); }
    public WindDirection sample(double x,double z) {
        double abs=latitude.absoluteDegrees(z), sign=Math.copySign(1,z);
        double base=abs<25? -1 : abs<60 ? 1 : -1;
        double meridional=abs<25 ? 0.25*sign : abs<60 ? -0.10*sign : 0.18*sign;
        double wave=variation*0.18*Math.sin((x+mix(seed))/regionalScale)+variation*0.12*Math.cos((z-mix(seed>>>1))/regionalScale);
        double angle=Math.atan2(meridional+wave,base);
        return new WindDirection(Math.cos(angle),Math.sin(angle));
    }
    private static long mix(long x){return MacroHash.mix(x);}
}
