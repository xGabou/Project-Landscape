# Task 1B correctness repairs

Comparator: Task 0 `e9dd8841a1b4a95e4bb2b23e084d40abbc1bea70` and accepted
Task 1A `c773b8b48b229e7150f54b00ef0a33973c491d0e`.
Task 1A evidence is immutable. New runs and relational correctness checks live here.
No terrain golden is being established and no geography/climate/biome redesign is in scope.

Completed 2026-09-07. All nine required defect families are independently repaired,
tested and committed, with separate dormant-noise validation and legacy-geometry
safeguards. Final gate: **64 checks PASS, 994 canonical comparator rows unchanged,
480 scheduling digests unchanged**, standalone/TerraBlender/vanilla save-reopen smoke
PASS. The known direct/filtered discrepancy and unexplained FULL-block differences
remain explicitly recorded below; this is not a claim of universal world determinism.

## Verification protocol

Run the real Forge `runReproductionClient` harness before and after each fix, plus
`compileJava classes jar build compileReproductionJava reproductionClasses`.
Archive the complete JSON observations separately, including lossless FULL block snapshots.
`scripts/task1b/Verify-Repairs.ps1` evolves the relevant reproduction checks into
correctness assertions and compares independently generated raw/filtered fixtures,
whole core-tile hashes and real chunk exact samples/stored quart biomes to Task 1A.
FULL blocks remain observations, not an asserted golden (Task 1A F12).

Each numbered repair is a separate commit. A commit cannot embed its own SHA; resolve
each section's introducing commit with Git history, and the final handoff lists full SHAs.
Toolchain, preset, seeds, coordinates and default tile geometry remain those of Task 1A.

## 01 — Cross-context cell cache identity

Inherited rerun `00-inherited` reproduced all 27 original checks (Forge launch 1m34s).
After `01-after`: A-B-A, B-A-B, both worker orders, and both same-seed/different-context
orders return their own refreshed values: **zero contaminated fields in all six sequences**.
Previously HEIGHT, CONTINENT, EROSION and BIOME_REGION leaked between seed 8675309 and 42.
Same-seed tests give only one context a tile so distinct ownership is observable.

`WorldLookup` owns a final opaque identity object. `CellSampler.Cache2d` compares that
token by reference alongside position; it invalidates before recomputing so a thrown
lookup cannot leave a valid old key. Tokens do not retain worlds/registries/tiles on
long-lived worker threads. New worlds, dimensions or rebuilt generation contexts get
distinct lookup tokens regardless of seed equality. Context mutation is not a supported
version transition; Task 2 must create a new context for changed version/config.

Changed production files: `cell/heightmap/WorldLookup.java`, `densityfunction/CellSampler.java`
(both under `src/main/java/Gabou/reterraforged/world/worldgen/`). Existing MIT notice
retained at root and provenance headers added to these touched derived files.
Developer changes: `.gitattributes`, `ReproductionSuite.java`, new verifier and this record/evidence.

Compile/classes/jar/build + reproduction classes **PASS, 7s**. Actual Forge full run
**PASS, 1m43s**. Canonical comparisons: 340 raw/filtered pairs, 510 seed-pair rows,
120 whole core-tile digests and 24 real FULL-chunk exact samples/stored quart biomes:
**zero differences** against Task 1A. Finished block hashes are not asserted equivalent:
F12 remains unassigned and will be rerun at the final gate. Seed narrowing is unchanged.

Cost: one object per lookup, one reference per scratch cache, one identity comparison
per fallback/transient density evaluation; no extra generation, per-query allocations
or locks. No trustworthy isolated performance delta measured (Task 1C remains later).
Sampling mode and tile-warmth staleness deliberately remain for Fix 2.
Evidence: `00-inherited`, `00-inherited-verification`, `01-after`,
`01-cross-world-cache/verification.json`, `logs/01-{build,runtime}.log`.

## 02 — Sampling contract/source identity

Before-fix evidence is the immediately preceding full Forge run, `01-after`:
climate false-to-true disagrees with fresh true mode in 11 cell/hint fields, and
same-position direct-to-warm queries keep stale HEIGHT/GRADIENT/HEIGHT_EROSION/SEDIMENT.

Actual contracts (no invented mode enum):

