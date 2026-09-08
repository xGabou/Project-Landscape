# Task 2 performance comparison

The legacy hot path has no added hash, codec, resource lookup or new seed-service call.
Performance was nevertheless measured, not inferred from that fact.

## Protocol and investigation

All runs use the accepted Task 1C harness: Java 17.0.17+10, Forge 47.4.22,
Minecraft 1.20.1, 24 workers, 6 GiB heap, mapped integrated client/server, identical
seed/preset/coordinates, one warmup world followed by three measured worlds. Point tests
retain three warmups and five measured repetitions of 65,536 queries. Tile tests retain
one warmup and three measured repetitions across five coordinates. The original cache
observers remain enabled; Gradle elapsed time is not used as generation timing.

Hardware remains i7-13700K / 24 logical processors / approximately 64 GiB RAM,
Windows 11 build 26200, high-performance power plan. Live desktop load and JVM scheduling
are not controlled. See `evidence/machine.json` and each run's `environment.json`.

Three runs are preserved, not cherry-picked into a replacement golden:

| Run | Source | Purpose |
|---|---|---|
| `benchmark-final` | Task 2 foundation | Initial measurement; several >5% slowdowns flagged |
| `benchmark-reference-current` | Exact accepted Task 1C `d2164f25...` in a detached worktree | Contemporary source control |
| `benchmark-repeat-current` | Task 2 foundation again | Check whether the initial slowdown reproduces |

The initial run's raw throughput was about 6% below historical Task 1C; filtered tile
throughput was about 22% below. This was not accepted as harmless variance without further
measurement. The contemporary accepted-source run followed by the Task 2 repeat did not
reproduce a >5% median hot-path regression. Largest median time increase versus that
reference: **filtered tiles +3.37%**, with candidate repeat CV **7.63%**. No individual
background process, JIT effect or other specific cause of the initial slow run is proven.

Versus the historical numbers, repeated filtered-tile time is still **+7.03%** (and
sequential FULL workload +5.11%). Those differences are retained and reported, not erased.
The contemporary exact-source control is the evidence against attributing them to a
sustained Task 2 code regression. This is a live-client engineering comparison, not a
claim of statistically precise improvements. Future work must preserve this distinction.

## Measured Task 2 repeat

Percentiles are nearest-rank. Warm point medians of 100 ns reflect the timer's granularity;
batched throughput is more informative. FULL workloads include prerequisites and downstream
Minecraft work; they are not isolated RTF terrain costs.

| Operation | Median throughput | Median latency | p95 latency | Time delta vs contemporary accepted source |
|---|---:|---:|---:|---:|
| Raw Heightmap point | 379,213/s | 2.6 µs | 3.3 µs | −1.50% |
| Direct approximate point | 376,089/s | 2.6 µs | 3.2 µs | −1.16% |
| Canonical warm point | 6.595 million/s | 0.1 µs | 0.2 µs | +0.18% |
| Opportunistic cached point | 7.010 million/s | 0.1 µs | 0.2 µs | +1.68% |
| Warm tile lookup/copy | 6.624 million/s | 0.1 µs | 0.2 µs | +2.11% |
| Filtered tile | 36.596/s | 27.326 ms | 39.981 ms | +3.37% |
| Cold cache tile | 38.976/s | 25.657 ms | 27.867 ms | −5.23% |
| Cold exact point (tile generation) | 38.641/s | 25.879 ms | 38.912 ms | −5.20% |
| FULL cold exploration | 4.560 chunks/s | 121.294 ms | 743.658 ms | −0.26% |
| FULL sequential adjacent | 7.705 chunks/s | 58.590 ms | 612.522 ms | −1.48% |
| FULL scattered | 1.936 chunks/s | 513.700 ms | 623.331 ms | −5.48% |
| FULL warmed terrain | 3.397 chunks/s | 76.686 ms | 1,765.229 ms | −4.03% |

Negative deltas are observations, not optimization claims. The code does not intentionally
improve legacy terrain performance. Query warmup/outlier CVs are in the machine-readable
summaries; in particular some warm-query repeats contain large timing outliers.

## Allocations and cache memory

The separate 24-worker-plus-caller allocation probe measured **2,996,848; 2,996,880;
2,996,880 bytes per filtered tile**, versus Task 1C approximately 2,996,952–2,996,984.
This is unchanged within instrumentation overhead, not a memory optimization.

Initial Task 2 process heap peak sampled by the harness: 3,269,087,744 bytes. Maximum
sampled cache entries: 223; published-cell upper bound: 5,708,800, as in the reference
workload. Pool live-borrow counts converge to zero after teardown. All raw cache counters,
joins, timings and repeated memory samples remain in the benchmark evidence.

These heap values are process-wide observations, **not** dominator-retained tile/cache
or manifest bytes. Renderer and other Minecraft allocations are included in process-wide
counters. No exact retained-memory claim is made. New manifest data is initialized once;
no new records/seed hashes are allocated by the existing per-cell density hot path.

The reused summary helper discovers the sibling Task 2 allocation probe even when
summarizing the contemporary reference. That field is not a fresh reference allocation
measurement; use the original Task 1C allocation file for the allocation comparison.

## Disposition and future gates

Task 2's >5% investigation trigger was exercised. The paired contemporary comparison is
within that threshold and observed variation; there is no reproduced sustained hot-path
regression attributable to the foundation. Historical and initial deltas remain visible
in `evidence/performance_comparison.json` alongside all raw runs.

Future extraction still targets no more than ~10% throughput/allocation regression beyond
variance. Complete Phase 1 additions exceeding ~25% time/retained memory need explicit
review; ~2x generation time is a hard failure signal. These are future gates, not permission
to spend those budgets in Task 2. Metadata fingerprints and physical climate APIs must not
be moved into the legacy hot loop.
