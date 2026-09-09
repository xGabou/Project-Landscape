# Task 1A: inherited determinism, cache and lifecycle audit

Recorded 2026-09-05 through 2026-09-06 against accepted Task 0 commit
`e9dd8841a1b4a95e4bb2b23e084d40abbc1bea70`.

This is a reproduction record, **not a corrected terrain golden baseline**. No
production Java, resources, algorithms, dependencies or default generation settings
were changed. Task 1B is not started. Original test tooling/documentation is All
Rights Reserved; inherited MIT/LGPL and other notices remain untouched.

## Summary and finding register

The inherited engine is repeatable for the tested fixed tile geometry and generation
orders, but it does not provide a context-isolated, lifetime-safe, semantically
consistent point-query facility. Tile geometry itself is part of terrain behavior.
Do not treat these results as permission to change it as a performance setting.

Classification: D1 correctness; D2 determinism; D3 cache isolation; D4 lifecycle/thread
safety; D5 API semantic ambiguity; D6 legacy generation semantics to version/freeze;
D7 unconfirmed. Severity describes the demonstrated defect and credible exposure,
not an assertion that a corrupted live chunk was observed.

| ID | Finding | Class | Severity / scope | Evidence status |
| --- | --- | --- | --- | --- |
| F01 | Long world seeds narrow to int for RTF generation | D2, D6 | high / worldgen, biome | All 34 recorded cell/hint fields collide at 85 fixtures for the low-32-bit pair; full Minecraft chunks do not collide |
| F02 | Direct and filtered queries have different semantics; cache lifetime selects the path | D5, D2 | high / query, biome; structure exposure | 340/340 sample rows disagree; actual post-chunk queries can revert to direct |
| F03 | Static thread-local CellSampler cache ignores context and sampling mode | D3, D1 | high / multiworld, query, biome | A-B-A and B-A-B reproduce; changing position refreshes; separate fresh thread isolates |
| F04 | Noise Cache2d ignores compute seed | D5, D3 | medium / API, latent worldgen risk | Toy graph reproduces; registered graph experiment does not reproduce finite-value contamination |
| F05 | Tile raw X/Z bounds alias other rows | D1 | high / internal query, potentially worldgen filters | Five invalid-axis requests return valid cells |
| F06 | Finite tile partition changes filtered output | D6 | high / worldgen | Size/border variants and overlapping halos disagree; batch/order/worker comparisons do not |
| F07 | Retained tile readers/entries survive release; stale close resets a new owner | D4 | high / query and potential worldgen | Controlled single-thread and retained-worker-reader reproductions |
| F08 | Expiry/failure/shutdown do not consistently dispose ownership | D4, D5 | medium / lifecycle, memory, multiworld | Expiry removes entry without pool return; close stops polling only; failed filter misses pool return |
| F09 | Nested resource usage works; fallback double-close duplicates pool entries | D4 | low / API, development-only misuse demonstrated | Depths 1/2/8/64 and normal concurrency pass; deliberate double-close aliases |
| F10 | Missing context causes deferred NPE or structure-rule fail-open | D1, D5 | medium / query, structure, configuration | Real RandomState initialization/registry tests reproduce |
| F11 | Disabling custom biome features breaks preset bootstrap | D1 | high / datapack, world creation with this option | Real RegistrySetBuilder rejects 17 unreferenced placed-feature keys |
| F12 | Repeated FULL-chunk snapshots differ, despite stable exact samples/biomes | D7 cause; observed mismatch | high investigation priority / worldgen, decorations | 10/24 repeated chunks differ; mostly dripstone; not attributed to RTF terrain or worker scheduling |
| F13 | Two registered, currently unreferenced noise graphs return NaN | D1 | low / development, latent datapack consumer | terrain/erosion and terrain/ridges use scale=1, octaves=200 Perlin; all three tested positions non-finite |

No critical-severity live-world corruption is claimed. F07 permits corruption of an
active internal reader, but ordinary Minecraft chunk generation was not made to hit
that interleaving. Runtime exposure and controlled reproductions are distinguished below.

## Environment and methodology

- Windows 11 amd64, Eclipse Temurin Java `17.0.17+10`.
- Minecraft `1.20.1`, Forge `47.4.22`, official mappings.
- Gradle `8.11`, ForgeGradle `6.0.42`, MixinGradle `0.7.38`, Mixin `0.8.5`.
- Standalone runtime: TerraBlender, PA and the optional development mods absent;
  TB `3.0.1.10` remains the baseline compile-only dependency.
