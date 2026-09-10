# Task 3 extraction performance

The extraction passes the approximately 10% time/allocation budget against accepted Task 2
`203dc3c1c2e1d4e09fa911a3d6dc7472664f4ba7`. No production optimization or algorithm change
was made during the resumed performance investigation.

## Protocol and retained investigation

The frozen Task 1C point/tile/FULL harness runs unchanged in live Forge clients. Candidate
`benchmark-resumed/` is compared with the fresh accepted-source `benchmark-task2-reference-stable/`.
Both use the same preset, fixtures, JVM, worker count, observer instrumentation and machine.
The reference runner rejects modified production source/config. Only developer harness additions
are allowed there. Runs are sequential, not concurrent benchmark processes.

The initial candidate (`benchmark/`) exceeded the investigation threshold, including approximately
30% warm-query time against the historical Task 2 run. Its complete report is retained in
`initial_performance_comparison.json`. The earlier control (`benchmark-task2-reference/`) and all
raw runs remain available. The resumed candidate and a fresh control did not reproduce that
sustained slowdown. This does not identify a specific cause for the first run or claim statistically
precise improvements. No result was discarded or golden data changed to obtain acceptance.

The public immutable provider is measured separately from internal Cell-copy queries. Its original
three-warmup/five-measurement protocol was still changing substantially within the measured portion.
Both versions therefore use the identical supplementary `protocol.java` archived beside their data:
20 discarded warmup batches, then 10 measured batches of 262,144 queries. Results escape through a
volatile field. Per-query timing overhead is included. Both protocol files have identical SHA-256.

## Fresh paired results

Time deltas use reference median throughput / candidate median throughput minus one. Negative
values are observations, not optimization claims. Percentiles are nearest-rank; the nanosecond
timer granularity makes batched throughput more useful than 100 ns point medians.

| Operation | Candidate throughput | Median / p95 | Time delta |
|---|---:|---:|---:|
| Raw point | 367,005/s | 2.7 / 3.4 us | -0.46% |
| Direct approximation | 373,213/s | 2.7 / 3.4 us | -1.61% |
| Internal canonical warm point | 6.073 million/s | 0.1 / 0.2 us | +0.30% |
| Opportunistic cached point | 6.564 million/s | 0.1 / 0.2 us | -7.46% |
| Warm tile lookup/copy | 6.121 million/s | 0.1 / 0.2 us | -11.36% |
| Filtered tile | 36.309/s | 27.541 / 28.462 ms | -0.87% |
| Cold cached tile | 37.668/s | 26.548 / 28.548 ms | -1.38% |
| Cold canonical point, including tile generation | 37.809/s | 26.449 / 29.345 ms | +0.27% |
| FULL cold exploration | 4.446 chunks/s | 119.943 / 724.474 ms | -2.42% |
| FULL sequential adjacent | 7.301 chunks/s | 60.873 / 645.757 ms | +2.37% |
| FULL scattered | 1.840 chunks/s | 521.697 / 672.150 ms | -4.56% |
| FULL warmed neighboring terrain | 3.442 chunks/s | 77.288 / 1,758.589 ms | -1.45% |

FULL includes Minecraft prerequisites and downstream generation, not isolated geography.
Repeat CVs, raw distributions, counters and process memory observations are preserved in the JSON
summaries. The largest positive isolated time delta is +0.30%; FULL's largest is +2.37%.

Public immutable samples: **4.123 million/s** candidate versus **4.068 million/s** accepted Task 2,
or **-1.33%** time. After the longer warmup, both allocate **464 bytes/query**. The earlier short
reference's 576 bytes/query is retained and is not used to claim an allocation improvement.

## Allocation and limitations

The separate warmed 24-worker-plus-caller probe measured a median **3,201,752 bytes/tile**,
against **2,996,880** in accepted Task 2: **+6.84%**, within budget. The three private mountain
floats increase published Cell layout by eight bytes on this JVM, or 204,800 bytes per 25,600-cell
tile. Warm workspaces are pooled; this is not the same metric as first-generation memory cost.
No per-cell GeoSample, mountain wrapper, registry lookup or additional noise evaluation was added.

Whole-process heap and allocation counters include other Minecraft activity. They are not exact
retained-cache measurements. The reused summary helper's sibling candidate allocation probe is
explicitly removed from reference summaries; the comparison uses the named accepted Task 2 probe.

Reproduce the aggregate with `scripts/task3/Resolve-Performance.ps1`. The report and raw evidence
are under `docs/task3/evidence/`; status is `PASS_PAIRED_REFERENCE`.
