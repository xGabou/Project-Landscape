package com.gabou.projectlandscape.compat.projectatmosphere;

import com.gabou.projectlandscape.runtime.*;
import java.lang.reflect.*;
import java.util.Optional;
import java.util.function.BiFunction;
import net.minecraft.server.level.ServerLevel;

/** Optional versioned PA API adapter. Reflection is confined here; PA classes are never bundled. */
public final class PaRuntimeClimateProvider implements RuntimeClimateProvider {
    private Object session;
    private final Method sample,close;
    private final int regionSize;
    private final long[] windows;
    public PaRuntimeClimateProvider(ServerLevel level,BaselineContextStore context) throws ReflectiveOperationException {
        String pkg="net.Gabou.projectatmosphere.api.climate.";
        Class<?> api=Class.forName(pkg+"RuntimeClimateBridge");
        if(api.getField("API_VERSION").getInt(null)!=1)throw new IllegalStateException("Unsupported PA climate API");
        Class<?> geo=Class.forName(pkg+"GeographicClimate");
        Constructor<?> constructor=geo.getConstructors()[0];
        BiFunction<Integer,Integer,Optional<Object>> baseline=(x,z)->context.sample(x,z).map(c->{
            var b=c.baseline();var g=c.geography();var wind=b.prevailingWind().orElseThrow();
            try{return constructor.newInstance(c.blockX(),c.blockZ(),c.latitudeDegrees(),b.meanTemperatureCelsius(),b.annualRainfallMm(),
                    g.elevationBlocks(),g.coastDistanceBlocks(),g.oceanDistanceBlocks(),g.marineClass()!=com.gabou.projectlandscape.api.geography.MacroGeographyProvider.MarineClass.LAND,wind.x(),wind.z());}
            catch(ReflectiveOperationException ex){throw new IllegalStateException("PA context API mismatch",ex);}
        });
        Object opened=api.getMethod("open",ServerLevel.class,BiFunction.class).invoke(null,level,baseline);
        try {
            sample=opened.getClass().getMethod("sample",int.class,int.class);close=opened.getClass().getMethod("close");
            regionSize=((Number)opened.getClass().getMethod("regionSize").invoke(opened)).intValue();
            if(regionSize!=BaselineContextStore.REGION_SIZE)throw new IllegalStateException("Unsupported PA climate grid");
            Class<?> calendar=Class.forName("net.Gabou.projectatmosphere.seasons.SeasonTimeHelper");
            long day=((Number)calendar.getMethod("dayDuration",net.minecraft.world.level.Level.class).invoke(null,level)).longValue();
            long season=((Number)calendar.getMethod("seasonDuration",net.minecraft.world.level.Level.class).invoke(null,level)).longValue();
            windows=new long[]{day,season,Math.multiplyExact(season,4),Math.multiplyExact(season,16)};
            new ClimateHistoryRegion(windows);session=opened;
        } catch(ReflectiveOperationException|RuntimeException ex) {
            try{((AutoCloseable)opened).close();}catch(Exception suppressed){ex.addSuppressed(suppressed);}throw ex;
        }
    }
    @Override public Optional<RuntimeClimateSample> sample(int x,int z) {
        if(session==null)throw new IllegalStateException("PA climate provider disposed");
        try {
            Optional<?> result=(Optional<?>)sample.invoke(session,x,z);
            return result.map(v->new RuntimeClimateSample(number(v,"observedGameTick").longValue(),number(v,"temperatureCelsius").doubleValue(),
                    number(v,"precipitationIntensity").doubleValue(),number(v,"humidityFraction").doubleValue(),number(v,"windXMetresPerSecond").doubleValue(),
                    number(v,"windZMetresPerSecond").doubleValue(),number(v,"pressureHpa").doubleValue()));
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("PA observation failed",ex);}
    }
    private static Number number(Object v,String name) {
        try{return (Number)v.getClass().getMethod(name).invoke(v);}catch(ReflectiveOperationException ex){throw new IllegalStateException("PA observation API mismatch",ex);}
    }
    @Override public int regionSize(){return regionSize;}
    @Override public String source(){return "projectatmosphere:regional_server_v1";}
    @Override public long[] windowTicks(){return windows.clone();}
    @Override public void close(){Object owned=session;session=null;if(owned!=null)try{close.invoke(owned);}catch(ReflectiveOperationException ex){throw new IllegalStateException("PA close failed",ex);}}
}
