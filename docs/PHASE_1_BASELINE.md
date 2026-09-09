# Phase 1 Task 0 — pure Forge baseline

Recorded 2026-09-05. Scope: source repair, loader/build conversion, launch and conversion verification only. The accepted architecture audit and implementation plan remain the historical architecture baseline; their old module paths now map to root `src/main`.

## Freeze identity

- Original repository commit: `e7bc3e0128e240bb77ec201f2c7e6190ac817652`.
- Repaired multi-loader comparator: `c7387b3e474f87d4ded0bfa7506eb7aa021d327d`.
- Tested pure Forge code freeze: `74193a5fac92515a5a9c2841f831e7af69eba8a0`.
- The commit introducing this record is documentation/evidence only. Resolve its exact SHA with `git log -1 --format=%H -- docs/PHASE_1_BASELINE.md`; a Git commit cannot embed its own hash. Both commits contain the same production code.
- Mod artifact: `build/libs/reterraforged-forge-1.20.1-0.0.6.jar` (1,064,698 bytes).
- Artifact SHA-256: `FA950D83F886901ADF2B47223C45A6809F21F7AAB658C0F1128F721D9D9416C8`. Two successive native builds produced this same hash.
- An additional `-sources.jar` is source documentation, not another loader/mod artifact. The development smoke mod is excluded from both production source sets and the production mod jar.

## Toolchain and dependencies

| Component | Frozen value |
| --- | --- |
| Minecraft | 1.20.1, official Mojang mappings |
| Forge | 47.4.22 |
| JDK used | Eclipse Temurin 17.0.17+10, Windows 11 amd64 |
| Java target/toolchain | 17 |
| Gradle wrapper | 8.11 |
| ForgeGradle | 6.0.42 |
| MixinGradle | 0.7.38 |
| Mixin annotation processor/runtime | 0.8.5 |
| Mod version / package | 0.0.6 / `Gabou.projectlandscape` |
| TerraBlender | 3.0.1.10; Curse file 6290448, compile-only by default |
| Architectury plugin / Loom | Removed from final build; repaired comparator resolved 3.4.164 / 1.9.436 |
| Fabric Loader / Fabric API / Architectury API | No final dependency |

`build.gradle` applies Java, Eclipse, IDEA, ForgeGradle and MixinGradle only. Repositories are Maven Central, Forge Maven, and group-filtered Curse Maven. There are no subprojects, source transformations, shadow/common configurations, Fabric runs, or loader-dispatch annotations.

Standalone defaults: `withDevelopmentMods=false`, `withTerraBlender=false`. TerraBlender is available for compilation but absent from the default runtime classpath. Set `-PwithTerraBlender=true` for its independently tested runtime profile.

The user's intended mod coordinates are preserved behind `-PwithDevelopmentMods=true`:

| Optional development mod | Pinned Curse coordinate |
| --- | --- |
| Project Atmosphere | `curse.maven:project-atmosphere-1258344:8799655` |
| Gabou's Libs | `curse.maven:gabous-libs-1367332:8774253` |
| GlitchCore | `curse.maven:glitchcore-955399:5787839` |
| Serene Seasons | `curse.maven:serene-seasons-291874:8246702` |
| Simple Clouds | `curse.maven:simple-clouds-1121215:6928978` |

These optional profiles are preserved, not certified as an integration/modpack. No PA climate authority or other Phase 2 behavior was added. Minecraft/Forge's transitive runtime dependency tree is captured in [runtime-dependencies.txt](task0/runtime-dependencies.txt). The wrapper requires a Java 17 installation and network access for uncached dependencies/assets. Do not overlap Gradle game/build invocations that rewrite the same ForgeGradle cache.

## Final layout and platform migration

```text
build.gradle, settings.gradle, gradle.properties, gradlew[.bat], gradle/wrapper/
src/main/java/Gabou/reterraforged/     existing common + native Forge code
src/main/resources/                       common assets + Forge metadata/mixins/AT
src/generated/resources/                  tracked language + pack metadata
src/smoke/java/baseline/smoke/             development-only lifecycle harness
src/smoke/resources/                      development-only mod metadata
docs/                                    accepted audit/plan and baseline evidence
```

