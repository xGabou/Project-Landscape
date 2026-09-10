# Task 3 — geography extraction

Status: Task 3 complete. Exact legacy comparators, performance, build/datagen and runtime gates pass.
Started from clean accepted Task 2 `203dc3c1c2e1d4e09fa911a3d6dc7472664f4ba7`.
All 342 Task 2 indexed evidence hashes verified before changes. LEGACY_RTF_V0 is immutable
comparator behavior, not permission to update goldens after a refactor.

## Exact starting orchestration

`Heightmap.make` constructs, in order: region warp/config; RegionModule; mountain selector
(Worley edge, Perlin warp, curve/clamp/map); ground registry noise; TerrainProvider/RegionSelector;
border terrain; RegionLerper; mountain-chain populator; ContinentType implementation; Climate;
land Blender; deep/shallow ocean/coast populators; continent lerpers; beach noise.
Keep every `Seed.next/offset` call and noise graph construction in that order. Legacy does not
use the Task 2 named seed service.

There is **no** `Heightmap.applyContinent` or `applyPost` method in the accepted source.

| Method / actual caller | Writes and ordering | Ownership |
|---|---|---|
| Heightmap.applyTerrain | terrain=FLATS, beachNoise; Continent.apply; RegionModule.apply; terrain graph at scaled X/Z | Physical geography, with legacy parameter writes inside populators |
| Continent.apply | continentId/Edge/X/Z; variant-specific masking | Physical geography; float ID is a selector/hash, not connected landmass identity |
| RegionModule.apply | terrainRegionId/Edge | Physical geography; normalized cellular selector/edge, not a stable globally unique region ID |
| TerrainPopulator/OceanPopulator/VolcanoPopulator | height/type plus erosion/weirdness | Height/type physical; erosion/weirdness are legacy Minecraft parameter hints |
| Blender / RegionLerper | blend height, erosion, weirdness; Blender chooses categorical type | Physical height/type mixed with legacy parameter interpolation; mountain weights currently discarded |
| ContinentLerper2/3 | blend height while retaining upper populator's other values | Physical geography; do not introduce interpolation of existing parameter channels |
| Heightmap.applyRivers | Rivermap.apply then VolcanoPopulator.modifyVolcanoType | Hydrology followed by volcano classification correction |
| Heightmap.applyClimate (before Climate) | valley threshold .675; river/lake/wetland erosion/weirdness overrides | Legacy Minecraft parameter hints, not physical erosion |
| ClimateModule.apply(mask=true) | region fields, macroBiomeId; biome-center continent query; coast mutation; moisture; BiomeType; altitude-adjusted regionTemperature; exported temperature/moisture | Legacy classification, with a geographically effective coast compatibility side effect |
| Climate.apply | initial module if enabled; submerged COAST becomes SHALLOW_OCEAN even if climate disabled; possible offset second module without mask | Geographic compatibility correction must stay between legacy classification passes |
| Heightmap.applyClimate (after Climate) | negate weirdness if non-valley and macroBiomeId>.5 | Legacy Minecraft parameter hint |
| WorldFilters.apply | optional erosion then smoothing; always steepness then beach; optional quart correction | Geography finalization; ordered over whole tile after all point tasks finish |
| WorldLookup.sampleDirectApproximate | Heightmap.apply then point-only coast→beach | Explicit noncanonical legacy approximation |
| CellSampler.CacheChunk | reads finalized tile for owning chunk; otherwise Cache2d fallback | Minecraft bridge; existing fallback contract must remain legacy-explicit |
| CellSampler.Cache2d | copies a selected cached tile or direct sample; caches context/position/tile token/climate flag | Sampling cache only; Task 1B identities must survive extraction |
| CellSampler.Field | reads hints; CONTINENT maps terrain/edge/beachNoise/height into vanilla continentalness | Legacy Minecraft parameter bridge, not ocean distance or physical continentality |

`TileGenerator.generate` runs each chunk's 16×16 cells in batches: terrain → reused Rivermap
lookup → rivers → climate/parameters. `generateZoomed` uses the same point order at transformed
float coordinates, optional filtering, and fixed tile (0,0). Its output is preview approximation,
not canonical owning-tile geography. `finish` drains accepted writers, applies WorldFilters,
detaches the snapshot, and returns workspace arrays in finally. Keep those lifetime rules.

## Field ownership

