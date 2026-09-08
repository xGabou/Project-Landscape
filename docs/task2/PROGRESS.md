# Task 2 implementation ledger

Comparator: Task 1C `d2164f25cd06aafcae15cc15f9f4babe977d4bdb`, corrected legacy
production `cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee` (`LEGACY_RTF_V0`).
This is a progress ledger, **not a completion report**.

## Versions and planned configuration codecs

New source lives exclusively under `com.gabou.atmospheregen`; no legacy IDs changed.
Separate schema, geography, baseline-climate and biome-resolver versions exist.
Only the legacy tuple passes functional backend dispatch. Planned controls are
explicitly inactive, not a working new generator.

The developer `foundation` profile runs inside a real standalone Forge world.
First run discovered that DFU range codecs carry invalid partial values into record
constructors. Strict validation now returns errors without partial defaults. Optional
configuration decoding also rejects malformed present fields instead of silently
treating them as absent. Constructors independently enforce valid values.

Verification: `runReproductionClient -PreproProfile=foundation` succeeded, Java 17,
Minecraft 1.20.1 / Forge 47.4.22. Run `reproduction-1788833318178`; six round trips,
nine rejection/unsupported-backend cases. No production caller uses these types yet.
Full comparator/build/persistence gates remain pending.

## Named seed streams

`atmospheregen:named-seed-v1` hashes length-prefixed UTF-8 scheme/dimension/domain/
domain-algorithm-version and big-endian full signed 64-bit seed/salt using SHA-256.
The first eight digest bytes form the output long. No enum ordinals, construction
order or sequential allocation participate. Only the domain's own algorithm version
participates; changing climate version leaves geography/biome streams alone.

Real Forge run `reproduction-1788833477027` passed: 189 stream cases (seven fixed
seeds, three dimensions, nine domains), salt/order/worker checks, nine high-bit
collision-divergence checks and nine dimension-separation checks. Legacy production
does not call this service. Legacy terrain equality still awaits the final corpus gate.

## Manifest, fingerprint and context primitives

Real Forge run `reproduction-1788833763477` passed the expanded foundation suite.
The manifest codec round-trips full-long seed strings, dimension, separate versions,
immutable configuration sections, sorted data checksums and optional future catalog identity.
SHA-256 covers canonical JSON: sorted object keys, preserved array order, normalized finite
numbers, no paths/timestamps/runtime tokens/workers. A changed content checksum is rejected.
Legacy snapshots capture effective fields omitted by the old preset codec; geometry is explicit.

The store writes a forced temporary file then renames without replacement under the caller's
world storage lock. No manifest + no recognized owner leaves the world untouched. Explicit legacy
assignment, existing-manifest reopen, foreign ownership and changed seed were tested. Rejected
mismatches leave the existing file unchanged. Automatic production world binding is still pending.
Context IDs identify immutable content; separate load instances get distinct cheap cache tokens.

## Public contracts

Real Forge run `reproduction-1788833975729` passed API validation/unavailable-service checks.
Geography/hydrology expose immutable values, optional quality/resolution-bearing metrics and
explicit canonical block-coordinate semantics. No inherited type appears in `api`.
Baseline climate has documented physical units and ecological (not relative) humidity semantics;
the coordinate contract allows future bounded neighboring geography. Biome resolution is a
distinct unimplemented contract; Minecraft's source remains unchanged. Planned geography and
physical climate fail explicitly rather than supplying legacy approximations/hints.

## World ownership and canonical adapter binding

Real Forge run `reproduction-1788834616159` passed create/save/reopen, identical persisted
fingerprint/context ID, distinct reopen cache token, canonical warm/evicted queries and rejected
post-shutdown provider reads. Actual noise-router CellSampler markers plus active preset identify
legacy Overworld ownership; availability of additional RTF tags alone is insufficient. Nether/End
are not bound to the new companion context. Existing legacy seed/cache algorithms are unchanged.

Binding occurs after RandomState initialization in ChunkMap construction, before publication or
chunk scheduling. Metadata shares the existing lookup token; no hashing/codecs enter hot loops.
Fingerprints cover effective preset, active generator, worldgen registries, resolved tags, relevant
JSON and NBT resources, the legacy biome table and conservative external mod versions (excluding
the two known developer harness mods). Optional TerraBlender config/region metadata is read behind
the existing optional-mod gate; no new biome candidates or selection logic are implemented.

The thin canonical adapter always provides the owning filtered tile, never direct fallback. It
returns detached immutable elevations/classifications and honest unknown metrics. Legacy climate
hints stay dimensionless and separate. This does not extract or split Heightmap.

Task 2 freeze policy rejects live server datapack reload while a manifest-bound world is open,
before new tags/templates can be applied. Initial loading and vanilla-only reload are unaffected.
Safe change-aware live reload is not implemented; restart with matching generation data instead.
Comprehensive golden/TB/vanilla/performance gates remain pending.
