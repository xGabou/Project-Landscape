# Task 6B cache audit

Status: source audit and offline policy experiments; production remains Task 6.

The baseline source owns a canonical surface LRU (1024 detached fields), a
climate-result LRU (4096 results), and a separate coarse-distance provider. Its
`PaGeographyProvider` also constructs a coarse-distance provider. Debug climate
commands construct additional geography and distance instances per invocation.
Runtime instance counts and disposal verification are still required.

Each 128 × 128 surface field has two float arrays: exactly 131,072 primitive
payload bytes, or 134,217,728 bytes at the 1024-entry bound. Array headers, field
objects, map nodes, boxed keys and table storage are additional Java overhead.
Both elevation and mountain influence are part of the exposed exact input;
no quantization or precision reduction is authorized.

The surface cache and climate cache hold their respective object monitors
through expensive misses. This serializes unrelated keys but also prevents
duplicate concurrent calculation within each individual instance. The shared
terrain TileCache separately coordinates generation. Counting repeated loads
must distinguish capacity/expiry churn from concurrent duplicate calculation.

Canonical tile publication already snapshots pooled terrain before publication
(Task 1B). A surface field copies primitive values from that finalized snapshot.
No optimization may retain pooled mutable readers.

## Offline policy comparison

The replay obtains exact requested coordinates by executing the unchanged
climate model. Strategy A is the current 1024-field LRU. B combines 128 fields
with 65,536 exact coordinate samples. C groups the entire corpus by tile; D
combines that grouping with B. C/D are whole-corpus lower bounds, not claims
that an online BiomeSource can reorder Minecraft's future requests.

On the sequential corpus, A needs 228 field loads. B needs 271 and obtains no
exact sample hits. Tile sharing is substantial even though exact coordinates
do not repeat. This evidence rejects blindly shrinking the tile cache in favor
of a sample cache for this corpus. Repeated Y requests must also account for
the existing climate-result cache before claiming an extra sample-cache win.
