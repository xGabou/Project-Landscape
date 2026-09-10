# Phase 2A completion report

Status: PASS. Phase 1 remains frozen at `b9e27263075ab8274c9206bb837e92c768c56a2e`. Phase 2B has not started.

Production commits: Landscape `d2ce7113c28671886b82bfc73c884f373526fd82`; PA bridge `919ab8ba`, bound sampler fix `1296044e9255f1097e9c76a73301d17ff040720c`. Later commits retain the harness, documentation and evidence. Obtain final delivery HEADs and chronological commits with `git log --reverse` from each frozen base; final HEADs are also provided in the delivery response.

## Completed gates

| Gate | Result |
|---|---|
| Versioned companion API | RuntimeClimateBridge API 1, detached baseline and separate runtime observation |
| Authoritative observation | Existing active PA regional server state, Overworld lease; no client weather inputs |
| Worker authority | Worker-thread service sample rejected; PA session also enforces server thread |
| Baseline into PA | Actual TemperatureGenerator accepted 22.330036861073662 C from retained (0,0); callback equality checked |
| PA runtime into Landscape | Real observations at ticks 660 and 1319, native region size 2000 |
| Static fallback | PA absent creation/reopen both passed; command explicitly reports baseline available and dynamic disabled |
| History | 4 observations / 600 covered ticks before save; restored 4, continued to 7 / 1000 ticks |
| Session gap | Reopened session added only 400 covered ticks from three observations; no cross-session interpolation |
| Persistence | Schema 1, bounded 4096 regions, four persisted calendar durations; strict identity/schema/size/calendar refusal |
| World isolation | Independent save UUIDs, stable reopen UUID, missing owner refusal; history mismatch rejects; old live level disposed |
| Dimension isolation | Actual Nether runtime absent; explicit Overworld gate; incompatible dimension history rejected |
| Unload | Both unloads unregister service, disconnect history, close PA lease, clear PA world/callback references, reject stale access |
| Runtime terrain acquisition | 100 sample calls plus active/missing diagnostics caused 0 generations in each session |
| Diagnostic | /geo runtimeclimate works for active and missing context/state and without PA |
| Representative determinism | Fixed north-west 2000-block grid corner only when already observed; other completion coordinates ignored |
| Phase 1 exactness | Geography digest, baseline fixtures/digests, initial biome fixtures/digests and six finalized terrain digests all exact |
| Focused checks | 31 passed; original history suite extended only for discovered representative/UUID/float-ceiling issues |
| Builds | Both final builds successful; Landscape jar contains neither PA classes nor reproduction harness |

## Interpreting baseline coverage

Startup in the measured world preceded capture of the fixed representative point, so natural startup initialization used PA's existing fallback. Later ordinary generation supplied the point. The harness then called PA's real TemperatureGenerator and verified its baseline-backed initialization counter increased; seasonal/daily/random variation and clamping stayed in PA. This probe did not replace actual live weather. The separately sampled runtime temperature was 11.54172134399414 C, demonstrating that baseline and runtime remain separate.

On reopen the representative store was empty while persisted PA state and climate history were available. The diagnostic reported absence and acquired no terrain. The raw initializer probe deliberately passed -12345 as a fallback sentinel; that number is neither measured weather nor a baseline value. No context persistence or broad acquisition was introduced. Fixed-coordinate selection removes parallel completion-order selection of values; availability and PA initialization timing still depend on activity, and neither is part of the frozen worldgen contract.

## Performance and memory

Two 100-call probes took 1.8321 ms and 0.3242 ms (18.321 and 3.242 microseconds/call). These are small live integration measurements, not a throughput/tail-latency benchmark or a PA simulation benchmark. At most four player-region candidates are examined per tick; successful observations are normally separated by 200 ticks. Existing inactive state can be retried within the four-candidate bound. No world/registry scan is added. Full PA startup terrain and forecast costs are separate.

Maximum retained history is approximately 40-45 MiB plus 2-3 MiB representative context under typical compressed-reference JVM layouts; about 10-11 KiB per fully bucket-populated history region. This is a source-based object-layout estimate, not a heap measurement. NBT serialization adds temporary allocations and sparse worlds use much less. See [RUNTIME_CLIMATE.md](RUNTIME_CLIMATE.md) for calculation assumptions and bucket bounds.

