/* Original Project Landscape compatibility API. All Rights Reserved. */
package com.gabou.projectlandscape.api.atmosphere;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;

/**
 * Optional read-only bridge for atmosphere forecast setup. Version 1 uses macro geography and a
 * forecast-only climate estimate; it never requests terrain tiles, chunks, or surface filters.
 */
public final class LandscapeAtmosphereApi {
    public static final int API_VERSION = 1;

    private LandscapeAtmosphereApi() {
    }

    public static boolean supports(ServerLevel level) {
        return service(level).isPresent();
    }

    public static Optional<AtmosphereForecastSample> sampleForAtmosphere(ServerLevel level, int x, int z) {
        return service(level).map(service -> service.sample(x, z));
    }

    private static Optional<com.gabou.projectlandscape.compat.projectatmosphere.LandscapeForecastService> service(ServerLevel level) {
        if (level == null || !((Object) level.getChunkSource().randomState() instanceof RTFRandomState state)) {
            return Optional.empty();
        }
        var context = state.generatorContext();
        if (context == null || context.generationContext() == null
                || !context.generationContext().dimension().equals(level.dimension())) {
            return Optional.empty();
        }
        return Optional.ofNullable(context.atmosphereForecastService());
    }
}
