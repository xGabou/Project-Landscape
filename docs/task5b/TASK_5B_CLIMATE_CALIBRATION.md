# Task 5B — baseline-climate calibration audit

## Finding

No production recalibration was required. The reported `0.522–0.526` range was the range of the 16 per-seed *mean* moisture indices in the Task 5 summary, not the range of all surveyed samples. The Task 5B harness measures all 4,624 samples jointly.

The actual sample-wise moisture-index range is `0.3301–0.6656`, with median `0.5558` and standard deviation `0.0843`. Rainfall spans `445.7–1620.2 mm/year`; evaporation spans `805.8–939.0 mm/year`. Thus the narrow prior report was a survey aggregation artifact, not formula saturation, cache interpolation, or rainfall-driven evaporation tracking.

## Exact equation

Production code computes:

```text
temperature = latitudeTerm - max(0, elevationBlocks) * 0.0065
              + oceanModeration + continentalityContribution + regionalVariation

evaporation = max(0, 520
                    + max(-5, temperature) * 18 * evaporationStrength
                    + continentality * 120 * evaporationStrength)

moistureIndex = rainfall / (rainfall + evaporation + 1)
```

Rainfall is already bounded nonnegative by the geography/profile model and receives low-amplitude regional variation. Evaporation is bounded nonnegative. The index has no sigmoid, smoothstep, map, normalization, or additional clamp. The `+1` is a small denominator stabilizer, not a saturation transform.

Representative equation breakdowns, including wettest/driest survey samples, high-rain/high-evap, low-rain/low-evap, and the three Okanagan cases, are in `evidence/moisture_formula_breakdown.csv`.

## Joint analysis

Across all 4,624 rows:

| quantity | min | p05 | median | p95 | max | SD |
|---|---:|---:|---:|---:|---:|---:|
| rainfall | 445.7 | 531.6 | 1089.4 | 1433.7 | 1620.2 | 303.0 |
| evaporation | 805.8 | 808.8 | 862.9 | 927.8 | 939.0 | 38.3 |
| rainfall / evaporation | 0.493 | 0.617 | 1.252 | 1.697 | 1.993 | 0.366 |
| rainfall − evaporation | -457.9 | -333.1 | 221.4 | 580.8 | 807.3 | 313.3 |
| moisture index | 0.330 | 0.381 | 0.556 | 0.629 | 0.666 | 0.084 |

Pearson correlations are: rainfall/index `0.986`, evaporation/index `-0.320`, rainfall/evaporation ratio/index `0.991`, temperature/evaporation `0.877`, temperature/index `0.006`, and rain-shadow/index `-0.651`. Evaporation responds to temperature and continentality; rainfall does not enter its equation.

## Synthetic extremes and Task 6 recommendation

The controlled cases distinguish cold/wet `0.743`, cold/dry `0.261`, temperate/moderate `0.518`, hot/moderate `0.387`, hot/dry `0.064`, and extreme desert `0.015`. These are climate descriptors only; no biome names or resolver logic were added.

Recommendation: **Model B** — Task 6 should use temperature, rainfall, potential evaporation, and rainfall/evaporation (or an equivalent aridity measure). Moisture index may remain a convenient bounded derived input, but should not be the sole moisture authority.

## Latitude and reachability

The original ±32,768-block survey covers only approximately ±28.5° under the 100,000-block latitude scale, explaining its warm temperature range. The extended audit samples ±2,000,000 blocks and reaches approximately ±85.7° and about `-14.9 °C`, while the equatorial sample is about `25 °C`. Both hemispheres are symmetric within the seeded regional perturbation. This proves cold and polar/alpine-capable temperatures are reachable without retuning latitude.

The reachability CSV demonstrates warm/temperate/cold wet and dry combinations, high-elevation cold, rain-shadow dry, and maritime wet controlled states. All are continuous climate inputs, not biome classification.

## Regression and scope

No geography code or geography seed domain was changed. Task 4’s accepted geography digest remains exact, and the legacy comparator remains exact. Okanagan, barrier removal, wind reversal, and multi-range tests remain passing. Provider cost and cache behavior are unchanged; this audit adds offline reads only. No biome resolver or runtime PA climate integration was started.