| Caller | Contract |
| --- | --- |
| CellSampler.compute | Opportunistic cached tile, else direct with climate=true |
| CacheChunk fallback (outside chunk / single column) | Opportunistic cached tile, else direct with climate=false |
| CacheChunk in-chunk | Explicit chunk reader, not scratch Cache2d |
| WorldLookup load=true | Force filtered tile, not scratch Cache2d |
| Preview generateZoomed(false) | Deliberately skips optional filters, not scratch Cache2d |

Scratch identity now includes `(lookup token, X/Z, sampleClimate, tile token or null)`.
`cachedTile` observes the source once, retaining the inherited join of an already queued
tile; direct fallback uses the newly named `sampleDirectApproximate` method. This avoids
tagging a result with the identity of a different lookup performed after a publication
race. New tile objects have unique lightweight tokens; eviction and replacement cannot
reuse them. Cached tiles still contain climate regardless of the direct-mode flag.
Neither mode is declared canonical over the other and no filter/climate math is changed.

Cost: one cache-map read/join check per scratch evaluation instead of only on position
changes. No extra tile generation or per-query allocation. The high-volume in-chunk
reader path remains unchanged. This intentionally trades a small fallback lookup cost
for source correctness; timing with observers is not an isolated benchmark result.
Tile read-versus-release safety remains Fix 4, not a claim made by this identity fix.

After `02-after`: both climate directions, cold/warm/evicted/regenerated sequence,
and replacement without an intervening direct query pass. Zero stale fields; the
six context-isolation sequences still pass. All 994 comparator rows (340 sample pairs,
510 seed pairs, 120 tile hashes, 24 real chunk samples/biomes) are unchanged.
The direct/filtered discrepancy remains **340/340**, with the same field counts as
Task 1A: gradient 340, height 319, heightErosion 176, sediment 96, terrain 6.
Build **PASS, 15s**; real Forge three-world run **PASS, 1m37s**.
Evidence: `02-after`, `02-sampling-mode-cache/verification.json`, `logs/02-*.log`.
Changed production: WorldLookup, CellSampler and Tile. Test changes: ReproductionSuite,
Verify-Repairs; documentation/evidence updated separately from Task 1A.

## 03 — Independent raw tile bounds

Before: `03-before` reruns the inherited accessor after Fix 2, additionally recording
every cell of the 160x160 generated tile in `tile_bounds_halo.json.gz`. The original
five aliases remain: (-1,1)->(159,0), (160,0)->(0,1), (160,1)->(0,2),
(320,0)->(0,2), (-160,1)->(0,0). These are raw array coordinates, not world coordinates.

The accessor now checks each axis against `[0,total)` **before** multiplication/addition.
It retains the existing `Cell.empty()` miss contract, with no clamping or new exception.
All original alias fixtures are permanent correctness checks in Verify-Repairs.

Caller audit: Filter.iterate supplies valid centers. Smoothing bounds centers by its
radius and visits only in-range neighbors. NoiseCorrection visits full in-range quart
groups. Steepness deliberately probes -1..2 neighbors beyond the outer halo and skips
absent neighbors. BeachDetect probes +/-8 and already substitutes its center when a
neighbor is absent. Those last two are actual consumers of the broken horizontal
wrapping; neither requires it. Erosion accesses its separately bounded backing array.
Other raw-access sites are in the older cell/filter family, not active tile generation.
No consumer is changed, and no halo size/filter order/erosion parameter is changed.

Only production change: Tile.getCellRaw's bounds check. Tests add the full-halo capture,
Compare-TileHalo and the permanent no-alias assertion.

After `03-after`: **all 11 original bounds cases pass**, including the five former
aliases now returning absent. All 994 canonical comparator rows remain unchanged.
Full tile comparison: **480/25,600 cells change, zero in the 128x128 core**. Exact raw
and world coordinates, before/after fields and values are in
`03-tile-bounds/halo_comparison.json`; these are corrected invalid-neighbor effects,
not a new terrain golden. The original filter formulas remain untouched.
Build **PASS, 5s**; Forge full run **PASS, 1m48s**. Before run **PASS, 1m44s**.
No canonical chunk terrain or stored quart-biome difference observed. This does not
claim all custom zero-halo geometries are equivalent: geometry is legacy semantics.
The added cost is four integer comparisons before indexing, with no allocation/locking.

