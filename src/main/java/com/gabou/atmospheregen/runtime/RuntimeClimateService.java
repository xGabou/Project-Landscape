package com.gabou.atmospheregen.runtime;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

/** At most four player-region candidates and four existing-state reads per tick; no world/chunk scan. */
public final class RuntimeClimateService implements AutoCloseable {
    public static final int WORK_PER_TICK=4;
    private ServerLevel level;
    private RuntimeClimateProvider provider;
    private final ClimateHistory history;
    private final BaselineContextStore baseline;
    private int cursor;
    private boolean closed;
    public RuntimeClimateService(ServerLevel level, RuntimeClimateProvider provider, ClimateHistory history, BaselineContextStore baseline) {
        this.level=level;this.provider=provider;this.history=history;this.baseline=baseline;
    }
    public void tick() {
        requireOpen();var players=level.players();int count=players.size();
        for(int i=0;i<Math.min(WORK_PER_TICK,count);i++) {
            var player=players.get(Math.floorMod(cursor++,count));int x=player.getBlockX(),z=player.getBlockZ();long now=level.getGameTime();
            var old=history.region(x,z);
            if(old.isPresent() && now-old.get().lastTick()<ClimateHistoryRegion.CADENCE)continue;
            provider.sample(x,z).ifPresent(sample->history.observe(x,z,sample));
        }
    }
    public Optional<RuntimeClimateSample> sample(int x,int z){requireOpen();return provider.sample(x,z);}
    public ClimateHistory history(){requireOpen();return history;}
    public BaselineContextStore baseline(){requireOpen();return baseline;}
    public String source(){requireOpen();return provider.source();}
    public int regionSize(){requireOpen();return provider.regionSize();}
    private void requireOpen(){
        if(closed)throw new IllegalStateException("Runtime climate service disposed");
        if(!level.getServer().isSameThread())throw new IllegalStateException("Runtime climate requires server thread");
    }
    @Override public void close(){if(!closed){closed=true;history.close();provider.close();provider=null;level=null;}}
}
