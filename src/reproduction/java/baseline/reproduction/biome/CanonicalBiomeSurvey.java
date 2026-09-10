/* Original development survey. All Rights Reserved. Not distributed. */
package baseline.reproduction.biome;

import baseline.reproduction.Evidence;
import com.gabou.projectlandscape.biome.*;
import com.gabou.projectlandscape.climate.*;
import com.gabou.projectlandscape.config.*;
import com.gabou.projectlandscape.generation.seed.*;
import com.gabou.projectlandscape.generation.version.*;
import com.gabou.projectlandscape.geography.terrain.*;
import net.minecraft.server.level.ServerLevel;
import raccoonman.reterraforged.world.worldgen.*;
import raccoonman.reterraforged.registries.RTFRegistries;
import java.util.*;

/** Actual filtered terrain and exact climate; this is not the macro elevation proxy survey. */
public final class CanonicalBiomeSurvey {
    private static final long[] SEEDS={8675309,4303642605L,67890,20260908};
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
                var resolver=new ClimateBiomeResolver(new VanillaBiomeCatalog(),seeds,new BiomeResolverConfig.Planned(64,1));
                var site=context.generator.getHeightmap().paBridge().macro().sites().site(0,0);
                // The same declared site per seed, snapped to the global lattice; no biome-based search.
                int side=3,step=4,originX=-4,originZ=-4;
                var targets=new LinkedHashSet<PreparedCanonicalInputs.Point>();
                for(int z=0;z<side;z++)for(int x=0;x<side;x++)targets.add(new PreparedCanonicalInputs.Point(originX+x*step,originZ+z*step));
                int[][] broad=seed==8675309||seed==4303642605L?
                    new int[][]{{-8192,-500000},{-8192,-150000},{8192,150000},{8192,60000},{0,-2000000},{-8192,0},{8192,0},{-28672,-12288}}:
                    new int[][]{{seed==67890?-24576:-8192,-8192}};
                for(var p:broad)targets.add(new PreparedCanonicalInputs.Point(p[0],p[1]));
                var input=new PreparedCanonicalInputs(geography,seeds,CLIMATE,targets,out);
                var climate=new PaBaselineClimateProvider(input,seeds,CLIMATE);
                CanonicalTargetChecks.run(seed,targets,input,climate,resolver,seeds,CLIMATE,out);
                String[] winners=new String[side*side];double[] margins=new double[winners.length];
                for(int iz=0;iz<side;iz++){
                  for(int ix=0;ix<side;ix++){
                    int x=originX+ix*step,z=originZ+iz*step,index=iz*side+ix;
                    var g=input.geography.get(new PreparedCanonicalInputs.Point(x,z));var c=climate.sample(x,z);var r=resolver.explain(g,c);
                    winners[index]=r.winner().key().toString();margins[index]=r.second().map(s->r.winner().score()-s.score()).orElse(1.0);
                    out.row("canonical_biome_grid","seed",Long.toString(seed),"x",x,"z",z,"stepBlocks",step,
                            "biome",winners[index],"traits",r.winner().descriptor().traits().stream().map(Enum::name).sorted().toList(),
                            "temperatureC",c.meanTemperatureCelsius(),"rainfallMm",c.annualRainfallMm(),"evaporationMm",c.potentialEvaporationMm(),
                            "ratio",BiomeScorer.aridity(c),"rainShadow",c.rainShadow(),"elevationBlockY",g.elevationBlockY(),
                            "water",g.water().name(),"landform",g.landform().name(),"margin",margins[index]);
                  }
                  out.flush();
                  System.out.println("Task6 canonical seed="+seed+" row="+(iz+1)+"/"+side);
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
                        "climateCache",climate.cacheStats());
                out.flush();
            }finally{context.cache.close();}
        }
    }
}