## 04 — Published tile lifetime (design before implementation)

Before, reproduced again in `03-after`:

```text
TileCache -> CacheEntry/future -> Entry -> Tile -> pooled Cell[] / Chunk[]
                                         ^        |
                               readers retain     +-> drop 64 -> reset/return pool
                                         |                           |
                                         +-> observe replacement <---+
stale Tile.close -> resets replacement; expiry/failure have separate disposal gaps
```

The current interfaces publish raw mutable Cells and uncloseable chunk readers, and
Minecraft NoiseChunk has no matching RTF reader-release hook. Adding only a refcount
to Tile would not account for those readers. Changing all consumers into mandatory
leases would expand this fix and still leave raw Cell references untracked.

Chosen boundary: **detach storage once after filtering, before future publication**.
Only the generation workspace is pooled. Published Tiles/Cells/chunk readers own
non-recycled storage and remain readable while referenced, including after eviction.
Published close relinquishes logical cache ownership, never resets or recycles their
contents. Workspace close is guarded once per Tile, independent of a reusable pool
wrapper. This uses the requested detached-snapshot approach, not a new geography API.

```text
generator -> pooled workspace -> batches -> filters -> detached Tile snapshot
                   |                                      |
                   +-> close once -> pool                  +-> cache/future/readers
                                                                    |
                                                         drop/expiry/unload
                                                                    |
                                             last ordinary reference dies -> GC
```

Tradeoff: one Cell copy per published tile cell plus detached arrays/chunk objects.
Pooling still serves terrain/erosion workspaces; no per-density-call allocation or
reader synchronization is added. This cost must be measured, not described as free.
Mutation by consumers remains unsupported; existing Cell fields are not being redesigned.
Failure/cancel/expiry/global-cache disposal gaps stay separately scoped to Fix 5.

After `04-after`: retained Cell, chunk reader, lazy entry, and retained worker continue
to observe their original sample after 64 drops and a replacement allocation. Published
arrays are distinct. Stale close and repeated close leave the replacement unchanged.
Successful generation has already returned the workspace arrays before publication;
readers no longer keep the generation pool borrowed. All 994 canonical comparisons pass.

Measured snapshot-only copy: **474,700 ns and 2,975,000 caller allocation bytes for
25,600 cells** (one observation, not a benchmark). Mean five-tile workload including
hashing: 747,376,958 ns before versus 740,422,121 ns after across the 24 order/repeat
workloads; noise/GC/hash costs prevent interpreting this as a speedup. The allocation
increase is real and is recorded for Task 1C; no catastrophic slowdown observed here.
Build **PASS, 5s**; Forge three-world run **PASS, 1m38s**.

Changed production: Tile and TileGenerator only. Development: ReproductionSuite,
Verify-Repairs, this document and `04-after`, `04-tile-lifetime/verification.json`,
`logs/04-*.log`. Filter failure still bypasses workspace return; TTL still does not
logically close its snapshot, and Cache.close still only cancels polling. Those are
explicitly unclaimed until Fix 5, where expiry/shutdown/failure tests are extended.

## 05 — Disposal and pool-return paths

Before (`04-after`): forced filter failure returns zero arrays, expiry removes its
entry without logical close, Cache.close leaves its map/global registration alive.
These are concrete missed-return/retention paths, not a claim of permanent heap leaks.

The generation future now drains all accepted batches, including after partial
submission failure or public-future cancellation, before closing its workspace.
Both successful and exceptional paths release the workspace before publishing a
completed result. A concurrently cancelled publication closes its detached result.
Partial allocation closes an already borrowed cell array if chunk allocation fails.

The production bounded cache's capacity/expiry/clear paths dispose entries outside
its map lock. CacheEntry attaches disposal to its future even when never read through
LazyCallable. TileCache.Entry implements SafeCloseable. Close is idempotent, excludes
new insertions, clears entries, cancels/removes polling tasks, and unregisters the cache.
Late futures dispose upon completion; no blocking join is imposed on cache maintenance.
The unused generic Future fallback waits on an asynchronous common-pool task; actual
tile generation uses CompletableFuture callbacks, not this fallback.

