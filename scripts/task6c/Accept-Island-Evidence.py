"""Validate and retain completed production IslandModel gates without rerunning them."""
import json,pathlib,shutil,statistics
r=pathlib.Path(__file__).resolve().parents[2];out=r/'docs/task6c/evidence/island-validation';out.mkdir(exist_ok=True)
def read(p):return json.loads(p.read_text())
macro=read(r/'build/task6c-island-macro-01/determinism_digests.json')
assert set(macro.values())=={'c3930838fa0f75d1541c097838f00dfce5707e69a6f6dd12b2ca7b7e0dfdfee6'}
baseline=read(r/'docs/task6b/evidence/canonical_determinism.json')['workerRuns'][0]['tiles']
candidate=read(r/'build/task6c-island-determinism-01/determinism.json')['tiles']
assert {(t['tileX'],t['tileZ']):t['sha256'] for t in baseline}=={(t['tileX'],t['tileZ']):t['sha256'] for t in candidate}
for name in ['island-macro-01','island-determinism-01','island-ownership-01','island-experiment-01','island-experiment-02']:
    dest=out/name;dest.mkdir(exist_ok=True)
    for p in (r/('build/task6c-'+name)).glob('*.json'):shutil.copyfile(p,dest/p.name)
experiment=read(r/'build/task6c-island-experiment-02/island_model_experiment.json')
rows=[]
for workers in [1,8,24,48]:
    row=dict(workers=workers)
    for candidate in [False,True]:
        values=[v for v in experiment['rows'] if v['workers']==workers and v['candidate']==candidate and v['pass']>=0 and v['workload']=='hot']
        row['candidate' if candidate else 'reference']=dict(medianMilliseconds=statistics.median(v['wallNanos']/1e6 for v in values),blockedThreadMillis=sum(v['blockedThreadMillis'] for v in values))
    rows.append(row)
(out/'summary.json').write_text(json.dumps(dict(macroExact=True,frozenTask6BTilesExact=True,isolated=rows,productionStageComparison='../island_acquisition_02.json',diagnostics='../island_diagnostics.json',releaseFrontHitCounterEnabled=False),indent=2)+'\n')
print(json.dumps(rows,indent=2))
