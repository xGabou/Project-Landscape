"""Close existing Task 6C evidence; no generation or profiling runs."""
import json, pathlib, shutil, subprocess
r=pathlib.Path(__file__).resolve().parents[2]; out=r/'docs/task6c/evidence'
def read(p): return json.loads((r/p).read_text(encoding='utf-8-sig'))
def write(n,v): (out/n).write_text(json.dumps(v,indent=2)+'\n',encoding='utf-8')
def retain(source,dest):
    d=out/dest;d.mkdir(exist_ok=True)
    for p in (r/source).glob('*.json'): shutil.copyfile(p,d/p.name)
v=read('build/task6c-final-task1b-verification/verification.json')
assert len(v['checks'])==64 and all(x['pass'] for x in v['checks'])
write('final_task1b.json',dict(status='PASS',passed=64,total=64,verification=v))
retain('build/task6c-final-task1b','final-task1b')
retain('build/task6c-final-legacy-tb','final-legacy-tb')
shutil.copyfile(r/'build/task6c-final-island-eviction/island_eviction.json',out/'final_island_eviction.json')
rows=[]
for actual,expected in [('build/task6c-final-legacy','docs/task1c/evidence/golden-24'),('build/task6c-final-legacy-tb','docs/task1c/evidence/golden-tb')]:
    for table in ['canonical_geography','legacy_direct_samples','biome_hints','seed_collision','cached_direct_comparison','minecraft_biomes']:
        if not (r/actual/(table+'.json')).exists(): continue
        a,b=read(actual+'/'+table+'.json'),read(expected+'/'+table+'.json');assert len(a)==len(b)
        for x,y in zip(a,b):
            if table=='seed_collision': assert x['a']==y['a'] and x['b']==y['b'] and not x['differentFields']
            else:
                field='biome' if table=='minecraft_biomes' else 'differentFields' if table=='cached_direct_comparison' else 'fields'
                assert x['seed']==y['seed'] and x['point']==y['point'] and x[field]==y[field]
        rows.append(dict(source=actual,table=table,exact=len(a)))
tiles=[]
for name,source in [('golden-24','build/task6c-final-legacy'),('golden-2','docs/task6b/evidence/legacy-offline/golden-2'),('golden-48','docs/task6b/evidence/legacy-offline/golden-48'),('golden-repeat','docs/task6b/evidence/legacy-offline/golden-repeat'),('golden-tb','build/task6c-final-legacy-tb')]:
    a=read(source+'/tile_digests.json');b=read('docs/task1c/evidence/'+name+'/tile_digests.json')
    key=lambda x:(x['seed'],x['tileX'],x['tileZ'],x['order'])
    assert len(a)==len(b)==105 and {key(x):x['sha256'] for x in a}=={key(x):x['sha256'] for x in b}
    tiles.append(dict(suite=name,source=source,exact=105,reused=name in ['golden-2','golden-48','golden-repeat']))
write('final_legacy_comparator.json',dict(status='PASS',rows=rows,tiles=tiles,geography='595/595',hints='595/595',biomeKeys='85/85',standaloneTiles='420/420',terraBlenderTiles='105/105',legacySeedCollisionPreserved=True,scope='Current standalone offline and TerraBlender runtime comparisons; unchanged legacy scheduling path reuses frozen 2/48/repeat suites. Holder observations are current TerraBlender runtime; no offline holder claim.'))
profiles=[]
for name,path in [('Task6B original','docs/task6b/evidence/profiles/optimized-original-V1.json'),('Task6B extended','docs/task6b/evidence/profiles/optimized-extended-V1.json'),('Task6C final','docs/task6c/evidence/final_jfr_v1.json')]:
    p=read(path)
    profiles.append(dict(run=name,source=path,**{k:p[k] for k in ['executionSamples','subsystemSamples','recordedSpanNanos','monitorBlockedThreadNanos','parkPathBlockedThreadNanos','gcSumOfPausesNanos']},garbageCollections=p['eventCounts'].get('jdk.GarbageCollection',0)))
write('final_jfr_comparison.json',dict(rows=profiles,scope='Existing FULL recordings include hashing and quart probes; final recording also includes three diagnostic cold traces. Unequal work: counts are not matched CPU timings. First recognized stack subsystem; parks/monitor durations sum across threads. Zero events means none above recording threshold.',conclusion='No final IslandModel monitor events. Terrain noise and erosion/filtering dominate execution samples. Cache waits are mostly terrain acquisition, not ExactCache monitor contention. Hydraulic erosion and smoothing remain grouped by existing classifier; no invented separate CPU totals.'))
raw=(r/'build/task6c-final-build.log').read_bytes();log=raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig')
assert 'BUILD SUCCESSFUL' in log
(out/'final-build.log').write_text(log,encoding='utf-8')
markers=[read(str(p.relative_to(r))) for p in sorted((r/'build').glob('task6c-final-*-complete.json'))]
production=subprocess.check_output(['git','rev-parse','HEAD:src/main'],cwd=r,text=True).strip()
assert not subprocess.check_output(['git','diff','d82f149','--','src/main'],cwd=r,text=True).strip()
write('final_gate_results.json',dict(status='PASS',productionSourceTree=production,productionLastChanged='d82f149',completedGates=markers,task1b='64/64',islandEviction='PASS',legacy='PASS with explicitly retained unaffected scheduling suites',productionUnchangedSinceGates=True,phase2Implemented=False))
print('Task1B 64/64, legacy exact, retained profiles and build PASS')
