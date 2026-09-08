/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.generation.version;
import com.mojang.serialization.*;
/** Stable serialized enum names; unknown identifiers never become newest defaults. */
public final class VersionCodecs {
    private VersionCodecs() {}
    public static <E extends Enum<E>> Codec<E> of(Class<E> type) {
        return Codec.STRING.comapFlatMap(s -> {
            try { return DataResult.success(Enum.valueOf(type,s)); }
            catch(IllegalArgumentException e) { return DataResult.error(() -> "Unsupported "+type.getSimpleName()+" '"+s+"'; restore a supported manifest, do not auto-upgrade"); }
        }, Enum::name);
    }
}