`common/`, `forge/`, and `fabric/` are removed as source/build modules. Their ignored historical build/run/cache contents were moved intact to `build/task0-reference/legacy-layout/{common,forge,fabric}`; no old saves were deleted. An isolated detached comparator worktree may also remain under `build/task0-reference/legacy-source`. These ignored archives are not production source or runtime dependencies.

The exact implementation changes, removals and rename destinations from the original commit through the code freeze are in [changes.tsv](task0/changes.tsv). `R100` means byte-identical Git content. Git may attribute the identical shared logo to Fabric even though the Forge resource was retained. New record/evidence files are enumerated at the end of this document.

| Former abstraction | Final native implementation |
| --- | --- |
| `ConfigUtil` / `platform.forge.ConfigUtilImpl` | `ConfigUtil` directly uses `FMLPaths.CONFIGDIR` |
| `ModLoaderUtil` / `ModLoaderUtilImpl` | `ModLoaderUtil` uses early `FMLLoader.getLoadingModList()` checks, preserving TB mixin timing |
| `DataGenUtil` / `DataGenUtilImpl` | `DataGenUtil` constructs Minecraft `RegistriesDatapackGenerator` |
| `RegistryUtil` / `RegistryUtilImpl` | `RegistryUtil` owns the existing `DeferredRegister` and `DataPackRegistryEvent` implementation |
| `platform.forge.DeferredRegistry` | Moved to `platform.DeferredRegistry`; retained as an internal deferred registry abstraction |
| `BiomeModifiers` / `BiomeModifiersImpl` | Existing Forge add/replace codec construction in `BiomeModifiers`; Forge records made public for parent-package construction |
| `RegistryUtil.getBiomeModifierRegistry` | Removed: no callers and no Forge implementation |
| Five `ExpectPlatform` dispatch owners | Annotations/imports removed after Forge behavior was inlined |
| `RTFForge`, `RTFForgeClient`, Forge mixins | Moved into main; native mod bus/client preset registration/server template hooks preserved |

Removed Fabric systems: `RTFFabric`; its four mixins (biome modification, chunk status, server templates, preset editor); four platform implementations; Fabric add/replace feature modifiers and codec bridge; Fabric metadata, mixin manifest, datagen/run setup; Fabric dependencies and build module. No separate networking abstraction existed. Generic biome modifiers, registry wrappers and feature infrastructure remain; no vegetation or tree policy was changed.

Full source-family proof and pre-move classification: [TASK_0_PLATFORM_MIGRATION.md](TASK_0_PLATFORM_MIGRATION.md).

## Active preset family and the six original errors

Authoritative type: `data.worldgen.preset.settings.Preset`, registered by `RTFRegistries.PRESET`. Active generators are `data.worldgen.preset.Preset*`. The live export chain is `PresetConfigScreen -> Datapacks.makePreset -> settings.Preset.buildPatch`; `MixinRandomState` and `MixinNoiseChunk` consume the same settings type.

All Java references, resources/datapacks, registries, GUI/export, mixins and datagen entry points were searched before removal. The obsolete family had no external consumers, so no active consumer needed migration. Thirty-one inactive classes were removed: nineteen old top-level `data/worldgen` generators/helpers and twelve old `data/worldgen/preset` settings classes. Their exact paths are in the change inventory.

| Original compiler error | Resolution / classification |
| --- | --- |
| `BiomeModifierData.java:59` stale `BiomeModifiers.add` overload | Remove inactive generator; duplicate/inactive source removal |
| `BiomeModifierData.java:69` same overload mismatch | Same; active generator already supplies filter behavior |
| old `preset/Preset.java:39` incompatible registered Preset type | Remove unregistered parallel type; duplicate/inactive source removal |
| old `preset/Preset.java:47` same type mismatch | Same; keep `preset.settings.Preset` codec and registry |
| `RTFConfiguredFeatures.java:68` obsolete erosion config constructor | Remove inactive generator; duplicate/inactive source removal |
| `RTFConfiguredFeatures.java:72` obsolete snow config constructor | Same; active constructors already match current source |