- PHYSICAL GEOGRAPHY: height, terrain, continentId/Edge/X/Z, terrainRegionId/Edge,
  beachNoise (legacy shore perturbation). Height/type can later be changed by hydrology/finalization.
- HYDROLOGY: riverMask and erosionMask; RiverCarver writes height, riverMask, erosionMask and RIVER;
  Lake writes height, riverMask and LAKE; Wetland writes height, riverMask, erosionMask and WETLAND.
  Lake/wetland state is categorical terrain, not separate distance/flow fields. Rivermap warps once,
  then Network recursively applies existing carvers/lakes/wetlands. Volcano post-carving type correction
  reads height/riverMask; no new volcano or structure policy.
- GEOGRAPHY FINALIZATION: Erosion deposits height/sediment, removes height/heightErosion;
  Smoothing changes neighbor height; Steepness writes gradient; BeachDetect and NoiseCorrection
  write terrain. `heightErosion` is a signed accumulated removal delta, NOT final eroded elevation.
  `gradient` is a clamped asymmetric neighborhood statistic, NOT physical rise/run slope.
- LEGACY MINECRAFT PARAMETER HINT: erosion, weirdness (written by terrain and compatibility),
  CellSampler continentalness conversion and exported temperature/moisture channels.
- LEGACY CLIMATE/BIOME CLASSIFICATION: biomeRegionId/Edge, macroBiomeId, regionMoisture,
  regionTemperature, BiomeType, temperature/moisture. BiomeType is selected BEFORE the regional
  altitude temperature adjustment; exported parameters derive from BiomeType, not physical units.
- Cell constructor/reset/copy and Tile.snapshot copy all relevant state; these are transport/lifecycle
  writes, not new generation stages. Inactive `cell/filter` duplicates are not WorldFilters' imports;
  the active filters are under `densityfunction/tile/filter`.

The climate-side coast mutation uses the **biome-region center's** continent value, not the current
point's value. Moving it after erosion or replacing it with local continentEdge would change V0.
It will be isolated as a legacy coast-compatibility rule, never future baseline-climate authority.

## Extraction direction and constraints

Original contracts/orchestration will use `com.gabou.atmospheregen.geography` with generic internal
workspace/tile types. Legacy algorithm adapters remain MIT-derived in the inherited namespace.
Cell remains the pooled workspace, implementing a geography-only view without a per-cell wrapper.
Compatibility fields remain separately documented in Cell until later migration; no public API leaks it.

```text
ContinentStage → TerrainStage → HydrologyStage
  → explicit LEGACY_RTF_V0 pre-filter compatibility (parameters + old climate/coast rules)
  → batch barrier → GeographyFinalizationStage → detached canonical tile → GeographyProvider
```

The new orchestration must not import physical baseline-climate or biome-resolver APIs. Legacy
river caches currently live on continent implementations and their network generators query continent
geometry. Task 3 will centralize retrieval/reuse/application behind HydrologyStage while retaining
that private legacy cache ownership to avoid changing network construction/seed order or adding cycles.

Structure CellTest currently requests opportunistic, climate-disabled lookup; silently forcing exact
tiles would change legacy eligibility. Retain this in an explicitly named legacy path. Preview remains
an explicitly named zoomed approximation. Normal chunk and public canonical queries use finalized tiles.

Slope/relief may remain unavailable: neighboring halo values are not canonical owning-tile values,
and a correct boundary query may force additional eroded tiles. No relabeling of gradient is acceptable.
Mountain selector/blend information will be captured from existing evaluations, not resampled graphs.

## Verification plan

Before production edits, the developer `stage-baseline` profile captures 595 fixed coordinates after
continent, terrain, hydrology, compatibility, and finalized tile sampling. It uses real loaded Forge
registries and exact Task 1C field encoding. Later staged captures must match these snapshots before
complete golden/scheduling/Task 1B/smoke/performance gates. Task 1C/2 evidence remains read-only.

Task 4 continent/ocean redesign, new climate/biome authority and runtime integrations remain out of scope.

## Implemented architecture

Original ARR contracts under `com.gabou.atmospheregen.geography`:
`GeographyWorkspace`, `ContinentStage`, `TerrainStage`, `HydrologyStage`,
`GeographyFinalizationStage`, and generic batched `GeographyPipeline<W,R,T>`.
The explicit `compat.legacy.LegacyPreFilterCompatibility` hook is the only old-classification
extension point in this orchestrator. There are no future climate/biome API dependencies and no
new geography version factory. Float point coordinates are deliberate for inherited zoomed previews;
public coordinates remain integer blocks. No per-point workspace or immutable sample allocation.

