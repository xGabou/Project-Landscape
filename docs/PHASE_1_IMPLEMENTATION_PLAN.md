# Phase 1 implementation plan

Status: proposed, following the [source audit](PHASE_1_ARCHITECTURE_AUDIT.md). No production geography, climate or biome implementation is introduced by these documents.

## 1. Work order and delivery gates

Each numbered task is a reviewable change. Do not combine the initial extraction with new continent algorithms or a new biome source. Keep the working Forge 1.20.1 baseline available as a comparator; a legacy compatibility implementation is temporary isolation, not the new architecture.

### 0. Restore and record the baseline

Resolve the six compiler errors recorded in the audit. Prefer consolidating the inactive `data.worldgen.preset.Preset` / older `data.worldgen.RTF*` family after checking all Java imports, resource references, GUI paths and registry consumers. Do not repair both competing pipelines into permanent support. If a file still has consumers, migrate them individually before retiring it.

The user's `fg.deobf` correction has been verified: Gradle configuration now passes and compilation reaches the same six common-source errors. Preserve the user's dependency edits. Dependency resolution reaching common compilation does not prove those optional mods launch together or establish Phase 2 scope.

Record the effective JDK, Gradle, Forge and dependency versions. Pin dynamic build plugin versions once the build is reproducible; do not mix a toolchain upgrade with a worldgen refactor. Create a minimal Forge run profile without PA, seasons, clouds or biome mods to establish standalone operation, plus an optional integration run profile. This does not require making PA a mandatory dependency.

Gate: `:common:compileJava`, `:forge:compileJava`, `:forge:build` and a new/existing-world smoke check succeed. A minimal exported RTF preset loads. Collect any remaining runtime defects as separate baseline fixes rather than claiming them solved by compilation.

### 1. Capture invariants and a performance baseline

Add a deterministic sampling harness using the active preset/noise registry bootstrap. Keep pure noise tests lightweight; use a Minecraft/Forge bootstrap harness where holders and registries require it. Add tests for real contracts, not getters or copies of implementation formulas.

Capture canonical terrain and biome samples across fixed seeds, negative coordinates, origin, tile borders, river mouths, coast, plateaus and mountains. Include seeds sharing their low 32 bits; record the existing terrain collision as legacy behavior, and require new seed streams to distinguish them. Exercise cached/uncached differences explicitly instead of recording whichever value happened to be warm.

Add focused reproductions for cross-world `CellSampler.Cache2d` reuse, per-noise cache seed changes, tile horizontal bounds, pool reuse, nested cell resources, disabled custom vegetation datapack generation and missing preset/context handling. Repair correctness bugs in isolated changes before accepting golden outputs. Keep both pre-fix evidence and post-fix expected behavior when a fix changes terrain.

Add a benchmark runner with fixed geography extent, seed set, preset, worker configuration and hardware/JVM metadata. Measure raw terrain, filtered tiles, query latency and real chunk throughput. Profile warm/cold caches independently. A baseline must exist before timing comparisons are claimed.

Gate: repeat runs are reproducible under the declared legacy sampling contract; meaningful known discrepancies are documented and tested. Benchmark reports contain actual measurements, not target numbers.

### 2. Add API boundaries, versioning and config codecs

Create original public geography, hydrology, baseline climate and biome contracts with the units/validity rules from the audit. Include world-coordinate identity for deterministic biome weighting. Keep immutable public snapshots and internal mutable/batch interfaces separate; no cell allocation per density evaluation.

Create three algorithm versions (`geography`, `baselineClimate`, `biomeResolver`) plus a schema version. Add separate immutable `WorldGeographyConfig`, `BaselineClimateConfig` and `BiomeResolverConfig`. Use an aggregate only as a serialized world manifest referencing those configs, not as an all-purpose mutable settings object passed into every factor.

Create a generation manifest and codec before changing algorithms. Persist the selected algorithm versions, effective generation-affecting config, catalog snapshot/fingerprint, effective noise/resource fingerprint and dimension association at world creation. The world seed remains the world's authoritative long seed. Thread and queue counts are not generation settings unless tests show they change output. Canonical erosion tile geometry is pinned as generation input until partition invariance is proven.

