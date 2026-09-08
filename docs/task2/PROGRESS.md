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
