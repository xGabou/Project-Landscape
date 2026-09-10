/* Original exact projection experiment. All Rights Reserved. */
package baseline.reproduction.performance;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import com.gabou.atmospheregen.geography.terrain.PaGeographyProvider;
import java.nio.file.Path;
import java.util.*;
final class MarineProjectionExperiment {
    static void opportunity(GeneratorContext context,Path out)throws Exception {
        var macro=context.generator.getHeightmap().paBridge().macro();
        var path=Path.of("../../docs/task6b/evidence/runtime-runs/optimized-extended/task6b_locality_trace.json.gz");
        var tiles=new HashSet<Long>();var landTiles=new HashSet<Long>();var marineTiles=new HashSet<Long>();
        var coordinates=new HashSet<Long>();var classes=new TreeMap<String,Long>();
        long count=0,marine=0,land=0,coastal=0;
        try(var reader=new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(java.nio.file.Files.newInputStream(path)))) {
            var row=com.google.gson.JsonParser.parseReader(reader).getAsJsonArray().get(0).getAsJsonObject();
            if(row.get("dropped").getAsLong()!=0)throw new AssertionError("Truncated trace");
            for(var item:row.getAsJsonArray("events")) {
                var e=item.getAsJsonObject();if(!e.get("kind").getAsString().equals("profile_sample"))continue;
                int x=e.get("x").getAsInt(),z=e.get("z").getAsInt();long tile=((long)(x>>7)<<32)^((z>>7)&0xffffffffL);
                count++;tiles.add(tile);coordinates.add(((long)x<<32)^(z&0xffffffffL));
                var s=macro.sampleMacro(x,z);classes.merge(s.waterBody().name(),1L,Long::sum);
                if(s.land()){land++;landTiles.add(tile);}else{marine++;marineTiles.add(tile);}
                if(Math.abs(s.shorelineProfileBlocks())<=384)coastal++;
            }
        }
        var avoided=new HashSet<>(marineTiles);avoided.removeAll(landTiles);
        var result=new LinkedHashMap<String,Object>();
        result.put("source",path.toString());result.put("profileSamples",count);result.put("uniqueProfileCoordinates",coordinates.size());
        result.put("uniqueProfileTiles",tiles.size());result.put("marineSamples",marine);result.put("landSamples",land);
        result.put("coastalWithin384BlocksOverlappingLandMarine",coastal);result.put("waterClasses",classes);
        result.put("potentialEligibleMarineSamples",marine);result.put("marineSamplePercent",100.0*marine/count);
        result.put("uniqueTilesWithMarineSamples",marineTiles.size());result.put("uniqueTilesRequiredByLand",landTiles.size());
        result.put("maximumProfileTilesAvoided",avoided.size());result.put("maximumProfileTilesAvoidedPercent",100.0*avoided.size()/tiles.size());
        result.put("scope","Opportunity only: all non-LAND macro samples are candidates because finalization resets marine height and mountain fields. Tiles needed for other generation work may still be generated. Coastal count overlaps; not a third partition.");
        WorldgenBenchmark.write(out,"marine_opportunity.json",result);
    }
    static void run(GeneratorContext context,Path out)throws Exception {
        var geography=new PaGeographyProvider(context);var rows=new ArrayList<Object>();long compared=0;
        var categories=new TreeMap<String,Long>();
        var fixtures=new ArrayList<int[]>(List.of(new int[][]{{0,0},{-1,-1},{47,48},{-224,-96},{-64,-1172},{64,468},{-64,-3907},{-63,-66},{-14,-66},{234373,234373},{-234374,-234374}}));
        var selected=new HashSet<String>();var model=context.generator.getHeightmap().paBridge().macro();
        for(int z=-131072;z<=131072;z+=512)for(int x=-131072;x<=131072;x+=512){
            var m=model.sampleMacro(x,z);String tag=m.waterBody().name();
            if((tag.equals("INLAND_SEA")||Math.abs(m.shorelineProfileBlocks())<128)&&selected.add(tag))fixtures.add(new int[]{x>>7,z>>7});
        }
        for(int z=-8;z<=8;z++)for(int x=-8;x<=8;x++)for(var island:model.islands().islands(model.sites().site(x,z)))
            if(selected.add(island.kind().name()))fixtures.add(new int[]{((int)(island.x()+island.radius()))>>7,((int)island.z())>>7});
        // Original/extended fixtures plus negative tile boundaries and near coordinate limits.
        for(int[] point:fixtures) {
            var tile=context.cache.provide(point[0],point[1]);int ox=point[0]*128,oz=point[1]*128;long marine=0;
            var surface=geography.snapshotSurfaceTile(ox,oz);
            for(int dz=0;dz<128;dz++)for(int dx=0;dx<128;dx++) {
                int x=ox+dx,z=oz+dz;var macro=geography.macroProvider().sampleMacro(x,z);
                if(macro.land())continue;
                var cell=tile.lookup(x,z);
                float projected=(float)(context.levels.water-macro.marineDepthBlocks()/context.levels.worldHeight);
                float expectedElevation=cell.height*context.levels.worldHeight;
                float actualElevation=projected*context.levels.worldHeight;
                if(Float.floatToRawIntBits(cell.height)!=Float.floatToRawIntBits(projected)
                   ||Float.floatToRawIntBits(expectedElevation)!=Float.floatToRawIntBits(actualElevation)
                   ||Float.floatToRawIntBits(cell.mountainChainSelector())!=Float.floatToRawIntBits(Float.NaN)
                   ||Float.floatToRawIntBits(cell.mountainChainContribution())!=0
                   ||Float.floatToRawIntBits(cell.regionalMountainContribution())!=0
                   ||cell.riverMask!=1||cell.erosionMask
                   ||cell.terrain!=(macro.shelfFraction()>0.5?raccoonman.reterraforged.world.worldgen.cell.terrain.TerrainType.SHALLOW_OCEAN:raccoonman.reterraforged.world.worldgen.cell.terrain.TerrainType.DEEP_OCEAN))
                    throw new AssertionError("Marine projection at "+x+","+z);
                // Existing coarse-distance halo exceeds the macro domain near the world limit.
                // Keep raw finalized-field checks there; compare complete climate inputs inside its domain.
                if(Math.abs(x)<29980000&&Math.abs(z)<29980000){
                var distances=geography.distanceField().sample(x,z);
                var candidate=new com.gabou.atmospheregen.climate.ClimateGeography.Sample((double)actualElevation-context.levels.waterLevel,
                    distances.marineShoreline().valueBlocks(),distances.oceanWater().valueBlocks(),0,macro.waterBody());
                var reference=new com.gabou.atmospheregen.climate.ClimateGeography.Sample(surface.seaRelativeElevation(x,z),distances.marineShoreline().valueBlocks(),distances.oceanWater().valueBlocks(),surface.mountainInfluence(x,z),macro.waterBody());
                if(!reference.equals(candidate)||geography.marineSeaRelativeElevation(macro)!=candidate.elevationBlocks())throw new AssertionError("Downstream climate projection at "+x+","+z);
                var geo=geography.sample(x,z);
                if(geo.elevationBlockY()!=(double)actualElevation||geo.seaRelativeElevationBlocks()!=candidate.elevationBlocks()
                   ||geo.landform()!=com.gabou.atmospheregen.api.geography.Landform.OCEAN)
                    throw new AssertionError("Public geography projection");
                }
                categories.merge(macro.waterBody().name(),1L,Long::sum);
                if(Math.abs(macro.shorelineProfileBlocks())<=384)categories.merge("coastalWithin384",1L,Long::sum);
                marine++;compared++;
            }
            rows.add(Map.of("tileX",point[0],"tileZ",point[1],"marineCells",marine));
        }
        if(compared==0)throw new AssertionError("No marine coverage");
        WorldgenBenchmark.write(out,"marine_projection.json",Map.of("exact",true,"marineCellsCompared",compared,"tiles",rows,
            "categories",categories,"scope","All marine cell height/elevation bits, all three mountain fields, terrain, river/erosion masks, public elevation/landform, and complete downstream ClimateGeography.Sample including both distance fields; no production shortcut yet"));
    }
}
