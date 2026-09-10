# Task 6C final performance

Frozen Task 6B: `39c597cb605c051ff0c0f484d65a50ec47120496`. Final production: `d82f149`; subsequent commits change experiments, evidence and documentation only.

## Canonical acquisition

Same five scenarios, seed/configuration, eight processors, fresh world per scenario and cold plus warm passes. These are retained ordered runs, not randomized repeated confidence intervals. All raw climate outputs match exactly. Detailed cold/warm statistics and cache counts: [final_acquisition.json](evidence/final_acquisition.json).

| Scenario | Task 6B seconds | Task 6C seconds | Reduction |
|---|---:|---:|---:|
| sequential_generation | 10.911 | 9.347 | 14.33% |
| spawn_expansion | 8.069 | 6.675 | 17.27% |
| scattered_generation | 63.020 | 34.381 | 45.44% |
| interleaved_regions | 23.235 | 14.725 | 36.62% |
| climate_heavy | 5.247 | 4.485 | 14.52% |

Incremental reductions in scenario order: smoothing 2.64/2.80/1.67/2.05/2.41%; IslandModel 2.40/1.88/7.02/4.04/-0.48%; marine 9.85/13.25/40.33/32.58/12.83%. Smoothing local time was 44.1 to 17.7 ms and 47.1 to 17.8 ms; 33,177,600 exact comparisons. IslandModel diagnostic summed monitor wait fell from 76.207 to 0.020 seconds, protected work from 2.446 to 0.003 seconds. These overlapping thread durations are not wall time.

## FULL generation

Original 12 and extended 14 corpora are separate. Final 12 is the first twelve ordered requests of the final fourteen-point run. The Task 6B extended run's twelve-point subset is also shown to expose between-run variance. Timings bracket new FULL requests, including prerequisite work; counter deltas also include output hashing. JFR enabled; detailed production timers disabled. No intermediate smoothing-only or IslandModel-only FULL runs were executed.

| Run | Corpus | Version | Total s | Median s | p95 s | Min s | Max s | Sample SD s | Chunks/s | Terrain tiles |
|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Task6B original | 12 | V1 | 112.883 | 1.266 | 29.111 | 0.101 | 29.111 | 11.490 | 0.106 | 2709 |
| Task6B original | 12 | legacy | 8.166 | 0.581 | 2.057 | 0.070 | 2.057 | 0.667 | 1.470 | 112 |
| Task6B extended | 12 | V1 | 146.423 | 1.509 | 35.445 | 0.107 | 35.445 | 14.973 | 0.082 | 2708 |
| Task6B extended | 12 | legacy | 8.669 | 0.632 | 2.106 | 0.072 | 2.106 | 0.689 | 1.384 | 112 |
| Task6B extended | 14 | V1 | 169.834 | 1.663 | 35.445 | 0.107 | 35.445 | 14.396 | 0.082 | 3192 |
| Task6B extended | 14 | legacy | 10.462 | 0.683 | 2.106 | 0.072 | 2.106 | 0.637 | 1.338 | 133 |
| final Task6C | 12 | V1 | 78.842 | 1.385 | 25.286 | 0.091 | 25.286 | 8.529 | 0.152 | 1620 |
| final Task6C | 12 | legacy | 8.292 | 0.618 | 2.015 | 0.069 | 2.015 | 0.655 | 1.447 | 112 |
| final Task6C | 14 | V1 | 89.437 | 1.516 | 25.286 | 0.091 | 25.286 | 8.051 | 0.157 | 1856 |
| final Task6C | 14 | legacy | 9.974 | 0.669 | 2.015 | 0.069 | 2.015 | 0.605 | 1.404 | 133 |

Original twelve: total time improves 30.16%, although median rises from 1.266 to 1.385 s. Comparing the extended runs' twelve-point subsets gives 46.15%; fourteen gives 47.34%. Cold tails remain material: final p95/max 25.286 s. The small corpus uses nearest-rank p95, which equals maximum at both sizes. Standard deviation is sample SD.

Final V1 overhead against contemporary legacy is +850.79% for twelve and +796.69% for fourteen (9.508x and 8.967x). The approximately +25% target was not reached. Different generation versions intentionally generate different terrain; this is a workload comparison, not identical terrain.

