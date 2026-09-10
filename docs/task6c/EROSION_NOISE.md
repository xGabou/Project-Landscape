# Erosion-noise reuse decision

Reject the isolated generic cross-sample value cache for production in Task 6C.
This is a correctness/lifecycle rejection, not a finding that reuse is small.

The original per-octave 25-slot cache computes the union of the 3×3 candidates'
3×3 neighbor sets. The retained two-octave graphs therefore evaluate 50 lattice
input values per query, in addition to the direct input sample. Six 160×160
row-major input-coordinate scans of each of the actual three mountain input
graphs show 99.20646% overlap with the preceding sample. These isolated scans
exclude outer domain warp and production worker/batch interleaving; they are not
a measured production terrain-worker trace or an end-to-end speedup prediction.

A reproduction-only 128-entry direct-mapped candidate keys exact float coordinate
bits and compute seed, scoped to one immutable input graph. All 1,843,200 output
comparisons (three graphs × four passes × six tiles × 25,600 samples) match raw
bits. Candidate hit rates 62.19/69.34/69.74%; median reference/candidate ms:
1068.75/567.60, 1400.19/622.29, 1531.56/662.56. Both timed paths include diagnostic
set allocation and use reference-first order, so timings are exploratory.
The previous-coordinate working set is 50 values across two octaves; 128 entries
already permits substantial reuse, but collisions and changed scan order matter.

The generic Noise interface has no purity guarantee: valid inputs may read cell
state. Matching coordinate/seed alone is not a sufficient cache key for such
inputs. The candidate also has only local single-thread experiment ownership;
it has neither a production world-disposal hook nor proven shared-worker safety.
No complete public-cell or finalized-tile digest equivalence was established for
an installed candidate. Consequently it is not eligible to ship, despite local
bit equivalence and promising timing. Production Erosion remains unchanged.

A follow-up would need an explicit pure-graph eligibility contract, captured
actual warped worker scans, world-owned bounded storage, and all-field/final-tile
proof before performance acceptance. Task 6C does not widen into that graph and
lifecycle redesign. Seed, octave order, coordinate scaling and float evaluation
order were not changed. Evidence: `evidence/erosion-noise`; experiment code is
excluded from production artifacts. First failed run was a reflection-access
harness error; subsequent runs and logs are preserved under `build/task6c-*`.
