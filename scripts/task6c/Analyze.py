"""Summarize retained acquisition evidence without treating diagnostics as performance."""
import json, math, statistics, sys
from pathlib import Path
root=Path(__file__).resolve().parents[2]
scenarios=['sequential_generation','spawn_expansion','scattered_generation','interleaved_regions','climate_heavy']
def stats(values):
    a=sorted(values); total=sum(a)
    return dict(totalSeconds=total, medianSeconds=statistics.median(a), p95Seconds=a[math.ceil(.95*len(a))-1], minimumSeconds=a[0], maximumSeconds=a[-1], standardDeviationSeconds=statistics.pstdev(a), throughputQueriesPerSecond=len(a)/total)
rows=[]
for arg in sys.argv[1:]:
    folder=root/arg
    for scenario in scenarios:
        data=json.loads((folder/(scenario+'.json')).read_text())
        for prefix in ['pass0_','pass1_']:
            measures=[m for m in data['measurements'] if m['name'].startswith(prefix)]
            row=dict(run=arg,scenario=scenario,passName=prefix,**stats([m['wallNanos']/1e9 for m in measures]),cpuSeconds=sum(m['processCpuNanos'] for m in measures)/1e9)
            row['surfaceCacheEndOfBothPasses']=data['surfaceCache'];row['climateCacheEndOfBothPasses']=data['climateCache']
            row['outputDigest']=data['outputDigest'];rows.append(row)
print(json.dumps(rows,indent=2))