No active worldgen API call was changed to accommodate these errors. The subsequent platform/build changes are **pure-Forge migration fixes**. Native Forge refmap generation replaces the pre-existing invalid intermediary development refmap. Tracking the existing datagen outputs supplies pack metadata and language resources in a fresh build. No geography baseline bug fix was necessary.

## Resources, access and Mixins

All active common resources, tree NBT/templates, texture, logo, Forge metadata and inherited notices were retained. Generated language and pack metadata are tracked; `.cache` remains ignored. Ordinary `runData` emits those two resources; full dynamic worldgen registry export is separately exercised by the smoke harness using the same `Datapacks.makePreset` path as the GUI.

The jar has exactly two ReTerraForged mixin manifests, both JAVA_17 and required:

- `reterraforged-common.mixins.json`: NoiseChunk, RandomState, ChunkMap, Util, BiomeGenerationSettings, SurfaceSystem, Structure, MinecraftServer, SpawnFinder; client-only `ScreenInvoker`; four conditionally gated TB mixins.
- `reterraforged-forge.mixins.json`: TagsProvider, BiomeGenerationSettingsPlainsBuilder, ChunkStatus, MinecraftServer.
- Shared generated `reterraforged-refmap.json`: SRG targets verified; no `net/minecraft/class_*` intermediary targets. Both manifests are declared in the jar manifest and development run configuration.
- `MixinPlugin` retains early TB gating. Standalone run logs disable compatibility; TB run logs enable it and show all four injections. `MixinSpawnFinder` is an unchanged empty/commented stub, not a functioning new spawn hook.

All 65 old access-widener entries map exactly to the native `META-INF/accesstransformer.cfg`. The mappings were checked against official-to-SRG output and the old Forge jar. See the exhaustive [access entry/replacement table](TASK_0_ACCESS_MIGRATION.md). No access widener remains and no new production invoker/accessor was needed.

## Build and runtime gates

Commands run from the root, sequentially:

```text
.\gradlew.bat compileJava classes jar build
.\gradlew.bat runData
.\gradlew.bat runSmokeClient
.\gradlew.bat runSmokeClient -PwithTerraBlender=true
```

| Gate | Final observed result |
| --- | --- |
| `compileJava` | SUCCESS, executed in final four-gate run |
| `classes` | SUCCESS |
| `jar` + `reobfJar` | SUCCESS; one Forge mod artifact |
| `build` | BUILD SUCCESSFUL in 4s; 13 actionable tasks, 10 executed, 3 up-to-date |
| `runData` (with all build gates) | BUILD SUCCESSFUL in 10s; 19 actionable tasks, 11 executed, 8 up-to-date; zero changed output files on rerun |
| Standalone `runSmokeClient` | TASK0 PASS; BUILD SUCCESSFUL in 55s |
| TB-enabled `runSmokeClient` | TASK0 PASS; BUILD SUCCESSFUL in 47s |

The baseline intentionally has no Task 1 determinism/unit-test suite: Gradle reports `test NO-SOURCE`. Smoke timings are verification wall times, not performance benchmark claims.

The development-only harness observes `TitleScreen` after resource loading, checks mod presence, exports Legacy Default, creates a creative/peaceful world with seed **8675309**, verifies non-null RTF generator context and preset, requests FULL chunks and checks non-air surfaces and biome holders, saves all dimensions, disconnects, reopens the same world and requests additional remote FULL chunks. It exits automatically. The Gradle smoke task fails if its fresh PASS marker is absent. It skips first-run accessibility onboarding, not mod loading failures.

Standalone saved world: `run/task0-smoke/saves/task0-1788618932948`. The TB run used `task0-1788618789154`. Generated test worlds are retained, not distributed. Seven direct coordinate checks were made before reopen and seven after; additional category-selected chunks and spawn-neighbor chunks were generated. Coordinates and exact observations are in [runtime-evidence.txt](task0/runtime-evidence.txt).

No final standalone/TB log reported an ERROR, generator exception, missing preset/registry failure, or fatal mixin injection. Debug logs show required common-origin and Forge-origin injections. The TB run is a compatibility-library smoke, **not certification of biome mods** such as BOP/RU/BWG.

