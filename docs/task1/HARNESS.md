# Task 1A developer harness

This runs the inherited implementation inside Forge. It is intentionally not a new
geography API. No mock registries, replacement algorithms, production fixes or terrain
goldens are involved. The Task 0 comparator remains unchanged.

## Run

From the repository root on this Windows/Java 17 setup (run Gradle game tasks serially):

```powershell
.\gradlew.bat compileReproductionJava reproductionClasses
.\gradlew.bat runReproductionClient --console=plain
.\gradlew.bat runReproductionClient -PreproProfile=concurrency -PreproProcessors=2 --console=plain
.\gradlew.bat runReproductionClient -PreproProfile=concurrency -PreproProcessors=48 --console=plain
```

Each launch makes a unique `run/task1a/evidence/reproduction-<timestamp>` directory.
Fresh world saves go under `run/task1a/saves`. `task1a-pass.txt` is deleted before each
launch and written only after successful completion, save/disconnect and observations.
Unexpected harness exceptions fail the Gradle task via the missing marker. Expected
defects are caught and recorded, not swallowed as successful correctness assertions.

Requires a working Forge development client/assets/display, as established in Task 0.
Do not point this harness at valuable worlds; it always creates new worlds. It has a
30-minute timeout and leaves evidence/worlds for inspection, with no recursive cleanup.
Options/accessibility onboarding can be skipped by the harness; default title/world
creation screens and proper registry load remain required. No network services/modpacks
other than normal Forge dependency/assets resolution are needed.

| Option | Behavior |
| --- | --- |
| `-PreproProfile=full` (default) | Full sampling/reproduction suite, scheduling suite, three actual worlds/chunk captures |
| `-PreproProfile=concurrency` | One real world, scheduling/geometry only |
| `-PreproProfile=chunks` | Three actual worlds/chunk captures, without the long sampling suite |
| `-PreproSeeds=8675309,4303642605,42,-987654321` | Long seed suite; first seed schedules, first three create worlds; cross-world isolation includes seed 42 |
| `-PreproRepeats=3` | Scheduling repeat count |
| `-PreproProcessors=2` or `48` | Test JVM ActiveProcessorCount, set before the inherited executor initializes (minimum effective workers 2) |
| `-PreproPoints=100:200,-1:-17` | Append block-coordinate fixtures to every seed's standard suite |
| `-PreproPreset=path/to/preset.json` | Decode with actual Preset.DIRECT_CODEC, export and load via real registries; no mock holder resolution |

The checked-in relational verifier expects the default seed/preset suite. Custom-input
runs are exploratory observations and are not guaranteed to reproduce every default
fixture. Configuration identity is captured in `preset.json`; isolated context IDs and
effective seeds are in `contexts.json`. Live rows identify actual dimension and seed.
Missing/wrong/closed-context experiments are deliberate, separate from live-world samples.
`reproPreset` expects the bare codec object (the `value` in a preset observation), not
the enclosing observation array. Custom preset/points and the chunks-only profile are
available exploratory entry points; the archived verification uses default full and
concurrency profiles, not a compatibility guarantee for arbitrary input configs.

## Verify observations, not terrain constants

```powershell
.\scripts\task1a\Verify-Reproductions.ps1 `
  -FullRun run/task1a/evidence/reproduction-<full> `
  -SchedulingRuns run/task1a/evidence/reproduction-<2>,run/task1a/evidence/reproduction-<48> `
  -OutputDirectory build/task1a-summary

.\scripts\task1a\Compare-ChunkSnapshots.ps1 `
  -RunA run/task1a/evidence/reproduction-<full-A> `
  -RunB run/task1a/evidence/reproduction-<full-B> `
  -OutputDirectory build/task1a-repeat-summary
```

The first script makes machine-readable `summary.json` and runs relational reproduction
checks: e.g. A and B disagree, contaminated B agrees with A but not refreshed B,
released arrays are reused, and fixed tile hashes agree across order/worker runs.
It fails if a confirmed inherited behavior is no longer reproduced. Task 1B must turn
each affected reproduction into a correctness check after choosing its contract; do not
silently update expected bad terrain. `build` does not automatically launch this client.

The second script compares actual block snapshots, recording changed block coordinates,
types/properties, height range, cell and biome agreement. It does not assert a causal
stage for differences in FULL chunks. A difference is evidence to investigate, not a
new golden. It checks metadata/snapshot presence instead of silently comparing missing
chunks. Unchanged chunks are recognized from canonical block hashes.

## Evidence tables

All tables are JSON arrays. Raw floats are signed int bits, transient density values
are signed long double bits; ordinary measurement times/counts and coordinates are
numeric values. Decode float bits with Java Float.intBitsToFloat or .NET BitConverter.
Do not convert long bit patterns to JavaScript Number before comparison.

