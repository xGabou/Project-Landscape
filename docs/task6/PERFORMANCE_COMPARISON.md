# Task 6 performance — checkpoint, not acceptance

## Resolver-only measurement

The uncontended `task6-checks-07` run uses preconstructed climate/geography, a 100,000-query warmup,
and seven 100,000-query batches. Median elapsed time is 17.225 ms per batch, approximately 172 ns
per query. Six measured batches allocate zero bytes on the calling thread; the first records
1,592 bytes of measurement/startup overhead. Hot selection agrees with verbose ranking at 256 points.
This excludes geography, climate construction, biome holder lookup and chunk generation.

## Exact climate reuse

The V1 source caches detached canonical tile elevation/mountain arrays, without snapping query
coordinates or changing Task 5 equations. At 128-by-128 blocks, the two float arrays retain 128 KiB
per tile. The world-scoped LRU has a 1,024-entry maximum (128 MiB array capacity, excluding object,
map and distance-cache overhead). This bound is substantial and still needs total-memory review.
Task 5's separate climate-result cache remains bounded at 4,096 entries.

Marine, river, shore and mountain selections that only require temperature use an exact projection
of the existing temperature equation. This avoids constructing an irrelevant precipitation profile.
Twenty-four signed/extreme-coordinate fixture checks prove raw-double agreement with the full
model. Actual runtime checks also compare a complete climate query and nine canonical surface inputs.

## Runtime observation

Run `reproduction-1788967364037` creates and reopens a V1 world successfully. Its first phase retains
130 surface tiles / 17,039,360 array bytes; its reopened phase retains 95 / 12,451,840 bytes.
Biome digests agree across reopen. The Gradle invocation took 75 seconds, including initialization,
world creation, verification and reopen. This is not a paired chunk-generation benchmark.

An earlier, superseded implementation stalled in spawn preparation. Its JFR samples primarily
showed retained terrain generation/erosion costs. Exact surface reuse plus temperature-only physical
selection improved startup, but the historical +25% Phase 1 target remains unproven. A contemporary
paired full-chunk and memory benchmark is required; resolver microbenchmarks cannot satisfy it.
