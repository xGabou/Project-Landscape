package baseline.reproduction.performance;
import java.nio.file.Path;
import java.util.*;
import com.gabou.projectlandscape.geography.continent.*;
import com.gabou.projectlandscape.config.MacroGeographySettings;
import com.gabou.projectlandscape.generation.seed.NamedSeedService;
import com.gabou.projectlandscape.generation.version.GenerationVersions;
import net.minecraft.resources.ResourceLocation;
final class IslandEvictionChecks {
    static void run(Path out)throws Exception {
        var macro=new PaMacroGeography(new NamedSeedService(8675309,new ResourceLocation("minecraft","overworld"),GenerationVersions.planned()),MacroGeographySettings.defaults());
        var model=macro.islands();var field=macro.sites();MacroSiteField.Site retained=null;List<IslandModel.Island> value=null;
        for(int i=0;i<4096;i++){var site=field.site(i%64-32,i/64-32);var list=model.islands(site);if(!list.isEmpty()){retained=site;value=list;break;}}
        if(retained==null)throw new AssertionError("No nonempty island fixture");
        model.clear();value=model.islands(retained);int slot=(int)MacroSiteField.mix(retained.id())&63,inserted=0;
        for(int i=0;inserted<300;i++){var s=field.site(i+100,200);if(((int)MacroSiteField.mix(s.id())&63)==slot)continue;model.islands(s);inserted++;}
        // The untouched oldest backing key was evicted; its direct front slot still owns the same immutable list.
        if(model.cacheStats().get("entries")!=256||model.islands(retained)!=value)throw new AssertionError("Front retention after backing eviction");
        MacroSiteField.Site collision=null;
        for(int i=0;collision==null;i++){var s=field.site(i+500,300);if(((int)MacroSiteField.mix(s.id())&63)==slot)collision=s;}
        model.islands(collision);if(!model.islands(retained).equals(value))throw new AssertionError("Replacement changes value");
        model.clear();if(model.cacheStats().get("entries")!=0||model.cacheStats().get("frontEntries")!=0)throw new AssertionError("Clear");
        if(!model.islands(retained).equals(value))throw new AssertionError("Clear changes value");model.close();
        try{model.islands(retained);throw new AssertionError("Closed query");}catch(IllegalStateException expected){}
        WorldgenBenchmark.write(out,"island_eviction.json",Map.of("frontSurvivesBackingEviction",true,"collisionReplacementExact",true,"clearReconstructionExact",true,"postCloseRejected",true,"afterClose",model.cacheStats()));
    }
}
