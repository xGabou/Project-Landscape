# Task 6C — completed scoped terrain optimization

Task 6B remains frozen at `39c597cb605c051ff0c0f484d65a50ec47120496`. Production last changed in `d82f149`. Task 6C is complete; the +25% legacy performance target remains unmet. Generation semantics are unchanged. No Phase 2 functionality was implemented.

## Decisions

- Retain exact smoothing (`a506198`): identical reads, accumulation/scan order and writes; 33,177,600 exact public-field comparisons. Small immutable kernel only.
- Retain [IslandModel hot front](ISLAND_MODEL.md) (`25e5b61`): 64 atomic slots plus bounded authoritative 256-entry synchronized LRU. Final eviction/front-retention, collision replacement, clear reconstruction and post-close rejection pass. No more IslandModel validation is needed.
- Retain [exact marine climate projection](MARINE_PROJECTION.md) (`d82f149`): only `!macro.land()` bypasses full terrain acquisition, using identical finalization arithmetic. 117,189 marine cells tested exactly; land continues through canonical filtered terrain.
- Reject [erosion-noise cross-sample cache](EROSION_NOISE.md). Isolated equality/reuse is promising, but graph purity, mutable cell state, interleaving, lifecycle and installed finalized-tile equivalence are unproven. Future work requires an explicit pure-noise graph contract and a dedicated task. Zero production cache/memory impact.

## Cause and performance

Old 400–640-tile bursts came from the union of many climate queries and their sparse 24 km upwind profiles, including FULL prerequisite chunks. The retained 463-tile burst had 463 distinct surface loads, not repeated loading of one tile. Marine projection removes 217 of those profile tile dependencies (46.87%) while preserving all coordinates and land dependencies. Each terrain workspace remains a 128x128 core plus 16-block halo, or 160x160; no stage recursively loads neighboring finalized tiles. [Dependency table](DEPENDENCIES.md).

| Scenario | Task 6B seconds | Task 6C seconds | Reduction |
|---|---:|---:|---:|
| sequential_generation | 10.911 | 9.347 | 14.33% |
| spawn_expansion | 8.069 | 6.675 | 17.27% |
| scattered_generation | 63.020 | 34.381 | 45.44% |
| interleaved_regions | 23.235 | 14.725 | 36.62% |
| climate_heavy | 5.247 | 4.485 | 14.52% |

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
