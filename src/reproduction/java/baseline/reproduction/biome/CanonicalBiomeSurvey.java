/* Original development survey. All Rights Reserved. Not distributed. */
package baseline.reproduction.biome;

import baseline.reproduction.Evidence;
import com.gabou.atmospheregen.biome.*;
import com.gabou.atmospheregen.climate.*;
import com.gabou.atmospheregen.config.*;
import com.gabou.atmospheregen.generation.seed.*;
import com.gabou.atmospheregen.generation.version.*;
import com.gabou.atmospheregen.geography.terrain.*;
import net.minecraft.server.level.ServerLevel;
import raccoonman.reterraforged.world.worldgen.*;
import raccoonman.reterraforged.registries.RTFRegistries;
import java.util.*;

/** Actual filtered terrain and exact climate; this is not the macro elevation proxy survey. */
public final class CanonicalBiomeSurvey {
    private static final long[] SEEDS={8675309,4303642605L,42,-987654321,0,Long.MAX_VALUE,Long.MIN_VALUE+1,-1,1,12345,67890,314159,271828,20260908,-424242,987654321012345L};
    private static final BaselineClimateConfig.Planned CLIMATE=new BaselineClimateConfig.Planned(100000,0,.0065,.5,1,4096,.85,12000,24000,256,1,.10);
    public static void run(ServerLevel level,Evidence out)throws Exception{
        var state=(RTFRandomState)(Object)level.getChunkSource().randomState();
        var noises=level.registryAccess().lookupOrThrow(RTFRegistries.NOISE);
        for(long seed:SEEDS){
            var context=GeneratorContext.makeCached(state.preset(),noises,(int)seed,3,6,false);
            var seeds=new NamedSeedService(seed,level.dimension().location(),GenerationVersions.planned());
            PaGeographyInstallation.install(context,seeds,MacroGeographySettings.defaults(),6,false);
            try{
                var geography=new PaGeographyProvider(context);
                var input=new CanonicalClimateGeography(geography);
                var climate=new PaBaselineClimateProvider(input,seeds,CLIMATE);
                var resolver=new ClimateBiomeResolver(new VanillaBiomeCatalog(),seeds,new BiomeResolverConfig.Planned(64,1));
                var site=context.generator.getHeightmap().paBridge().macro().sites().site(0,0);
                // The same declared site per seed, snapped to the global 64-block lattice; no biome-based search.
                int side=17,step=64,originX=Math.floorDiv((int)site.x(),step)*step-512,
                        originZ=Math.floorDiv((int)site.z(),step)*step-512;
                String[] winners=new String[side*side];double[] margins=new double[winners.length];
                for(int iz=0;iz<side;iz++)for(int ix=0;ix<side;ix++){
                    int x=originX+ix*step,z=originZ+iz*step,index=iz*side+ix;
                    var g=BiomeGeography.sample(geography,x,z);var c=climate.sample(x,z);var r=resolver.explain(g,c);
                    winners[index]=r.winner().key().toString();margins[index]=r.second().map(s->r.winner().score()-s.score()).orElse(1.0);
                    out.row("canonical_biome_grid","seed",Long.toString(seed),"x",x,"z",z,"stepBlocks",step,
                            "biome",winners[index],"traits",r.winner().descriptor().traits().stream().map(Enum::name).sorted().toList(),
                            "temperatureC",c.meanTemperatureCelsius(),"rainfallMm",c.annualRainfallMm(),"evaporationMm",c.potentialEvaporationMm(),
                            "ratio",BiomeScorer.aridity(c),"rainShadow",c.rainShadow(),"elevationBlockY",g.elevationBlockY(),
                            "water",g.water().name(),"landform",g.landform().name(),"margin",margins[index]);
                }
                int edges=0,boundaries=0,isolated=0;boolean[] visited=new boolean[winners.length];
                for(int z=0;z<side;z++)for(int x=0;x<side;x++){
                    int i=z*side+x;
                    if(x+1<side){edges++;if(!winners[i].equals(winners[i+1]))boundaries++;}
                    if(z+1<side){edges++;if(!winners[i].equals(winners[i+side]))boundaries++;}
                    if(visited[i])continue;
                    var queue=new ArrayDeque<Integer>();queue.add(i);visited[i]=true;
                    int area=0,minX=x,maxX=x,minZ=z,maxZ=z;boolean censored=false;
                    while(!queue.isEmpty()){
                        int q=queue.removeFirst(),qx=q%side,qz=q/side;area++;
                        minX=Math.min(minX,qx);maxX=Math.max(maxX,qx);minZ=Math.min(minZ,qz);maxZ=Math.max(maxZ,qz);
                        censored|=qx==0||qz==0||qx==side-1||qz==side-1;
                        for(int[] delta:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                            int nx=qx+delta[0],nz=qz+delta[1];if(nx<0||nz<0||nx>=side||nz>=side)continue;
                            int n=nz*side+nx;if(!visited[n]&&winners[n].equals(winners[i])){visited[n]=true;queue.add(n);}
                        }
                    }
                    if(area==1&&!censored)isolated++;
                    out.row("canonical_biome_patches","seed",Long.toString(seed),"biome",winners[i],"sampledAreaBlocks2",area*step*step,
                            "minX",originX+minX*step,"maxX",originX+maxX*step,"minZ",originZ+minZ*step,"maxZ",originZ+maxZ*step,"boundaryCensored",censored);
                }
                out.row("canonical_coherence","seed",Long.toString(seed),"stepBlocks",step,"samples",winners.length,"adjacentEdges",edges,
                        "differentBiomeEdges",boundaries,"boundaryFraction",boundaries/(double)edges,"isolatedInteriorCells",isolated,
                        "lowMarginBelow002",Arrays.stream(margins).filter(m->m<.02).count(),"biomeDigest",Evidence.hash(Arrays.asList(winners)),
                        "climateCache",climate.cacheStats(),"surfaceCache",input.cacheStats());
                out.flush();
            }finally{context.cache.close();}
        }
    }
}
