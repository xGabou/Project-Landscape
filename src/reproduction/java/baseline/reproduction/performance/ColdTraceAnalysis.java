/* Reproduction-only classification of retained runtime coordinates. */
package baseline.reproduction.performance;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
final class ColdTraceAnalysis {
    static void run(GeneratorContext context,Path out)throws Exception {
        Path source=Path.of("../../build/task6c-final-full-8");
        var traces=JsonParser.parseString(Files.readString(source.resolve("task6b_locality_trace.json"))).getAsJsonArray();
        var queries=JsonParser.parseString(Files.readString(source.resolve("task6c_cold_queries.json"))).getAsJsonArray();
        var macro=context.generator.getHeightmap().paBridge().macro();var results=new ArrayList<Object>();
        for(int index=0;index<traces.size();index++){
            var trace=traces.get(index).getAsJsonObject();if(trace.get("dropped").getAsLong()!=0)throw new AssertionError("Truncated trace");
            var coordinates=new HashSet<Long>();var tiles=new HashSet<Long>();var landTiles=new HashSet<Long>();var loadTiles=new HashSet<Long>();
            long samples=0,marine=0,land=0,coastal=0,loads=0,biomeCalls=0;
            for(var item:trace.getAsJsonArray("events")){
                var e=item.getAsJsonObject();String kind=e.get("kind").getAsString();int x=e.get("x").getAsInt(),z=e.get("z").getAsInt();
                long tile=((long)(x>>7)<<32)^((z>>7)&0xffffffffL);
                if(kind.equals("biome_quart")){biomeCalls++;continue;}
                if(kind.equals("surface_load")){loads++;loadTiles.add(tile);continue;}
                if(!kind.equals("profile_sample"))continue;
                samples++;coordinates.add(((long)x<<32)^(z&0xffffffffL));tiles.add(tile);
                var s=macro.sampleMacro(x,z);if(s.land()){land++;landTiles.add(tile);}else marine++;
                if(Math.abs(s.shorelineProfileBlocks())<=384)coastal++;
            }
            var row=new LinkedHashMap<String,Object>();var q=queries.get(index).getAsJsonObject();
            row.put("query",q);row.put("profileSamples",samples);row.put("biomeQuartCalls",biomeCalls);row.put("uniqueProfileCoordinates",coordinates.size());
            row.put("uniqueOwningTiles",tiles.size());row.put("eligibleMarineSamples",marine);row.put("landSamples",land);row.put("coastalWithin384Overlapping",coastal);
            row.put("uniqueTilesRequiredByLand",landTiles.size());row.put("maximumProfileTilesAvoidable",tiles.size()-landTiles.size());row.put("surfaceLoads",loads);row.put("uniqueLoadTiles",loadTiles.size());
            row.put("surfaceHits",q.getAsJsonObject("surfaceAfter").get("hits").getAsLong()-q.getAsJsonObject("surfaceBefore").get("hits").getAsLong());
            row.put("surfaceMisses",q.getAsJsonObject("surfaceAfter").get("misses").getAsLong()-q.getAsJsonObject("surfaceBefore").get("misses").getAsLong());
            row.put("terrainTilesGenerated",q.getAsJsonObject("afterCounters").get("tileGenerationCalls").getAsLong()-q.getAsJsonObject("beforeCounters").get("tileGenerationCalls").getAsLong());
            results.add(row);
        }
        WorldgenBenchmark.write(out,"cold_query_analysis.json",Map.of("source",source.toString(),"rows",results,"scope","Diagnostic traces separate from release timing corpus. Coastal count overlaps land/marine. Terrain generation also serves FULL/prerequisite chunks; surface misses count detached surface loads only."));
    }
}
