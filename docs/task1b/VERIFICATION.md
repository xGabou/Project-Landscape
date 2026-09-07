# Task 1B verification workflow

Use Java 17 and the pinned ForgeGradle wrapper. Tests require the actual development
Forge client, registry/datapack bootstrap and integrated server. They are not mock
worldgen tests and are never included in the production jar. Run game tasks serially.

```powershell
.\gradlew.bat compileJava classes jar build compileReproductionJava reproductionClasses
.\gradlew.bat runReproductionClient --console=plain
$id = (Get-Content run/task1a/task1a-pass.txt).Trim()
.\scripts\task1b\Verify-Repairs.ps1 -Run "run/task1a/evidence/$id" -ThroughFix 11 -OutputDirectory build/task1b-verification
```

The Java harness captures observations, including intentionally retained defects.
**The PowerShell verifier is the correctness gate**, not the legacy-named PASS marker
alone. It requires repaired behavior and compares valid independently produced paths
to Task 1A. It never substitutes contaminated observations into golden expectations.
The default full profile now loads a fourth, custom-vegetation-disabled world after
the original three comparator worlds. The original 24 FULL snapshots remain separate.

Scheduling and real save/reopen smoke gates:

```powershell
.\gradlew.bat runReproductionClient -PreproProfile=concurrency -PreproProcessors=2
.\gradlew.bat runReproductionClient -PreproProfile=concurrency -PreproProcessors=48
.\gradlew.bat runSmokeClient
.\gradlew.bat runSmokeClient -PwithTerraBlender=true
.\gradlew.bat runSmokeClient -PsmokeVanilla=true
```

Each run creates its own timestamped evidence directory and fresh disposable worlds.
Archive each run before the next command replaces the completion marker. Do not run
against valuable saves. The Task 1A profile/seed/coordinate options remain available;
the relational verifier intentionally expects the accepted default fixture suite.

The optional vanilla smoke mode skips the RTF preset export/enable step and asserts
that all three dimensions have no RTF context, no ownership requirement and no preset,
with zero RTF structure rules. It generates seven FULL chunks, saves, reopens and
generates seven more. Default smoke behavior is unchanged; no production classes
or generation algorithms are changed by this test-only option.

Repeated finished chunks are compared with the unchanged diagnostic script:

```powershell
.\scripts\task1a\Compare-ChunkSnapshots.ps1 -RunA <full-run-A> -RunB <full-run-B> -OutputDirectory <comparison-dir>
```

`Compare-PresetPacks.ps1` compares every exported file byte-for-byte. `Compare-TileHalo.ps1`
records all changed cells and rejects changes inside the core. `Write-FinalReport.ps1`
indexes commit/file provenance, cumulative checks, final scheduling and smoke results,
and hashes the new evidence. The index's source SHA precedes the documentation-only
handoff commit; use Git history for that final documentation commit's own identity.

See `TASK_1B_CORRECTNESS_REPAIRS.md` for per-family before/after results and limits.
Performance counters remain instrumentation groundwork; Task 1C benchmarking has not begun.
