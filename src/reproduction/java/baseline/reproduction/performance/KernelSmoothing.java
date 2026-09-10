/* Derived from ReTerraForged Smoothing, MIT License. Development experiment. */
package baseline.reproduction.performance;

import raccoonman.reterraforged.world.worldgen.densityfunction.tile.filter.*;
import raccoonman.reterraforged.world.worldgen.noise.NoiseUtil;

final class KernelSmoothing implements Filter {
    private final Smoothing source;
    KernelSmoothing(Smoothing source) { this.source=source; }
    public void apply(Filterable map,int seedX,int seedZ,int iterations) {
        int radius=NoiseUtil.round(source.smoothingRadius()+0.5F);
        float radiusSq=source.smoothingRadius()*source.smoothingRadius();
        int width=2*radius+1;
        int[] xs=new int[width*width],zs=new int[width*width];
        float[] weights=new float[width*width];
        int count=0;
        for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++) {
            float dist2=(float)(dx*dx+dz*dz);
            if(dist2<=radiusSq) { xs[count]=dx;zs[count]=dz;weights[count++]=1.0F-dist2/radiusSq; }
        }
        int limit=map.getBlockSize().total()-radius;
        while(iterations-->0)for(int z=radius;z<limit;z++)for(int x=radius;x<limit;x++) {
            var cell=map.getCellRaw(x,z);
            if(!cell.erosionMask) {
                float total=0.0F,sum=0.0F;
                for(int i=0;i<count;i++) {
                    var neighbor=map.getCellRaw(x+xs[i],z+zs[i]);
                    if(!neighbor.isAbsent()) { total+=neighbor.height*weights[i];sum+=weights[i]; }
                }
                if(sum>0.0F) {
                    float dif=cell.height-total/sum;
                    cell.height-=source.modifier().modify(cell,dif*source.smoothingRate());
                }
            }
        }
    }
}
