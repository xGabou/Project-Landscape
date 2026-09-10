"""Compare one independently measured exact optimization with its preceding stage."""
import json, math, pathlib, shutil, statistics, sys
root=pathlib.Path(__file__).resolve().parents[2]
before,after,name=sys.argv[1:]
out=root/'docs/task6c/evidence'; out.mkdir(parents=True,exist_ok=True)
def read(p): return json.loads(p.read_text(encoding='utf-8-sig'))
def summary(data):
    values=sorted(m['wallNanos']/1e9 for m in data['measurements'])
    return dict(totalSeconds=sum(values),medianSeconds=statistics.median(values),p95Seconds=values[math.ceil(.95*len(values))-1],minimumSeconds=min(values),maximumSeconds=max(values),sampleStandardDeviationSeconds=statistics.stdev(values),throughputQueriesPerSecond=len(values)/sum(values),processCpuSeconds=sum(m['processCpuNanos'] for m in data['measurements'])/1e9,surface=data['surfaceCache'],climate=data['climateCache'])
rows=[]
for scenario in ('sequential_generation','spawn_expansion','scattered_generation','interleaved_regions','climate_heavy'):
    b=read(root/before/(scenario+'.json')); a=read(root/after/(scenario+'.json'))
    assert b['outputs']==a['outputs'],scenario
    sb,sa=summary(b),summary(a)
    rows.append(dict(scenario=scenario,before=sb,after=sa,rawClimateExact=True,reductionPercent=100*(1-sa['totalSeconds']/sb['totalSeconds'])))
dest=out/name;dest.mkdir(exist_ok=True)
for f in (root/after).glob('*.json'):shutil.copyfile(f,dest/f.name)
(out/(name+'.json')).write_text(json.dumps(dict(before=before,after=after,methodology='Sequential independent JVMs; cold plus warm pass, diagnostics disabled. Small fixed corpus, no confidence claim. Raw climate outputs exact.',scenarios=rows),indent=2)+'\n')
print(json.dumps([(r['scenario'],r['reductionPercent']) for r in rows]))