Stale drop removal now compares the expected entry by reference, so it cannot remove
a replacement under the same key. The bounded-map write-lock path rechecks presence
before evicting capacity. Other internal LongMap implementations explicitly reject
identity-conditional removal; the production tile cache uses StampedBoundLongMap.
The old custom-map factory overload is not a certification of arbitrary external
maps' disposal policies; only the production disposing bounded-map configuration is used.

Array-pool handles represent individual borrows (not resettable shared wrappers).
Close is once-only even when the pool is full. Counters under the existing pool lock
record array allocations, borrows, returns, live borrows and retained pool items.
ThreadLocalPool fallback behavior remains separate, pending Fix 6.

Forge server-level unload closes only that level's tile cache. Actual integrated-server
shutdown exercises the same unload events. RandomState reinitialization closes the old
cache when replacing its context; samplers no longer memoize a disposed lookup. No global
current-world state is introduced. Published snapshots survive these closures safely.
Normal context shutdown rejects new cache work; descriptive missing-initialization and
foreign-generator handling remain for Fix 7.

After `05-after`: **all 33 cumulative correctness/comparator checks pass**.
Filter failure returns its one workspace array pair. Expiry closes the snapshot.
Capacity eviction closes an unread completed future; shutdown closes a subsequently
completed pending future. A wrong/stale entry cannot remove the replacement.
Twelve real tile generations cancel successfully, then both pools converge to
**15 borrows / 15 returns / 0 live**; after the stale-workspace test each is 17/17/0.
A capacity-one array pool closes all handles, including a rejected return, with 3/3/0.
All three actual worlds unload all three dimension contexts: closed, unregistered,
zero live pooled borrows. RandomState replacement retires its cache and samples the
same expected climate through the new lookup. No old world lookup is memoized.

All 994 canonical comparisons remain unchanged. Build **PASS, 3s**; final full Forge
run **PASS, 1m45s**. One earlier exploratory full run also passed; archived evidence
uses the final source with partial-allocation and cancelled-timer cleanup included.
Counter/handle overhead is per tile borrow/return, not per density evaluation. No
isolated speed claim is made. Rejected executor submission and catastrophic allocation
failure cleanup are code-reviewed paths, not a forced shutdown/OOM of the live executor.

Production changed: Cache, CacheEntry, CacheManager, LongMap, StampedBoundLongMap,
ArrayPool, TileCache, TileGenerator, RTFForge and MixinRandomState. Developer changes:
new DisposalChecks, ReproductionSuite, ReproductionClient and Verify-Repairs.
Evidence: `05-after`, `05-disposal/verification.json`, `logs/05-*.log`.

## 06 — Fallback pool duplicate release

Before (`05-after`): closing a fallback resource twice makes two subsequent borrows
alias one Cell. Proper depths 1/2/8/64 and normal concurrent sampling already pass.

The fallback pool retains values, not reusable lease wrappers. Every borrow has its
own once-only close state, so a stale wrapper cannot return a newly borrowed value.
Closed get fails explicitly; foreign-thread get/close fails rather than modifying
another thread's free list. The originating thread can still release that live borrow.
No synchronization is added: fallback ownership is thread-confined. One small wrapper
is allocated per nested/fallback borrow. The normal Cell.LOCAL SimpleResource fast
path, pool capacity and legacy spare-slot behavior are unchanged.

Production file: ThreadLocalPool.java. Developer files: ReproductionSuite and
Verify-Repairs, plus documentation and new evidence.
After `06-after`: double/stale close cannot alias or release new borrows. All four
nesting depths survive exception unwind; both fast and nested fallback concurrency
hold four independent Cells. Foreign-thread release fails and leaves the borrow open
for its proper owner. All 39 cumulative checks pass, with zero canonical differences.
Build **PASS, 3s**; final full Forge run **PASS, 1m47s**. Evidence:
`06-after`, `06-pool-release/verification.json`, `logs/06-*.log`.

## 07 — Explicit context requirements

Before (`06-after`): missing initialization/preset produces deferred NPEs and the
structure geography rule silently passes. After `07-after`, owned density/structure
queries fail with a descriptive IllegalStateException including operation, dimension
(explicitly unbound before ChunkMap initialization), seed and missing initialization.
An RTF router with no preset fails during initialization. Rebinding one RandomState
to another dimension and sampling a shut-down context fail explicitly.