Add a named seed derivation service: deterministic 64-bit mixing of world seed, dimension identity, stage name and stage version, narrowing only at the boundary to a retained noise algorithm that requires an int. Stage seeds must not depend on object construction or task execution order. Retain legacy `Seed.next()` allocation only inside the legacy-version backend.

Gate: codec round trips, invalid-range rejection and version dispatch work; no public API imports `Gabou.projectlandscape` implementation types. Unsupported/missing manifests cannot silently default an existing world to the newest generator.

### 3. Extract geography without changing continent design

Split `Heightmap` into explicit continent, ocean blend, terrain, hydrology and finalization stages. Keep noise graphs, terrain frequency, seeded selection and river network reuse intact initially. Separate biome-parameter hints from physical geography fields.

Climate currently changes coast classifications and those types can affect erosion. Preserve that old behavior inside a clearly labeled legacy backend during extraction; then replace it with geography-only coast finalization under a new geography version. Do not embed legacy biome-center sampling in the new continent model merely to obtain exact legacy coast shapes.

Bind `GeographyProvider` to canonical filtered tiles. An exact query must return the same result before chunk generation, during generation and after cache eviction. Expose a separately named approximation for previews if useful; never let cache warmth select fidelity. Fix null-cache access and context-insensitive scratch caches. Establish safe tile ownership before external callers query concurrently with chunk drops.

Add mountain blend contributions to the terrain workspace where they are already calculated. Combine regional mountain selection with chain influence; exporting only the chain selector misses other mountains. Add final slope and bounded local relief/valley metrics. Keep ridge direction and basin identity explicitly unavailable until a real calculation supplies them.

Hydrology initially exports valley influence and water categories. Keep `Rivermap`, carving and cached networks; replace `Rivermap.get(..., Heightmap)` with a river-map provider. Add geometric river proximity only if the warped network can provide it at measured acceptable cost.

Gate: retained geography matches the declared comparator except documented versioned fixes; no climate/biome dependency remains in the new geography stages. Hot/cold results, thread scheduling, negative coordinates and tile boundaries pass. Extraction alone should stay within the performance guardrails below.

### 4. Build the continent/ocean model and measurement tool

First implement the measurement definitions below against the legacy backend. Then add a new macro geography model using retained cellular/domain-warp primitives. Use stable macro sites and neighboring topology to establish continent extents and reserve major ocean corridors before adding fine shoreline detail. This is a new geography algorithm, not a different threshold on `continentEdge`.

Separate land topology from bathymetry. `ContinentModel` supplies landness/site identity; `OceanModel` supplies ocean class, coastline, shelf profile and separation constraints. Reuse RTF deep/shallow/coast terrain blending where it fits. Compose bounded coastal detail, islands and archipelagos subject to major-corridor clearance. Inland seas have explicit topology/classification; do not detect “the global ocean” by flooding from the edge of an arbitrary sampled square.

Expose `minimumMajorOceanWidthBlocks`, initially 1000, alongside continent scale/area targets, ocean coverage/separation, shelf width/depth, shoreline detail and archipelago frequency. Validate incompatible settings rather than silently accepting every combination. A single continent-scale slider does not establish width.

Add a coarse distance/topology field with stable coordinate alignment, halos and error bounds. Refine crossings for coastline/width measurements against the actual final land/ocean predicate. Rivers and coast queries must use that same definition; adapt center-to-ocean ray assumptions that break on non-star-shaped continents. Limit searches and report a failure rather than looping indefinitely.

Gate: a multi-seed regional report demonstrates distinct large continents, substantial marine separation, islands and continental interiors, with measured width failures exposed. All declared major ocean corridors in the acceptance domain satisfy the configured width within measurement tolerance. Do not claim a globally proven minimum from finite sampling; construction constraints and sampled validation are separate evidence.

### 5. Implement baseline climate as geographic factors

Implement explicit components with immutable inputs:

