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
        try(var file=new RecordingFile(path)){
            while(file.hasMoreEvents()){
                var event=file.readEvent();String type=event.getEventType().getName();
                if(type.equals("jdk.ObjectAllocationSample"))allocationWeight+=event.getLong("weight");
                if(type.equals("jdk.JavaMonitorEnter"))monitorEvents++;
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
        return Map.of("executionSamples",samples,"subsystemSamples",groups,"topStacks",top,
                "sampledAllocationWeightBytes",allocationWeight,"monitorEnterEvents",monitorEvents,
                "methodology","JFR profile settings; first recognized subsystem from leaf; sample counts are not exact CPU time; allocation weights are estimates");
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