Ownership is determined by RTF CellSampler markers actually visited in the router,
not by the mere presence of the mod. A proper bootstrapped vanilla overworld router
samples without an RTF context; unused additional RTF registry tags are not wrapped
when no preset exists. An explicitly applied RTF geography structure rule cannot be
satisfied without geography and returns false for a foreign router. This does not
install any new rule into vanilla worlds. Owned missing context throws instead.

A deliberately uncached context now supports direct approximate lookup (previously
an unrelated null-cache NPE); a forced exact request explains that tiles are required.
This does not unify direct and filtered semantics or change either calculation.

Production: RTFRandomState, MixinRandomState, MixinChunkMap, WorldLookup and CellTest.
Tests: ReproductionSuite and Verify-Repairs. All **50 cumulative checks pass**, with
**zero changes in 994 canonical comparator rows**. Build PASS, 11s; real three-world
Forge run PASS, 1m38s. No isolated performance delta measured; density diagnostic
operation strings are constructed once per mapped sampler, not per query.
Evidence: `07-after`, `07-missing-context/verification.json`, `logs/07-*.log`.

## 08 — Disabled custom vegetation holder resolution

Before (`07-after`): disabled export fails registry creation with 17 unreferenced
custom-tree placed-feature keys. PresetPlacedFeatures registers these only when
customBiomeFeatures is true; PresetBiomeModifierData previously requested all 17
holders before its matching condition. The holder requests now share that condition.
No tree replacement policy, generic decoration, registration order or enabled feature
definition changes. Production file: PresetBiomeModifierData.java only.

After `08-after`: export succeeds and a fourth actual world loads the disabled pack.
All placed/configured-feature holders are bound, zero RTF custom-tree placed keys are
required, and eight representative chunks reach FULL without feature-order failures.
Plains retains minecraft:trees_plains, forest minecraft:trees_birch_and_oak, and dark
forest minecraft:dark_forest_vegetation. All four worlds dispose their caches cleanly.
The enabled preset's **175 exported files are byte-identical** to the pre-fix export.
All **55 cumulative checks pass**, including zero changes in 994 canonical rows.
Build PASS, 8s; four-world Forge run PASS, 1m54s. The change removes disabled bootstrap
work and adds no generation hot-path cost; no isolated performance delta measured.

Developer changes: ReproductionClient, Verify-Repairs and new Compare-PresetPacks.
Evidence: `08-after`, `08-vegetation-disabled/{verification,enabled_pack_comparison}.json`,
`logs/08-*.log`. Disabled mode previously could not load, so its new chunk results are
functional evidence, not an invented pre-fix terrain golden. Enabled output is unchanged.

## 09 — Noise cache compute-seed identity

Before (`08-after`), one simplex cache at (17,29), seeds 123 -> 456 -> 123, returns
float bits 1054603234 for seed 456 instead of uncached/new-graph 1051087284.
After `09-after`, each result equals uncached and freshly cached evaluation. The
reverse sequence on another worker also passes. Production registry graph sharing
still shows no finite-result contamination (it was not reproduced in Task 1A either).

Chosen invariant B: Cache2d supports Noise.compute's explicit compute-seed argument.
NoiseFunction wraps holders with an int seed; mapAll normally copies cache nodes,
while the public Noise API does not enforce exclusive ownership of a seeded graph.
Imposing invariant A would constrain valid API calls without a proven performance
benefit. One int comparison and one valid bit per thread-local Cached are sufficient;
there is no world map, object equality, lock or per-query allocation.
The valid bit also permits the packed Long.MIN_VALUE position (-0.0,0.0): its first
query computes 1056964608 rather than accepting an uninitialized zero cache value.

Production file: noise/module/Cache2d.java. Tests: ReproductionSuite and Verify-Repairs.
All **59 cumulative checks pass**, zero differences in all 994 canonical rows.
Build PASS, 5s; four-world Forge run PASS, 2m1s. No isolated performance delta claimed.
Evidence: `09-after`, `09-noise-cache-seed/verification.json`, `logs/09-*.log`.
Minecraft long seeds are still narrowed exactly as before; this fix honors an already
supplied compute int and does not implement the deferred 64-bit seed-stream redesign.

## 10 — Requested validation of dormant registered NaN definitions