| Component | Responsibility |
| --- | --- |
| `LatitudeModel` | Shared geographic latitude from signed Z, configurable equator and scale. An initial bounded monotonic mapping can approach the poles without abrupt wrapping; no direct biome table. Document world X east and the chosen sign of north. |
| `AltitudeClimateFactor` | Temperature lapse from sea-relative elevation with explicit blocks-to-model-height conversion. Never use normalized `Cell.height` as physical altitude. |
| `OceanInfluenceModel` | Marine proximity, open-water fetch and coastal moderation; inland lakes/seas have distinct configurable moisture effects. |
| `ContinentalityModel` | Interior influence derived from geography; calibrate with ocean influence to avoid counting the same dryness twice. |
| `PrevailingWindModel` | Deterministic regional normalized wind vector from latitude/config and optionally slow seeded variation; no runtime weather. |
| `OrographicPrecipitationModel` | Moisture supply and precipitation from rising terrain along bounded upwind profiles; local slope alone cannot describe the upstream barrier. |
| `RainShadowModel` | Persist moisture depletion beyond a crest and recover with distance/marine replenishment; distinguish dry lee valleys from wet windward terrain. |
| `BaselineTemperatureModel` | Compose solar tendency, altitude and maritime/interior effects into mean temperature. |
| `BaselineRainfallModel` | Compose marine moisture, broad circulation and terrain effects; derive bounded humidity/moisture and potential evaporation estimates with documented units. |
| `BaselineClimateModel` | Orchestrate those factors and return `ClimateBaseline`; it neither selects biomes nor mutates geography. |

Use a deterministic bounded upwind transect initially. Obtain a world-aligned coarse geographic profile, traverse ocean-to-site, replenish over appropriate water, precipitate on rises, reduce remaining moisture beyond barriers and recover according to a configured length scale. Return explanatory contributions for debug queries. It is a calibrated baseline model, not an atmospheric solver or a claim of meteorological prediction.

Cache/amortize regional profile results at a fixed geographic scale. Use final local elevation for lapse/slope adjustments. Geography tasks must not wait on climate tasks; climate must not recursively generate neighboring climate tiles. A local `ClimateBaseline sample(GeoSample)` implementation can hold a read-only geographic-neighborhood dependency established at context construction.

Gate: synthetic geography fixtures prove every climate invariant below before tuning real seeds. At the same temperate latitude, a coastal windward site and a sheltered inland valley have substantially different rainfall. Initial snow/ice surface policy and biome selection use baseline outputs consistently without implementing runtime weather.

### 6. Add the data catalog and resolver, then the Minecraft bridge

Define a datapack descriptor codec using biome keys or tag selectors, temperature/rainfall/humidity ranges, land/water class, elevation/slope, landform influences, rain-shadow eligibility, ecological tags, priority and positive weight. Add separate surface/underground applicability. Keep defaults in data resources, including vanilla defaults; no large Java biome-ID switch.

Resolve descriptors into an immutable per-world catalog after registry/tag availability is confirmed. Precedence: explicit biome descriptor, explicit tag descriptor, optional imported candidate, documented conservative fallback. Define how multiple applicable descriptors merge or override; sort by explicit priority and resource key, never iteration order. Reject malformed ranges/weights. Report absent optional tags/biomes and unsupported candidates without failing unrelated worlds.

Resolve with hard geographic eligibility first (marine, coast, freshwater, terrestrial, underground), then temperature/moisture/landform scoring, then stable spatial weighted selection among eligible choices. Smooth spatial variation must not jump across hard eligibility boundaries or create independent per-block speckle. Use position/seed/config/version keys, never a shared random stream. A mandatory fallback exists for each environment class and is included in `possibleBiomes`; fallback events are visible in debug reports.

Register a dedicated companion `BiomeSource` codec and a dedicated world preset using its own noise settings/density IDs. Continue using Minecraft's `NoiseBasedChunkGenerator`. `getNoiseBiome(quartX, quartY, quartZ, sampler)` converts coordinates and delegates to the companion resolver. `collectPossibleBiomes` contains every eligible/fallback holder so features, locate and structure logic see the complete set.

Minecraft 1.20.1 `BiomeSource` does not receive the seed directly in `getNoiseBiome`. Implement a narrow context carrier on the supplied `Climate.Sampler`: initialize the global sampler from the dimension's `RandomState` context and propagate it into `NoiseChunk.cachedClimateSampler`. Use the existing RTF/TB carrier approach as evidence of the hook shape, not as climate authority. Scope consumption to the new biome source, publish once before generation, and fail explicitly if an own-source query lacks context. Prototype this binding and codec round trip before wiring all resolver logic. Do not store a mutable “active level” singleton or bind a registry biome source to whichever world initialized last.

