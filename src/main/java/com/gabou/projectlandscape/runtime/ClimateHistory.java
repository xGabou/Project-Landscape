package com.gabou.projectlandscape.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/** Versioned world/dimension-owned data. Capacity refuses new regions without discarding old history. */
public final class ClimateHistory extends SavedData implements AutoCloseable {
    public static final int SCHEMA=1, MAX_REGIONS=4096;
    public static final String DATA_NAME="project_climate_runtime_history";
    private final String identity;
    private final int regionSize;
    private final long[] windows;
    private final Map<Long,ClimateHistoryRegion> regions=new LinkedHashMap<>();
    private boolean closed;
    public ClimateHistory(String identity,int regionSize,long[] windows) {
        this.identity=java.util.Objects.requireNonNull(identity);this.regionSize=regionSize;this.windows=windows.clone();
        if(regionSize<=0)throw new IllegalArgumentException("Region size");
        new ClimateHistoryRegion(windows);
    }
    public boolean observe(int x,int z,RuntimeClimateSample sample) {
        requireOpen();long key=ClimateRegion.at(x,z,regionSize).key();
        ClimateHistoryRegion region=regions.get(key);
        if(region==null) {
            if(regions.size()>=MAX_REGIONS)return false;
            region=new ClimateHistoryRegion(windows);regions.put(key,region);
        }
        boolean changed=region.observe(sample);if(changed)setDirty();return changed;
    }
    public Optional<ClimateHistoryRegion> region(int x,int z) {requireOpen();return Optional.ofNullable(regions.get(ClimateRegion.at(x,z,regionSize).key()));}
    public int regionCount(){requireOpen();return regions.size();}
    private void requireOpen(){if(closed)throw new IllegalStateException("Climate history disposed");}
    @Override public void close(){if(!closed){regions.values().forEach(ClimateHistoryRegion::disconnect);closed=true;}}
    @Override public CompoundTag save(CompoundTag t) {
        t.putInt("schema",SCHEMA);t.putString("identity",identity);t.putInt("regionSize",regionSize);t.putLongArray("windowTicks",windows);
        ListTag list=new ListTag();regions.forEach((key,h)->{var row=h.save();row.putInt("x",(int)(key>>32));row.putInt("z",(int)(long)key);list.add(row);});
        t.put("regions",list);return t;
    }
    public static ClimateHistory load(CompoundTag t,String identity,int size,long[] windows) {
        if(!t.contains("schema",Tag.TAG_INT)||t.getInt("schema")!=SCHEMA||!identity.equals(t.getString("identity"))
                ||t.getInt("regionSize")!=size||!java.util.Arrays.equals(windows,t.getLongArray("windowTicks"))) {
            throw new IllegalArgumentException("Unsupported climate history schema, identity, grid or calendar; migration required");
        }
        ClimateHistory h=new ClimateHistory(identity,size,windows);ListTag list=t.getList("regions",Tag.TAG_COMPOUND);
        if(list.size()>MAX_REGIONS)throw new IllegalArgumentException("Persisted region bound");
        for(int i=0;i<list.size();i++) {
            var row=list.getCompound(i);long key=new ClimateRegion(row.getInt("x"),row.getInt("z")).key();
            if(h.regions.put(key,ClimateHistoryRegion.load(windows,row))!=null)throw new IllegalArgumentException("Duplicate climate region");
        }
        return h;
    }
}
