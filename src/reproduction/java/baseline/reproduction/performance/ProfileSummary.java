/* Original development profiling evidence. All Rights Reserved. */
package baseline.reproduction.performance;

import jdk.jfr.consumer.*;
import java.nio.file.Path;
import java.util.*;

public final class ProfileSummary {
    public static void main(String[] args)throws Exception {
        java.nio.file.Files.writeString(Path.of(args[1]),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(read(Path.of(args[0]))),java.nio.file.StandardOpenOption.CREATE_NEW);
    }
    static Map<String,Object> read(Path path)throws Exception {
        var groups=new TreeMap<String,Long>();var stacks=new HashMap<String,Long>();long samples=0,allocationWeight=0,monitorEvents=0;
        var parkCounts=new TreeMap<String,Long>();var parkNanos=new TreeMap<String,Long>();
        var monitorCounts=new TreeMap<String,Long>();var monitorNanos=new TreeMap<String,Long>();var monitorMaxNanos=new TreeMap<String,Long>();
        var monitorPaths=new TreeMap<String,Long>();var monitorPathNanos=new TreeMap<String,Long>();
        var parkStacks=new HashMap<String,Long>();var eventCounts=new TreeMap<String,Long>();
        var parkPaths=new TreeMap<String,Long>();var parkPathNanos=new TreeMap<String,Long>();
        long gcPauseNanos=0,compilationThreadNanos=0;java.time.Instant first=null,last=null;
        try(var file=new RecordingFile(path)){
            while(file.hasMoreEvents()){
                var event=file.readEvent();String type=event.getEventType().getName();
                eventCounts.merge(type,1L,Long::sum);
                if(first==null||event.getStartTime().isBefore(first))first=event.getStartTime();
                if(last==null||event.getEndTime().isAfter(last))last=event.getEndTime();
                if(type.equals("jdk.GarbageCollection"))gcPauseNanos+=event.getDuration("sumOfPauses").toNanos();
                if(type.equals("jdk.Compilation"))compilationThreadNanos+=event.getDuration().toNanos();
                if(type.equals("jdk.ObjectAllocationSample"))allocationWeight+=event.getLong("weight");
                if(type.equals("jdk.JavaMonitorEnter")){
                    monitorEvents++;String monitor=event.getClass("monitorClass").getName();long nanos=event.getDuration().toNanos();
                    monitorCounts.merge(monitor,1L,Long::sum);monitorNanos.merge(monitor,nanos,Long::sum);monitorMaxNanos.merge(monitor,nanos,Math::max);
                    String pathGroup=waitPath(event);monitorPaths.merge(pathGroup,1L,Long::sum);monitorPathNanos.merge(pathGroup,nanos,Long::sum);
                }
                if(type.equals("jdk.ThreadPark")&&event.getStackTrace()!=null){
                    String group="other";
                    for(var frame:event.getStackTrace().getFrames()){
                        String owner=frame.getMethod().getType().getName();
                        if(owner.contains("generation.cache.ExactCache")){group="ExactCache single-flight";break;}
                        if(owner.contains("TileCache")||owner.contains("CacheEntry"))group="terrain cache";
                    }
                    parkCounts.merge(group,1L,Long::sum);parkNanos.merge(group,event.getDuration().toNanos(),Long::sum);
                    String parkPath=parkPath(event);parkPaths.merge(parkPath,1L,Long::sum);parkPathNanos.merge(parkPath,event.getDuration().toNanos(),Long::sum);
                    StringBuilder trace=new StringBuilder();for(var frame:event.getStackTrace().getFrames())trace.append(frame.getMethod().getType().getName()).append('.').append(frame.getMethod().getName()).append('\n');
                    parkStacks.merge(trace.toString(),event.getDuration().toNanos(),Long::sum);
                }
                if(!type.equals("jdk.ExecutionSample")||event.getStackTrace()==null)continue;
                samples++;String group="other";var stack=new StringBuilder();
                for(var frame:event.getStackTrace().getFrames()){
                    String name=frame.getMethod().getType().getName()+"."+frame.getMethod().getName();
                    if(stack.length()<1800)stack.append(name).append('\n');
                    if(group.equals("other"))group=classify(name);
                }
                groups.merge(group,1L,Long::sum);stacks.merge(stack.toString(),1L,Long::sum);
            }
        }
        var top=stacks.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(20)
                .map(e->Map.of("stack",e.getKey(),"samples",e.getValue())).toList();
        var result=new LinkedHashMap<String,Object>(Map.of("executionSamples",samples,"subsystemSamples",groups,"topStacks",top,
                "sampledAllocationWeightBytes",allocationWeight,"monitorEnterEvents",monitorEvents,"monitorCounts",monitorCounts,"monitorBlockedThreadNanos",monitorNanos,"monitorMaxNanos",monitorMaxNanos,
                "threadParks",Map.of("counts",parkCounts,"blockedThreadNanos",parkNanos),
                "methodology","JFR profile settings; first recognized subsystem from leaf; sample counts are not exact CPU time; allocation weights are estimates"));
        result.put("eventCounts",eventCounts);result.put("recordingStart",String.valueOf(first));result.put("recordingEnd",String.valueOf(last));
        result.put("recordedSpanNanos",first==null?0:java.time.Duration.between(first,last).toNanos());
        result.put("gcSumOfPausesNanos",gcPauseNanos);result.put("compilationThreadNanos",compilationThreadNanos);
        result.put("monitorPathCounts",monitorPaths);result.put("monitorPathBlockedThreadNanos",monitorPathNanos);
        result.put("parkPathCounts",parkPaths);result.put("parkPathBlockedThreadNanos",parkPathNanos);
        result.put("topParkStacksByBlockedThreadNanos",parkStacks.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(12).map(e->Map.of("stack",e.getKey(),"blockedThreadNanos",e.getValue())).toList());
        result.put("durationCaveat","Monitor/park and compilation durations sum across threads and may exceed wall time; park stacks containing ExactCache include nested terrain waits, not solely follower waits. Events below recording thresholds are absent. GC sums use GarbageCollection.sumOfPauses, not overlapping phase events.");
        return result;
    }
    private static String waitPath(RecordedEvent event){
        String group="other";if(event.getStackTrace()==null)return group;
        for(var frame:event.getStackTrace().getFrames()){
            String owner=frame.getMethod().getType().getName();
            if(owner.contains("generation.cache.ExactCache"))return "ExactCache call path";
            if(owner.contains("CoarseDistanceField"))group="distance field call path";
            else if(group.equals("other")&&(owner.contains("TileCache")||owner.contains("CacheEntry")))group="terrain cache call path";
        }return group;
    }
    private static String parkPath(RecordedEvent event){
        boolean terrain=false;
        for(var frame:event.getStackTrace().getFrames()){
            String owner=frame.getMethod().getType().getName();
            if(owner.contains("TileCache")||owner.contains("CacheEntry"))terrain=true;
            if(owner.contains("generation.cache.ExactCache"))return terrain?"terrain wait inside ExactCache loader":"ExactCache wait without inner terrain frame";
        }return terrain?"terrain wait outside ExactCache":"other";
    }
    static String classify(String n){
        if(n.contains("noise.")||n.contains("NoiseUtil"))return "terrain noise";
        if(n.contains("filter.")||n.contains("WorldErosion"))return "erosion/filtering";
        if(n.contains("TileGenerator")||n.contains("geography.terrain.PaTerrainBridge")||n.contains("cell."))return "tile construction";
        if(n.contains("snapshotSurfaceTile"))return "surface-field extraction";
        if(n.contains("CanonicalClimateGeography")||n.contains("CoarseDistanceField"))return "climate geography sampling";
        if(n.contains("BaselineClimateModel")||n.contains("PrevailingWind")||n.contains("LatitudeModel"))return "climate equations";
        if(n.contains("BiomeScorer")||n.contains("ClimateBiomeResolver")||n.contains("GeographicBiomeRules"))return "biome resolver";
        if(n.contains("cache.")||n.contains("LinkedHashMap"))return "cache lookup";
        if(n.contains("feature.")||n.contains("structure."))return "features/structures";
        if(n.contains("net.minecraft.world.level.chunk")||n.contains("net.minecraft.world.level.levelgen"))return "Minecraft chunk pipeline";
        return "other";
    }
}