Respect Y: surface baseline is two-dimensional geography, but Minecraft biomes are sampled in quart volumes. Provide explicit underground eligibility/masks for cave biomes using retained cave/depth density behavior, with complete holders and surface-versus-depth transition tests. Nether and End retain their own sources. Retained underground density helpers must not regain authority over surface ecology.

Gate: saved chunk biome palettes, `/locate biome`, possible-biome enumeration, spawn and structure biome checks agree with the resolver. Codec reload recreates the same source. Two worlds/dimensions on the same thread do not contaminate one another. Newly created vanilla-normal worlds are unaffected by the companion preset.

### 7. Add modded biome import and surface compatibility

Add optional TerraBlender import only after checking the selected Forge 1.20.1 API and registry lifecycle. The current code exposes region count/uniqueness, not a ready-made descriptor enumeration API. First try supported region/parameter registrations; if faithful capture requires a narrow accessor or registration-time adapter, isolate and version-test it. Do not promise universal discovery from region counts.

Import candidate biome keys and useful hints, deduplicate repeated parameter entries and preserve provenance. Vanilla/TB normalized climate ranges are hints, not temperature in degrees C or annual rain in mm. Require explicit data/default conversion rules and assign low-confidence diagnostics where ecological metadata is absent. Imported weights and region multiplicity must not override hard eligibility or accidentally multiply a biome's weight hundreds of times.

Add optional tag-based descriptor packs for BOP, Regions Unexplored and the applicable BWG/equivalent artifact verified for Forge 1.20.1. Test missing mods, each mod alone, multiple mods and no TerraBlender. Keep adapter classes unlinked when the optional dependency is absent. Retain each biome's normal vegetation and assess surface-rule composition independently; a correctly selected modded biome with incorrect stone/soil is a failed compatibility test.

Gate: allowed modded candidates are actually chosen, excluded ones remain excluded, optional mod absence loads cleanly, and the new world source does not call the old TB uniqueness selection path. Compatibility claims name tested artifact versions.

### 8. Retire obsolete climate/tree paths and finalize initial generation

Remove the new preset's custom tree replacement and extra forest-grass policies coherently, including unconditional holder lookups. Test normal dark-forest, bamboo and modded vegetation; preserve useful generic placement, templates and surface features. Remove legacy `BiomeVariance` coupling from any retained Poisson users.

Remove old `Climate` / `ClimateModule` / `BiomeType` authority, legacy climate UI and TB uniqueness hooks only once every active consumer has migrated. Keep old codecs/resources/backend implementations necessary for an explicitly supported legacy generation version. Do not delete registry IDs and then claim old worlds still load. Migrate structure filters and preview to geography services. Replace inactive spawn scaffolding with deterministic dry-land suitability and a bounded search/failure report.

Complete provenance headers and third-party notices for touched/retained derived files, preserve TerraForged/Forge notices, correct mixed-license package metadata and include notices in Forge binary and source jars. Isolate original packages from derived internals. Decide Fabric packaging/support separately; no Phase 2 adapters are introduced.

Gate: standalone dedicated server, integrated client, datapack export/load, save/reopen, terrain/biome compatibility suite and performance gates pass. Distribution contains correct notices. Phase 1 does not require PA to generate a new world.

## 2. Generation version and persistence policy

Use a schema version plus `(geographyVersion, baselineClimateVersion, biomeResolverVersion)`. Mod release version is not an algorithm selector.

For new worlds, freeze the selected generation manifest before creating terrain, serialize it with the world preset/config data, and store a world-owned verification record. Reopen validates both before generation starts. The record is not a global config file. Effective descriptor/tag expansion and noise resources are included in the snapshot/fingerprint; registry holders themselves are re-resolved against the world registry.

An existing world without the manifest must not acquire new defaults. If an identifiable legacy RTF preset/backend is supported, assign the explicit legacy version and retain its behavior. Otherwise refuse companion generation with an explanation. Do not claim that a version integer alone keeps an old algorithm alive.

An unknown version, changed generation config/resource/catalog fingerprint or removed required biome produces an actionable load error unless the corresponding frozen implementation/data can be honored. For Phase 1, do not automatically migrate or rewrite already generated chunks. A future explicit upgrade tool can deliberately create new-terrain boundaries; it is outside this phase. Hot datapack reload must not silently replace generation decisions in an open world.

## 3. Objective validation definitions