- Native CPU availability: 24; test JVM overrides exercise 2 and 48 workers.
- JVM heap cap 6 GiB for the reproduction client. Worker overrides happen before
  `ThreadPools.WORLD_GEN` initializes; no executor or algorithm is patched.
- `src/reproduction` is a separate, non-packaged developer source set. It launches
  Forge, reaches the title screen, uses CreateWorldScreen's real registry bootstrap,
  exports/loads a Legacy Default preset, creates an integrated server, queries real
  registries/holders, and generates FULL chunks. Registries are not mocks.
- `GeneratorContext` sampling tests use isolated contexts with real loaded preset/noise
  holders. They deliberately give the `makeUncached` result a real TileCache and
  WorldLookup to exercise the existing two paths. The separate missing-context test
  exercises unmodified `makeUncached` without this harness assembly.
- Live chunk evidence uses the actual world's RandomState/context, not those isolated
  contexts. Overworld/Nether/End context presence is also inspected.
- Snapshot: all 24 public instance Cell fields plus 10 CellSampler hints. Float values
  use exact signed IEEE-754 int bits; transient density results use double bits. Terrain
  names and enum names replace unstable object/registry identities. Lists/maps have
  canonical order before SHA-256 hashing. JSON integer bit patterns must not be read
  through a lossy JavaScript double when comparing 64-bit values.
- Whole-tile hashes cover all 16,384 interior cells and all 24 fields, not pooled object
  identity. Hashes are relational observations, never expected terrain constants.
- Each canonical scheduling run has 3 repeats x 8 order labels x 5 tiles. Serial waits
  after each submission; parallel submits all five before joining. Internal chunk
  batches remain the inherited asynchronous implementation in both cases.
- Measurements include observer/allocation/canonicalization overhead; this is **not**
  the Task 1C performance baseline.

Read [HARNESS.md](HARNESS.md) for exact commands, supported parameters, field semantics,
evidence inventory, instrumentation and verification gates. The generated
[summary](evidence/summary.json) is the numerical index; raw per-run observations remain
under [evidence](evidence/). Run identities and build results are in
[verification](VERIFICATION.md).

### Frozen generation settings

Full configuration is recorded by the actual Preset codec in each run's `preset.json`.
Legacy Default uses `MULTI_IMPROVED` continents, continent scale 3000, jitter 0.7;
tile exponent 3 = 8x8 chunks = 128x128 core blocks; border 1 chunk = 16 blocks per
side; total 160x160; batchCount 6. Erosion uses 135 droplets/chunk, lifetime 12,
velocity/volume 0.7, erosion/deposit rates 0.5. Smoothing: one iteration, radius 1.8,
rate 0.9. Custom biome features and erosion decoration remain enabled.

Geometry experiments record the baseline first, then create separate contexts with
batch counts 1/3/12, tile exponents 2/4, borders 2/0. They do not change world config.
`PerformanceConfig.read` currently returns defaults; its threadCount is not the active
worker selector. `MixinRandomState` uses `ThreadPools.availableProcessors()` and enables
prequeueing only above four processors; isolated comparison contexts consistently set
queue=false so the tile geometry/order experiment does not conflate that switch.

### Seeds and coordinate discovery

Seeds: `8675309`, `4303642605` (= 8675309 + 2^32), `42`, `-987654321`.
The first three also get separate actual Minecraft worlds. The fourth gets raw,
filtered and eviction sampling, not FULL chunk generation.

For every seed the fixed axes are:

```text
-129 -128 -127 -17 -16 -15 -5 -4 -3 -1 0 1 3 4 5 15 16 17
63 64 65 126 127 128 129
```

Each supplies `(n,n)`, `(n,0)`, `(0,n)`; this includes origin, signed quart/chunk/tile
edges, corners and adjacent values. Duplicate coordinates with different labels are
retained explicitly: 85 fixture *rows* are not 85 unique coordinates.

Geography fixtures are discovered from direct Heightmap sampling in row-major order,
x/z -4096..4096 step 64, taking the first category match. Adjacent scan samples identify
mountain/region transitions (reset adjacency at each new row). Neither this search nor
its outputs are goldens. `fixtures.json` records all seeds, labels and coordinates.

