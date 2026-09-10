# Task 5 climate survey

The reproducible harness is `./gradlew task5ClimateSurvey -Ptask5Output=<directory>` and uses `PaMacroGeography` plus the real V1 climate provider adapter. It surveys 16 fixed signed 64-bit seeds over `[-32768,32768]²` at 4096-block spacing and writes machine-readable CSV/JSON evidence.

## Synthetic acceptance

The Okanagan-style fixture is ocean → flat coast → 1,700-block barrier → interior. With wind toward negative X, the upwind sample is 1,011.095 mm/year, the lee sample is 401.783 mm/year, and the unobstructed matched control is 937.481 mm/year. The lee is 42.9% of control, passing the 60% gate. Removing the barrier recovers 937.481 mm/year. Reversing wind changes the sample to 111.350 mm/year. A second range remains below full ocean moisture (458.368 versus 937.481 mm/year).

Temperature fixtures are monotonic across equator/mid/high latitude in both hemispheres and cool from 22.98 °C to 16.48 °C under the 1,000-block altitude fixture. All fixture outputs are finite and bounded.

## Real-seed distribution

Across the 16-seed coarse survey, temperature was approximately 14.84–23.15 °C, rainfall 445.7–1,620.2 mm/year, mean rainfall about 1,006 mm/year, mean evaporation about 865.7 mm/year, moisture index about 0.522–0.526 by seed, and mean rain-shadow about 0.267–0.278. Wind transitions were zero at the 4096-block survey spacing, showing coherent rather than checkerboard circulation. Full rows are in `evidence/climate_transects.csv` and the per-seed summary is `evidence/climate_distribution.csv`.

The accepted Task 4 geography digest remains `c3930838fa0f75d1541c097838f00dfce5707e69a6f6dd12b2ca7b7e0dfdfee6` for cold, warm, reverse, reconstructed, and worker-count variants. Climate uses a separate digest and does not write back to geography.

The evidence is intentionally a coarse diagnostic survey, not a mathematical proof of global climate continuity. Exact terrain coupling and large-scale real-world calibration remain future work.
