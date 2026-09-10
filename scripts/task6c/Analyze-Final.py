"""Compare final evidence against frozen references. Never runs generation."""
import json,pathlib,statistics,math,shutil
r=pathlib.Path(__file__).resolve().parents[2];out=r/'docs/task6c/evidence';out.mkdir(exist_ok=True)
def read(p):return json.loads((r/p).read_text(encoding='utf-8-sig'))
def write(name,data):(out/name).write_text(json.dumps(data,indent=2)+'\n')
def stats(a):
    a=sorted(a);return dict(samples=len(a),totalSeconds=sum(a),medianSeconds=statistics.median(a),p95Seconds=a[math.ceil(.95*len(a))-1],minimumSeconds=min(a),maximumSeconds=max(a),sampleStandardDeviationSeconds=statistics.stdev(a) if len(a)>1 else None,throughputPerSecond=len(a)/sum(a))
names=['sequential_generation','spawn_expansion','scattered_generation','interleaved_regions','climate_heavy']
stages={'Task6C baseline (frozen Task6B)':'build/task6c-baseline-01','smoothing':'build/task6c-smoothing-acquisition','smoothing + IslandModel':'build/task6c-island-acquisition-02','final (plus marine)':'build/task6c-marine-acquisition-01'}
acquisition=[]
for name in names:
    reference=None
    for stage,path in stages.items():
        data=read(path+'/'+name+'.json')
        if reference is None:reference=data['outputs']
        assert reference==data['outputs'],(name,stage)
        row=dict(scenario=name,stage=stage,rawClimateExact=True,surface=data['surfaceCache'],allQueries=stats([m['wallNanos']/1e9 for m in data['measurements']]))
        for passno in [0,1]:row['cold' if passno==0 else 'warm']=stats([m['wallNanos']/1e9 for m in data['measurements'] if m['name'].startswith(f'pass{passno}_')])
        acquisition.append(row)
write('final_acquisition.json',acquisition)
workers=[];reference={(v['tileX'],v['tileZ']):v['sha256'] for v in read('docs/task6b/evidence/canonical_determinism.json')['workerRuns'][0]['tiles']}
for w in [1,2,8,24,48]:
    v=read(f'build/task6c-final-determinism-{w}/determinism.json');assert v['requestedProcessors']==w and v['terrainWorkers']==max(2,w)
    assert {(t['tileX'],t['tileZ']):t['sha256'] for t in v['tiles']}==reference
    workers.append(v)
write('final_determinism.json',dict(status='PASS',workerRuns=workers,frozenTask6BExact=True))
macro=read('build/task6c-final-task4/determinism_digests.json');assert set(macro.values())=={'c3930838fa0f75d1541c097838f00dfce5707e69a6f6dd12b2ca7b7e0dfdfee6'}
write('final_macro.json',dict(status='PASS',digests=macro))
regressions=[]
for stage,source,ref in [('Task5','build/task6c-final-task5','docs/task6b/evidence/climate-fixtures'),('Task6','build/task6c-final-task6','docs/task6b/evidence/biome-fixtures')]:
    for p in (r/source).iterdir():
        if p.name=='resolver_performance.json':continue # measurements, not deterministic fixtures
        if p.suffix not in ['.json','.csv'] or not (r/ref/p.name).exists():continue
        a=p.read_text();b=(r/ref/p.name).read_text()
        try:equal=json.loads(a)==json.loads(b)
        except json.JSONDecodeError:equal=a==b
        assert equal,(stage,p.name)
        regressions.append(dict(stage=stage,file=p.name,exact=True))
write('final_offline_regressions.json',regressions)
for name in ['final-ownership','final-task4','final-task5','final-task6','final-cache','final-legacy']:
    dest=out/name;dest.mkdir(exist_ok=True)
    for p in (r/('build/task6c-'+name)).glob('*'):
        if p.suffix in ['.json','.csv']:shutil.copyfile(p,dest/p.name)
# Stage durations from diagnostic fresh Task6C baseline; overlap, never additive wall time.
previous=read('build/task6c-instrumented-01/microbenchmarks.json')[-1]['terrainCumulative'];profile=[]
for name in names:
    last=read('build/task6c-instrumented-01/'+name+'.json')['measurements'][-1]['terrainCumulative']
    profile.append(dict(scenario=name,summedDiagnosticSeconds={k[:-5]:(v-previous.get(k,0))/1e9 for k,v in last.items() if k.endswith('Nanos')}));previous=last
write('baseline_stage_profile.json',profile)
print('Acquisition, all five workers, Task4 digest, Task5/6 fixtures PASS')
