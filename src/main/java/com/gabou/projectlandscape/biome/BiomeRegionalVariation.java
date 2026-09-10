/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.biome;

/** Smooth world-aligned value field. No random state or registry numeric IDs. */
public final class BiomeRegionalVariation {
    private final long seed;
    private final double scale;
    public BiomeRegionalVariation(long seed, double scale) { this.seed=seed; this.scale=scale; }
    public double sample(int x, int z, int stableDescriptorHash) {
        double px=x/scale, pz=z/scale;
        long ix=(long)Math.floor(px), iz=(long)Math.floor(pz);
        double fx=smooth(px-ix), fz=smooth(pz-iz);
        double a=mix(value(ix,iz,stableDescriptorHash),value(ix+1,iz,stableDescriptorHash),fx);
        double b=mix(value(ix,iz+1,stableDescriptorHash),value(ix+1,iz+1,stableDescriptorHash),fx);
        return mix(a,b,fz);
    }
    private double value(long x,long z,int id) {
        long h=seed ^ x*0x9e3779b97f4a7c15L ^ z*0xc2b2ae3d27d4eb4fL ^ id;
        h=(h^(h>>>30))*0xbf58476d1ce4e5b9L;
        h=(h^(h>>>27))*0x94d049bb133111ebL;
        return ((h^(h>>>31))>>>11)*0x1.0p-53*2-1;
    }
    private static double smooth(double t){return t*t*(3-2*t);}
    private static double mix(double a,double b,double t){return a+(b-a)*t;}
}
