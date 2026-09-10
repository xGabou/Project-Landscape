package com.gabou.projectlandscape.runtime;

import net.minecraft.nbt.CompoundTag;

/** Time-weighted aggregates. Precipitation is intensity*ticks, not mm. */
public final class ClimateAccumulator {
    long ticks, wetTicks, frozenTicks;
    double temperatureSum, humiditySum, precipitationSum;
    double minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
    void add(RuntimeClimateSample sample, long duration) {
        ticks += duration;
        temperatureSum += sample.temperatureCelsius() * duration;
        humiditySum += sample.humidityFraction() * duration;
        precipitationSum += sample.precipitationIntensity() * duration;
        if (sample.precipitationIntensity() > 0) {
            wetTicks += duration;
            if (sample.temperatureCelsius() <= 0) frozenTicks += duration;
        }
        minimum = Math.min(minimum, sample.temperatureCelsius()); maximum = Math.max(maximum, sample.temperatureCelsius());
    }
    void merge(ClimateAccumulator other) {
        ticks += other.ticks; wetTicks += other.wetTicks; frozenTicks += other.frozenTicks;
        temperatureSum += other.temperatureSum; humiditySum += other.humiditySum; precipitationSum += other.precipitationSum;
        minimum = Math.min(minimum, other.minimum); maximum = Math.max(maximum, other.maximum);
    }
    public long elapsedTicks() { return ticks; }
    public double meanTemperature() { return ticks == 0 ? Double.NaN : temperatureSum / ticks; }
    public double meanHumidity() { return ticks == 0 ? Double.NaN : humiditySum / ticks; }
    public double precipitationIntensityTicks() { return precipitationSum; }
    public long wetTicks() { return wetTicks; }
    public long dryTicks() { return ticks - wetTicks; }
    public CompoundTag save() {
        CompoundTag t = new CompoundTag(); t.putLong("ticks", ticks); t.putLong("wet", wetTicks); t.putLong("frozen", frozenTicks);
        t.putDouble("temperature", temperatureSum); t.putDouble("humidity", humiditySum); t.putDouble("precipitation", precipitationSum);
        if (ticks > 0) { t.putDouble("min", minimum); t.putDouble("max", maximum); }
        return t;
    }
    public static ClimateAccumulator load(CompoundTag t) {
        ClimateAccumulator a = new ClimateAccumulator(); a.ticks=t.getLong("ticks"); a.wetTicks=t.getLong("wet"); a.frozenTicks=t.getLong("frozen");
        a.temperatureSum=t.getDouble("temperature"); a.humiditySum=t.getDouble("humidity"); a.precipitationSum=t.getDouble("precipitation");
        if (a.ticks > 0) { a.minimum=t.getDouble("min"); a.maximum=t.getDouble("max"); }
        if (a.ticks < 0 || a.wetTicks < 0 || a.wetTicks > a.ticks || a.frozenTicks < 0 || a.frozenTicks > a.wetTicks
                || !Double.isFinite(a.temperatureSum) || !Double.isFinite(a.humiditySum) || !Double.isFinite(a.precipitationSum)
                || a.humiditySum < 0 || a.precipitationSum < 0
                || (a.ticks > 0 && (!Double.isFinite(a.minimum) || !Double.isFinite(a.maximum) || a.minimum > a.maximum))) {
            throw new IllegalArgumentException("Invalid climate aggregate");
        }
        return a;
    }
}