| First seed representative | X | Z |
| --- | ---: | ---: |
| coast | -4032 | -4096 |
| deep ocean | -4096 | -1728 |
| shallow ocean | -4096 | -4096 |
| river | -64 | -4096 |
| mountain / mountain edge / region transition | -2688 | -4096 |
| plateau | 3392 | -3072 |
| plains | 1280 | -4096 |
| river-mouth proxy | -2752 | -2432 |

Limitation: the mouth fixture means river terrain below the inland continent threshold,
not a solved downstream network endpoint. `RiverCarver.getMouthModifier` is driven by
continentEdge; Rivermap/Network also warp coordinates. Exact mouth endpoint inversion
and sub-block transition localization were not proven. These labels must not be
promoted to topological truth in Task 1B.

## F01: seed narrowing

Reproduction: compare the four contexts at the first seed's identical fixture suite.
In `seed_collisions.json`, the pair 8675309/4303642605 has zero differing fields in
85/85 rows. Every unrelated pair differs in all 85 rows (not necessarily every field).
Matching fields include continent center/ID/edge, terrain/region identity, height,
riverMask/erosionMask, climate and BiomeType, and all ten Minecraft parameter hints.
Filtered exact live-context samples at the eight chunk coordinates also match for the
collision pair.

```text
Minecraft WorldOptions.seed : long
  -> RandomState constructor / MixinRandomState stores long
       +-> (int) seed -> GeneratorContext -> Seed -> Heightmap
       |                              -> continents, terrain, rivers, RTF climate
       +-> (int) seed -> NoiseFunction(Marker holder)
       +-> (int) seed -> RTFRandomState.seed / Noises.shiftSeed
       +-> original Minecraft visitor -> NormalNoise / full-long seeded systems
Minecraft world seed -> BiomeManager obfuscated biome-zoom seed (independent)
```

Source: `mixin/MixinRandomState.java` constructor redirect and `initialize`;
`world/worldgen/GeneratorContext.java`; `densityfunction/NoiseFunction.java`;
`data/worldgen/preset/PresetNoiseRouterData.java` registers cell-controlled temperature,
vegetation, continents, erosion and ridges, while retaining vanilla aquifer/ore/cave
NormalNoise density functions.