## Validation scope and test infrastructure

Acceptance used an actual Forge integrated server in `reproduction-1789052678232`, with real PA forecasts, atmospheric state and scheduler updates. No synthetic weather was injected. The separate dedicated-server executable and PA's full default startup forecast radius were not acceptance-tested.

The reproduction-only `PaForecastRadius` mixin bounds startup and per-region forecast setup sampling radii to 128 blocks. Production PA retains its default behavior and native 2000-block regions. Inspection of both final jars confirmed no reproduction classes, radius override or observer mixin configuration; Landscape also contains no bundled PA classes.

The development `phase2aPaJar` property stages the supplied PA jar as a SHA-256-addressed local module for ForgeGradle remapping. This is reproduction infrastructure; normal release dependencies remain unchanged.

Closure reused all successful validation. No production source changed, and no build, runtime integration, focused test or Phase 1 suite was rerun. Artifact hashes and source-tree identities were checked against retained evidence. The final targeted Phase 1 comparison covered Task 4 geography, Task 5 climate, Task 6 initial biome fixtures and six finalized terrain digests; it was not a full Task 6C rerun.

## Evidence and reproduction

- [PA_API_AUDIT.md](PA_API_AUDIT.md): authoritative source audit and integration findings.
- RUNTIME_CLIMATE.md: API, units, cadence, windows, persistence, lifecycle, memory estimate and limitations.
- evidence/completion_gate.json: machine-readable completion results.
- evidence/runtime/: final actual Forge runtime, save/reopen and disposal results.
- evidence/static/: explicit PA-absent results.
- evidence/history_checks.json: 31 focused checks.
- evidence/phase1_regression.json and evidence/terrain_determinism.json: frozen-reference comparison summary and terrain digests. Raw fixture copies remain local under evidence/task4, task5 and task6.
- evidence/saved-world/project_climate_world_id.txt: actual owner UUID. The 390-byte history file remains local; runtime JSON retains the save/reopen counts and coverage. No Minecraft save is committed.
- evidence/artifacts.json and evidence/build_summary.json: final jar hashes, packaging checks and successful build provenance.
- evidence/logs/: ignored local attempt/build logs and thread snapshots; not part of the committed review evidence. Original build/session files are preserved.
- PA worktree docs/phase2a/: companion API documentation, audit and final PA build evidence.

Run the existing reproduction with:

```powershell
.\gradlew.bat runReproductionClient -PreproProfile=task6-biomes -PreproSeeds=8675309 -PreproProcessors=8 -Pphase2aRuntime=true '-Pphase2aPaJar=G:/Project-Atmosphere-phase2a/build/libs/Forge-projectatmosphere-0.9.1.1-alpha.jar'
```

Omit phase2aPaJar for static fallback. `phase2aHistoryChecks` is the focused history/representative/identity check. Completed evidence should be reused; these commands are for future reproduction, not a request to rerun them.

## Limits and next phase

1. The bridge supports only the Overworld.
2. PA supports one live Overworld atmosphere owner per server process.
3. Baseline context exists only where ordinary generation already sampled the deterministic representative; it may be evicted and is not persisted.
4. Missing context uses PA's existing fallback initialization.
5. Baseline annual rainfall is not mapped to runtime humidity or precipitation.
6. Precipitation history is intensity ? ticks, not physical millimetres.
7. History is observational, with inactive-region and offline gaps.
8. No climate drift interpretation exists.
9. No biome or ecology changes occur.

One live PA Overworld owner; representative context can be missing/evicted and is not a mean or persisted context; only active player regions observed; no physical rain-unit conversion; frozen precipitation is only a wet-and-freezing proxy; game-calendar windows have bucket boundary uncertainty and require migration on duration change; history stops accepting new regions at capacity. Existing optional temperature integrations may retain their own PA initialization authority.

No anomaly detection, trends, ecological suitability, biome transitions, Dynamic Trees behavior, vegetation changes, surface conversion or player protection was added. Phase 2A changes no initial biome placement.

Recommend proceeding, in a separately authorized Phase 2B, to coverage-qualified long-term state and drift detection over these persisted game-calendar aggregates. Define minimum coverage and versioned statistical semantics before interpreting a trend; preserve missing coverage and precipitation index units. Keep ecology and biome transitions outside that work until separately specified. Stop here: Phase 2B is not implemented.
