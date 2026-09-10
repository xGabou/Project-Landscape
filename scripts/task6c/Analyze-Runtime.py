import json,pathlib,math,statistics,shutil,gzip
r=pathlib.Path(__file__).resolve().parents[2];out=r/'docs/task6c/evidence'
def read(p):return json.loads((r/p).read_text(encoding='utf-8-sig'))
def write(n,v):(out/n).write_text(json.dumps(v,indent=2)+'\n')
def stats(a):
    a=sorted(a);return dict(samples=len(a),totalSeconds=sum(a),medianSeconds=statistics.median(a),p95Seconds=a[math.ceil(.95*len(a))-1],minimumSeconds=min(a),maximumSeconds=max(a),sampleStandardDeviationSeconds=statistics.stdev(a) if len(a)>1 else None,throughputChunksPerSecond=len(a)/sum(a))
paths={'Task6B original':'docs/task6b/evidence/runtime-final-01','Task6B extended':'docs/task6b/evidence/runtime-runs/optimized-extended','final Task6C':'build/task6c-final-full-8'}
summary=[];raw={}
for name,path in paths.items():
    raw[name]=read(path+'/task6b_full_chunks.json')
    for limit in [12,14]:
        for version in ['V1','legacy']:
            selected=[v for v in raw[name] if v['version']==version and v['index']<limit]
            if len(selected)!=limit:continue
            fresh=[v for v in selected if not v['preexistingFull']];loaded=[v for v in selected if v['preexistingFull']]
            result=dict(run=name,version=version,corpus=limit,**stats([v['wallNanos']/1e9 for v in fresh]),terrainTilesGenerated=sum(v['afterCounters']['tileGenerationCalls']-v['beforeCounters']['tileGenerationCalls'] for v in fresh),alreadyLoadedRows=loaded)
            summary.append(result)
write('final_full_performance.json',dict(rows=summary,intermediateFull={'smoothing':'not executed','smoothing + IslandModel':'not executed'},methodology='Frozen original twelve points and extended fourteen; final original subset is the first twelve in the same ordered fourteen-point run. New FULL only; prerequisite chunks included. JFR enabled equally; detailed Task6C timers disabled. Separate loaded retrieval timings below.'))
reference=read(paths['Task6B extended']+'/task6b_full_outputs.json');actual=read(paths['final Task6C']+'/task6b_full_outputs.json');lookup={(v['blockX'],v['blockZ']):v for v in reference};exact=[]
for v in actual:
    b=lookup[v['blockX'],v['blockZ']]
    for field in ['geography','tileDigest','climateRawBits','surfaceBiome','macroShorelineProfileBlocks']:assert v[field]==b[field],(v['blockX'],v['blockZ'],field)
    exact.append(dict(blockX=v['blockX'],blockZ=v['blockZ'],exact=True,landform=v['geography']['landform'],water=v['geography']['water'],surfaceBiome=v['surfaceBiome']))
digests=[];lookup={(v['version'],v['blockX'],v['blockZ']):v for v in raw['Task6B extended']}
for v in raw['final Task6C']:
    b=lookup[v['version'],v['blockX'],v['blockZ']];assert v['biomesDigest']==b['biomesDigest'],('stored biome',v['index'],v['version'])
    digests.append(dict(version=v['version'],index=v['index'],biomesExact=True,blocksExact=v['blocksDigest']==b['blocksDigest']))
write('final_full_exactness.json',dict(canonical=exact,storedDigests=digests,blockScope='Block digest differences remain diagnostic only under the documented Task6B repeated-baseline feature-order variance. Canonical geography, finalized tiles, climate, surface winners and stored biomes require exact equality.'))
write('final_loaded_retrieval.json',dict(Task6B=stats([v['alreadyLoadedWallNanos']/1e9 for v in reference]),Task6C=stats([v['alreadyLoadedWallNanos']/1e9 for v in actual])))
disposal=read('build/task6c-final-full-8/task6b_cache_disposal.json');islands=read('build/task6c-final-full-8/task6c_island_disposal.json')
assert all(v['allEmpty'] and v['postCloseQueriesRejected'] for v in disposal)
assert all(v['cache']['entries']==0 and v['cache']['frontEntries']==0 and v['postCloseQueryRejected'] for v in islands)
write('final_runtime_disposal.json',dict(existing=disposal,islands=islands))
dest=out/'final-runtime';dest.mkdir(exist_ok=True)
for p in (r/'build/task6c-final-full-8').glob('*.json'):
    if p.name=='task6b_locality_trace.json':
        with gzip.open(dest/(p.name+'.gz'),'wb') as f:f.write(p.read_bytes())
    else:shutil.copyfile(p,dest/p.name)
print('FULL canonical/stored-biome exactness and runtime disposal PASS')
for s in summary:
    if s['run']=='final Task6C':print(s['corpus'],s['version'],s['totalSeconds'],s['terrainTilesGenerated'])
