# IslandModel production decision

Retain the bounded read front, with front-hit accounting enabled only by
`task6c.metrics`. Smoothing is independently accepted in `a506198`.

The original candidate's always-on LongAdder regressed all five acquisition
scenarios by 8.24–23.15%. Its source and results are retained in
`evidence/island-candidate-01-source` and `evidence/island_acquisition.json`.
Removing that shared hot-read write improved sequential/spawn/scattered/interleaved
by 2.40/1.88/7.02/4.04% versus smoothing alone; climate-heavy regressed 0.48%.
These are small fixed-corpus measurements, not statistical confidence claims.
All seven climate doubles match raw bits in every scenario.

The original access-order LinkedHashMap modifies its LRU list on each get.
A 64-slot AtomicReferenceArray now holds immutable `(site ID, island list)` entries.
Direct-mapped collisions replace a slot. Misses enter the existing instance monitor,
recheck the front, and read/construct through the 256-entry backing LRU. There is
no static world state, unbounded map, pending map, or new ThreadLocal.
Front hits intentionally do not refresh backing access order. Eviction changes
retention only: construction is deterministic and lists/Island records immutable.
An immutable list retained by the front remains correct after backing eviction.

Scattered diagnostic stage deltas: summed monitor wait 76.2073045 s → 0.0200402 s;
protected work 2.4455705 s → 0.0032251 s. The older ~90 s figure included previous
scenarios. These nested, instrumented sums are not elapsed wall time. Original
baseline has no separate compute/insert/eviction counters, so those before-values
are unavailable. Candidate scattered compute 0.00125 s, insertion 0.000045 s,
evictions zero. Candidate acquisition JFR recorded no IslandModel monitor events
at the 1 ms threshold; this does not mean every lock acquisition takes zero time.

Exact macro cold/warm/reverse/reconstruction/1/2/8 digest remains
`c3930838fa0f75d1541c097838f00dfce5707e69a6f6dd12b2ca7b7e0dfdfee6`.
This covers every MacroSample field, including site/cluster/corridor identity and
classification. Six finalized tile digests match frozen Task 6B exactly.
The production isolated experiment compares immutable island values over 4096
cold sites plus retained profile-site replay, hot and mixed workloads, at
1/8/24/48 workers. Hot median ms per 100,000 accesses: reference
32.003/14.2462/13.7431/12.5973; production 32.2123/1.503/1.0696/0.9678.
No measured candidate hot monitor blocking. One-worker timings include warmup/JIT
effects and show no improvement; these are not whole-worldgen speedups.

Memory: 64 atomic references plus at most 64 HotEntry objects. The backing LRU
holds at most 256 lists. Front/backing overlap is counted once; the conservative
union bound is 320 lists. Construction permits at most 1 coastal + 4 oceanic + 7
archipelago islands per list, before rejection: at most 3840 Island records in
the union, of which at most 768 (64 lists) are additional retention. With typical
compressed references, a 12-island list is approximately 664 bytes including its
Island objects; extra payload ≤42,496 bytes, front entries about 1536 bytes and
atomic array/wrapper about 288 bytes, roughly 0.043 MiB plus small counters/owner
fields. This is a layout estimate, not an allocation measurement. Disabled
LongAdder telemetry has no striped cells; enabled diagnostic stripes depend on
JVM processor count and are excluded from release memory estimates.

Context disposal registers `islands()::close`: close marks disposed and clears both
caches. The ownership test retains the context and observes front/backing entries
zero and post-close queries rejected. Queries already in flight may return an
immutable value acquired before close, but cannot repopulate after close. Clear
does not close the model. Diagnostic counters are cumulative, not cached payload.

Evidence: `evidence/island-validation`, `evidence/island_acquisition_02.json`,
`evidence/island_diagnostics.json`. ReferenceIslandModel is reproduction-only and
is excluded from production artifacts. Final FULL and final all-worker gates are
still pending; this decision is not Task 6C completion.
