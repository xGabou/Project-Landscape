/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package baseline.reproduction.geography;

import com.gabou.projectlandscape.api.geography.MacroGeographyProvider;
import com.gabou.projectlandscape.geography.ocean.MajorOceanCorridor;
import java.util.*;

/** Offline measurements call the production predicate. No duplicate topology implementation. */
public final class TopologyMeasurements {
    private TopologyMeasurements() {}
    public record Component(int label, long areaBlocksSquared, int minX, int minZ, int maxX, int maxZ,
            double majorAxisBlocks, boolean boundaryCensored, Set<Long> intersectingSiteIds) {}
    public record Window(int originX, int originZ, int extentBlocks, int stepBlocks,
            double landFraction, double oceanFraction, double inlandSeaFraction,
            double marchingSquaresCoastlineBlocks, List<Component> components) {}
    public record Width(long corridorId, long firstSiteId, long secondSiteId, double x, double z,
            double angleRadians, Double widthBlocks, double crossingUncertaintyBlocks,
            boolean opposingMajorLandFound, boolean obstructed, boolean violation) {}

    public static Window window(MacroGeographyProvider provider, int ox, int oz, int extent, int step) {
        if (extent <= 0 || step <= 0 || extent % step != 0) throw new IllegalArgumentException("Extent must be a positive multiple of step");
        int n = extent / step + 1;
        boolean[] land = new boolean[n*n]; long[] sites = new long[n*n];
        long landCount = 0, inland = 0;
        for (int z=0; z<n; z++) for (int x=0; x<n; x++) {
            var s=provider.sampleMacro(ox+x*step, oz+z*step); int k=z*n+x;
            land[k]=s.land(); sites[k]=s.siteId();
            if (s.land()) landCount++;
            if (s.waterBody()==MacroGeographyProvider.MarineClass.INLAND_SEA) inland++;
        }
        double coast=0;
        for(int z=0;z<n-1;z++) for(int x=0;x<n-1;x++) {
            int k=z*n+x;
            int mask=(land[k]?1:0)|(land[k+1]?2:0)|(land[k+n+1]?4:0)|(land[k+n]?8:0);
            // Mid-edge marching squares. Ambiguous diagonals retain two separate contour segments.
            coast+=switch(mask) {case 0,15 -> 0; case 3,6,9,12 -> step;
                case 5,10 -> step*Math.sqrt(2); default -> step/Math.sqrt(2);};
        }
        int[] labels=new int[n*n], queue=new int[n*n]; List<Component> components=new ArrayList<>();
        for(int start=0;start<land.length;start++) {
            if(!land[start]||labels[start]!=0)continue;
            int label=components.size()+1, read=0, size=1;queue[0]=start;labels[start]=label;
            int minX=n,minZ=n,maxX=0,maxZ=0;boolean censored=false;
            double sx=0,sz=0,sxx=0,szz=0,sxz=0;Set<Long> ids=new TreeSet<>();
            while(read<size) {
                int k=queue[read++],x=k%n,z=k/n;
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
                censored|=x==0||z==0||x==n-1||z==n-1; ids.add(sites[k]);
                sx+=x;sz+=z;sxx+=(double)x*x;szz+=(double)z*z;sxz+=(double)x*z;
                if(x>0)size=enqueue(k-1,label,land,labels,queue,size);
                if(x<n-1)size=enqueue(k+1,label,land,labels,queue,size);
                if(z>0)size=enqueue(k-n,label,land,labels,queue,size);
                if(z<n-1)size=enqueue(k+n,label,land,labels,queue,size);
            }
            double vx=sxx/size-sx*sx/size/size,vz=szz/size-sz*sz/size/size,cov=sxz/size-sx*sz/size/size;
            double eigen=(vx+vz+Math.sqrt((vx-vz)*(vx-vz)+4*cov*cov))/2;
            components.add(new Component(label,(long)size*step*step,ox+minX*step,oz+minZ*step,
                ox+maxX*step,oz+maxZ*step,4*Math.sqrt(eigen)*step,censored,Collections.unmodifiableSet(ids)));
        }
        double total=(double)n*n;
        return new Window(ox,oz,extent,step,landCount/total,(total-landCount-inland)/total,inland/total,coast,List.copyOf(components));
    }
    private static int enqueue(int k,int label,boolean[] land,int[] labels,int[] queue,int size) {
        if(land[k]&&labels[k]==0){labels[k]=label;queue[size++]=k;}return size;
    }

    /** 32-block search, 1-block crossing refinement; also tests nearby oblique orientations. */
    public static List<Width> widths(MacroGeographyProvider provider, MajorOceanCorridor corridor, double maxSearch) {
        List<Width> result=new ArrayList<>();
        for(int section=-4;section<=4;section++) for(double angle:new double[]{-0.12,0,0.12}) {
            double t=section*corridor.halfLengthBlocks()/4;
            double x=corridor.centerX()-corridor.normalZ()*t,z=corridor.centerZ()+corridor.normalX()*t;
            double nx=corridor.normalX()*Math.cos(angle)-corridor.normalZ()*Math.sin(angle);
            double nz=corridor.normalX()*Math.sin(angle)+corridor.normalZ()*Math.cos(angle);
            Crossing a=cross(provider,x,z,-nx,-nz,maxSearch,corridor.firstSiteId());
            Crossing b=cross(provider,x,z,nx,nz,maxSearch,corridor.secondSiteId());
            boolean found=a.found&&b.found;Double width=found?a.distance+b.distance:null;
            result.add(new Width(corridor.id(),corridor.firstSiteId(),corridor.secondSiteId(),x,z,angle,width,2,
                found,a.obstructed||b.obstructed,found&&width+2<corridor.requiredWidthBlocks()));
        }
        return result;
    }
    private record Crossing(double distance, boolean found, boolean obstructed) {}
    private static Crossing cross(MacroGeographyProvider p,double x,double z,double nx,double nz,double limit,long site) {
        boolean obstruction=false;
        for(double d=0;d<=limit;d+=32) {
            var sample=p.sampleMacro(x+nx*d,z+nz*d);
            if(!sample.land())continue;
            if(sample.siteId()!=site||sample.islandClass()!=MacroGeographyProvider.IslandClass.NONE){obstruction=true;continue;}
            double lo=Math.max(0,d-32),hi=d;
            while(hi-lo>1){double mid=(lo+hi)/2;if(p.sampleMacro(x+nx*mid,z+nz*mid).land())hi=mid;else lo=mid;}
            return new Crossing((lo+hi)/2,true,obstruction);
        }
        return new Crossing(Double.NaN,false,obstruction);
    }
}
