# Task 6B performance comparison

Status: baseline measurement in progress; no optimized performance claim.

The first complete acquisition run (`build/task6b-baseline-02`) used JDK
17.0.17+10, an 8-processor JVM and 6 GiB heap. It measured these cold-plus-warm
sequence totals, not FULL chunks:

| Sequence | Wall seconds | Process CPU seconds | Surface misses | Surface hits |
|---|---:|---:|---:|---:|
| Sequential | 18.002 | 51.203 | 228 | 1292 |
| Spawn expansion | 13.469 | 36.828 | 176 | 1344 |
| Scattered | 114.347 | 241.547 | 1520 | 0 |
| Four interleaved regions | 41.521 | 100.563 | 533 | 987 |
| Climate-heavy | 8.756 | 23.234 | 113 | 1407 |

Of 13,286 execution samples, 7,346 were classified as erosion/filtering, 5,101
as terrain noise, 761 as tile construction, 26 as climate geography sampling,
19 as surface extraction, 9 as cache lookup, and 1 as climate equations. The
remaining 23 were unclassified. Attribution uses the first recognized subsystem
from the leaf; these are sampled counts, not exact subsystem CPU durations.

The top stacks are `Modifier.modify → Erosion.erode → Erosion.applyDrop` and
`Erosion.applyDrop → Erosion.apply`, both under canonical tile finalization.

These measurements do not establish the 20% improvement gate, FULL-generation
overhead, or TPS impact. Paired repeats and final evidence remain required.
