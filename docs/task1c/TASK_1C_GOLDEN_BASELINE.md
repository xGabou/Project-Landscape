# Task 1C — corrected legacy comparator

Comparator: **`cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee`**, the accepted Task 1B production source. `LEGACY_RTF_V0` is a test/documentation label, not a production generation version. Task 2 has not begun.

## Identity and configuration

Minecraft 1.20.1, Forge 47.4.22, Temurin Java 17.0.17+10, Gradle 8.11, ForgeGradle 6.0.42, MixinGradle 0.7.38. Pure Forge, no Architectury/Fabric production modules. Optional TerraBlender 3.0.1.10 is measured separately; other development mods remain disabled. Machine/JVM details are in [machine.json](evidence/machine.json) and each run's `environment.json`.

Active preset: `data.worldgen.preset.settings.Presets.makeLegacyDefault()`, exported through `Datapacks.makePreset`, then **loaded back through Minecraft registries**. [identity.json](evidence/golden-24/identity.json) records both the loaded preset codec and its effective fields, including values omitted by that codec. Sorted-field configuration SHA-256: `1b510addf57022ab0df6e9066854017e0a034ceaeee4ea222d2dad419cfdcf30`.

Key effective settings (the JSON is the complete specification):

| Component | Frozen setting |
|---|---|
| Tile geometry | Exponent 3: 8×8 chunks / 128×128 core blocks; one-chunk halo each side, 160×160 allocated cells |
| Batching | 6×6 tasks; worker count is varied, geometry is not |
| Erosion | 135 droplets/chunk, lifetime 12, volume/velocity 0.7, erosion/deposit 0.5 |
| Smoothing | One iteration, radius 1.8, rate 0.9; inherited steepness/beach/quart finalization retained |
| Continents | MULTI_IMPROVED, EUCLIDEAN, scale 3000, jitter 0.7, variance/skipping 0.25, 5 octaves, gain 0.26, lacunarity 4.33 |
| Controls | Deep 0.1, shallow 0.25, beach 0.327, coast 0.448, inland 0.502 |
| World | Height 320, depth 64, sea 63, lava −54, CONTINENT_CENTER spawn |
| Terrain | Region 1200, vertical 0.98, horizontal 1, fancy mountains true, legacyMountainScaling false; all per-terrain weights/scales in JSON |
| Rivers | Offset 0, count 8; main/branch, lake and wetland settings fully recorded |
| Climate | Legacy biome size 225, warp 150/80; temperature scale 6, bias 0.05, range 0–0.98; moisture scale 6, range 0–1 |
| Vegetation/features | Existing customBiomeFeatures and erosion/snow/strata decorators enabled; no replacement policy changes |
| Seed | Minecraft long narrowed to int for RTF context; vanilla long-seed consumers remain independent |

The production default may queue neighboring tiles when processors >4. Isolated geography/benchmark contexts deliberately use `queue=false`; actual chunk tests keep the production queue behavior. These are explicitly different workloads, not interchangeable timing results.

## Canonical sampling authority

```text
RandomState / MixinRandomState → GeneratorContext
  → TileGenerator.generate (terrain → rivers → climate, batched)
  → WorldFilters.apply (erosion, smoothing, finalization)
  → detached Tile snapshot
  → MixinNoiseChunk constructor: cache.provideAtChunk(...).getChunkReader(...)
  → CellSampler.CacheChunk → NoiseRouter density functions → Minecraft terrain
```

For ordinary chunk density generation (`cellCountXZ > 1`), `MixinNoiseChunk` supplies the finalized tile's chunk reader. Therefore **`LEGACY_FILTERED_CANONICAL`** is `WorldLookup.applyCell(..., load=true, applyClimate=true)`, selecting the owning tile's **core**, not an overlapping halo. Publication happens only after filtering. Climate is populated before erosion; this inherited ordering is not silently changed.

**`LEGACY_DIRECT_APPROXIMATE`** calls `WorldLookup.sampleDirectApproximate(..., true)`: direct `Heightmap.apply`, followed by its point-only coast adjustment, with no tile filters. It is not just a slower version of canonical geography. Raw `Heightmap.apply` alone is separately timed and lacks that point-only adjustment.

Consumers that can use the approximate/opportunistic path include single-column/out-of-chunk density fallback, early climate/biome and structure queries. `StructureCellTest` uses opportunistic lookup without climate. The preview's `generateZoomed` path additionally has its own optional-filter semantics and is **not** a canonical golden source. Erosion/snow features and in-chunk density readers consume finalized tile data. Future extraction must not replace these canonical readers with a convenient direct point call.