This is separate from cache seed safety. Before (`09-after`), registered
reterraforged:terrain/erosion and terrain/ridges yield NaN through NoiseFunction at
(17,29), (100,200), (-2048,3072), for seeds 123 and 456. Their 200-octave Perlin
definitions remain registered/serialized and unmodified; removing their keys or
changing noise math could break compatibility. No active legacy router references them.

NoiseFunction now rejects non-finite output at the Minecraft density boundary with
key (or direct graph label), coordinate and compute seed. It does not normalize NaN,
modify the graph or fail datapack bootstrap for an unused definition. Raw Noise API
calls still expose the inherited invalid definition for diagnosis; this is a targeted
serialized-density safety guard, not a blanket mathematical-library redesign.

After `10-after`, all 12 explicit density invocations fail descriptively; finite
registry graphs and active generation remain unchanged. All **60 checks pass**, zero
differences in 994 canonical rows. Build PASS, 5s; four-world Forge run PASS, 1m51s.
Cost: one finite check per NoiseFunction.compute, no allocation on valid evaluations;
no isolated performance delta claimed. Production: NoiseFunction.java only. Tests:
ReproductionSuite and Verify-Repairs. Evidence: `10-after`,
`10-invalid-noise/verification.json`, `logs/10-*.log`.

## 11 — Freeze legacy geometry, not partition independence

Before (`11-before`, real Forge scheduling profile, PASS 1m), PerformanceConfig accepts
tile exponents 2, 3 and 4. Its reader currently ignores file overrides and returns
defaults, but its constructor and historical MAX_TILE_SIZE=8 misleadingly permit a
future performance-only override. The constructor now rejects non-3 exponents with
an explicit generation-version-boundary message; the maximum reflects that constraint.
Both production initialization and preview scheduling use this config. Lower-level
TileGenerator/test/preview geometry is deliberately not rewritten or normalized.

Frozen Legacy Default: exponent **3**, 8x8 core chunks = **128x128** blocks, **one-chunk
halo** = 16 blocks per side, total **160x160**, default batch count **6**. Existing
custom presets retain the exact persisted erosion-lifetime rule: lifetime 12/31 gives
16 border blocks, lifetime 32 gives 32 border blocks. That is preset generation
semantics, not a runtime performance setting. Do not silently reinterpret this rule
when Task 2 introduces explicit versioning. Batch/worker scheduling remains separate;
no erosion, halo formula, tile partition or thread-count default is changed here.

After `11-after`, exponents 2/4 are rejected; 3, the default reader, default batch and
all three preset-halo probes are unchanged. All **64 checks pass**, zero differences
in 994 canonical rows. Build PASS, 5s; four-world Forge run PASS, 2m2s. No generation
hot-path overhead: constructor validation only. Production: PerformanceConfig.java
(validation/documentation) and GeneratorContext.java (comment/provenance only).
Tests: ReproductionSuite and Verify-Repairs. Evidence: `11-before`, `11-after`,
`11-legacy-geometry/verification.json`, `logs/11-*.log`. Partition-dependent filtering
remains an explicitly retained legacy behavior; no terrain golden is updated.

## Remaining inherited semantics and investigation fixtures

### Seed narrowing deliberately retained (Task 1A F01, high)

Seed **8675309** and **4303642605** differ by 2^32 and still collide in all 34 recorded
Cell fields/parameter hints at **85/85** fixtures. Continent IDs/edges, terrain,
height, rivers, climate and biome-relevant fields are unchanged. The other 425 pair
rows are also unchanged against Task 1A. MixinRandomState and GeneratorContext still
use the existing int seed: Task 2 must supply versioned 64-bit named seed streams.
Full Minecraft seeds remain independent for other subsystems. The eight live paired
chunks retain equal exact RTF samples and stored quart-biome hashes, while full-seed
Minecraft density/noise and fuzzy biome sampling are not required to collide.
Do not treat the RTF collision as equivalence of entire Minecraft worlds.

### Direct versus filtered output deliberately not merged (F02)

