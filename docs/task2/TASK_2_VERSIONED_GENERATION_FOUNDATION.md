# Task 2 — versioned generation foundation

Status: Task 2 complete. Legacy comparators, correctness regressions, build/datagen,
standalone/TerraBlender/vanilla and existing-world runtime gates pass.
The accepted comparator remains Task 1C `d2164f25cd06aafcae15cc15f9f4babe977d4bdb`,
whose corrected production source is `cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee`.
This report does not authorize Task 3.

## Scope and architecture

All original architecture is under `com.gabou.atmospheregen`. New persisted data uses
`atmospheregen`. The mod ID, inherited `Gabou.projectlandscape` packages, registry IDs,
datapack IDs, presets and resource assets remain unchanged. Original files are ARR;
modified inherited files retain MIT provenance. Existing third-party notices remain intact.

```text
Minecraft saved seed + dimension + actual loaded preset/router/data
  -> LegacyWorldBinding (ChunkMap constructor, after old RandomState initialization)
  -> immutable effective configuration + canonical data fingerprints
  -> GenerationManifestStore: explicit legacy assignment OR validate existing manifest
  -> WorldGenerationContext (persisted content ID + separate runtime cache token)
       |-- new named 64-bit seed service (infrastructure only; unused by legacy terrain)
       |-- GenerationDiagnostics
       `-- thin LegacyRtfGeographyAdapter
             -> owning TileCache tile -> existing filtered finalized cells
             -> detached immutable GeoSample / HydrologySample

LEGACY_RTF_V0: existing Heightmap, int Seed, tiles/erosion, hints, biome source unchanged
PA_GEOGRAPHY_V1: version/config/seed metadata only; generation explicitly unavailable
BaselineClimateProvider / ClimateBiomeResolver: contracts only, no new algorithms
```

No Heightmap stage extraction, continent/ocean redesign, baseline-climate algorithm,
biome-source replacement, PA runtime, ecology, Dynamic Trees, temperature or TFC work occurred.

## Source layout

```text
com/gabou/atmospheregen/
  api/geography/   GeographyProvider, HydrologyProvider, GeoSample, HydrologySample,
                  BlockPosition, Landform, WaterCategory, GeographyMetrics, Metric
  api/climate/     BaselineClimateProvider, ClimateBaseline, WindDirection
  api/biome/       ClimateBiomeResolver, BiomeResolutionContext
  config/          WorldGeographyConfig, LegacyGeographySettings,
                   PlannedGeographySettings, BaselineClimateConfig,
                   BiomeResolverConfig, ConfigCodecs
  generation/version/  GenerationVersions, three algorithm enums, VersionCodecs
  generation/seed/     GenerationSeedService, NamedSeedService, SeedDomain
  generation/context/  WorldGenerationContext, GenerationDiagnostics,
                       UnavailableGenerationServices
  persistence/     ManifestContent, GenerationManifest, GenerationFingerprint,
                   CanonicalJson, GenerationManifestStore
  compat/legacy/   LegacyPresetSnapshot, LegacyGenerationData, LegacyTerraBlenderData,
                   LegacyWorldBinding, LegacyRtfGeographyAdapter
