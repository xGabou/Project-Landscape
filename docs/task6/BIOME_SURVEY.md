# Task 6 biome survey — checkpoint, not acceptance

The current evidence separates controlled fixtures, macro-elevation proxies and canonical filtered
terrain. Results from one scope must not be substituted for another.

## Executed proxy survey

`evidence/checkpoint-macro` contains the 16 fixed seeds, a 65,536-block square sampled every
4,096 blocks (4,624 total samples), plus 432 samples extending to Z = +/-2,000,000.
The broad square produces 1,702 deep ocean, 618 deep lukewarm ocean, 67 ocean, 67 lukewarm ocean,
1,441 plains, 699 forest, 26 birch forest, 2 savanna and 2 dark forest samples.
No desert or jungle winner occurs in this proxy window. This is an outstanding reachability concern,
not evidence that those descriptors should automatically be loosened.

Extended latitude reaches old-growth taigas, snowy taiga, ice spikes and frozen ocean families.
These results demonstrate cold-family selection, but use macro-proxy elevations and do not validate
real mountain zoning. The collision pair has different sampled winner digests.

## Controlled checks

Forty-four executed semantic/projection checks pass. The actual Task 5 Okanagan fixture gives
401.783 model mm/year leeward versus 937.481 in the barrier-removed control (42.86%). The wet-side
sample selects forest; the leeward sample selects savanna. These are measured fixture outputs,
not fixture-specific production rules.

All 42 catalog descriptors win somewhere in the controlled climate/geography search. Mushroom fields
are intentionally excluded. The search also encounters 10,640 uncovered combinations, including
climates outside the default model's generated domain. Those gaps are reported rather than assigned
a hidden plains fallback. Generated-state coverage still needs validation.

Temperature, moisture and elevation transects are exported separately. The elevation fixture uses
Task 5's actual temperature projection at fixed Z = 60,000 with constant coast distance; it does
not invent a second lapse rate. Synthetic elevations up to 2,000 blocks are model tests, not a claim
that ordinary Minecraft terrain reaches that height.

## Canonical patch survey

`CanonicalBiomeSurvey` is a separate Forge-context harness using actual filtered terrain and exact
climate profiles. It samples a 17-by-17 grid at 64-block spacing, centered on macro site (0,0) for
each fixed seed and snapped to the world lattice. Site choice is independent of winning biome.
It records connected patch areas, bounds, boundary censorship, adjacent biome changes, isolated
interior cells and winner margins. Execution/results are still pending at this checkpoint.

This 1,024-block patch workload supplements, rather than replaces, the broad proxy survey. It alone
cannot establish continent-wide diversity, quart-scale checkerboard absence, or all real orographic
transitions. Those remain completion gates.