After context, semantic identity, bounds and lifetime repairs, **340/340** direct/tile
pairs still disagree. Field counts remain exactly: gradient 340, height 319,
heightErosion 176, sediment 96, terrain 6, plus corresponding hints. No riverMask,
climate/BiomeType or other recorded parameter changes appear in this comparison.
The six terrain cases are direct coast versus filtered beach: seed 42 at
(-3520,-4096), (126,126), (127,127), (128,128), (129,129), and seed -987654321 at
(-2048,-4096). Warm repeats equal filtered, eviction returns direct, regeneration
returns filtered; all 680 eviction/reload rows preserve these separate contracts.

This is remaining legacy query ambiguity, not residual scratch-cache staleness.
Tiles include erosion, smoothing, steepness, beach and quart filtering; direct
Heightmap evaluation omits those and has its own point coast adjustment. Normal
in-chunk CacheChunk reads use filtered tile readers. Transient density and
single-column/outside-chunk fallbacks may still sample direct, as may locate/spawn
height queries. CellTest uses riverMask and a terrain blacklist, not height/gradient;
no changed structure-rule decision was demonstrated. No command-level locate/spawn
or climate-selected-biome change is claimed. A later canonical GeographyProvider
must define these contracts; Task 1B does not pick a universal winner.

### Tile partition semantics deliberately retained (F06)

Changing tile size/border still changes filtering output, and overlap probes still
show partition-dependent height/gradient/erosion/sediment. Batch submission order
and partition geometry are not interchangeable concepts. Bounds correction changes
480 outer-halo gradient cells in the detailed 25,600-cell comparison (raw X 0/158/159,
world X -16/142/143 in tile 0,0); zero core cells change. Those exact corrected invalid
neighbor effects are recorded in `03-tile-bounds/halo_comparison.json`, not blessed
as a new terrain baseline. Erosion and finite halos have not been normalized.

### Unexplained FULL blocks retained as an issue, not fixed (F12)

Final same-source pair `11-after` / `final-repeat`: **12/24** chunks differ in finished
blocks, compared with **10/24** in Task 1A. The observed count increased by two;
this does not establish a repair-induced regression or a cause. All 24 exact RTF
samples, stored quart biomes and fuzzy block-biome hashes agree between these runs.
Dripstone/decorations remain prominent; there is still no NOISE-stage isolation or
proof assigning the discrepancy to a particular stage, cache or scheduler.

| Seed | Fixture block X/Z | Differing blocks |
| --- | --- | ---: |
| 8675309 | -129,-129 | 2 |
| 8675309 | 127,127 | 2 |
| 8675309 | 128,128 | 24 |
| 8675309 | 3392,-3072 | 12 |
| 4303642605 | 0,0 | 1 |
| 4303642605 | -129,-129 | 1675 |
| 4303642605 | -4032,-4096 | 6 |
| 4303642605 | -2688,-4096 | 114 |
| 4303642605 | 3392,-3072 | 5 |
| 42 | -129,-129 | 2 |
| 42 | 128,128 | 6 |
| 42 | -2688,-4096 | 62 |

Exact block transitions, Y extents and example coordinates are in
`final-chunk-repeats/chunk_repeats.json`; lossless snapshots are retained in both runs.
Keep this high-priority investigation fixture. Do not turn these blocks into goldens.

### Limits of the result

Tests are finite, not proof over every seed and schedule. Production noise-registry
sharing contamination was never reproduced; the unsafe shared-noise API is now safe.
No normal nested Cell resource leak was reproduced; its duplicate-release misuse is
now guarded. Published Cells remain internally read-only by contract, not immutable
Java objects. Raw dormant Perlin definitions still produce NaN outside the guarded
density boundary. Catastrophic OOM and deliberately shutting down the shared worldgen
executor were not injected; accepted-task draining/partial-allocation paths were
reviewed and ordinary failure/cancel/unload/expiry paths tested. Performance instrumentation
works, but snapshot allocation cost still requires Task 1C measurement, not optimization here.

## Final build, runtime and evidence gate

Verified source head: `360c834497f5042d9ade3ca100e704781a5f72fb`. The last commit adds
only the optional vanilla-world smoke mode and its run property; `src/main` is identical
to repair head `408ba001568c44774a37d13cb4e924d4143ef4e5`. The final documentation-only
commit records its own identity through Git history, not a circular self-embedded SHA.