Every exported report includes seed, dimension, version tuple, config/resource/catalog hashes, origin/extent, sample step, algorithm backend and whether a measurement is exact, estimated or censored. JSON/CSV is the authoritative result; maps are a diagnostic companion.

### Geography

| Measure | Definition and acceptance use |
| --- | --- |
| Land/ocean fraction | Sample the finalized marine/land predicate on a fixed grid; distinguish freshwater/inland-sea classes. Report confidence/resolution and fractions by seed, not one attractive map. |
| Continent area | Connected land components at stated resolution plus model site IDs; report area quantiles and maxima. Mark components crossing the measurement boundary as censored. Do not equate one cellular site with one connected continent. |
| Coastline length | Marching-squares contour of the chosen shoreline predicate; measure in block units at a fixed resolution and repeat at finer resolution. Coastline length is resolution-dependent. |
| Major ocean width | Identify model-declared major marine corridors separating major landmasses. Measure coast-to-coast cross-sections perpendicular to the corridor centerline and confirm nearby alternative orientations. Include islands as obstructions, classify local straits separately, and report minimum/quantiles, coordinates and affected corridor IDs. Never define “major” solely as “already wide enough.” |
| Width refinement | Start with a 32-block grid and refine crossings near the 1000-block threshold to block scale. Coarse crossing uncertainty is at least on the order of two grid cells; account for oblique geometry and refine further when tolerance is inconclusive. Report violations, not just averages. |
| Continent separation | Nearest opposing shoreline distance and intervening water-body class between major components; distinguish short straits, broad open separation and components cut by the sample boundary. |
| Mountain ranges | Threshold documented continuous influence and relief; measure connected range area, major-axis length, transverse width, peak and ridge/relief distributions. Test chains and regional mountains separately. |
| Elevation | Histograms/quantiles in block Y and height above sea level, split by terrain/water class; finite values, generation bounds, slope distribution and seam continuity. |
| Hydrology | Mouths reach the intended water body, influence is bounded, map queries agree with carving, lake/wetland classes are consistent and all network searches terminate. Drainage conservation is not claimed for the retained synthetic river generator. |

Initial broad survey: at least 16 fixed seeds, 65,536×65,536 blocks per seed at coarse resolution, plus translated windows and adaptive boundary expansion. This is a proposed offline workload, not a completed measurement or proof about an infinite world. Include adversarial island/high-warp/narrow-ocean configurations and reject unsatisfiable generation settings. Detailed final-terrain samples can be stratified along coasts/corridors rather than eroding every block in the full survey.

### Baseline climate fixtures

Use synthetic providers so each test changes a known cause. Fixtures and bounds are versioned model requirements; suggested ratios below are acceptance targets, not observed results or empirical claims about British Columbia.

| Fixture | Assertion |
| --- | --- |
| Flat surface, equal elevation/ocean influence, increasing absolute latitude | Mean temperature is generally monotonic toward colder latitudes; test both hemispheres and no discontinuity at the equator. |
| Same coordinate tendency and moisture, ascending elevation | Temperature decreases with configured lapse, with declared clamp behavior and units. |
| Coast and deep interior at equal latitude/elevation | Ocean influence decreases inland; temperature/rainfall responses follow their configured factors. No seasonal-amplitude claim is made unless such an output is actually modeled. |
| Ocean → mountain → valley, fixed onshore wind | Windward rainfall exceeds leeward rainfall. In the strong-barrier fixture, valley rainfall is at most 60% of the matched unobstructed control. |
| Remove barrier while holding site latitude/elevation fixed | Valley rainfall recovers; ensures the difference comes from upstream geography, not local category or latitude. |
| Reverse wind / add second range / add ocean beyond | Wet and dry sides respond to wind and moisture fetch; a second range does not restore consumed moisture magically; later marine fetch can replenish it. |
| Same latitude, varied geography | Wet forest climate and dry grassland/shrubland climate both become eligible. No direct latitude-to-biome mapping exists. |
| Extreme configs and far coordinates | Finite bounded outputs, normalized wind, no overflow, no unbounded searches; unset/unknown geography is handled explicitly. |

Use real seed transects only after synthetic fixtures pass. Export every factor along an ocean–range–valley–range section to reveal why the lee valley is dry.

### Biomes and world integration

