/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.geography.ocean;

import com.gabou.atmospheregen.api.geography.MacroGeographyProvider;
import java.util.*;

/** Bounded world-scoped Euclidean distance to classified lattice nodes, never eroded terrain. */
public final class CoarseDistanceField {
    public static final int STEP=128, CORE=32, HALO=64, SIZE=CORE+2*HALO, CAPACITY=32;
    public static final double LIMIT=(HALO-2)*STEP;
    public enum Quality { SAMPLED_LATTICE_ESTIMATE, CENSORED_SAMPLED_LATTICE_LOWER_BOUND }
    public record Distance(double valueBlocks,int resolutionBlocks,double querySnappingErrorBlocks,Quality quality) {}
    public record Distances(Distance marineShoreline,Distance oceanWater) {}
    private record Tile(float[] coast,float[] ocean) {}
    private final MacroGeographyProvider provider;
    private final Map<Long,Tile> cache=new LinkedHashMap<>(CAPACITY,0.75f,true);
    private long hits,misses;
    private boolean closed;
    public CoarseDistanceField(MacroGeographyProvider provider){this.provider=provider;}
    public Distances sample(int x,int z) {
        int gx=(int)Math.round((double)x/STEP),gz=(int)Math.round((double)z/STEP);
        int tx=Math.floorDiv(gx,CORE),tz=Math.floorDiv(gz,CORE);
        Tile tile=tile(tx,tz);int i=Math.floorMod(gz,CORE)*CORE+Math.floorMod(gx,CORE);
        double snap=Math.hypot(x-(double)gx*STEP,z-(double)gz*STEP);
        return new Distances(metric(tile.coast[i],snap),metric(tile.ocean[i],snap));
    }
    private Distance metric(double value,double snap) {
        return new Distance(value,STEP,snap,value>=LIMIT?Quality.CENSORED_SAMPLED_LATTICE_LOWER_BOUND:Quality.SAMPLED_LATTICE_ESTIMATE);
    }
    private synchronized Tile tile(int tx,int tz) {
        if(closed)throw new IllegalStateException("Distance field context is disposed");
        long key=((long)tx<<32)|(tz&0xffffffffL);Tile tile=cache.get(key);
        if(tile!=null){hits++;return tile;}misses++;
        boolean[] land=new boolean[SIZE*SIZE],marine=new boolean[SIZE*SIZE],ocean=new boolean[SIZE*SIZE];
        for(int z=0;z<SIZE;z++)for(int x=0;x<SIZE;x++) {
            var s=provider.sampleMacro((double)(tx*CORE+x-HALO)*STEP,(double)(tz*CORE+z-HALO)*STEP);
            int i=z*SIZE+x;land[i]=s.land();marine[i]=!s.land();
            ocean[i]=marine[i]&&s.waterBody()!=MacroGeographyProvider.MarineClass.INLAND_SEA;
        }
        double[] dl=squaredDistance(land,SIZE,SIZE),dm=squaredDistance(marine,SIZE,SIZE),dOcean=squaredDistance(ocean,SIZE,SIZE);
        float[] coast=new float[CORE*CORE],ow=new float[CORE*CORE];
        for(int z=0;z<CORE;z++)for(int x=0;x<CORE;x++) {
            int i=(z+HALO)*SIZE+x+HALO,k=z*CORE+x;
            coast[k]=(float)Math.min(LIMIT,Math.sqrt(land[i]?dm[i]:dl[i])*STEP);
            ow[k]=(float)Math.min(LIMIT,Math.sqrt(dOcean[i])*STEP);
        }
        tile=new Tile(coast,ow);if(cache.size()>=CAPACITY)cache.remove(cache.keySet().iterator().next());cache.put(key,tile);return tile;
    }
    /** Exact squared Euclidean transform of the supplied finite lattice, in lattice units. */
    public static double[] squaredDistance(boolean[] targets,int width,int height) {
        double[] data=new double[targets.length];for(int i=0;i<data.length;i++)data[i]=targets[i]?0:1e15;
        int n=Math.max(width,height);double[] f=new double[n],d=new double[n],bounds=new double[n+1];int[] v=new int[n];
        for(int z=0;z<height;z++){System.arraycopy(data,z*width,f,0,width);transform(f,d,v,bounds,width);System.arraycopy(d,0,data,z*width,width);}
        for(int x=0;x<width;x++){for(int z=0;z<height;z++)f[z]=data[z*width+x];transform(f,d,v,bounds,height);for(int z=0;z<height;z++)data[z*width+x]=d[z];}
        return data;
    }
    private static void transform(double[] f,double[] d,int[] v,double[] bounds,int n) {
        int k=0;v[0]=0;bounds[0]=Double.NEGATIVE_INFINITY;bounds[1]=Double.POSITIVE_INFINITY;
        for(int q=1;q<n;q++) {
            double s=((f[q]+(double)q*q)-(f[v[k]]+(double)v[k]*v[k]))/(2.0*(q-v[k]));
            while(s<=bounds[k]){k--;s=((f[q]+(double)q*q)-(f[v[k]]+(double)v[k]*v[k]))/(2.0*(q-v[k]));}
            v[++k]=q;bounds[k]=s;bounds[k+1]=Double.POSITIVE_INFINITY;
        }
        k=0;for(int q=0;q<n;q++){while(bounds[k+1]<q)k++;d[q]=(double)(q-v[k])*(q-v[k])+f[v[k]];}
    }
    public synchronized Map<String,Long> cacheStats(){return Map.of("entries",(long)cache.size(),"capacity",(long)CAPACITY,"hits",hits,"misses",misses,"retainedArrayBytes",(long)cache.size()*CORE*CORE*8);}
    public synchronized void clear(){cache.clear();}
    public synchronized void close(){closed=true;cache.clear();}
}