MIT-derived implementations live under `raccoonman.reterraforged.world.worldgen.cell.geography`:

| Implementation | Responsibility |
|---|---|
| LegacyGeographyFactory | Frozen `Heightmap.make` graph construction and exact seed allocation order |
| LegacyContinentStage | Original point preparation, beach noise and unchanged continent application |
| LegacyTerrainStage | Region application, scaled terrain coordinates and unchanged composed terrain graph |
| LegacyHydrologyStage | Primitive site-key RiverMapSource, per-chunk map reuse, carving and volcano post-carving correction |
| LegacyMinecraftParameterAdapter | V0 valley/river/lake/wetland parameter overrides, old climate passes, weirdness sign; continentalness channel mapping |
| LegacyCoastCompatibility | Biome-center coast mask and submerged coast correction at their original call positions |
| LegacyGeographyFinalizationStage | Existing erosion/smoothing/steepness/beach/quart-correction algorithm and order |

`Heightmap` now contains the concrete stage bindings, Levels and ControlPoints. Its old point
methods delegate; compatibility accessors remain for feature random seeds and spawn/preview center
selection. It no longer constructs the graph or owns parameter correction formulas. Its direct apply
method remains a deliberately noncanonical compatibility facade. `WorldFilters` is a compatibility
subclass of the finalization stage; filter-failure tests can still replace TileGenerator's existing
filter slot and reach the real finish path.

```text
GeneratorContext → LegacyGeographyFactory → legacy stages + frozen graphs
  → TileGenerator.geographyPipeline
       per batch/chunk: Continent → Terrain → Hydrology → V0 compatibility
       all writers complete → finalization → detached publication
  → existing TileCache (unchanged context token and ownership)
  → LegacyRtfGeographyAdapter → immutable GeoSample / HydrologySample
```

Filtering still runs before snapshot publication. Submission/cancellation/failure draining, workspace
pool return, tile geometry, chunk traversal and batch scheduling have not been rewritten. New contexts
keep their Task 2 runtime token; the provider captures the owning immutable context and cache once.
No new global state or per-point registry/manifest/seed hash appears.

## Cell partition and captured mountain data

Cell implements the geography-only workspace view. Existing 24 public fields remain as legacy storage;
classification and parameter fields are explicitly documented as compatibility scratch rather than
new geography authority. Physical `heightErosion` is exposed as normalized removal delta, distinctly
named from the legacy Minecraft erosion parameter. Reset/copy/snapshot continue transporting all fields.

Three **private float** captures avoid changing the frozen public Cell field schema:

1. Existing Blender control sample, unavailable (internal NaN sentinel) where the ocean-only branch
   never evaluates the land mountain selector; public absence is Optional.empty, never numeric NaN.
2. Mountain-chain population contribution: existing blend alpha, multiplied by the existing continental
   land blend where applicable. Outside the transition it is zero or one.
3. Regional mountain population contribution: mountain population membership weighted by RegionLerper,
   then by the non-chain land fraction and continental land fraction.

Combined influence is their sum, bounded to [0,1] at the public diagnostic boundary to handle float
rounding. These are **pre-carving/pre-filter terrain synthesis contributions**, transported alongside
finalized height, not a measured fraction of final elevation or ridge geometry. No selector/noise graph
is reevaluated to populate them. A three-case synthetic blend test verifies weights and branch call counts.
The new fields have separate capture/digest tables; the old 24-field tile digest is unchanged.

The 24-worker allocation pilot measured 3,201,648–3,201,680 bytes per tile versus Task 2
2,996,848–2,996,880: about +6.83%. Private field layout adds eight bytes per published Cell on this JVM
(25,600 cells → 204,800 additional bytes). Warmed pooled workspaces are reused; first-allocation costs
and retained tile-memory growth are not identical to this warmed allocation metric. Final throughput
and allocation results below are measured independently of the pilot's short timings.

## Public sample semantics and enrichment