Test descriptor eligibility independently, then actual chunk generation. Desert descriptors depend on heat/moisture rather than an exact latitude band; test eligibility at more than one latitude with suitable conditions. Test a temperate dry valley with grassland/shrubland candidates, elevation-driven alpine candidates, distinct marine/coastal/interior pools, fallback behavior and weighted deterministic spatial selection.

Register a synthetic modded biome in the test registry to prove data-driven participation without relying exclusively on installed biome mods. Then verify real optional mod packs. Shuffle input descriptor/registry enumeration and require identical results after deterministic catalog resolution. Same seed/config/version/effective data yields identical biome keys; diagnostics list excluded candidates and reasons.

Exercise quart-to-block conversion at negative boundaries; surface/underground biome transitions; ocean floors and river/lake biomes; Nether/End isolation; multiworld same-thread use; cache eviction; chunk-order reversal; server restart; datapack/version mismatch; missing optional mods and missing required saved-world biome keys.

Validate structures against the resolved biome's tags and geographic constraints, including villages, ocean structures, buried treasure, underground structures and a modded structure. Confirm locate results can actually generate and avoid a blanket ban on all mountain structures. Confirm ordinary/modded vegetation generation and no feature-order cycle exceptions.

### Performance

Measure before/after every major stage, using equal JVM/hardware, world extent and workload, at least three measured repetitions following warmup. Separate legacy baseline, pure extraction, new continent model, climate and resolver costs. Include cold exploration, adjacent chunks, scattered locate/API queries, concurrent generation and save/reopen.

Record median and p95 tile/chunk latency, chunks/second, nanoseconds/query, allocation bytes/sample or chunk, peak retained cache bytes, cache hits/misses/evictions, outstanding tile tasks and time blocked joining futures. Profile CPU and GC where regression appears. Count coarse climate samples and forced fine-tile generations per climate query.

Provisional engineering gates: extraction alone should not regress throughput or allocation by more than 10% outside measured variance. The complete Phase 1 pipeline should target at most 25% extra generation time and retained memory for the same workload; any larger regression requires profiling and an explicit design decision before acceptance. A 2× generation-time regression is a failure for this plan. These are proposed budgets, not measurements or guarantees. Revisit numerical budgets with actual baseline evidence, not to conceal a failed run.

Batch and worker-count changes must preserve results. If tile dimensions change erosion output, keep them fixed in the generation version/config rather than advertising them as safe performance tuning. Do not solve climate cost by using cache-dependent approximation.

## 4. Debug tooling

Add `/geo` commands progressively alongside the services, initially through Forge command registration. Default to the executing player's X/Z and allow explicit coordinates for server/console use. Run bounded expensive work off the server tick thread and return results through the normal server task mechanism. Geography samples do not generate Minecraft chunks or mutate terrain.

| Command | Output |
| --- | --- |
| `/geo sample [x z]` | World/dimension/version/hash, exactness/resolution, continent/site ID, water class, shore/ocean distances with validity, block elevation, slope, terrain contributions, mountain influence, river/valley information |
| `/geo climate [x z]` | Latitude, temperature, annual rain, moisture index, evaporation, wind, rain shadow, component contributions and upwind barrier/fetch summary |
| `/geo continent [x z]` | Continent/ocean model values, shelf profile, major corridor identity, configured width, locally measured width and measurement limitations |
| `/geo biome [x z]` | Chosen key, descriptor provenance, top eligible scores/weights, hard rejection reasons, fallback status; optional Y for underground evaluation |
| Offline sample/benchmark runner | Seed sweeps, CSV/JSON metrics, spatial layers and reproducible fixture/benchmark reports using the same providers as generation |

Expose unknown values as unknown, including unavailable physical river distance or ridge orientation. Do not fill them with zero. Report whether biome output is a worldgen prediction or read from an already stored chunk. A map/overlay may later visualize existing layers, but command and machine-readable evidence comes first.

## 5. Completion boundary

The first implementation change is build/source-family repair, followed by reproducibility tests and baseline measurements. The final Phase 1 result is a separate, versioned Forge initial generator with coherent geography, deterministic geographic climate, descriptor-driven vanilla/modded biomes and normal biome vegetation. PA runtime weather, runtime biome/block mutation, succession, Dynamic Trees ecology, temperature integrations and TFC are not tasks in this plan.
