/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.compat.legacy;

import com.gabou.atmospheregen.config.LegacyGeographySettings;
import com.gabou.atmospheregen.persistence.CanonicalJson;
import com.google.gson.*;
import java.lang.reflect.Modifier;
import java.util.Map;
import raccoonman.reterraforged.data.worldgen.preset.settings.Preset;

/** Initialization-only read adapter. The legacy codec omits some effective generation fields. */
public final class LegacyPresetSnapshot {
    private LegacyPresetSnapshot() {}
    public static LegacyGeographySettings capture(Preset preset) {
        int border = Math.min(2, Math.max(1, preset.filters().erosion.dropletLifetime / 16));
        return new LegacyGeographySettings(CanonicalJson.of(value(preset)), 3, border);
    }
    private static JsonElement value(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof String s) return new JsonPrimitive(s);
        if (value instanceof Number n) return new JsonPrimitive(n);
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Enum<?> e) return new JsonPrimitive(e.name());
        if (value instanceof Map<?, ?> map) {
            JsonObject result = new JsonObject();
            map.forEach((k, v) -> result.add(k.toString(), value(v)));
            return result;
        }
        if (value instanceof Iterable<?> list) {
            JsonArray result = new JsonArray(); for (Object element : list) result.add(value(element)); return result;
        }
        if (!value.getClass().getPackageName().equals("raccoonman.reterraforged.data.worldgen.preset.settings"))
            throw new IllegalStateException("Unsupported legacy settings snapshot type " + value.getClass().getName());
        JsonObject result = new JsonObject();
        for (var field : value.getClass().getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
            try { field.setAccessible(true); result.add(field.getName(), value(field.get(value))); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot freeze legacy setting " + field.getName(), failure); }
        }
        return result;
    }
}
