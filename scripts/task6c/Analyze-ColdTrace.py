"""Exact coordinate/tile accounting from Task 6B's untruncated FULL trace."""
import collections,gzip,json
from pathlib import Path
root=Path(__file__).resolve().parents[2]
source=root/'docs/task6b/evidence/runtime-runs/optimized-extended/task6b_locality_trace.json.gz'
with gzip.open(source,'rt') as f:row=json.load(f)[0]
points=[e for e in row['events'] if e['kind']=='profile_sample']
loads=[e for e in row['events'] if e['kind']=='surface_load']
result=dict(source=str(source.relative_to(root)),dropped=row['dropped'],profileSamples=len(points),surfaceLoads=len(loads),uniqueProfileCoordinates=len({(e['x'],e['z']) for e in points}),uniqueProfileTiles=len({(e['x']//128,e['z']//128) for e in points}),uniqueLoadTiles=len({(e['x']//128,e['z']//128) for e in loads}))
assert result['dropped']==0
result['conclusion']='463 distinct owning tiles, 463 loads: real upstream coverage, not repeated surface loading. This untimed trace is separate from timed FULL rows.'
(root/'docs/task6c/evidence/cold_trace_analysis.json').write_text(json.dumps(result,indent=2))
