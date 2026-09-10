package com.gabou.projectlandscape.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.TreeMap;

/** Four independently bounded, bucketed windows. No weather ticks or raw sample series retained. */
public final class ClimateHistoryRegion {
    public static final int BUCKETS = 16;
    public static final long CADENCE = 200;
    private final long[] windows;
    private final java.util.List<TreeMap<Long, ClimateAccumulator>> buckets = new java.util.ArrayList<>();
    private RuntimeClimateSample previous; // Not persisted: no fabricated coverage across server sessions.
    private long observations, lastTick = -1;
    public ClimateHistoryRegion(long[] windows) {
        if (windows.length != 4) throw new IllegalArgumentException("Four window durations required");
        this.windows = windows.clone();
        for (long w : windows) {
            if (w < BUCKETS || w > 1_000_000_000L) throw new IllegalArgumentException("Window duration");
            buckets.add(new TreeMap<>());
        }
    }
    public boolean observe(RuntimeClimateSample sample) {
        long now=sample.gameTick();
        if (now <= lastTick || (previous != null && now-lastTick < CADENCE)) return false;
        if (previous != null && now-lastTick <= CADENCE*2) {
            for (int i=0;i<windows.length;i++) {
                long width=(windows[i]+BUCKETS-1)/BUCKETS;
                var map=buckets.get(i);
                for (long start=lastTick;start<now;) {
                    long id=Math.floorDiv(start,width), end=Math.min(now,(id+1)*width);
                    map.computeIfAbsent(id,ignored->new ClimateAccumulator()).add(previous,end-start); start=end;
                }
                long cutoff=Math.floorDiv(now-windows[i],width);
                map.headMap(cutoff,true).clear();
                while (map.size()>BUCKETS+1) map.pollFirstEntry();
            }
        }
        previous=sample; lastTick=now; observations++; return true;
    }
    public ClimateAccumulator snapshot(int window, long now) {
        ClimateAccumulator sum=new ClimateAccumulator(); long width=(windows[window]+BUCKETS-1)/BUCKETS;
        long cutoff=Math.floorDiv(now-windows[window],width);
        buckets.get(window).tailMap(cutoff,false).values().forEach(sum::merge); return sum;
    }
    public long observationCount() { return observations; }
    public long lastTick() { return lastTick; }
    public void disconnect() { previous=null; }
    public CompoundTag save() {
        CompoundTag t=new CompoundTag(); t.putLong("count",observations); t.putLong("lastTick",lastTick);
        ListTag all=new ListTag();
        for(var map:buckets) {
            ListTag list=new ListTag(); map.forEach((id,a)->{var row=a.save();row.putLong("bucket",id);list.add(row);});
            CompoundTag window=new CompoundTag();window.put("buckets",list);all.add(window);
        }
        t.put("windows",all);return t;
    }
    public static ClimateHistoryRegion load(long[] windows, CompoundTag t) {
        ClimateHistoryRegion h=new ClimateHistoryRegion(windows);h.observations=t.getLong("count");h.lastTick=t.getLong("lastTick");
        if(h.observations<0 || h.lastTick< -1)throw new IllegalArgumentException("History time/count");
        ListTag all=t.getList("windows",Tag.TAG_COMPOUND);
        if(all.size()!=4)throw new IllegalArgumentException("History windows");
        for(int i=0;i<4;i++) {
            ListTag list=all.getCompound(i).getList("buckets",Tag.TAG_COMPOUND);
            if(list.size()>BUCKETS+1)throw new IllegalArgumentException("History bound");
            for(int k=0;k<list.size();k++) {
                var row=list.getCompound(k);long id=row.getLong("bucket");
                if(id<0 || h.buckets.get(i).put(id,ClimateAccumulator.load(row))!=null)throw new IllegalArgumentException("History bucket identity");
            }
        }
        return h;
    }
}
