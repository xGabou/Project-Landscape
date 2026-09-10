"""Render final reports from authoritative retained summaries."""
import json,pathlib
r=pathlib.Path(__file__).resolve().parents[2];d=r/'docs/task6c';e=d/'evidence'
def read(n): return json.loads((e/n).read_text())
a=read('final_acquisition.json');f=read('final_full_performance.json')['rows']
acq='| Scenario | Task 6B seconds | Task 6C seconds | Reduction |\n|---|---:|---:|---:|\n'
for i in range(0,len(a),4):
    b,c=a[i],a[i+3];x,y=b['allQueries']['totalSeconds'],c['allQueries']['totalSeconds']
    acq+=f"| {b['scenario']} | {x:.3f} | {y:.3f} | {100*(1-y/x):.2f}% |\n"
full='| Run | Corpus | Version | Total s | Median s | p95 s | Min s | Max s | Sample SD s | Chunks/s | Terrain tiles |\n|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|\n'
for x in f:
    full+=f"| {x['run']} | {x['corpus']} | {x['version']} | "+' | '.join(f"{x[k]:.3f}" for k in ['totalSeconds','medianSeconds','p95Seconds','minimumSeconds','maximumSeconds','sampleStandardDeviationSeconds','throughputChunksPerSecond'])+f" | {x['terrainTilesGenerated']} |\n"
cold='| X / Z | Samples | Unique coordinates | Owning tiles | Marine | Land | Coastal | Surface hits / misses | Generated terrain tiles | Diagnostic s |\n|---|---:|---:|---:|---:|---:|---:|---|---:|---:|\n'
for x in read('cold_query_analysis.json')['rows']:
    q=x['query'];cold+=f"| {q['blockX']} / {q['blockZ']} | {x['profileSamples']} | {x['uniqueProfileCoordinates']} | {x['uniqueOwningTiles']} | {x['eligibleMarineSamples']} | {x['landSamples']} | {x['coastalWithin384Overlapping']} | {x['surfaceHits']} / {x['surfaceMisses']} | {x['terrainTilesGenerated']} | {q['diagnosticWallNanos']/1e9:.3f} |\n"
j=read('final_jfr_comparison.json')['rows'];jtable='| JFR observation | Task 6B extended V1 | Final Task 6C V1 |\n|---|---:|---:|\n'
for k in ['terrain noise','erosion/filtering','surface-field extraction','climate geography sampling','cache lookup']:
    jtable+=f"| {k} samples | {j[1]['subsystemSamples'].get(k,0)} | {j[2]['subsystemSamples'].get(k,0)} |\n"
for label,key in [('IslandModel','com.gabou.atmospheregen.geography.continent.IslandModel'),('ExactCache','com.gabou.atmospheregen.generation.cache.ExactCache')]:
    jtable+=f"| {label} summed monitor seconds | {j[1]['monitorBlockedThreadNanos'].get(key,0)/1e9:.6f} | {j[2]['monitorBlockedThreadNanos'].get(key,0)/1e9:.6f} |\n"
for k in sorted(set(j[1]['parkPathBlockedThreadNanos'])|set(j[2]['parkPathBlockedThreadNanos'])):
    jtable+=f"| {k}: summed parked seconds | {j[1]['parkPathBlockedThreadNanos'].get(k,0)/1e9:.3f} | {j[2]['parkPathBlockedThreadNanos'].get(k,0)/1e9:.3f} |\n"
