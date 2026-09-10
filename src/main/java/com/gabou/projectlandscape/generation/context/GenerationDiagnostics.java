/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.generation.context;

import com.gabou.projectlandscape.generation.seed.SeedDomain;
import java.util.*;

/** Immutable development dump; no sampling, chunk generation, global context or mutable identities. */
public final class GenerationDiagnostics {
    private GenerationDiagnostics() {}
    public static Map<String, String> describe(WorldGenerationContext context) {
        Map<String, String> result = new TreeMap<>();
        var versions = context.manifest().content().versions();
        result.put("contextId", context.contextId());
        result.put("worldSeed", Long.toString(context.worldSeed()));
        result.put("dimension", context.dimension().location().toString());
        result.put("manifestSchema", Integer.toString(versions.schemaVersion()));
        result.put("geographyVersion", versions.geography().name());
        result.put("baselineClimateVersion", versions.baselineClimate().name());
        result.put("biomeResolverVersion", versions.biomeResolver().name());
        result.put("fingerprint", context.manifest().fingerprint().sha256());
        result.put("seedServiceUsage", "new architecture infrastructure only; legacy generation does not consume these streams");
        for (SeedDomain domain : SeedDomain.values()) result.put("stageSeed/" + domain.id(), Long.toString(context.seeds().seed(domain)));
        return Collections.unmodifiableSortedMap(new TreeMap<>(result));
    }
}
