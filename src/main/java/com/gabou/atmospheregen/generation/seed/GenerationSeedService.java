/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.generation.seed;

/** New architecture only. This service MUST NOT replace the sequential int seeds of LEGACY_RTF_V0. */
public interface GenerationSeedService {
    default long seed(SeedDomain domain) { return seed(domain, 0L); }
    long seed(SeedDomain domain, long salt);
}