```

No new production module or loader abstraction was introduced. Development code is in
`src/reproduction` and `src/smoke`, outside the distributed mod jar.

## Versions and configuration

`GenerationVersions` contains independently encoded `schemaVersion` (currently 1),
`geography`, `baselineClimate`, and `biomeResolver`. Enum serialized names are stable IDs;
the mod release number is not an algorithm version.

| Family | Functional legacy | Reserved, not implemented |
|---|---|---|
| Geography | `LEGACY_RTF_V0` | `PA_GEOGRAPHY_V1` |
| Baseline climate | `LEGACY_RTF_HINTS_V0` | `PA_BASELINE_V1` |
| Biome resolver | `LEGACY_MULTINOISE_V0` | `PA_RESOLVER_V1` |

Only the complete legacy tuple can dispatch generation. Planned metadata can round-trip
and seed domains can be tested, but attempting to run it throws an actionable unsupported
backend error. No planned version is exposed as a working world preset or UI option.

`WorldGeographyConfig` requires exactly one backend section. Legacy settings contain an
immutable canonical snapshot of the **effective** active preset, including fields the
old codec omits, plus tile exponent/border. Snapshot reflection is confined to the known
legacy settings package and runs once at initialization. No mutable preset is exposed
through the manifest. Loaded world registry settings must match the persisted snapshot;
changed current defaults/data are rejected, never substituted into an existing world.

Frozen legacy tile exponent is 3. Border is the existing preset-derived
`min(2,max(1,dropletLifetime/16))`; the accepted default is 1, hence 128x128 core / 160x160
with halo. Batch count 6 remains the normal execution default. Worker count and batching
are excluded from fingerprints because the accepted scheduling tests found them invariant;
tile geometry is included because it changes output. Erosion and every other legacy setting
remain in the effective snapshot. The complete accepted configuration is still
`docs/task1c/evidence/golden-24/identity.json`.

Planned geography controls describe continent/terrain/mountain scales, minimum major ocean
width and river frequency. Planned climate controls describe latitude scale, lapse rate,
ocean influence and rain-shadow strength. Planned biome controls describe spatial resolution
and fallback weight. These are **inactive settings for unavailable algorithms**, not promises
that they affect legacy generation. Physical climate config is empty for legacy hints.

Codecs and constructors reject nonfinite/out-of-range quantities. Integer codecs reject
fractions and overflow. Invalid present optional fields are errors, not missing defaults.
DFU partial range values must not reach validated record constructors. No silent clamping.

## Manifest schema, fingerprint and lifecycle

The persisted JSON is `GenerationManifest { content, fingerprint }`. Its required content:

```text
versions { schemaVersion, geography, baselineClimate, biomeResolver }
worldSeed                  signed 64-bit decimal STRING (no JSON double precision loss)
dimension                  namespaced Minecraft dimension key
geography                  immutable backend-specific settings
baselineClimate            separate immutable config
biomeResolver              separate immutable config
data                       sorted named generation-data SHA-256 components
biomeCatalog               optional future catalog checksum; absent under legacy
```

The checksum is SHA-256 of UTF-8 canonical content: sorted object keys, preserved array
order, finite normalized numeric representations with signed floating zero preserved.
Nonfinite numeric trees are rejected before JSON serialization. It includes the seed and dimension,
so it is an environment identity, not just the Task 1C settings-only checksum.
No filesystem paths, clocks, JVM object IDs, workers, logs or mod release number are hashed.
Fingerprint schema is version 1; changing canonicalization later requires schema handling.

Legacy data coverage includes loaded worldgen registry definitions, the active chunk
generator codec, resolved sorted tag membership, generation JSON resources, structures
and explicitly referenced NBT templates, the legacy biome-classification table and external
mod versions. NBT uses sorted compound keys with typed values/list order preserved; gzip
headers do not affect identity. Optional TerraBlender metadata includes its effective config,
region IDs, weights and actual positional indices. No `Region.addBiomes` is called to
manufacture catalog data. The future descriptor catalog remains explicitly absent.

External mod versions are conservative: an unknown external mod may change generation in
code, not JSON. The current mod's release number and two known developer harness mods are
excluded. A non-generation external mod update can therefore cause a conservative mismatch;
this is reported rather than silently accepted. Arbitrary third-party mutable Java state or
configuration not exposed by that mod is not claimed to be fully frozen by this mechanism.

Each dimension's file is `<dimension storage>/data/atmospheregen/generation_manifest.json`.
The caller owns Minecraft's save lock. New content is flushed to a same-directory temporary
file and moved without replacement. Existing manifests are never overwritten or migrated.
A partial/corrupt file fails explicitly; no recovery via newest defaults is attempted.

| State | Resolution |
|---|---|
| Actual RTF Overworld router + active preset, no manifest | Explicit persisted `LEGACY_RTF_V0` assignment |
| Supported matching manifest | Use the persisted manifest |
| Changed seed/dimension/config/data | Error listing differing content paths; original file unchanged |
| Unknown schema/enum or corrupt checksum | Codec error with restore/matching-data guidance |
| Reserved PA algorithm in a valid manifest | Unsupported backend, no fallback |
| Foreign world without manifest | Untouched, no companion context/file |
| Foreign ownership with an atmospheregen manifest | Explicit ownership mismatch |

Recognition inspects the actual NoiseBasedChunkGenerator router for legacy CellSampler
markers. Merely having RTF tags loaded is not proof of ownership. Task 2 binds only the
Overworld; Nether/End retain their previous behavior. Seed/context types themselves support
any dimension. No generated chunks are rewritten and no world is auto-upgraded.

Live `/reload`/datapack reload is explicitly rejected while a manifest-bound server world
is open, before new tags/templates are applied. This is a conservative freeze policy, not
a geography algorithm change. Initial load and vanilla-only server reload remain available.
Safe change-aware live reload needs a later dedicated implementation; changing generation
data requires an explicit migration strategy, which Task 2 does not supply.

## Context and seed design

`WorldGenerationContext` is immutable: full world seed, dimension, manifest, seed service
and runtime token. Persistent context ID is `atmospheregen:<manifest checksum>`: identical
generation environments have the same content ID, even in different save folders. It is
not a unique save UUID. Reopened/separate contexts use different runtime tokens. Binding
reuses the existing Task 1B `WorldLookup.samplingIdentity()` token and is write-once before
ChunkMap publication. Existing density cache comparisons are untouched; no string equality,
hashing, serialization or registry traversal enters a per-cell loop. World unload still
closes the legacy cache through the existing Forge lifecycle path.

`atmospheregen:named-seed-v1` uses SHA-256 over, in order:

1. length-prefixed UTF-8 scheme ID;
2. full big-endian signed 64-bit world seed;
3. length-prefixed UTF-8 dimension ID;
4. length-prefixed stable domain ID;
5. length-prefixed domain-specific algorithm version;
6. big-endian signed 64-bit salt (zero if omitted).

The first eight digest bytes, big endian, form the result long. String lengths are 32-bit
big endian byte counts. No ordinal, Java hashCode, registry order or `Seed.next()` participates.
Domains are `continent`, `ocean`, `terrain_regions`, `mountain_chains`, `hydrology`, `surface`,
`baseline_temperature`, `baseline_precipitation`, `biome_spatial_selection`, all namespaced
`atmospheregen`. Each domain includes only its own geography/climate/biome algorithm version;
changing climate does not shift terrain streams. Adding domains does not shift existing ones.

The service is new infrastructure only. Legacy still narrows the world long to int and uses
its sequential allocation. No new stream is narrowed anywhere in Task 2. If a future adapter
must call an int-only noise implementation, it must narrow at that explicit boundary.
The known seed pair remains a legacy terrain collision and diverges in all nine new domains.
Different dimension keys yield distinct tested streams, and reversed/worker-thread calls agree.

## Public API semantics

`GeographyProvider.sample(int x,int z)` means canonical finalized block-coordinate geography,
independent of cache warmth and detached from mutable/pooled cells. A cold query may synchronously
generate/join an RTF tile, **not Minecraft chunks**. Its adapter selects the owning tile core,
never a neighboring halo or opportunistic/direct fallback. It rejects reads after cache shutdown.
This matches the existing production `MixinNoiseChunk -> CellSampler.CacheChunk` density path
documented in Task 1C. No stage was moved out of Heightmap.

`GeoSample` contains block position/spatial key, continuous block-Y elevation, sea-relative
elevation, companion-owned water/landform categories, optional geographic metrics and hydrology.
Legacy elevation is the existing float height times legacy height scale, then widened to double;
it is not a final Minecraft surface block height. Sea-relative Y subtracts configured sea level.
Continuous mountain influence has an explicit optional metric; a mountain classification does
not fabricate influence/ridge information. Coast distance, ocean distance, continentality, slope,
relief and latitude are unknown until a backend can truthfully calculate them. Legacy gradient
is not repackaged as physical slope. Metrics carry quality and positive support resolution.

`HydrologyProvider` shares canonical semantics. Its sample carries existing river/lake/wetland
classification and, when in range, `1 - riverMask` as a **legacy valley-influence heuristic**.
It is not discharge, exact distance, water table, basin or flow. Segment identity is absent.
Higher-level landforms not proven by legacy traits remain OTHER rather than guessed from names.

`BaselineClimateProvider.sample(x,z)` permits an implementation to own a bounded neighboring
GeographyProvider sampler later, rather than requiring climate to be a function of one isolated
cell. `ClimateBaseline` uses Celsius, annual rainfall/potential evaporation in mm/year,
ecological moisture and rain-shadow indices [0,1], and optional unit downwind direction (+X east,
+Z south). It is not runtime weather. The dimensionless `LegacyClimateHints` diagnostic does not
implement that contract. Physical climate is unavailable, not filled with invented conversions.

`ClimateBiomeResolver.resolve(GeoSample, ClimateBaseline, BiomeResolutionContext)` returns a
Minecraft biome holder. Its context carries frozen generation metadata, holder lookup and an
optional matching catalog fingerprint. It does not replace Minecraft's similarly named resolver
or current MultiNoiseBiomeSource. No new resolver implementation exists in Task 2.

## Verification and reproduction

Run Gradle/game processes sequentially. Foundation tests use a real bootstrapped Forge client
and world; no registry/holder mocks. `runReproductionClient -PreproProfile=foundation` tests
codecs, invalid values, manifests, context/seed identity, automatic creation/reopen, public API
semantics, filtered cache eviction and shutdown. Add `-PwithTerraBlender=true` for optional TB.
Diagnostics are emitted in `world_binding.json` via `GenerationDiagnostics`, including versions,
context ID, full seed, dimension, fingerprint and domain seeds; no full `/geo` command is added.

`scripts/task2/Collect-Run.ps1` archives only completed runs into a new directory; it refuses
overwrite. `Run-Measurements.ps1` runs sequential suites and stops on changed tile digests.
`Verify-Goldens.ps1` compares accepted Task 1C fields/keys/digests exactly, not broad tolerances.
`Compare-Performance.ps1` uses the original timed operations, not Gradle elapsed time.

### Final results

| Gate | Observed result |
|---|---|
| Canonical geography | 595/595 exact, all 34 field representations unchanged |
| Legacy biome/hints | 595/595 exact; 85/85 Minecraft biome keys unchanged |
| Legacy seed collision | 85/85 pairs still equal across all tested fields |
| Standalone tile corpus | 420/420 exact across workers 2/24/48 and tested orders |
| TerraBlender comparator | 105 tiles and full geography/hint/biome-key corpus exact |
| Final metadata-code rerun | Another 595 geography/hint rows, 85 biome keys and 105 tiles exact |
| Task 1B correctness | 64/64 checks pass, including 994 valid comparator samples |
| New seeds | Nine domains diverge for the legacy collision pair; three dimensions isolated |
| Foundation | Real Forge create/reopen, codecs, rejected inputs, persistence, API/cache lifecycle pass |
| Runtime | Standalone, TB, vanilla, copied existing Task 1C save: 14 FULL checks each, save/reopen/additional chunks pass |
| Build | compileJava, classes, jar, build, reproductionClasses, smokeClasses, runData pass |

The 340-row filtered/direct diagnostic remains 340/340 different: gradient 340, height 319,
heightErosion 176, sediment 96, terrain 6. This is the intentional separation of filtered
canonical output and direct approximation, not a new correctness golden for all backends.
All 362 archived Task 1C evidence files retain their original hashes.

The foundation seed suite has seven seeds, three dimensions and nine domains (189 stream
rows per execution; create/reopen repeats those assertions). It verifies salted/reversed/
worker execution and independence when another domain's algorithm version changes. For example,
the continent domain maps the legacy pair to `9046651019689194314` and `249920126168250857`,
while the legacy int seeds remain identical. These are seed tests, not PA terrain results.

See [final summary](evidence/final_summary.json), [build/artifact verification](evidence/build_verification.json),
[complete legacy comparison](evidence/legacy_golden_comparison.json),
[final production-code comparator rerun](evidence/release_legacy_golden_comparison.json),
[Task 1B regression rerun](evidence/task1b-release-verification/verification.json), and
[final runtime matrix](evidence/runtime_smoke-final.json).

Current standalone foundation evidence is in `evidence/foundation-release`; the final optional-TB
suite is `evidence/foundation-tb-release`. Both test automatic world binding/reopen. Earlier
prototype directories remain historical, not migration targets. Some early table descriptions
predate automatic binding; the current `world_binding_checks` and runtime matrix are authoritative.
All evidence is indexed with SHA-256 in [manifest.json](evidence/manifest.json); provenance names
the source revision and immutable Task 1C fixtures. The exact per-commit/path inventory is
[files_and_commits.json](evidence/files_and_commits.json). Its containing final Git commit identifies
the completed report without a self-referential embedded commit hash.

### Performance and warnings

See [performance comparison](PERFORMANCE_COMPARISON.md) for raw measurements and methodology.
Task 2's first timing run exceeded the 5% investigation threshold and remains archived. A fresh
exact Task 1C source run followed by a Task 2 repeat found no >5% median hot-path regression;
largest increase was filtered tiles +3.37% versus contemporary reference. Historical tile time
remains +7.03%, within candidate repeat CV 7.63%; that discrepancy is reported, not discarded.
Worker/caller allocations are approximately 2.997 MB/tile, unchanged within instrumentation
overhead. No new hash, codec or registry traversal executes in the inherited density hot path.

Warnings remain inherited deprecation/removal/unchecked compilation notices, Gradle deprecations,
mapped-development refmap notices, initial Forge server-config defaults, offline Realms authorization,
IPv6/shader notices and integrated-server loading delays. Successful injections and generation
are verified by actual runtime runs, not inferred from compilation. `test` is NO-SOURCE; correctness
assertions run through the real Forge developer source sets. No new missing registry/preset or
fatal mixin failure occurred. This is automated chunk/data validation, not a claim of manual
visual approval of landscape aesthetics.

### Deliberate non-algorithm behavior changes

The three inherited production edits only bind/freeze metadata (`GeneratorContext`, `MixinChunkMap`)
and reject unsafe live resource reload (`MixinMinecraftServer`). Manifests fail closed on unsupported
or mismatched generation data. No preset, registry/resource ID, terrain, climate, biome, vegetation
or structure algorithm was intentionally changed. Source provenance and all existing licenses remain.

## Known limitations retained

- Legacy 32-bit seed narrowing and filtered/direct disagreement are intentionally unchanged.
- Legacy tile geometry remains generation-affecting; erosion is not made partition-independent.
- FULL decoration-block nondeterminism remains the Task 1C deferred issue; canonical geography,
  hints and tiles, not downstream finished block equality, are the extraction comparator.
- No production generation migration or PA geography/climate/biome implementation exists yet.
- Live generation-data reload is conservative (restart/matching data), not change-aware.
- Broader third-party mutable config/catalog coverage needs explicit adapters; external mod
  version and current registry/resource fingerprints are not a claim of universal mod isolation.