Legacy biome hints flow from `CellSampler.Field` through `PresetNoiseRouterData`, Minecraft `Climate.Sampler` and `MultiNoiseBiomeSource` to a biome holder; optional TerraBlender contributes its existing integration. The RTF `Cell.biome` enum is not the final Minecraft biome key and none of these values is the future baseline climate model.

## Permanent corpus and encoding

[fixture_manifest.json](evidence/fixture_manifest.json), schema/fixture version 1, freezes seven seeds as exact longs:

`8675309`, `4303642605`, `42`, `-987654321`, `0`, `9223372036854775807`, `-9223372036854775807`.

There are **85 coordinate rows per seed / 595 rows**, intentionally preserving the original 75 axis/diagonal cases (including duplicate origin rows) and ten geographic labels. The original discovery was row-major −4096..4096, step 64, first matching category. Subsequent runs **read the frozen coordinates** and reject a configuration mismatch; they do not rediscover convenient replacement locations.

Coverage: origin/signs, quart/chunk/tile edges and corners with neighboring coordinates, coast, shallow/deep ocean, river, mountain, mountain transition, plateau, plains, region transition and river-mouth proxy. Mountain/region transitions are adjacent scan changes; the river-mouth label is a below-inland-threshold proxy, not a solved drainage endpoint. Discovery used direct terrain, so a label is not a promise that post-filter beach classification is identical. Invalid-coordinate/lifecycle/cache reproducers remain separately in the immutable Task 1A/1B evidence, not in canonical expected terrain.

`canonical_geography.json` freezes all **24 public Cell fields plus 10 Field.read density hints**. Every float uses exact raw IEEE-754 signed-int bits; integers, booleans, terrain names and BiomeType names compare exactly. No epsilon, JVM object IDs, task/thread IDs, timestamps, or registry iteration order enter these samples. `fieldSchema` defines each encoding. Existing hydrology is represented by riverMask and terrain identity; there are no invented lake-distance/water-table fields. Supplemental terrain traits record existing categories/river/lake/wetland flags.

Canonical files reside in `evidence/golden-24/`; direct diagnostic samples and biome hints are separate tables. Final Minecraft holder queries (quart Y=20) are recorded separately because they are actual legacy biome-source queries, **not** a new filtered-climate resolver. TerraBlender-enabled results reside in their own run directory.

## Exactness and legacy defects

The 340 shared canonical and 340 shared direct rows match accepted Task 1B output exactly. The original 340/340 direct-versus-filtered disagreement remains: gradient 340, height 319, heightErosion 176, sediment 96, terrain 6 (corresponding hints duplicate those numeric changes). The six terrain changes are point coast versus filtered beach classification. No tolerance or new golden was used to hide a discrepancy.

The collision pair matches **all 34 canonical fields at 85 locations**. This is expected **only under LEGACY_RTF_V0**; the future-backend divergence assertion is explicitly inactive until Task 2. Full-seed vanilla noise and downstream block output are not asserted to collide.

Tile digests preserve Task 1A's SHA-256 strategy: 16,384 core cells in `Tile.iterate` order, each cell's sorted 24-field exact encoding, Gson pretty UTF-8 serialization with no trailing newline in the hash. Coordinates (0,0), (0,−1), (1,0), (0,1), (−1,0), across seven seeds and center-first/reverse/parallel submission. Worker runs 2/24/48 agree on every matching tile. Finite-halo behavior remains legacy semantics: changing tile exponent/border is not a performance-only change.

The disagreement is a semantic fixture, not a requirement that future providers preserve ambiguity forever. The protected behavior is that canonical finalized geography cannot accidentally become unfiltered geography.

## Verification commands

```powershell
.\gradlew.bat runReproductionClient -PreproProfile=golden
.\gradlew.bat runReproductionClient -PreproProfile=golden-scheduling -PreproProcessors=2
.\gradlew.bat runReproductionClient -PreproProfile=golden-scheduling -PreproProcessors=48
.\gradlew.bat runReproductionClient -PreproProfile=golden -PwithTerraBlender=true
scripts/task1c/Collect-Run.ps1 -Name <new-evidence-directory>
scripts/task1c/Verify-Goldens.ps1 -Candidate <new-standalone-run>
```

