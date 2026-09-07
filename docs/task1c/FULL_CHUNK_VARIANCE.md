# Deferred issue: downstream FULL-block variance

Comparator `cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee`; no production repair attempted. Task 1B observed 12/24 differing repeated FULL chunks. Task 1C's **different, stage-request protocol** observes 10/24. These counts are not evidence of improvement: explicit intermediate requests alter demand/order, and neither run isolates every neighbor or postprocessing operation.

## Focused experiment

Six fresh Forge worlds: seeds 8675309, 4303642605, 42, then those same three again. Eight fixed locations/world, matching the prior block-snapshot suite. Each location requests NOISE → SURFACE → CARVERS → FEATURES → FULL, saving complete block-state arrays at each return and recording **actual** status. No gamerule, decoration, structure, random source, or production scheduling change.

[full_chunk_variance.json](evidence/full_chunk_variance.json) has every differing pair, block-state transition counts, changed Y minima/maxima, 16-block Y-band counts and coordinate examples. Raw compressed snapshots and status records reside in `evidence/variance/`. Final exact RTF samples and stored biome hashes are also compared.

| Requested status | Pairs | Different | Both actual statuses equal requested | Differences among those isolated pairs |
|---|---:|---:|---:|---:|
| NOISE | 24 | 6 | 13 | 0 |
| SURFACE | 24 | 6 | 13 | 0 |
| CARVERS | 24 | 6 | 13 | 0 |
| FEATURES | 24 | 6 | 13 | 0 |
| FULL | 24 | 10 | 24 | 10 |

The six early differences belong to chunks **already advanced beyond the requested status**. They cannot establish NOISE/SURFACE/CARVERS nondeterminism. Thirteen pairs were observed at the actual requested earlier statuses and all matched through their own FEATURES stage. Four of those diverged between the FEATURES return and FULL return:

- Seed 8675309, chunk (212,−192): 4 changed fern/grass-to-air cells, Y=65.
- Seed 4303642605, chunk (−168,−256): 114 changed cells, Y=−27..41, including pointed dripstone/dripstone and surrounding rock.
- Seed 4303642605, chunk (−4,−256): 5 changed cells, Y=−13..−8, pointed dripstone/dripstone/rock.
- Seed 42, chunk (−168,−256): 344 changed cells, Y=−50..−1, dripstone/pointed dripstone/water/rock.

Other FULL differences include ores, seagrass, flowers and fluids as well as dripstone. **All 24 exact RTF sample pairs and stored biome hashes match.** Independent filtered tile scheduling digests also match. FULL block equality is therefore unsuitable as the golden geography invariant.

## Code trace and attribution limit

The local Forge-mapped 1.20.1 source archive was inspected (not guessed from block names):

- `ChunkStatus.FEATURES` primes heightmaps, constructs a `WorldGenRegion` with a one-chunk write radius, calls `ChunkGenerator.applyBiomeDecoration`, then generates border ticks.
- `INITIALIZE_LIGHT` follows FEATURES; LIGHT has neighboring dependencies; SPAWN and FULL follow. `ChunkStatus.STATUS_BY_RANGE` includes neighboring INITIALIZE_LIGHT, whose prerequisites include FEATURES. Requesting FULL may consequently run **neighboring** feature work after the target's own FEATURES snapshot.
- `ChunkMap.prepareTickingChunk` invokes `LevelChunk.postProcessGeneration`; that method can tick fluids and apply neighbor-shape block updates. A live spawn/active chunk can also have advanced before this harness requests it.

This establishes concrete downstream mechanisms that make a status-return snapshot weaker than an operation-level write trace. It does **not** prove which placed feature, neighbor, Forge ordering decision, fluid update, random tick, or scheduler event caused each changed block. No per-write feature identity was recorded. Dripstone-looking differences are not proof that the dripstone implementation is itself nondeterministic; interactions and ordering remain candidates. Structures were not isolated and are not declared innocent or guilty.

**Disposition:** retain a dedicated downstream variance issue. Earliest observed divergence for four previously matched pairs is after their own FEATURES return and by FULL; root cause remains unproven. A later focused investigation would instrument target-coordinate writes with feature/neighbor/postprocessing identity, outside timing runs. No broad scheduler or decoration rewrite is authorized here. The findings do not invalidate canonical RTF/tile geography measurements; total FULL throughput remains a workload timing, with its natural variance reported.

Reproduce with `runReproductionClient -PreproProfile=variance`, archive using `Collect-Run.ps1`, then `Compare-Variance.ps1 -Run <archive> -Output <new-report>`. Do not bless these block arrays as goldens or overwrite Task 1A/1B evidence.
