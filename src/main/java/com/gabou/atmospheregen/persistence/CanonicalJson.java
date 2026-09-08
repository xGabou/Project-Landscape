/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.persistence;

import com.google.gson.*;
import com.mojang.serialization.*;
import java.math.BigDecimal;
import java.util.TreeSet;

/** Immutable, sorted JSON value. Accessors return detached trees, never mutable owned storage. */
public record CanonicalJson(String text) {
    public static final Codec<CanonicalJson> CODEC = Codec.PASSTHROUGH.comapFlatMap(dynamic -> {
        try { return DataResult.success(of(dynamic.convert(JsonOps.INSTANCE).getValue())); }
        catch (RuntimeException invalid) { return DataResult.error(() -> "Invalid canonical JSON: " + invalid.getMessage()); }
    }, value -> new Dynamic<>(JsonOps.INSTANCE, value.tree()));

    public CanonicalJson { text = sorted(JsonParser.parseString(text)).toString(); }
    public static CanonicalJson of(JsonElement value) { return new CanonicalJson(value.toString()); }
    public JsonElement tree() { return JsonParser.parseString(text); }

    private static JsonElement sorted(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (String key : new TreeSet<>(value.getAsJsonObject().keySet())) result.add(key, sorted(value.getAsJsonObject().get(key)));
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement entry : value.getAsJsonArray()) result.add(sorted(entry));
            return result;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            // Reject NaN/infinity and normalize equivalent finite numeric spellings, without long->double loss.
            BigDecimal number = new BigDecimal(value.getAsString()).stripTrailingZeros();
            return new JsonPrimitive(number.signum() == 0 ? BigDecimal.ZERO : number);
        }
        return value.deepCopy();
    }
}