The Task 2 adapter now queries tiles produced by the extracted pipeline. It captures the owner context
and cache and rejects shutdown. It reads the nonpooled published tile only while constructing detached
records; it does not allocate an intermediate Cell copy, return a Cell/Tile, or use direct fallback.
Task 1B's reader-lifetime invariant makes retained published storage safe across eviction. This eliminates
an unnecessary query-only allocation while adding the new immutable diagnostic fields.

`GeoSample` retains its previous constructor and adds optional `LegacyTerrainSignals`, containing
properly labeled land value, existing continent center/site, terrain-region selector/edge, selector and
continuous mountain contributions, physical removal delta and sediment in block units. No hash selector
is advertised as a globally unique connected continent/region ID. Existing normalized land value is not
mapped to ocean distance or physical continentality. Exact PLATEAU/HILLS terrain identities now populate
those public landforms; ambiguous composite terrains remain conservative rather than name-guessed.

`GeographyMetrics.mountainInfluence` is now populated with LEGACY_HEURISTIC quality at block sampling
resolution. Slope, local relief, physical continentality, ocean/coast distance, latitude and ridge direction
remain explicitly unavailable. Using the tile halo would not satisfy canonical neighbor semantics at
partition boundaries, and generating extra adjacent eroded tiles is not silently added to every query.

Hydrology still exposes water category, river/lake/wetland flags and normalized `1-riverMask` valley
influence when valid. Segment ID, exact distance, basin, flow, discharge and water table are absent.
New physical baseline climate and biome resolver remain unavailable; legacy temperature/moisture and
BiomeType diagnostics are emitted separately from physical fields.

## Legacy consumer disposition

`WorldLookup.sampleFilteredTile` is explicit canonical internal sampling;
`sampleDirectApproximate` is unfiltered plus its point coast correction;
`sampleLegacyOpportunistic` deliberately retains cache-dependent V0 semantics. Deprecated `applyCell`
overloads remain compatibility shims for frozen harness/external inherited consumers, not a public
GeographyProvider. Production structure checks explicitly call the opportunistic climate-disabled path
because changing it to exact would change frozen structure eligibility. No blacklist/cutoff changed.

CellSampler retains its context/tile/climate-mode cache and exact in-chunk readers; its fallback is already
explicit direct-or-selected-tile sampling from Task 1B. Continentalness conversion now delegates to the
legacy parameter adapter without changing operations or floats. Old temperature/moisture/biome-region
field reads stay in this legacy Minecraft bridge until a future resolver replaces them.

Preview calls `generatePreviewApproximate`, with the old `generateZoomed` name a deprecated shim.
Its UI explicitly labels “Legacy preview approximation”. It keeps transformed coordinates and skips
optional filters as before; no forced canonical tile generation per preview pixel.

## Verification records

Pre-extraction `stage-baseline` captured 2,975 rows (595 × five stages). Point-stage extraction,
tile-pipeline integration, coast/parameter isolation and mountain capture each passed every legacy
field at every stage exactly. `provider-checks-fixed` and `geography` test 85 real public locations,
warmth, eviction/regeneration, copy/reset and synthetic mountain blend evaluation counts.

One initial developer export failed because Gson tried reflecting into Java Optional. The evidence
serializer was corrected to export explicit values/nulls, without opening JDK modules or changing the
production API. The failed log remains a harness-development failure, not a terrain defect.

Run `scripts/task3/Verify-Dependencies.ps1` for package-direction assertions. Run `Run-Gates.ps1`
sequentially for geography/goldens/scheduling/full regressions/foundation/stage/benchmarks. Stage tests
compare the new pipeline directly against the pre-extraction capture. Aggregate results and
performance disposition are recorded below; accepted evidence is never overwritten.

## Resumed verification

The interrupted session left the extraction commits through `9edf3503772d67c948d4ed5d4348428c004a293f`
and unfinished performance/runtime reports. Work resumed in the `task3-completion` branch and
isolated checkout, because the original workspace contained a separate uncommitted package rename.
That rename is preserved in the original workspace and is outside this Task 3 result. The inherited
package and resource identities in this branch remain those used by the accepted comparator.

No production source changes were needed to resume verification. The initial benchmark and its
historical comparison are retained as `benchmark/`, `benchmark_summary.json`, and
`initial_performance_comparison.json`. The fresh same-protocol candidate is `benchmark-resumed/`.
A separate accepted Task 2 checkout supplies the control; its production source/config are checked
against the accepted commit before running. Test harness additions are not production changes.