Visual limitation: desktop automation was unavailable after its prescribed retries. Title-screen state and terrain generation are objectively checked through Minecraft/Forge APIs and logs, not screenshots. Manual visual terrain inspection, interactive GUI button/export UX, and a separately installed packaged-client run remain unperformed. The GUI's underlying registry/datapack generation path and real integrated-client world lifecycle were exercised.

## Worldgen-equivalence verification

The stored [class SHA-256 comparison](task0/worldgen-class-sha256.tsv) compares the repaired multi-loader compilation with the native Forge compilation. **332 of 333 worldgen classes are byte-identical**, including noise, cells, continent models, terrain, climate, hydrology, tiles, erosion, structures and surface implementations. The one expected difference is `BiomeModifiers`, whose existing Forge codec/constructor dispatch was inlined. No engine class is missing.

Representative generated coverage includes origin `(0,0)`, negative `(-129,-129)`, tile-edge `(127,127)/(128,128)`, coast `(-4032,-4096)`, mountain `(-2688,-4096)`, river `(-64,-4096)`, and plateau `(3392,-3072)`. Raw sample float bits and full-chunk status are preserved in runtime evidence. This compares loader conversion, not cached-versus-uncached semantics.

The isolated repaired multi-loader comparator also completed its client/world/save/reopen smoke: **BUILD SUCCESSFUL in 56s**. To make the existing development launch usable, its invocation disabled the broken refmap (`-Dmixin.env.disableRefMap=true`) and supplied the existing generated pack metadata. It compiled the identical current smoke harness, registered its development mod in the archival Forge metadata, and used a separate run directory. No legacy geography source was changed. These adjustments exist only in the ignored detached `build/task0-reference/legacy-source` worktree, not the native Forge project.

**18/18 recorded before/after observations match exactly, zero differences**: fourteen surface-height/block/biome/full-chunk observations (seven after reopen), plus four coast/mountain/river/plateau records containing raw elevation, river, temperature and moisture float bits and full-chunk status. Seed, Legacy Default preset, settings, coordinate order, Minecraft, Forge and JDK are the same. The comparison is in [worldgen-sample-comparison.json](task0/worldgen-sample-comparison.json). This is representative equivalence evidence, not a full block-by-block world diff or a new determinism guarantee. Known cache/seed deficiencies are deliberately preserved.

The full exported Legacy Default datapacks also match: **175/175 files byte-identical**, equal before/after file counts and no differing/missing files. This includes the dynamic registry JSON and preset metadata, not only the settings record.

## Default generation settings

The current GUI's Legacy Default factory remains `preset.settings.Presets.makeLegacyDefault()`. The unmodified serialized export is captured in [legacy-default-preset.json](task0/legacy-default-preset.json), derived from ReTerraForged's existing MIT implementation. Some fields are omitted by existing codecs and restored by their existing defaults; consult the frozen factory for full in-memory values. This omission was not fixed during Task 0.

- Continents: MULTI_IMPROVED, Euclidean, scale 3000, jitter 0.7; factory skip/variance 0.25, five octaves, gain 0.26, lacunarity 4.33.
- Control points: deep ocean 0.1, shallow ocean 0.25, beach 0.327, coast 0.448, inland 0.502; unchanged mushroom defaults.
- Height 320, depth 64, sea 63, lava -54; CONTINENT_CENTER spawn selection setting.
- Terrain region size 1200, vertical scale 0.98, horizontal scale 1, fancy mountains enabled; original terrain weights and individual scales retained.
- Rivers seed offset 0, count 8; main/branch/lake/wetland settings unchanged in snapshot.
- Erosion 135 droplets, lifetime 12; smoothing one iteration, radius 1.8, rate 0.9.
- Existing climate/biome noise, caves, custom trees, vegetation modifiers, surfaces and structure settings retained verbatim.
- Performance defaults: tile exponent 3 (8 chunks / 128 blocks), batchCount 6, CPU-derived worker count. Existing `PerformanceConfig.read` returns defaults instead of reading the file.

## Known warnings, defects and deferred risks

