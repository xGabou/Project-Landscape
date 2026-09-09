package baseline.reproduction.task6b;

import baseline.reproduction.Evidence;
import java.util.*;

/** Test-only, bounded observer. Enabled only outside the paired timed corpus. */
public final class LocalityTrace {
    public static volatile boolean enabled;
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    private static long dropped;
    public static void record(String kind,int x,int y,int z){
        if(!enabled)return;
        synchronized(rows){
            if(rows.size()>=500000){dropped++;return;}
            rows.add(Map.of("sequence",rows.size(),"kind",kind,"x",x,"y",y,"z",z,"thread",Thread.currentThread().getId()));
        }
    }
    public static void flush(Evidence out){
        enabled=false;
        synchronized(rows){out.row("task6b_locality_trace","events",List.copyOf(rows),"dropped",dropped,"scope","untimed real FULL chunk; surface_load means detached field constructed; owning tile=floorDiv(block,128)");rows.clear();dropped=0;}
    }
}