The initial short public-provider benchmark still changed substantially during measured repetitions.
Both versions therefore receive an identical supplementary protocol: 20 discarded warmup batches,
then 10 measured batches of 262,144 queries. Immutable results escape through volatile storage;
timer overhead remains included. The short-run archives are retained, not replaced. The standalone
legacy point/tile/FULL benchmark protocol and frozen golden fixtures are unchanged.

Performance evidence is engineering evidence from live Minecraft clients, not an assertion that
every scheduling or JIT effect has been isolated. Changes between process runs are reported alongside
the initial measurements; no unmeasured cause or exact retained-heap attribution is claimed.

## Final comparator and performance results

| Check | Result |
|---|---|
| Five-stage comparisons | 2,975 / 2,975 exact |
| Canonical geography | 595 / 595 exact |
| Legacy biome/router hints | 595 / 595 exact |
| Minecraft biome keys | 85 / 85 unchanged |
| Standalone legacy tile digests | 420 / 420 exact across workers 2, 24, 48 and repeat ordering |
| TerraBlender legacy tile digests | 105 / 105 exact |
| Independently encoded mountain digests | 525 / 525 scheduling invariant |
| Task 1B lifecycle/cache/correctness regressions | 64 / 64 pass |
| Accepted Task 1C / Task 2 archived files | 362 / 342 hashes unchanged |

The legacy seeds `8675309` and `4303642605` still collide across all 85 fixture pairs.
Legacy int narrowing is preserved; Task 2's new 64-bit seed service is not substituted into V0.
Filtered/direct disagreement remains 340 / 340 diagnostic locations, with the same field counts:
gradient 340, height 319, erosion height 176, sediment 96, terrain 6. Direct approximation is not
being presented as canonical output. Stage captures and complete golden comparators use exact
float bits, not an epsilon or updated expected values.

The ownership map classifies 133 explicit Cell writes over 24 legacy fields: 54 physical geography,
23 hydrology, 13 finalization, 21 legacy Minecraft parameter hints, and 22 legacy climate/biome
classification. Transport/reset and inactive filter-family writes are marked separately in the map;
these counts are not a claim that every listed write belongs to the active point path.

The fresh paired performance gate passes. Largest isolated positive time delta is +0.30%; filtered
tiles measure -0.87%, public immutable queries -1.33%, and worker/caller allocation +6.84%.
See [PERFORMANCE_COMPARISON.md](PERFORMANCE_COMPARISON.md) for all operations, original slower
runs, control protocol, public query warmup, allocation scope and measurement limitations.

`Cell`, old Climate/ClimateModule/BiomeType classification, legacy parameter scratch and the private
continent-owned river caches remain explicitly transitional. Climate's biome-center coast effects
still run before filtering through the V0 compatibility boundary. Public unknown distances, physical
continentality, latitude, slope, local relief, basin, flow and ridge orientation remain unavailable.
Structure eligibility retains the named V0 opportunistic path; preview remains labeled approximate.

No new continent/ocean algorithm, climate model, biome resolver, runtime ecology or integration was
implemented. `PA_GEOGRAPHY_V1` remains unavailable. Task 4 was not started.

## Build, runtime and handoff

`compileJava`, `classes`, `jar`, `build`, `reproductionClasses`, `smokeClasses` and `runData` pass.
Standalone, TerraBlender, vanilla and copied existing Task 2 world tests each pass 14 FULL chunk
checks across create/open, save, reopen and additional generation. The existing world's persisted
manifest remains byte-identical; its source save is unchanged. Existing development warnings remain
in the logs; no new fatal mixin or registry failure occurred. Developer observer/smoke classes are
excluded from the production artifact.

The finalizer validates the accepted evidence hashes, stage/golden/mountain/dependency checks,
64 Task 1B regressions, benchmark completion, performance status, all runtime cases, build logs and
artifact contents before generating `evidence/final_summary.json` and `evidence/manifest.json`.
The manifest hashes every included evidence file except itself and the unarchived raw-log directory.
Successful logs are copied into `verified-logs/`. `evidence/files_and_commits.json` records the
complete Task 3 commit/file inventory; the containing Git commit identifies the final report itself.

The completion branch is `task3-completion`, located at `build/task3-completion` beneath the original
workspace. Its production source retains inherited identities. The original workspace's separate
uncommitted rename has not been merged into or overwritten by this result. Only this branch is
claimed as the completed, clean Task 3 checkout.