Toolchain unchanged: Minecraft 1.20.1, Forge 47.4.22, Java 17.0.17+10, Gradle 8.11,
ForgeGradle 6.0.42, MixinGradle 0.7.38 / Mixin 0.8.5; optional TerraBlender 3.0.1.10.
Dependencies, mapping versions and production source layout are unchanged. No PA,
Dynamic Trees, temperature or TFC dependency/integration work was done.

| Gate | Result |
| --- | --- |
| compileJava, classes, jar, build, reproductionClasses | PASS; final production build 2s |
| Build plus optional vanilla smoke classes | PASS, 4s |
| Final full regression, 4 worlds | PASS, 1m57s; 64 correctness/comparator checks |
| Worker count 2 | PASS, 1m16s; 120 tile digests |
| Worker count 48 | PASS, 59s; 120 tile digests |
| Full runs + worker runs | 480/480 digests match Task 1A, all orders/repeats |
| Standalone RTF save/reopen smoke | PASS, 57s; title, preset load, 14 FULL chunk observations |
| TerraBlender RTF save/reopen smoke | PASS, 1m3s; optional Mixins enabled, 14 FULL chunks |
| Vanilla world without RTF preset | PASS, 50s; 14 FULL chunks, save/reopen, all 3 dimensions unclaimed |
| Enabled preset export | 175/175 files byte-identical to pre-Fix-8 export |
| Production jar | One Forge mod; two required Mixin manifests, AT, mods.toml and MIT license; zero development classes |

The vanilla test verifies context=false, required=false, preset=false for overworld,
nether and end, both before and after reopen; zero RTF structure rules. This extends
Fix 7 beyond its already-tested bootstrapped vanilla router. Normal RTF smoke also
generates coast/river/mountain/plateau representatives before saving. These are actual
client/server/registry/chunk tests, with objective logs and surface/block evidence;
no new manual aesthetic terrain inspection is claimed.

Artifact: `build/libs/reterraforged-forge-1.20.1-0.0.6.jar`, 1,075,208 bytes,
SHA-256 `25cad0f81a03c2508a9ceb00637fc870534f00b016ff1d80e6190a81451916d4`.
The sources jar is a source archive, not a second mod. Normal Gradle `test` is NO-SOURCE:
the real correctness gate is the Forge development harness plus Verify-Repairs.ps1,
not a claim of an unconfigured unit-test suite passing.

### Warning categories

No fatal generator, required-Mixin, registry or preset error remains in the successful
smoke runs. Expected negative tests catch and record their descriptive failures.
Remaining warnings are inherited deprecation/removal/unchecked compiler diagnostics
(including three reproduction isAbsent warnings), Gradle 9 deprecations, development
refmap warnings, optional Fernflower debug decompiler unavailability, Forge library
mods.toml notices, shader/sound warnings, Netty IPv6 parsing and fresh Forge server
config defaults. Realms development-account authentication messages are unrelated to
world generation. The repeated-world missing reterraforged:clay tag diagnostic remains
a known non-fatal issue. Bulk server-thread harness work logs Can't keep up warnings;
those are not an isolated throughput benchmark. One final-run shell wrapper initially
treated a compiler note on stderr as a terminating PowerShell error; it was rerun with
proper native exit-code handling, with no production change.

### Provenance and file inventory

All repair SHAs and **exact per-commit file paths**, including reproduction changes,
logs and JSON snapshots, are in `evidence/commit_files.json`. The numbered sections
above describe the production boundary and before/after evidence for each family.
`evidence/final_regression_summary.json` is the machine-readable final gate;
`evidence/manifest.json` hashes every evidence file except itself (782 indexed files).
`VERIFICATION.md` gives rerun commands and explains why finished blocks are not goldens.

Task 1A evidence and the accepted Task 0 documents are untouched. Root MIT permission
notice, inherited TerraForged notices and Forge LGPL-2.1-only LanguageProvider provenance
are retained. New original harness/scripts/docs remain ARR; touched MIT-derived code
retains or adds its derived-source notice. No blanket license-header rewrite occurred.

**Stop at Task 1B.** No geography extraction, continent/ocean/climate/biome redesign,
integration, runtime ecology or Task 1C benchmarking has started. Seed narrowing and
legacy generation geometry remain intentionally unchanged, apart from guarding misuse
of the performance configuration and correcting demonstrated invalid halo indexing.