jtable+=f"| GC pause seconds | {j[1]['gcSumOfPausesNanos']/1e9:.3f} | {j[2]['gcSumOfPausesNanos']/1e9:.3f} |\n"
(d/'PERFORMANCE_COMPARISON.md').write_text('''# Task 6C final performance

Frozen Task 6B: `39c597cb605c051ff0c0f484d65a50ec47120496`. Final production: `d82f149`; subsequent commits change experiments, evidence and documentation only.

## Canonical acquisition

Same five scenarios, seed/configuration, eight processors, fresh world per scenario and cold plus warm passes. These are retained ordered runs, not randomized repeated confidence intervals. All raw climate outputs match exactly. Detailed cold/warm statistics and cache counts: [final_acquisition.json](evidence/final_acquisition.json).

'''+acq+'''
Incremental reductions in scenario order: smoothing 2.64/2.80/1.67/2.05/2.41%; IslandModel 2.40/1.88/7.02/4.04/-0.48%; marine 9.85/13.25/40.33/32.58/12.83%. Smoothing local time was 44.1 to 17.7 ms and 47.1 to 17.8 ms; 33,177,600 exact comparisons. IslandModel diagnostic summed monitor wait fell from 76.207 to 0.020 seconds, protected work from 2.446 to 0.003 seconds. These overlapping thread durations are not wall time.

## FULL generation

Original 12 and extended 14 corpora are separate. Final 12 is the first twelve ordered requests of the final fourteen-point run. The Task 6B extended run's twelve-point subset is also shown to expose between-run variance. Timings bracket new FULL requests, including prerequisite work; counter deltas also include output hashing. JFR enabled; detailed production timers disabled. No intermediate smoothing-only or IslandModel-only FULL runs were executed.

'''+full+'''
Original twelve: total time improves 30.16%, although median rises from 1.266 to 1.385 s. Comparing the extended runs' twelve-point subsets gives 46.15%; fourteen gives 47.34%. Cold tails remain material: final p95/max 25.286 s. The small corpus uses nearest-rank p95, which equals maximum at both sizes. Standard deviation is sample SD.

Final V1 overhead against contemporary legacy is +850.79% for twelve and +796.69% for fourteen (9.508x and 8.967x). The approximately +25% target was not reached. Different generation versions intentionally generate different terrain; this is a workload comparison, not identical terrain.

Already-loaded retrieval, fourteen points: Task 6B total 5.4 microseconds, median 0.4, p95 0.6, min 0.3, max 0.6, sample SD 0.0864; Task 6C total 5.1 microseconds, median 0.3, p95 0.6, min 0.3, max 0.6, sample SD 0.0929. Nominal throughput 2.593M/2.745M requests/s is timer-scale diagnostic data, not a sustained throughput claim. [Retrieval evidence](evidence/final_loaded_retrieval.json).

## Remaining cold acquisition

'''+cold+'''
Coastal counts overlap marine/land and mean absolute macro shoreline profile <=384 blocks. Traces are untruncated and separate from timed corpus; wall time includes diagnostics/flush. Profile events are cache-dependent, so the all-marine remote trace records only 1,409 samples. Terrain generations also serve FULL prerequisites: zero surface misses does not mean zero chunk terrain generation.

The first trace repeats the old 65,274-coordinate, 463-owning-tile profile: 31,913 marine samples bypass terrain and 33,361 land samples retain it. 217 profile tiles (46.87%) become avoidable; surface misses fall from 463 to 246. The second requires 78 surface loads across 500 profile tiles; the third needs none. No climate distance or terrain halo changed. [Authoritative cold analysis](evidence/cold_query_analysis.json).

## Retained JFR comparison

'''+jtable+'''
No IslandModel contention was recorded above threshold in the final profile. Terrain noise plus erosion/filtering account for about 83% of final execution samples. Hydraulic erosion and smoothing share the existing filter classifier; separate CPU totals cannot be inferred. Top stacks show actual erosion-noise/Perlin/warp generation. Surface extraction and climate geography themselves are small; terrain waits inside surface/ExactCache loaders remain substantial. Other parking includes idle executor/runtime threads and is not CPU work. GC is present but does not explain the legacy gap.

These recordings have unequal workloads: hashing and quart probes are included, and final JFR includes three extra cold traces. Sample counts are not matched CPU-time deltas. Monitor/parking durations overlap across threads; absent events mean none above threshold. Original and extended Task 6B summaries, exact paths and final details are retained in [JFR comparison](evidence/final_jfr_comparison.json) and [final profile summary](evidence/final_jfr_v1.json); raw recordings remain at paths in [profile references](evidence/final-runtime/task6b_profiles.json).

Required land terrain generation, noise, hydraulic erosion and RTF workspace architecture remain the dominant cost. A separate architectural terrain task is justified if +25% remains mandatory; further Task 6C micro-optimization is not justified.
''',encoding='utf-8')
(d/'TASK_6C_TERRAIN_OPTIMIZATION.md').write_text('''# Task 6C — completed scoped terrain optimization

Task 6B remains frozen at `39c597cb605c051ff0c0f484d65a50ec47120496`. Production last changed in `d82f149`. Task 6C is complete; the +25% legacy performance target remains unmet. Generation semantics are unchanged. No Phase 2 functionality was implemented.

## Decisions

- Retain exact smoothing (`a506198`): identical reads, accumulation/scan order and writes; 33,177,600 exact public-field comparisons. Small immutable kernel only.
- Retain [IslandModel hot front](ISLAND_MODEL.md) (`25e5b61`): 64 atomic slots plus bounded authoritative 256-entry synchronized LRU. Final eviction/front-retention, collision replacement, clear reconstruction and post-close rejection pass. No more IslandModel validation is needed.
- Retain [exact marine climate projection](MARINE_PROJECTION.md) (`d82f149`): only `!macro.land()` bypasses full terrain acquisition, using identical finalization arithmetic. 117,189 marine cells tested exactly; land continues through canonical filtered terrain.
- Reject [erosion-noise cross-sample cache](EROSION_NOISE.md). Isolated equality/reuse is promising, but graph purity, mutable cell state, interleaving, lifecycle and installed finalized-tile equivalence are unproven. Future work requires an explicit pure-noise graph contract and a dedicated task. Zero production cache/memory impact.

## Cause and performance

Old 400–640-tile bursts came from the union of many climate queries and their sparse 24 km upwind profiles, including FULL prerequisite chunks. The retained 463-tile burst had 463 distinct surface loads, not repeated loading of one tile. Marine projection removes 217 of those profile tile dependencies (46.87%) while preserving all coordinates and land dependencies. Each terrain workspace remains a 128x128 core plus 16-block halo, or 160x160; no stage recursively loads neighboring finalized tiles. [Dependency table](DEPENDENCIES.md).

'''+acq+'''
Final FULL: original twelve Task 6B 112.883 s, Task 6C 78.842 s, contemporary legacy 8.292 s. Extended fourteen Task 6B 169.834 s, Task 6C 89.437 s, contemporary legacy 9.974 s. Final overhead +850.79%/+796.69%; target not reached. JFR records zero IslandModel monitor events, with terrain noise and erosion/filtering dominant. All distributions, retrieval timings, caveats and cold traces are in [performance comparison](PERFORMANCE_COMPARISON.md).

## Final validation

| Gate | Result |
|---|---|
| Requested processors 1/2/8/24/48 | PASS; actual terrain workers 2/2/8/24/48; frozen finalized digests exact |
| Task 4 | Exact `c3930838fa0f75d1541c097838f00dfce5707e69a6f6dd12b2ca7b7e0dfdfee6` |
| Task 5 / Task 6 | Accepted frozen climate / biome fixtures exact |
| FULL semantics | Canonical geography, finalized tile digests, raw climate bits, surface winners, stored biome digests and captured macro shoreline exact |
| Block digests | Diagnostic under unchanged Task 6B feature-order variance policy |
| Task 6B ownership / caches | One geography provider, climate provider, surface cache and distance field; ExactCache single flight, boundedness, isolation and disposal pass |
| Source semantics | 20 direct Holder identity checks, 9 cave delegation checks; surface winner reuse preserved |
| Task 1B | 64/64 against final production |
| Legacy | 595/595 geography, 595/595 hints, 85/85 keys, 420/420 standalone tiles, 105/105 TerraBlender tiles; seed collision preserved |
| Legacy reuse scope | Current standalone offline and TerraBlender runtime; unaffected 2/48/repeat legacy scheduling suites retained from Task 6B; no invented offline Holder observations |
| TerraBlender / save-reopen | PASS; source codec and biome digest preserved across reopen |
| Runtime disposal | Empty bounded caches/fronts and post-close rejection; actual unload retained |
| Final build | PASS, existing Gradle deprecation warnings only |

Completed expensive gates were reused. The sole remaining IslandModel eviction check ran once; no production edits followed final production gates. A JFR-summary invocation initially failed from PowerShell argument parsing, then succeeded with the property quoted; this did not rerun generation.

Task 6B detached cache estimate remains 130.8011 MiB including 128 MiB surface primitive arrays. IslandModel adds approximately 0.043 MiB plus small counter/object overhead: 64 references, at most 64 HotEntry objects and at most 64 extra immutable-list retentions. Smoothing adds small immutable kernel data; marine adds no cache/retained terrain field. Approximate combined detached cache estimate 130.8441 MiB, not total JVM retained heap. No heap profiler was added.

## Evidence and closure

Authoritative summaries: [gates](evidence/final_gate_results.json), [determinism](evidence/final_determinism.json), [Task 4](evidence/final_macro.json), [Task 5/6](evidence/final_offline_regressions.json), [FULL performance](evidence/final_full_performance.json), [FULL exactness](evidence/final_full_exactness.json), [legacy comparator](evidence/final_legacy_comparator.json), [Task 1B](evidence/final_task1b.json), [runtime disposal](evidence/final_runtime_disposal.json), [eviction](evidence/final_island_eviction.json), [cold queries](evidence/cold_query_analysis.json), [JFR](evidence/final_jfr_comparison.json), [build](evidence/final-build.log). Original valid evidence and unrelated main-worktree changes are preserved.

Chronological original Task 6C commits: `4392aa8`, `a506198`, `25e5b61`, `d82f149`, `03a1c54`. Final evidence, regression and documentation commits follow without squash. Obtain the complete final list with `git log --reverse --oneline 39c597c..task6c-terrain`; final HEAD and clean status are reported after the last commit to avoid self-reference.

Phase 1 correctness and scoped Task 6C gates can close. Overall Phase 1 performance acceptance requires explicitly deferring the unmet +25% target; otherwise a separate architectural terrain task is justified. Phase 2 has not begun and is not authorized by this completion. No PA runtime climate, climate history/drift, dynamic biome transitions, Dynamic Trees, mortality, succession or player protection was implemented. Stop after Task 6C.
''',encoding='utf-8')