| File | Contents |
| --- | --- |
| environment.json | Baseline SHA, JVM, workers, seed/geometry settings, repeat count |
| preset.json | Actual loaded configuration encoded by Preset.DIRECT_CODEC |
| contexts.json | Isolated context identity, long requested/int effective seed, tile geometry |
| fixtures.json | Seed-specific fixed/discovered coordinates and discovery semantics |
| seed_collisions.json | Both complete Cell+hint snapshots and differing field names for every pair |
| cached_uncached.json | A/B full snapshots; A/B and B/C differences |
| eviction_reload.json | D snapshot versus A; regeneration differences versus B |
| cross_world_cache.json | Same-thread/reverse/fresh-worker and mode/warmth experiments |
| noise_cache.json, noise_cache_bits.json | Same graph/new graph, compute-seed experiments; finite checks on registry-derived copies |
| tile_bounds.json | Raw coordinate, computed index, axis validity, absent flag and aliased coordinates |
| generation_order.json | Repeat/order/workers/tile coordinate, cell count and canonical digest |
| tile_geometry.json | Baseline and size/border/batch variants at center/edge/corner/negative probes |
| tile_overlap.json | Independently addressed overlapping core/halo samples |
| lifecycle.json | Drop threshold, retained reader/entry, stale close, TTL and forced-failure observations |
| closed_cache_ownership.json | Closed test contexts still held by CacheManager |
| cell_resources.json | Proper nested/exception/concurrent use and isolated deliberate double-close |
| missing_context.json | Real RandomState, empty real preset registry, structure rule and dimension/close cases |
| vegetation_disabled.json | Real bootstrap result with full suppressed exception tree |
| generated_chunks.json | FULL status; column heights; blocks, stored quart biomes and block-query biome hashes; live/direct/exact lookup; vanilla density samples |
| chunk_blocks/*.json.gz | Lossless JSON array of block-state strings; index order z, x, then y (y fastest), bounds given in chunk metadata |
| measurements.json | Raw/query/tile workload timing and caller allocations; not a benchmark |
| observer_counters.json | Cumulative observation snapshots before suite, before chunks and after chunks |
| completion.json | PASS/FAIL and run identity |

Repeated block snapshot files for (127,127) and any other coordinate in the same chunk
share a path only if their chunk actually matches; the default eight points select
eight distinct chunks. Hashing includes block states/properties, not entities, block
entity NBT, tick queues or lighting. FULL snapshots may include ticking/decorations and
are not a frozen NOISE-stage comparator. Heights use WORLD_SURFACE, including features.
`biomesSHA256` is `Level.getBiome` (seeded zoom); `storedQuartBiomesSHA256` is
ChunkAccess.getNoiseBiome at aligned quart samples. They must not be conflated.

## Instrumentation and performance groundwork

Three required, remap=false observer Mixins load **only** via runReproductionClient's
extra `--mixin.config task1a-observers.mixins.json` argument:

- TileCacheObserver counts provide, queue/drop and provideIfPresent hit/miss returns.
- EntryObserver counts get calls/initially pending futures and inclusive successful
  get duration. This includes lock/cached-return/join overhead, not pure blocked time.
- TileReleaseObserver counts successful drop-threshold releases. It does not count
  every kind of TTL/capacity removal or prove reader-safe eviction.

Observers do not cancel/redirect calls, replace results, or write generator fields.
They only read a future's completion flag and update test counters/timers. Their
overhead can change scheduling/timing, so they are not transparent performance probes
and are not a final benchmark. Successful get timing excludes exceptional exits; an
exceptional call leaves an unused start frame on that test thread until process exit.
Counters are JVM-wide and cumulative, so use differences between phase snapshots;
in-progress workers can advance them during a snapshot. No correctness result depends
on exact counts/durations.

The only reflective **writes** are a test-owned cache entry timestamp for immediate
expiry and a test-owned generator filter field temporarily made null for failure
injection, restored afterwards. Other reflection reads record ownership/array/pool
state. No live world's cache is forcibly reset/evicted by those lifecycle tests.
Normal live chunk generation still owns its own eviction behavior.

Measurement plumbing covers raw point cost, opportunistic versus force-tile query
latency, warm/cold result flag, caller thread allocation bytes, five-tile workloads,
real FULL-chunk workload timings, cache hits/misses, threshold releases and inclusive
entry join/get time. Tile/chunk timers include canonicalization/hashing; allocations
exclude worker threads. Task 1C must separate generation from hashing, measure true
worker allocation/blocked time, account for every eviction cause, warm up the JVM and
control heap/GC/runtime ticks. No optimization or final performance claim is made now.

## Production separation

Only build.gradle's additional source set/run configuration affects tracked build code.
No src/main or src/generated file is edited. The reproduction mod, observers, JSON
manifest and dev pack metadata are excluded from both production mod/source jars.
Task 0's smoke source set and optional dependencies are preserved. Validate with:

```powershell
.\gradlew.bat compileJava classes jar build compileReproductionJava reproductionClasses --console=plain
git diff e9dd8841a1b4a95e4bb2b23e084d40abbc1bea70 -- src/main src/generated gradle.properties
jar --list --file build/libs/reterraforged-forge-1.20.1-0.0.6.jar
Get-FileHash build/libs/reterraforged-forge-1.20.1-0.0.6.jar -Algorithm SHA256
```

The expected production SHA-256 remains Task 0's
`FA950D83F886901ADF2B47223C45A6809F21F7AAB658C0F1128F721D9D9416C8`.
Archive evidence without cleaning/replacing previous observations. A Task 1A commit
cannot contain its own SHA; resolve it with `git log -1 --format=%H -- docs/task1`.
The evidence-only .gitattributes rule preserves exact recorded bytes across Windows
and Unix checkouts so the inventory SHA-256 values remain meaningful.
