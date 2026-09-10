# Task 4 geography survey

Status: final macro survey passed; combined Task 4 acceptance evidence is captured.

The offline harness calls `PaMacroGeography`, the production V1 macro provider. It does not generate
eroded tiles or Minecraft chunks. Canonical filtered terrain is independently tested in Forge.
Run `gradlew task4Survey -Ptask4Output=<new-directory>`; output directories cannot be overwritten.
JSON is authoritative. Diagnostic maps are generated from the same predicate.

## Workload and measurements

Sixteen fixed full-width seeds, including the legacy collision pair, zero, positive/negative seeds
and signed-long extremes. Each primary window is [-32768,32768]², sampled at 128 blocks, inclusive.
The geographic extent is 65,536 blocks per axis. The follow-up harness also surveys translated
65,536-block windows and repeats coastline measurements at 64 blocks for seeds 8675309 and 42.
Interior measurements include an 8,192-block halo. Distances are to sampled marine lattice nodes;
the halo avoids censoring the default-sized continental interiors in the primary window.

Connected land uses four-neighbor lattice connectivity. One node represents 128² blocks; inclusive
endpoints slightly enlarge the represented sample area relative to geometric window area. Components
report boundary censoring, extents, intersecting site IDs and a covariance-based major axis. Major land
is a measured component of at least 10,000,000 sampled square blocks, not a cellular identity.
Coastline length is mid-edge marching squares. Ambiguous diagonals retain two contour segments.

Each corridor connects a pair of neighboring sites. Nine sections span its declared centerline segment;
each section tests the normal and ±0.12-radian directions. Crossings are searched at 32 blocks and
refined to one block. Failed opposing-continent searches and island obstructions remain explicit.
The tolerance is two blocks. Island geometry separately obeys whole-disc strip clearance.

## Initial measurements

These values come from `docs/task4/evidence/survey-final`, including the translated-window/map extension.
No topology parameters were tuned to obtain them. The first survey attempt completed the default
survey but rejected an intended-valid high-coverage adversarial case; that test requested an infeasible
combination. The revised high-coverage test uses 56% target land at scale 65,536.

| Metric | Initial measured result |
| --- | --- |
| Land fraction, per-seed range / mean | 43.11–44.60% / 43.78% |
| Open marine fraction, mean | 56.01% |
| Inland-sea fraction, mean | 0.213% |
| Major land components | 256 total; 16 per primary seed window |
| Major component sampled area, min / mean / max | 108,691,456 / 117,775,296 / 125,878,272 blocks² |
| Major width min / p10 / median / p90 | 2,990 / 3,810 / 4,414 / 5,039 blocks |
| Invalid default corridor sections | 0 |
| Coastline length per window, min / mean / max | 722,260 / 732,648 / 749,764 blocks at step 128 |
| Land beyond sampled coast distances 500 / 1000 / 2000 | 84.91% / 68.41% / 40.87%, mean across seeds |
| Largest interior distance per seed | 5,862–6,000 blocks on the sampled lattice |
| Constructed islands | 75 coastal, 306 oceanic, 177 archipelago members |
| Constructed archipelagos | 34 clusters, 4–7 members (mean 5.21) |

Constructed islands and coarse connected components are different measurements. A small island can
be missed or split by the sampling grid; island geometry reports continuous disc area and cluster
extent, while the component table reports the sampled result. Inland seas are explicit closed marine
basins; the macro harness has no freshwater hydrology, so it does not invent a freshwater fraction.

## Default acceptance criteria

Use these criteria for this development version and fixed workload; they are not universal Earth
statistics or a proof over an infinite world. The 40–48% land interval allows seed variation around
the configured 44% target. Requiring large components and interiors checks the intended later need
for regional climate and multiple biome zones without implementing either system.

- Per primary seed: land fraction 40–48%; at least 12 components above 10 million sampled blocks².
- Every measured declared corridor has opposing continental endpoints and width >=998 blocks.
- No land or island intrudes into the reserved 1,000-block marine strip.
- At least 30% of sampled land exceeds 2,000 blocks from sampled marine nodes in each primary window.
- Islands and explicit clusters exist across the seed corpus. Report all sub-grid uncertainty.
- All invalid configuration cases reject; every accepted adversarial case passes its corridor tests.
- Same-input macro samples agree across order, worker counts and reconstructed contexts; the full-seed
  collision pair differs quantitatively. Restart/save persistence is a separate runtime gate.

The listed criteria pass for the final survey. The deliberately regular macro-cell separation network
is a current limitation. Maps expose its silhouette and spacing; objective width success does not by
itself establish aesthetic acceptance.
