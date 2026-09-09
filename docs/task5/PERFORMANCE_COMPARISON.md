# Task 5 performance comparison

The climate provider is not queried by the chunk/biome generation path in Task 5, so Task 5 introduces no measured chunk-generation regression. The standalone survey sampled 16 seeds × 289 macro points with a bounded 94-point upwind profile per query and completed in 1m57s on the development workstation, including Gradle/JVM startup and compilation. The expensive work is therefore explicit profile sampling rather than hidden in terrain generation.

`PaBaselineClimateProvider` uses a world-scoped bounded 4,096-entry LRU. Repeated queries reuse climate results; `clear()` is used by the deterministic cold-cache check. The profile has a fixed maximum distance and step, and no global world-independent cache exists. Future integration must benchmark paired warm/cold filtered tiles before enabling climate in a hot loop.

Task 4’s accepted contemporary V1 comparison remains the baseline: its eight-tile batch was approximately 21.9% above the legacy contemporary run. Task 5 deliberately keeps climate out of that path until regional profile amortization is reviewed.
