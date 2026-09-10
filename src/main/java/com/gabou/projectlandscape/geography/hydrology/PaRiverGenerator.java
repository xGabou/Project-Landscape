/* Adapts ReTerraForged river-network construction, Copyright (c) 2023 ReTerraForged,
 * MIT License. See LICENSE. Original shoreline search and V1 binding are ARR. */
package com.gabou.projectlandscape.geography.hydrology;

import com.gabou.projectlandscape.geography.terrain.PaTerrainBridge;
import com.gabou.projectlandscape.geography.continent.MacroSiteField;
import com.gabou.projectlandscape.generation.seed.*;
import java.util.*;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.cell.rivermap.Rivermap;
import raccoonman.reterraforged.world.worldgen.cell.rivermap.gen.GenWarp;
import raccoonman.reterraforged.world.worldgen.cell.rivermap.river.*;

/** Bounded first-water roots; retained synthetic branching/carving, not a drainage solver. */
public final class PaRiverGenerator extends BaseRiverGenerator<PaTerrainBridge> {
    public record Mouth(double x,double z,String target,long siteId) {}
    private record Entry(Rivermap map,List<Mouth> mouths) {}
    private final long topologySeed;
    private final Map<Long,Entry> cache=new LinkedHashMap<>(128,0.75f,true);
    public PaRiverGenerator(PaTerrainBridge continent,GeneratorContext context,GenerationSeedService seeds) {
        super(continent,context);topologySeed=seeds.seed(SeedDomain.HYDROLOGY);
        continentScale=(int)continent.macro().settings().continentScaleBlocks();
    }
    public synchronized Rivermap map(int x,int z){return entry(x,z).map;}
    public synchronized List<Mouth> mouths(int x,int z){return entry(x,z).mouths;}
    private Entry entry(int x,int z) {
        var site=continent.macro().sites().at(x,z);Entry found=cache.get(site.id());if(found!=null)return found;
        Random random=new Random(MacroSiteField.mix(topologySeed^site.id()));
        List<Network.Builder> roots=new ArrayList<>();List<Mouth> mouths=new ArrayList<>();
        for(int i=0;i<count;i++) {
            double angle=(i+random.nextDouble()*0.6)*2*Math.PI/Math.max(1,count);
            double nx=Math.cos(angle),nz=Math.sin(angle),start=site.radius()*(0.35+random.nextDouble()*0.15);
            double sx=site.x()+nx*start,sz=site.z()+nz*start;
            if(!continent.macro().sampleMacro(sx,sz).land())continue;
            // Stop at first marine body, including an inland sea; never assume one exit from center.
            double hit=-1;
            for(double d=32;d<=continentScale*2;d+=32) {
                var s=continent.macro().sampleMacro(sx+nx*d,sz+nz*d);
                if(!s.land()&&s.shorelineProfileBlocks()<-96){hit=d;break;}
            }
            if(hit<0)throw new IllegalStateException("V1 river found no marine target within bounded search at "+sx+","+sz);
            float ex=(float)(sx+nx*hit),ez=(float)(sz+nz*hit);
            var target=continent.macro().sampleMacro(ex,ez);
            if(target.land())throw new IllegalStateException("V1 river endpoint rounded onto land");
            River river=new River((float)sx,(float)sz,ex,ez);
            RiverCarver.Settings settings=creatSettings(random);settings.fadeIn=main.fade;settings.valleySize=275*River.MAIN_VALLEY.next(random);
            var root=Network.builder(new RiverCarver(river,RiverWarp.create(0.1f,0.85f,random),main,settings,levels));
            roots.add(root);mouths.add(new Mouth(ex,ez,target.waterBody().name(),site.id()));
        }
        // Disable the legacy whole-map coordinate warp: it otherwise moves an already solved shoreline.
        for(var root:roots)generateForks(root,River.MAIN_SPACING,fork,random,GenWarp.EMPTY,roots,0);
        for(var root:roots)generateWetlands(root,random);
        var map=new Rivermap((int)site.x(),(int)site.z(),roots.stream().map(Network.Builder::build).toArray(Network[]::new),GenWarp.EMPTY);
        var result=new Entry(map,List.copyOf(mouths));if(cache.size()>=128)cache.remove(cache.keySet().iterator().next());cache.put(site.id(),result);return result;
    }
    public synchronized int retainedEntries(){return cache.size();}
}
