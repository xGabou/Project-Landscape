/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.geography.continent;

import com.gabou.projectlandscape.config.MacroGeographySettings;
import com.gabou.projectlandscape.generation.seed.*;

/** World-aligned cellular ownership. Pure coordinate hashes; no counters or global cache. */
public final class MacroSiteField {
    public record Site(int gridX,int gridZ,long id,double x,double z,double radius,double phase) {}
    private final long seed;
    private final MacroGeographySettings settings;
    public MacroSiteField(GenerationSeedService seeds,MacroGeographySettings settings) {
        this.seed=seeds.seed(SeedDomain.CONTINENT);this.settings=settings;
    }
    public Site at(double x,double z) {return site((int)Math.floor(x/settings.continentScaleBlocks()),(int)Math.floor(z/settings.continentScaleBlocks()));}
    public Site site(int gx,int gz) {
        long id=hash(seed,gx,gz);double scale=settings.continentScaleBlocks();
        return new Site(gx,gz,id,(gx+0.5+(unit(id)-0.5)*0.04)*scale,
            (gz+0.5+(unit(mix(id))-0.5)*0.04)*scale,
            scale*Math.sqrt(settings.landCoverageTarget()/Math.PI)*(0.97+0.06*unit(mix(id+1))),
            unit(mix(id+2))*Math.PI*2);
    }
    public static long hash(long seed,int x,int z) {return mix(seed^mix(((long)x<<32)|(z&0xffffffffL)));}
    public static long mix(long x) {x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;x=(x^(x>>>27))*0x94d049bb133111ebL;return x^(x>>>31);}
    public static double unit(long x) {return (x>>>11)*0x1.0p-53;}
}
