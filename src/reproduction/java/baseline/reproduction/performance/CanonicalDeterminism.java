/* Original V1 filtered-tile equivalence check. All Rights Reserved. */
package baseline.reproduction.performance;

import baseline.reproduction.Evidence;
import raccoonman.reterraforged.world.worldgen.*;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.Tile;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.generation.TileGenerator;
import java.nio.file.Path;
import java.util.*;

final class CanonicalDeterminism {
    static void run(GeneratorContext context,Path out)throws Exception {
        // Same V1 graph, halo and droplet order; reference retains the original modifier path.
        var reference=new TileGenerator(context.generator.getHeightmap(),new WorldFilters(context),3,1,6);
        var rows=new ArrayList<Object>();
        int[][] points={{0,0},{-1,-1},{47,48},{-224,-96},{-64,-1172},{64,468}};
        var parallel=new ArrayList<java.util.concurrent.CompletableFuture<Tile>>();
        for(var p:points)parallel.add(context.generator.generate(p[0],p[1]));
        for(int i=0;i<points.length;i++){
            var p=points[i];var actual=parallel.get(i).join();var expected=reference.generate(p[0],p[1]).join();
            String ad=digest(actual),ed=digest(expected);if(!ad.equals(ed))throw new AssertionError("V1 filtered tile mismatch "+Arrays.toString(p));
            rows.add(Map.of("tileX",p[0],"tileZ",p[1],"sha256",ad,"referenceSha256",ed,"exact",true));actual.close();expected.close();
        }
        WorldgenBenchmark.write(out,"determinism.json",Map.of("requestedProcessors",Runtime.getRuntime().availableProcessors(),"terrainWorkers",raccoonman.reterraforged.concurrent.ThreadPools.availableProcessors(),
                "tiles",rows,"scope","parallel V1 finalized tiles vs unchanged modifier reference on the same V1 graph; complete public cells and mountain capture bits"));
    }
    static String digest(Tile tile){
        var values=new ArrayList<Object>();tile.iterate((cell,x,z)->{
            values.add(Evidence.cell(cell,null));
            values.add(new int[]{Float.floatToRawIntBits(cell.mountainChainSelector()),Float.floatToRawIntBits(cell.mountainChainContribution()),Float.floatToRawIntBits(cell.regionalMountainContribution())});
        });return Evidence.hash(values);
    }
}
