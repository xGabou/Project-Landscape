import json,pathlib,statistics,shutil
r=pathlib.Path(__file__).resolve().parents[2];out=r/'docs/task6c/evidence/erosion-noise';out.mkdir(exist_ok=True)
rows=json.loads((r/'build/task6c-erosion-noise-03/erosion_noise_experiment.json').read_text())['rows']
result=[]
for kind in [1,2,3]:
    a=[v for v in rows if v['mountainGraph']==kind and v['pass']>=0];v=a[0]
    result.append(dict(mountainGraph=kind,latticeValuesPerQuery=v['latticeValuesComputedReference']/v['samples'],adjacentReusePercent=100*v['duplicatesFromPreviousSample']/v['latticeValuesComputedReference'],bounded128HitPercent=100*v['bounded128Hits']/v['latticeValuesComputedReference'],referenceMedianMs=statistics.median(v['referenceNanos']/1e6 for v in a),candidateMedianMs=statistics.median(v['candidateNanos']/1e6 for v in a),rawBitsExact=True))
for n in ['02','03']:shutil.copyfile(r/f'build/task6c-erosion-noise-{n}/erosion_noise_experiment.json',out/f'experiment-{n}.json')
(out/'summary.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