- Javac reports existing deprecation/removal and unchecked warnings (default output caps displayed warnings at 100). This is not a new warning cleanup project.
- Annotation processor cannot determine descriptors for the optional Forge reload lambda (`require=0`) and TB synthetic target. Constructor/worldgen/TB injections succeed. The best-effort template resource-reload lambda is not independently proven to inject; no reload behavior was redesigned.
- Development runs warn that the refmap is not on the source-set runtime resource path. Native named targets launch successfully; the production jar does contain the verified SRG refmap. This warning is not the old fatal intermediary-mapping error.
- Forge library jars warn about missing `mods.toml`; vanilla resource URLs use `union:`; first runs fill default Forge config keys. Native render/audio warnings include goat-horn sounds and a shader sampler. These did not block tested generation.
- Smoke's forced synchronous remote chunk requests cause occasional server "Can't keep up" warnings. This is not a measured general worldgen regression. Forge's version checker advertises 47.4.23; baseline deliberately remains pinned at tested 47.4.22.
- Gradle 9 deprecations originate in plugin use of filtered resolved dependencies and `Project.javaexec`; wrapper is pinned to supported/tested 8.11. Missing Eclipse diffplug APT configuration is informational.
- An early smoke harness supplied an invalid simulation-distance value; the harness was corrected to 5. Final smoke has no such error. An overlapping earlier build/run temporarily encountered a rewritten Forge cache jar; sequential rerun succeeded. Neither required production generator edits.
- Known 32-bit world-seed narrowing remains. `CellSampler.Cache2d` global/cache identity risks remain. Cached versus uncached climate/coast/erosion behavior discrepancies remain in source and were **not fixed or recharacterized**. Tile geometry, eviction and batching semantics remain unchanged.
- The audit's potential non-default vegetation/preset export issues remain unmodified; only Legacy Default was smoke-tested. No runtime bug is claimed fixed outside build/platform compatibility.
- No generation-version/migration mechanism, new geography API, determinism fix, ocean-width change, climate/biome redesign, PA/Dynamic Trees/temperature/TFC integration was introduced.

## Licensing

Root LICENSE and inherited notices were not removed or overwritten. Rewritten/inlined RTF helper implementations carry `Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.` and point to the retained permission notice. TerraForged notices in inherited files remain. `client/data/LanguageProvider.java` retains Forge Development LLC **LGPL-2.1-only** provenance. Existing `mods.toml`'s ARR metadata was not used to relicense derived files. The new isolated smoke harness is original ARR code. Both production jar and sources archive include the root license; sources retain per-file notices.

## Pure Forge migration verification

```text
Architectury imports in tracked production source: 0
Fabric imports in tracked production source: 0
Fabric project: removed
Architectury project structure: removed
Fabric/Architectury references in production class files: 0
Fabric/Architectury dependencies in standalone runtime tree: 0
Single Forge mod artifact: verified (sources archive is not a second mod)
Access-widener entries accounted for: 65/65
Required ReTerraForged Mixin manifests packaged: 2
Development smoke classes packaged in production mod: 0
```

## Evidence inventory and handoff

Implementation inventory: [changes.tsv](task0/changes.tsv), from original SHA to code freeze. Documentation-only additions after that freeze:

- `docs/PHASE_1_BASELINE.md`
- `docs/task0/changes.tsv`
- `docs/task0/worldgen-class-sha256.tsv`
- `docs/task0/runtime-evidence.txt`
- `docs/task0/runtime-dependencies.txt`
- `docs/task0/legacy-default-preset.json`
- `docs/task0/worldgen-sample-comparison.json`
- `docs/task0/legacy-runtime-evidence.txt`

Additional local full logs: `build/task0-preconversion-build.log`, `build/task0-preconversion-client.log`, `build/task0-forge-build.log`, `build/task0-datagen.log`, `build/task0-final-build.log`, `build/task0-final-gates.log`, `build/task0-smoke.log`, `build/task0-smoke-terrablender.log`, `build/task0-standalone-debug.log`, `build/task0-terrablender-debug.log`, and `build/task0-legacy-smoke.log`. Logs/artifacts/worlds under build/run are intentionally not shipped.

Stop at Task 0. No geography/climate/biome algorithm was intentionally changed; Task 1 determinism work and architectural extraction have not begun.