Already-loaded retrieval, fourteen points: Task 6B total 5.4 microseconds, median 0.4, p95 0.6, min 0.3, max 0.6, sample SD 0.0864; Task 6C total 5.1 microseconds, median 0.3, p95 0.6, min 0.3, max 0.6, sample SD 0.0929. Nominal throughput 2.593M/2.745M requests/s is timer-scale diagnostic data, not a sustained throughput claim. [Retrieval evidence](evidence/final_loaded_retrieval.json).

## Remaining cold acquisition

| X / Z | Samples | Unique coordinates | Owning tiles | Marine | Land | Coastal | Surface hits / misses | Generated terrain tiles | Diagnostic s |
|---|---:|---:|---:|---:|---:|---:|---|---:|---:|
| 7152 / 7264 | 65274 | 65274 | 463 | 31913 | 33361 | 11711 | 33005 / 246 | 253 | 12.406 |
| 300000 / 160000 | 76190 | 75541 | 500 | 68068 | 8122 | 18902 | 8029 / 78 | 84 | 4.348 |
| -310000 / -170000 | 1409 | 1409 | 16 | 1409 | 0 | 0 | 0 / 0 | 16 | 0.513 |

Coastal counts overlap marine/land and mean absolute macro shoreline profile <=384 blocks. Traces are untruncated and separate from timed corpus; wall time includes diagnostics/flush. Profile events are cache-dependent, so the all-marine remote trace records only 1,409 samples. Terrain generations also serve FULL prerequisites: zero surface misses does not mean zero chunk terrain generation.

The first trace repeats the old 65,274-coordinate, 463-owning-tile profile: 31,913 marine samples bypass terrain and 33,361 land samples retain it. 217 profile tiles (46.87%) become avoidable; surface misses fall from 463 to 246. The second requires 78 surface loads across 500 profile tiles; the third needs none. No climate distance or terrain halo changed. [Authoritative cold analysis](evidence/cold_query_analysis.json).

## Retained JFR comparison

| JFR observation | Task 6B extended V1 | Final Task 6C V1 |
|---|---:|---:|
| terrain noise samples | 8854 | 7049 |
| erosion/filtering samples | 9861 | 3977 |
| surface-field extraction samples | 41 | 10 |
| climate geography sampling samples | 11 | 14 |
| cache lookup samples | 82 | 42 |
| IslandModel summed monitor seconds | 1.458432 | 0.000000 |
| ExactCache summed monitor seconds | 0.051980 | 0.018569 |
| ExactCache wait without inner terrain frame: summed parked seconds | 87.399 | 51.343 |
| other: summed parked seconds | 5105.614 | 1967.325 |
| terrain wait inside ExactCache loader: summed parked seconds | 200.133 | 95.104 |
| terrain wait outside ExactCache: summed parked seconds | 6.801 | 4.873 |
| GC pause seconds | 1.746 | 1.008 |

No IslandModel contention was recorded above threshold in the final profile. Terrain noise plus erosion/filtering account for about 83% of final execution samples. Hydraulic erosion and smoothing share the existing filter classifier; separate CPU totals cannot be inferred. Top stacks show actual erosion-noise/Perlin/warp generation. Surface extraction and climate geography themselves are small; terrain waits inside surface/ExactCache loaders remain substantial. Other parking includes idle executor/runtime threads and is not CPU work. GC is present but does not explain the legacy gap.

These recordings have unequal workloads: hashing and quart probes are included, and final JFR includes three extra cold traces. Sample counts are not matched CPU-time deltas. Monitor/parking durations overlap across threads; absent events mean none above threshold. Original and extended Task 6B summaries, exact paths and final details are retained in [JFR comparison](evidence/final_jfr_comparison.json) and [final profile summary](evidence/final_jfr_v1.json); raw recordings remain at paths in [profile references](evidence/final-runtime/task6b_profiles.json).

Required land terrain generation, noise, hydraulic erosion and RTF workspace architecture remain the dominant cost. A separate architectural terrain task is justified if +25% remains mandatory; further Task 6C micro-optimization is not justified.