Runs use the actual Forge client, exported datapack, integrated server, registry holders and existing generator. The PASS marker is written after unload/disposal checks. `Collect-Run` refuses to overwrite an archived run. Verification must not use `-FreezeManifest` to update a changed result; that switch is initial-corpus creation only and refuses an existing manifest. Evidence integrity/provenance is indexed by `evidence/manifest.json`.

## Scope and remaining issues

No production geography, climate, biome, continent, ocean, vegetation, structure or seed algorithm is intentionally changed. Instrumentation lives in `src/reproduction`, never in the shipped jar. The added tile-submission observer counts calls only; it does not wrap futures, change output, or choose task order.

Remaining known legacy limitations: seed narrowing; distinct filtered/direct contracts; tile partition/halo dependence; dormant invalid 200-octave definitions guarded at density evaluation; unexplained downstream FULL-block variance. Task 1C does not fix those. Runtime ecological systems, generation manifests, version architecture and public geography APIs remain out of scope.

## Completion and verification

All Task 1C measurement gates passed:

- **595** canonical samples ×34 fields, **595** direct samples, **595** biome/hint rows. A fresh fixed-corpus rerun matches all three tables exactly; 340 canonical and 340 direct rows also match accepted Task 1B evidence.
- **420** standalone tile digest observations (315 across workers 2/24/48 plus 105 in a second 24-worker run), 35 distinct seed/tile pairs, zero order/worker/repeat differences. TerraBlender's separate run contributes another 105 digest observations, not mixed into the standalone scheduling count.
- **85** Minecraft biome-key queries per standalone/TB run; the repeated standalone run matches, and TB alone changes none of these keys or the 595 RTF samples. This does not claim compatibility with third-party biome packs that were not installed.
- Seed collision: 85/85 matching 34-field canonical rows, explicitly legacy-only. Filtered/direct disagreement: original 340/340 with unchanged field counts.
- Actual benchmark repetitions, counters, workspace-pool observations and a worker-scoped allocation probe recorded. Exact retained cache bytes and isolated total FULL-chunk allocations remain explicitly unmeasured.
- Standalone, TerraBlender-enabled and vanilla-control smoke: each reaches title, creates/generates, saves, reopens and generates additional chunks; **14 FULL observations and two save confirmations per profile**. Vanilla remains without an RTF generation context. Automated logs/objective terrain evidence, not manual visual appearance certification.
- `compileJava classes jar build reproductionClasses runData`: **BUILD SUCCESSFUL**, 21 actionable tasks (10 executed, 11 up-to-date), with datagen reporting zero rewritten generated files. All golden, scheduling, benchmark, allocation and variance harness launches reached PASS.
- Shipped jar: 1,075,208 bytes, SHA-256 `25cad0f81a03c2508a9ceb00637fc870534f00b016ff1d80e6190a81451916d4` — **byte-identical to Task 1B**. Both production mixin manifests, Forge AT, metadata and LICENSE are present; no harness classes or observer manifest are packaged. `src/main` and `src/generated` have zero changes from the comparator.

See [final_summary.json](evidence/final_summary.json), [golden_verification.json](evidence/golden_verification.json), and the [performance report](PERFORMANCE_BASELINE.md). The focused [FULL-block investigation](FULL_CHUNK_VARIANCE.md) observed 10/24 differing FULL pairs with exact RTF and stored biome equality; four initially matched pairs first diverged after their own FEATURES return. Root cause remains deferred, not guessed or repaired.

Remaining warnings are inherited development/compiler warnings: deprecated/unchecked APIs, Gradle 9 deprecations, development refmap/decompiler notices, Forge config defaults, shader/Realms/account messages, and occasional live-client/server catch-up warnings. No fatal mixin, registry, preset or generator failure occurred in successful gates. Two initial Task 1C launches failed on **test-only serialization/Windows filename handling** and were corrected; their logs are retained as failed harness attempts, not runtime defects of the comparator.

Task 1C commits are intentionally separate: harness/frozen fixtures (`4984b8c2534b6c563acfa6f8782e2edd5728a0db`), measurement/variance evidence (`f50444cabab2d9969e985eb36bc0f26f4991cdf9`), then final documentation. The evidence manifest records the preceding tooling source head; it cannot self-reference the commit that stores its own hash. Final commit IDs are listed in the handoff and Git history. No production algorithm, licensing notice or Task 2 architecture was changed.
