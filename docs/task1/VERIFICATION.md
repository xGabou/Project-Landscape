# Task 1A verification record

Completed against Task 0 `e9dd8841a1b4a95e4bb2b23e084d40abbc1bea70`,
2026-09-05/06. This record is not a new production worldgen baseline. Resolve this
Task 1A commit with `git log -1 --format=%H -- docs/task1`; the commit cannot embed
its own SHA. The handoff response supplies its SHA after committing.

## Archived runtime runs

| Evidence directory | Runtime identity | Profile | Workers | Result |
| --- | --- | --- | ---: | --- |
| full-24-a | reproduction-1788646419834 | full, 3 seed worlds | 24 | PASS; Gradle 1m45s |
| full-24-b | reproduction-1788646585103 | full, 3 seed worlds | 24 | PASS; Gradle 1m44s |
| workers-2 | reproduction-1788712781787 | concurrency | 2 | PASS; Gradle 1m23s |
| workers-48 | reproduction-1788712893477 | concurrency | 48 | PASS; Gradle 1m01s |

Each full run contains 340 A/B/C rows, 680 eviction/reload rows, 510 seed-pair rows,
120 whole-tile order digests, 80 overlaps, 32 geometry probes and 24 FULL-chunk captures.
Each scheduling-only run contains 120 order digests plus overlap/geometry probes.
Across the four runs: 480 digests, 96 per tile, **one distinct hash per tile**, workers
2/24/48. None of those hashes is a golden assertion. Default configuration is retained.

The compressed block-snapshot comparison finds 10/24 differing FULL chunks in the two
full runs. All 24 exact RTF sample and biome comparisons agree. See F12, not a blanket
claim of end-to-end deterministic Minecraft chunks.

The verifier passed **27 relational reproduction checks** on the default archived
suite, including ownership, bounds, cache isolation, mode/warmth, non-finite dormant
noise definitions and disabled-feature bootstrap failure. A PASS here means the
inherited observation was reproduced, not that the implementation is correct.

```powershell
.\scripts\task1a\Verify-Reproductions.ps1 `
  -FullRun docs/task1/evidence/full-24-a `
  -SchedulingRuns docs/task1/evidence/full-24-b,docs/task1/evidence/workers-2,docs/task1/evidence/workers-48 `
  -OutputDirectory docs/task1/evidence
.\scripts\task1a\Compare-ChunkSnapshots.ps1 `
  -RunA docs/task1/evidence/full-24-a -RunB docs/task1/evidence/full-24-b `
  -OutputDirectory docs/task1/evidence
.\scripts\task1a\Write-EvidenceManifest.ps1
```

`summary.json` and `chunk_repeats.json` are machine-readable indexes.
`manifest.json` enumerates exact archived filenames, byte sizes and SHA-256 hashes
(excluding itself). Earlier exploratory local runs remain in ignored run/task1a; they
are not silently substituted for these archived runs.

## Build and isolation gates

Command:

```text
gradlew.bat compileJava classes jar build compileReproductionJava reproductionClasses --console=plain
```

Final output:

```text
:compileJava UP-TO-DATE
:processResources UP-TO-DATE
:classes UP-TO-DATE
:jar
:reobfJar
:sourcesJar UP-TO-DATE
:assemble
:compileTestJava NO-SOURCE
:test NO-SOURCE
:check UP-TO-DATE
:build
:compileReproductionJava
:processReproductionResources UP-TO-DATE
:reproductionClasses
BUILD SUCCESSFUL in 4s
15 actionable tasks: 9 executed, 6 up-to-date
```

The Forge reproduction tests are separate from vanilla Gradle `test`; `NO-SOURCE`
is not represented as JUnit coverage. Both actual Forge launches and the separate
relational verifier are required for this audit. Log copies are archived in evidence/logs.

Production artifact remains `build/libs/reterraforged-forge-1.20.1-0.0.6.jar`,
1,064,698 bytes, SHA-256:

```text
FA950D83F886901ADF2B47223C45A6809F21F7AAB658C0F1128F721D9D9416C8
```

This is **byte-for-byte identical** to Task 0's recorded Forge jar. Neither production
jar nor sources jar contains baseline/reproduction, task1a observer manifests, or the
developer mod. `git diff <Task0> -- src/main src/generated gradle.properties` is empty.
No licensing notice, production Mixin, access transformer, dependency or default
configuration changed. Reproduction observers load only in the developer launch.

## Warnings and known limits

- Three for-removal warnings from deliberately observing Cell.isAbsent, plus use of
  deprecated inherited APIs in the harness. No unrelated warning cleanup performed.
- MixinGradle optional Eclipse APT plugin notice; Gradle 9 deprecation warning inherited
  from the build/toolchain. This project remains on Gradle 8.11.
- Forge language-provider jars report missing mods.toml; dev refmap warnings remain as
  in Task 0. Required production and observation Mixins launch without fatal injection
  failures; live queue/drop counters independently verify those hooks execute.
- Known development rendering/resource warnings: union asset scheme, optional missing
  sounds/shader sampler, Netty IPv6 boolean parsing, first-world Forge config defaults.
- Subsequent world/datapack loads can warn that `reterraforged:clay` block tag is not
  defined. This inherited warning remains; no tag/preset architecture was changed.
- Server "Can't keep up" warnings are expected while the test deliberately runs bulk
  sampling and hashing on the server callback. These timings are not normal gameplay
  throughput or Task 1C benchmark results.
- Expected missing-context NPEs, forced filter failure, and disabled vegetation builder
  failure are captured observations. They do not imply an unexpected harness crash.
- F12 FULL-chunk differences remain unattributed; F07 naturally racing saved-chunk
  corruption is not demonstrated. Exact mouth-endpoint fixture and command-level
  locate/spawn/structure outcome changes are not proven. No TB/modpack claims.

## Exact source/document changes

Modified:

- `build.gradle`: reproduction source set/client run, configurable test properties,
  test-only observer argument and fresh-completion marker gate only.

Added repository metadata:

- `.gitattributes`: disable newline conversion only for Task 1A evidence, preserving
  recorded byte hashes across checkouts. This does not alter production source handling.

Added Java (all under `src/reproduction/java/baseline/reproduction/`):

- `Evidence.java`
- `Metrics.java`
- `ReproductionClient.java`
- `ReproductionSuite.java`
- `observer/EntryObserver.java`
- `observer/TileCacheObserver.java`
- `observer/TileReleaseObserver.java`

Added developer resources:

- `src/reproduction/resources/META-INF/mods.toml`
- `src/reproduction/resources/pack.mcmeta`
- `src/reproduction/resources/task1a-observers.mixins.json`

Added scripts:

- `scripts/task1a/Verify-Reproductions.ps1`
- `scripts/task1a/Compare-ChunkSnapshots.ps1`
- `scripts/task1a/Write-EvidenceManifest.ps1`

Added documentation:

- `docs/task1/TASK_1A_DETERMINISM_AUDIT.md`
- `docs/task1/HARNESS.md`
- `docs/task1/VERIFICATION.md`
- `docs/task1/evidence/` files individually enumerated in `manifest.json`.

Removed/migrated production files: **none**. Task 1B fixes, Task 1C benchmarking,
geography extraction and all later architecture/integration work remain unstarted.
