"""Read retained diagnostic runs; cumulative counters are differenced at scenario boundaries."""
import json,pathlib,shutil
root=pathlib.Path(__file__).resolve().parents[2]
names=['sequential_generation','spawn_expansion','scattered_generation','interleaved_regions','climate_heavy']
results=[]
for folder in ['build/task6c-instrumented-01','build/task6c-island-instrumented-01']:
    p=root/folder
    previous=json.loads((p/'microbenchmarks.json').read_text())[-1]['terrainCumulative']
    for name in names:
        data=json.loads((p/(name+'.json')).read_text());last=data['measurements'][-1]['terrainCumulative']
        delta={k:last[k]-previous.get(k,0) for k in last};previous=last
        results.append(dict(run=folder,scenario=name,stages=delta))
dest=root/'docs/task6c/evidence/island-instrumented-01';dest.mkdir(exist_ok=True)
for p in (root/'build/task6c-island-instrumented-01').glob('*.json'):shutil.copyfile(p,dest/p.name)
(root/'docs/task6c/evidence/island_diagnostics.json').write_text(json.dumps(dict(methodology='Summed instrumented durations, not wall time. Nested stages overlap. Includes all queries per scenario; excludes earlier scenarios and microbenchmarks.',rows=results),indent=2)+'\n')
for r in results:
    if r['scenario']=='scattered_generation':print(r['run'],{k:v/1e9 for k,v in r['stages'].items() if k.startswith('ISLAND') and k.endswith('Nanos')})