The eight FULL chunks do **not** become identical worlds: all block hashes differ;
sampled aquifer barrier, ore vein toggle and finalDensity at Y=20 differ in every pair.
Stored quart-biome hashes match at all eight chunks, while block-position biome queries
using `Level.getBiome` differ (Minecraft BiomeManager's full-seed positional zoom).
WORLD_SURFACE heightmaps include features/trees and are not raw RTF terrain heights.
Their differing columns are enumerated in `summary.json`; do not interpret those as
failure of the recorded RTF height collision. Independent caves, surface/decorations,
ores and Minecraft RNG can still use the long seed. Structure layout collisions were
not measured; this audit does not assert that every non-RTF system differs everywhere.

Boundary for the later fix: seed identity and seed propagation/version dispatch only.
Keep an explicit legacy int-seeded version; do not change unrelated noise mathematics.

## F02: cache-dependent query semantics

For each seed, fixtures are grouped by tile to prevent accidental warmth:

```text
A: absent tile -> WorldLookup(load=false, climate=true) -> direct Heightmap.apply
B: cache.provide/join tile -> same WorldLookup -> filtered Cell copy
C: repeat warm lookup
D: 64 actual TileCache.drop calls -> same lookup -> direct again
reload: provide/join again -> compare with B
E: actual Minecraft FULL chunk -> its own context lookup; record whether cache hit
   then raw Heightmap sample and load=true exact sample at that same point
```

`WorldLookup.compute` also has a point-only coast->beach rule. `TileGenerator.generate`
runs terrain/rivers/climate per cell, then WorldFilters: erosion, sequential smoothing,
steepness, beach detection and quart correction. Climate is not rerun after erosion.
`load=true` means force the tile path, not an independent ground-truth algorithm.

Across 340 rows:

| Differing raw field | Rows |
| --- | ---: |
| gradient | 340 |
| height | 319 |
| heightErosion | 176 |
| sediment | 96 |
| terrain | 6 |

Corresponding hints differ in the same counts. No riverMask, erosionMask, continent,
climate, BiomeType, erosion/weirdness or other hint differences were observed in this
A/B suite. Six terrain differences are direct `coast` versus filtered `beach`:
seed 42 at (-3520,-4096), (126,126), (127,127), (128,128), (129,129), and seed
-987654321 at (-2048,-4096). C matches B; D matches A; reloaded tiles match B exactly.

Case E proves normal generation does not keep every tile alive. The precise cache hit
counts and coordinates are recorded per run: an after-chunk `load=false` result can
match direct instead of exact even though that chunk is FULL. Do not make a golden
from E's opportunistic lookup. Its path availability is a finding, not desired behavior.

### Consumers and impact (not inferred from class names)

| Consumer/call path | Meaning / demonstrated limit |
| --- | --- |
| CellSampler.compute -> static Cache2d -> WorldLookup(load=false,true) | Transient router and climate/biome queries depend on tile availability; additional static-cache staleness in F03 |
| MixinNoiseChunk -> CacheChunk with Tile.Chunk for cellCountXZ>1 | Normal in-chunk terrain density reads are filtered; not the static global cache path |
| CacheChunk outside current chunk, or cellCountXZ=1 -> per-NoiseChunk Cache2d -> WorldLookup(load=false,false) | Query-only and density-edge/height-query paths can fall back direct; climate flag is different as well |
| CellTest.test -> WorldLookup(...,false) | Four-argument overload means climate=false, not load=true. Tests riverMask and mountain blacklist, not height/gradient. Those rule outcomes were not shown to change in this suite |
| MixinStructure / Minecraft structure height checks | Structure rule and getBaseHeight/NoiseChunk single-column paths expose querying; no changed accepted/rejected structure placement was demonstrated |
| Minecraft MultiNoiseBiomeSource / Climate.Sampler | Cell parameter path exposed; no A/B climate parameter or selected-biome change proved by the fixture suite |
| Minecraft biome/structure locate and spawn sampling | Can call these samplers/height paths. No command-level locate/spawn result difference is claimed |
| PresetEditorPage preview | Explicit makeUncached -> generateZoomed(...,false); omits optional filters deliberately. Does not call broken makeUncached.lookup.applyCell |
| ErodeFeature, DecorateSnowFeature, elevation/biome-edge chance, FastPoissonModifier | Fetch Tile.Chunk explicitly for generation/features; retain F07 lifetime exposure |

Source inventory is searchable under `src/main/java/Gabou/reterraforged`; the
active filter family is `world/worldgen/densityfunction/tile/filter`, not the parallel
older `cell/filter` classes.

Later fix boundary: define explicit raw/filtered query contracts, cache identity and
consumer choices. No automatic decision that A or B is universally correct.

## F03: CellSampler isolation, mode and warmth

At P=(127,127), contexts A=8675309 and B=42, a same-thread sequence invalidates at
another position, samples A.P, B.P, A.P, then changes position before sampling B.P.
The B-after-A result is exactly A in all ten density fields; compared to refreshed B,
HEIGHT, CONTINENT, EROSION and BIOME_REGION differ. Reverse B-A-B and a single-worker
executor reproduce the same effect. A different fresh worker obtains uncontaminated B.
Other fields happen to match at this P; all cell fields share the same unsafe cache.

At (125,-127), false->true sampleClimate on the same Cache2d and position fails to
populate climate. A fresh true-mode cache differs in biome, biomeRegionEdge/Id,
macroBiomeId, moisture, temperature, regionMoisture/Temperature and corresponding
biome-region/moisture/temperature hints. At (3,3), direct->tile-warm retains stale
HEIGHT, GRADIENT, HEIGHT_EROSION and SEDIMENT until another position is sampled.
Before-tile and same-position after-tile values are identical; a position change
exposes the filtered values. There is no tile-generation epoch in the cache key.

```text
thread T
  static CellSampler.CELL -> Cache2d { lastPos=P, mutable Cell }
                                    ^ missing: lookup/context, seed, mode, tile lifetime
  sampler/world A -> getAndUpdate(A.lookup,P) -> fills A
  sampler/world B -> getAndUpdate(B.lookup,P) -> returns A without calling B.lookup
                                                   |
                           Field.read(A-cell, B-heightmap) can mix contexts/settings

NoiseChunk instance -> its own Cache2d + optional retained Tile.Chunk
  in-chunk -> Tile.Chunk (not static CELL)
  fallback -> instance Cache2d (normally one RandomState owner)

Noise Cache2d instance -> per-thread {last float X/Z, value}; no compute seed key
TileCache instance -> packed tile X/Z -> CacheEntry -> Entry -> Tile
  tile position map itself belongs to one GeneratorContext, not a static world map
```

This is confirmed cross-world **data contamination**, not a demonstrated contaminated
saved chunk. Minecraft's in-chunk wrapper avoids static CELL; out-of-chunk density
fallback has separate ownership. Direct sampler calls can still affect biome/query
decisions. A new thread hides the defect; another position overwrites it. There is no
context-aware reset in CellSampler. Integrated servers may use different server threads,
but static/shared worker threads can survive successive worlds. Default dimensions
share the same long seed, which can also conceal missing dimension identity.

Later fix boundary: cache key/lifetime/mode only, followed by targeted caller tests.
Do not fix seed narrowing or alter climate at the same time.

## F04: noise Cache2d ownership

Same Noises.cache2d(Noises.simplex(0,100,3)), P=(17,29), compute seeds 123 then 456:
the second result keeps bits `1054603234` (~0.4296256), while uncached and a fresh
cache return `1051087284` (~0.3248421). Switching back to 123 matches again. This is
an unsafe reusable-noise API.

Classification: **UNSAFE API / LATENT BUG** for production exposure. Context-created
terrain caches in Populators are constructed with their context's seeded graph and
normally computed with seed 0; their current ownership is safe for this assumption.
`Cache2d.mapAll` constructs a new cache. `NoiseFunction.Marker.mapAll` only deep-maps
the holder's noise if the visitor also implements Noise.Visitor; MixinRandomState's
ordinary visitor instead wraps the existing holder in NoiseFunction with an int seed.
Thus the interface does not guarantee immutable seed ownership for every consumer.

The harness copies real registry graphs and intentionally shares those copies between
NoiseFunctions with seeds 123/456, at three positions. No finite-value contamination
was reproduced there. Cached iceberg subgraphs were present, but matching outputs
at these points do not establish all-position safety. Newly mapped graphs and distinct
world-owned registries avoid sharing. NaNs from two registered noise graphs are recorded
as non-finite observations, not counted as contamination (`NaN != NaN` would be a testing
error); see F13 for their actual construction. No vanilla noise Cache2D behavior is
being diagnosed here.

Later fix boundary: define/assert per-graph seed ownership or key the existing cache
by compute seed; do not replace composable noise math.

## F05: raw tile bounds

Tile (0,0), total width 160, valid raw indices x/z=0..159 inclusive.
`Size.indexOf(x,z)` computes z*160+x; `Tile.getCellRaw` checks only the resulting
linear index. These raw coordinates include the halo, not world block coordinates.

| Invalid request | Index | Returned raw cell |
| --- | ---: | --- |
| (-1,1) | 159 | (159,0) |
| (160,0) | 160 | (0,1) |
| (160,1) | 320 | (0,2) |
| (320,0) | 320 | (0,2) |
| (-160,1) | 0 | (0,0) |

Z-only under/overflow and the tested invalid corners return Cell.empty; valid corners
(0,0)/(159,159) return their own cells. Expected boundary contract would reject either
invalid axis (absent or explicit failure); the test does not implement that choice.

Steepness and BeachDetect read neighborhood raw coordinates; they process the outer
halo too. Smoothing bounds its center loops. Therefore this is not merely a hypothetical
external misuse. Default core points have a 16-block halo; a visible core seam caused
specifically by raw-index aliasing has not been isolated. F06 is not proof that F05 alone
caused every overlap discrepancy. `Tile.lookup` also masks coordinates without verifying
the caller selected the proper world tile; current WorldLookup selects it first.

Later fix boundary: independent axis bounds and tightly scoped caller/filter regression
tests; document any legacy halo-output change instead of updating invalid-path goldens.

## F06: tile partition versus task scheduling

Five tiles: center(0,0), north(0,-1), east(1,0), south(0,1), west(-1,0).
Orders: center_first, reverse, north/south/east/west_first, clockwise, parallel.
Clockwise and north_first deliberately use the same N/E/S/W/C ordering; they are not
claimed to be independent permutations. Eight labels, three repeats per run.

Every fixed tile has one hash across the tested order/repeat/worker-count runs.
Batch counts 1,3,6,12 agree at all four geometry probe coordinates. This does not prove
arbitrary scheduling safe, but it does **not reproduce algorithm scheduling nondeterminism**.

Compare the same world point (64,64), (127,64), (127,127), (-129,-64) while altering
only tile size/border in new contexts: exponents 2/4 and borders 2/0 each differ at
4/4 points from (size3,border1,batch6). Differences concern height, gradient,
heightErosion, sediment and corresponding hints; exact per-variant fields are recorded.

Compare world x=120..135, z in {0,1,64,126,127} between tile(0,0)'s core/halo and
tile(1,0)'s halo/core using independently in-bounds raw coordinates. All 80 points
differ in height/gradient/heightErosion and hints; 70 differ in sediment/hint. This is
partition dependence, not evidence that a correctly selected core tile changes with
neighbor generation order.

Trace: TileGenerator batches disjoint cells, waits for all futures, then applies
WorldFilters. Erosion seeds droplets by world chunk and iteration, but reads/writes
the finite array, truncates at array boundaries and uses array-sized brushes.
Smoothing mutates sequentially in row order. Halo size changes available terrain,
which droplets interact, and prior updates. Rivers and terrain/climate populate before
these filters. No terrain, riverMask or climate field differences were observed in the
overlap sample. This audit has not isolated each filter's individual contribution.

Later fix boundary: treat geometry/halo/filter order as versioned generation semantics.
Do not optimize tileSize/border under the assumption that only cost will change.

## F07/F08: ownership and release

```text
CacheManager.CACHES (static strong list, no unregister)
  -> Cache (maintenance ScheduledFuture; locked position map)
      -> CacheEntry (Future -> lazily stored value)
          -> TileCache.Entry { completed-drop count, Tile }
              -> Tile { Resource<Cell[]>, Resource<Chunk[]>, direct array references }
                   +-> mutable Cell -> callers may retain
                   +-> Tile.Chunk -> NoiseChunk / feature readers may retain

drop >= number of core chunks (64 at baseline)
  -> Tile.close: reset every Cell, null chunk array, return resources to pools
  -> map removal; existing entries, futures, Tile.Chunk and Cell references not revoked
  -> generator pool reborrows SAME resource wrapper/arrays for another Tile
  -> retained reader sees new tile's data
  -> stale old Tile.close resets the new owner's arrays and can republish resources

TTL poll / capacity removal -> remove map entry without the above disposal
Cache.close -> cancel poll only; map and global registration remain
failed tile filter -> exceptional future, no Tile.close cleanup path
```

Controlled reproduction: retain origin Cell, Tile.Chunk and CacheEntry. After 63 drops
the published tile/ref is unchanged. After drop 64, the retained cell has zero height,
terrain `none`, default climate. Generate tile(2,0): the old and new tile have the same
backing array, the retained chunk still returns the same Cell now containing different
terrain, and the old entry still returns a value. A worker deliberately held past the
release observes the changed cell. Closing the stale Tile resets the replacement.

These are real internal classes and real pooled arrays; the harness controls when the
reader is allowed to continue. It does not reproduce a naturally racing Minecraft
NoiseChunk. Drop counting tracks completions, not outstanding readers or unique chunk
IDs. No pin/lease connects readers to eviction. Chunk-status hooks are live: observer
counters record nonzero queue/drop calls **before** any harness-injected drops. Feature
and density readers therefore share this ownership regime in production.

Separate expiry reproduction: set only a test-owned CacheEntry timestamp to zero,
invoke Cache.poll, observe removal, resource still open and zero arrays returned to
the pool. Test cleanup then explicitly closes the retained tile. Capacity eviction
uses StampedBoundLongMap.removeFirst without a disposer too (source-confirmed; not
separately stress-tested to 257 large tiles).

Inject a null filter into a test-owned TileGenerator only, after saving the field;
join generation, record the expected NPE and zero returned arrays, then restore the
field. This proves missing failure cleanup/pool return, **not a proven permanent heap
leak**: unreachable arrays may be garbage-collected. Cached failed futures and globally
retained caches require separate retention analysis. Completed test contexts remain in
CacheManager after close; close stops polling and does not invalidate future queries.
CacheManager.clear loops close without clearing its own list/maps. Only GUI preset
editor calls to clear were found, not a world-disposal ownership protocol.

Still unproven: simultaneous threshold drops both releasing one tile, concurrent TTL
poll versus reader acquisition, and a saved chunk showing recycled-cell corruption.
The check/increment/close/remove sequence permits problematic interleavings but this
report does not claim all were observed. Lazy getChunkReader after a cleared slot also
passes world chunk coordinates to a constructor expecting region coordinates; normal
generation prepopulates writers, so that separate suspected path remains unisolated.

Later fix boundary: explicit acquisition/reader lifetime, single-owner disposal,
idempotent release independent of reusable wrapper identity, and disposal for all
eviction/failure/shutdown paths. No climate or tile-geometry redesign is required.

## F09: nested Cell resources

Normal usage obtains and opens each resource before nesting, keeps it on its originating
thread and closes once in reverse order. Depth 1/2/8/64: unique cells, preserved outer
values, and closed thread-local SimpleResource after a deliberately thrown exception.
Four concurrent workers hold four distinct Cells at a barrier. No leak/unsafe sharing
was reproduced under that contract.

Isolated ThreadLocalPool(4) test reserves two resources, closes one twice, then borrows
twice: both borrows return the same Cell. PoolResource.close has no released state and
isOpen always returns true. This is confirmed misuse sensitivity, not evidence that
existing try-with-resources callers double-close in production. Crossing threads with
a retained pool resource was not stress-tested and is not declared supported.

Later fix boundary: resource lifecycle assertions/idempotence only. Retain allocation
and normal nested sampling characteristics.

## F10: missing/incorrect/closed context

Real bootstrapped noise settings and holders are used for each RandomState experiment.

| Case | Observed result |
| --- | --- |
| GeneratorContext.makeUncached.lookup.applyCell | NPE: WorldLookup.cache is null |
| RTF NoiseRouter sampled before RTFRandomState.initialize | Deferred supplier NPE accessing generatorContext.lookup |
| Same state after initialize with real registries | Context present |
| Initialized with an actual empty Preset registry replacing the loaded one | Initialization does not report missing preset; later sampler NPE |
| Registered structure cell rule with that missing context | Returns true silently |
| Actual Overworld, Nether, End states | All have preset and context in this preset setup |
| Test cache closed, then exact lookup | Still generates/returns a cell; close is not context shutdown |

The missing-preset exception is commented out in MixinRandomState. CellTest returns true
when no context exists. PresetEditorPage uses makeUncached.lookup only to reach Heightmap
and generates a zoomed tile directly, so its normal preview does not trigger the lookup
NPE. Presence of a context in Nether/End is not proof that vanilla dimension terrain uses
the Overworld RTF model; additional density-function wrapping initializes context too.
No dimension-specific climate/biome authority change was made or proposed here.

Later fix boundary: initialization invariants and explicit missing/closed-context contracts.
Decide vanilla/non-RTF fallback separately from corrupt RTF configuration; do not
substitute default geography silently.

## F11: customBiomeFeatures=false

Use a fresh Legacy Default, set only `miscellaneous.customBiomeFeatures=false`, call
the real Datapacks.makePreset and run its providers with CreateWorldScreen's registry
context. RegistrySetBuilder.buildPatch throws `IllegalStateException: Errors during
registry creation`. The full suppressed-exception tree is in `vegetation_disabled.json`.

The 17 keys are `reterraforged:` plains_trees, forest_trees, flower_forest_trees,
birch_trees, dark_forest_trees, savanna_trees, swamp_trees, meadow_trees, fir_trees,
windswept_hills_fir_trees, pine_trees, spruce_trees, spruce_tundra_trees, redwood_trees,
jungle_trees, jungle_edge_trees, wooded_badlands_trees. The exception calls them
**Unreferenced key** in `minecraft:worldgen/placed_feature`; do not substitute an
invented missing-file error.

PresetBiomeModifierData.bootstrap obtains these holders before its custom-feature
conditional; PresetPlacedFeatures conditionally registers their values. The builder
retains unresolved requested keys even though replacement modifiers are not registered.
Bootstrap fails before a complete datapack exists, so loading/resolving a successful
disabled pack is impossible in the baseline. Default enabled export and real load pass.
No vegetation policy or Dynamic Trees integration was changed.

Later fix boundary: guard matching holder acquisition/registration consistently, with
the same real-registry test. Do not remove custom trees as part of that fix.

## F12: repeated FULL-chunk block differences (cause unproven)

The two archived full 24-worker runs used identical seeds, preset and harness inputs.
Both completed successfully. Of 24 matched FULL chunks, 10 have different block-state
snapshots, while all 24 retain equal exact RTF samples, stored quart-biome hashes and
block-query biome hashes. This is an observed end-to-end mismatch; it must not be hidden
by reporting only the stable tile hashes. `chunk_repeats.json` records every changed
chunk, transition counts and up to 20 exact block coordinates per chunk; lossless
compressed snapshots retain all blocks for independent comparison.

Changed-block counts: seed 8675309: (127,127)=12, (3392,-3072)=16;
seed 4303642605: (0,0)=1, (-129,-129)=1747, (-2688,-4096)=78,
(-64,-4096)=5, (3392,-3072)=5;
seed 42: (0,0)=1, (-129,-129)=2, (-2688,-4096)=13.
The largest change involves dripstone/deepslate/water/pointed-dripstone from Y=-56..92.
Other differences include coal ore, glow lichen, grass/fern and fence connections.
These are actual generated-world blocks, not merely API snapshots.

The capture point is FULL via ServerLevel.getChunk, after Minecraft generation stages
and decoration; it is not a NOISE-stage snapshot. Requests can generate neighbors, and
worlds also tick before the sampling callback. The block types locate much of the
variation in decoration, but do not prove its cause or exclude a prior density/height
query changing feature placement. In particular, equal stored biomes do not prove that
every earlier feature query was equal. No statement that random ticks alone, vanilla
dripstone alone, cross-world CELL, or RTF tile worker scheduling caused this is justified.

Classification is D7 for causal attribution, high-priority worldgen/decoration
investigation, not a confirmed RTF terrain-algorithm determinism bug. Next evidence
boundary: capture NOISE/SURFACE/FEATURES stage outputs with controlled neighbor order
and tick-free capture, then attribute the first differing stage. Do not disable/remove
features or alter structure/vegetation rules in Task 1A to make these hashes match.

## F13: non-finite registered noise graphs

The registry graph experiment found NaNs in `reterraforged:terrain/erosion` and
`reterraforged:terrain/ridges` at (17,29), (100,200), (-2048,3072), with both tested
compute seeds and fresh graph copies. Cache node count is zero for these graphs;
this is not noise-cache contamination. The IEEE-754 values/finite flags are retained
in noise_cache.json/noise_cache_bits.json.

Trace: PresetTerrainNoise.makeErosion and makeRidges both construct
`Noises.mul(Noises.perlin(0, 1, 200), Noises.worley(0, 200))`.
Noises.perlin's arguments are **seed, scale, octaves**, not seed, octaves, scale.
That is 200 octaves at scale 1; Perlin.compute repeatedly doubles float coordinates,
eventually overflowing at these nonzero positions. Both cached and uncached comparisons
thus have the same non-finite result. Swapped intended parameters are plausible but
not assumed as the correction without reviewing ownership.

Search found registration/export, but no active Java/resource consumer of those keys;
the active biome hints use CellSampler fields instead. Severity low, D1,
development/datapack latent consumer scope. No NaN cell heights or chunk generation
exception was observed. Later boundary: validate/fix the unused graph definitions or
their supported input constraints separately, without changing active terrain noise.

## Remaining unproven concerns and limits

- No naturally corrupted saved chunk caused by cross-world static CELL or concurrent
  eviction was reproduced. Vulnerable internal/query paths are demonstrated separately.
- No default structure-rule outcome change, locate result change, spawn choice change
  or cache-induced selected-biome change was demonstrated. Caller exposure is not proof
  of every downstream outcome.
- Fixed tile hashes are stable; arbitrary scheduling, every seed, queue latency and
  all modded dimensions are not proven safe. Worker-count experiments primarily compare
  tile generation, not a complete controlled parallel Minecraft chunk-status matrix.
- Whole FULL-chunk block snapshots include decorations and possible live-world ticks.
  They are observations, not terrain goldens or a pure NOISE-stage determinism test.
- River-mouth proxy is not an exact network endpoint; terrain-edge scan is 64-block
  resolution. No broad fixture-discovery geographic promises are made.
- Registered cached-noise graphs did not reproduce finite contamination in the tested
  positions. Shared noise graph reuse across real different-seed world registry sets
  was not forced; toy API misuse is distinct from observed default ownership.
- Expiry/failure missing pool return is not equated with a permanent heap leak. Heap
  retained-size analysis, capacity pressure, simultaneous close/drop and cancellation
  stress remain later evidence work before choosing a lifecycle fix.
- No TerraBlender/modpack determinism claims; no PA, Dynamic Trees, ecology, temperature
  or TFC integration. These runtimes were deliberately absent.

## Task 1B handoff boundaries

Fix one finding at a time, replacing its reproduction assertion with a correctness
assertion after the contract is decided. Preserve old raw and filtered observations
as competing paths, not goldens. Prioritize isolation and ownership before API fixtures;
handle long-seed versioning independently; keep tile geometry/filter semantics explicit.
Task 1C will establish comparable timings after correctness contracts stabilize.
This document proposes boundaries only; no fixes, geography API or extraction begins here.
