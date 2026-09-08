# Task 3 — geography extraction

Status: method-level audit and pre-extraction stage capture in progress. Not a completion report.
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
