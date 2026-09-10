"""Collect FULL performance, exact canonical outputs, cache metrics and unload evidence."""
import json,pathlib,statistics,math,shutil,gzip
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'docs/task6b/evidence'
def read(path):return json.loads((ROOT/path).read_text(encoding='utf-8-sig'))
def write(name,data):(OUT/name).write_text(json.dumps(data,indent=2)+'\n')
def stats(values):
 s=sorted(values);n=len(s)
 return {'samples':n,'median':statistics.median(s),'p95NearestRank':s[math.ceil(.95*n)-1],'min':s[0],'max':s[-1],'sampleStandardDeviation':statistics.stdev(s) if n>1 else None}
def summary(rows):
 new=[r for r in rows if not r['preexistingFull']];loaded=[r for r in rows if r['preexistingFull']];seconds=sum(r['wallNanos'] for r in new)/1e9
 return {'newFullChunks':len(new),'newFullWallSeconds':seconds,'newFullProcessCpuSeconds':sum(r['processCpuNanos'] for r in new)/1e9,'newFullTimingMilliseconds':stats([r['wallNanos']/1e6 for r in new]),'newFullThroughputChunksPerSecond':len(new)/seconds,'preexistingFullChunks':len(loaded),'liveThreadAllocationDeltaBytes':sum(r['liveThreadAllocationDeltaBytes'] for r in new),'allocationScope':'sum of observed live-thread allocation counters; excludes threads already terminated at snapshot, not exact whole-process allocation'}
runs={
 'baseline-original':'docs/task6b/evidence/full-baseline',
 'baseline-repeat':'build/task6b-full-baseline-03',
 'baseline-extended':'build/task6b-full-baseline-04',
 'optimized-original':'docs/task6b/evidence/runtime-final-01',
 'optimized-extended':'build/task6b-full-optimized-02',
 'optimized-24':'build/task6b-full-workers-24',
 'optimized-48':'build/task6b-full-workers-48',
}
raw={name:read(path+'/task6b_full_chunks.json') for name,path in runs.items()}
results={name:{v:summary([r for r in rows if r['version']==v]) for v in ('V1','legacy')} for name,rows in raw.items()}
# Original 12-point scope allows all repeated 8-worker runs to be compared independently of added coverage.
common={name:{v:summary([r for r in raw[name] if r['version']==v and r['index']<12]) for v in ('V1','legacy')} for name in runs}
pairs=[]
for before,after in [('baseline-original','optimized-original'),('baseline-repeat','optimized-extended')]:
 b,a=common[before]['V1'],common[after]['V1'];pairs.append({'baseline':before,'optimized':after,'chunks':12,'baselineWallSeconds':b['newFullWallSeconds'],'optimizedWallSeconds':a['newFullWallSeconds'],'reductionPercent':100*(1-a['newFullWallSeconds']/b['newFullWallSeconds'])})
b,a=results['baseline-extended']['V1'],results['optimized-extended']['V1']
extended={'chunks':14,'baselineWallSeconds':b['newFullWallSeconds'],'optimizedWallSeconds':a['newFullWallSeconds'],'reductionPercent':100*(1-a['newFullWallSeconds']/b['newFullWallSeconds']),'scope':'same appended mountain/coastal coverage; one independent 8-worker extended pair, supported by original 12-point repeats'}
write('full_chunk_performance.json',{'scope':'fresh worlds, fixed ordered requests to FULL with prerequisite neighbors; hashing and exact-output validation outside timed calls','p95Caveat':'nearest-rank p95 of 12 or 14 per-chunk observations equals the largest observation; descriptive corpus tail, not a population tail estimate','runs':results,'common12PointRuns':common,'pairedCommon12':pairs,'extended14Pair':extended,'common12BaselineRunWallSpread':stats([common[n]['V1']['newFullWallSeconds'] for n in ('baseline-original','baseline-repeat','baseline-extended')]),'common12OptimizedRunWallSpread':stats([common[n]['V1']['newFullWallSeconds'] for n in ('optimized-original','optimized-extended')])})
comparisons=[]
for name in ('optimized-original','optimized-extended','optimized-24','optimized-48'):
 v,l=results[name]['V1'],results[name]['legacy'];comparisons.append({'run':name,'V1':v,'legacy':l,'relativeTimeOverheadPercent':100*(v['newFullWallSeconds']/l['newFullWallSeconds']-1),'scope':'same seed and coordinates, V1 world followed by legacy world in the same JVM; version-specific terrain is expected, no identical-terrain claim'})
write('legacy_v1_full_comparison.json',{'contemporary':comparisons,'historicalTargetOverheadPercent':25,'targetIsNotPermissionToChangeOutputs':True})
# Exact output comparison uses maps keyed by the fixed coordinate, never row order or rounded values.
bo=read(runs['baseline-extended']+'/task6b_full_outputs.json');ao=read(runs['optimized-extended']+'/task6b_full_outputs.json')
lookup={(r['blockX'],r['blockZ']):r for r in bo};exact=[]
for r in ao:
 b=lookup[(r['blockX'],r['blockZ'])]
 for field in ('geography','tileDigest','climateRawBits','surfaceBiome','macroShorelineProfileBlocks'):assert b[field]==r[field],(r['blockX'],r['blockZ'],field)
 exact.append({'blockX':r['blockX'],'blockZ':r['blockZ'],'exactCanonicalGeography':True,'exactFinalizedTile':True,'exactClimateRawBits':True,'exactSurfaceWinner':True,'surfaceBiome':r['surfaceBiome'],'landform':r['geography']['landform'],'water':r['geography']['water'],'macroShorelineProfileBlocks':r['macroShorelineProfileBlocks'],'tileDigest':r['tileDigest']})
blocks=[]
for before,after in [('baseline-original','baseline-repeat'),('baseline-repeat','baseline-extended'),('baseline-original','optimized-original'),('baseline-extended','optimized-extended'),('optimized-original','optimized-extended'),('optimized-extended','optimized-24'),('optimized-extended','optimized-48')]:
 lookup={(r['version'],r['blockX'],r['blockZ']):r for r in raw[before]}
 rows=[]
 for r in raw[after]:
  b=lookup.get((r['version'],r['blockX'],r['blockZ']))
  if b is None:continue
  biome=b['biomesDigest']==r['biomesDigest'];assert biome,(before,after,r['version'],r['index'],'stored biomes')
  rows.append({'version':r['version'],'blockX':r['blockX'],'blockZ':r['blockZ'],'storedBiomeDigestExact':biome,'blockDigestExact':b['blocksDigest']==r['blocksDigest'],'beforeBlockDigest':b['blocksDigest'],'afterBlockDigest':r['blocksDigest']})
 blocks.append({'before':before,'after':after,'rows':rows})
write('full_output_equivalence.json',{'status':'PASS_CANONICAL_AND_STORED_BIOMES','canonicalComparisons':exact,'fullDigestComparisons':blocks,'blockScope':'Block digests are diagnostic: repeated baseline and unchanged legacy feature scheduling can vary. Canonical geography, finalized tiles, raw climate and stored quart biomes require exact equality; block differences are never accepted by epsilon.'})
write('already_loaded_chunks.json',{'baselineMilliseconds':stats([r['alreadyLoadedWallNanos']/1e6 for r in bo]),'optimizedMilliseconds':stats([r['alreadyLoadedWallNanos']/1e6 for r in ao]),'scope':'untimed-output phase repeated access to same loaded chunk, identity asserted; excludes new FULL generation'})
metrics=[];disposal=[]
for name in ('optimized-original','optimized-extended','optimized-24','optimized-48'):
 path=runs[name]
 for r in read(path+'/task6b_cache_metrics.json'):
  r=dict(r);r['run']=name
  for key in ('surface','climate','geography','winners'):
   c=dict(r[key]);total=c['hits']+c['misses']+c['waits'];c['hitRate']=c['hits']/total;c['missRate']=c['misses']/total;c['waitRate']=c['waits']/total;r[key]=c
  if 'distance' in r:
   c=r['distance'];c['hitRate']=c['hits']/(c['hits']+c['misses']);c['evictionsDerivedBeforeClear']=c['misses']-c['entries'];c['pendingWaits']='not applicable: existing serialized distance loader';c['failures']='not instrumented; no runtime query failures observed'
  metrics.append(r)
 for r in read(path+'/task6b_cache_disposal.json'):
  for key in ('surface','climate','geography','winners'):assert r[key]['entries']==0 and r[key]['inFlight']==0
  if 'distance' in r:assert r['distance']['entries']==0 and r['postCloseQueriesRejected']
  disposal.append({'run':name,**r})
 for r in read(path+'/disposal.json'):
  if r.get('case')=='actual world unload':assert r['allClosed'] and r['allUnregistered'] and r['livePooledBorrows']==0;disposal.append({'run':name,**r})
write('production_cache_metrics.json',{'scope':'real Minecraft generation including spawn and ordered FULL corpus; snapshots precede untimed exact-output validation; original run used 12 chunks, extended/worker runs use 14. Locality trace is an additional untimed FULL request.','snapshots':metrics,'singleFlightInterpretation':'wait counts prove concurrent same-key requests coalesced; original synchronized miss path already deduplicated within one cache, so counts alone do not quantify incremental speedup.','capacityDecision':'retain existing bounds when capacityWaits is zero and peakInFlight remains below capacity','memoryReference':'cache_memory_summary.json'})
write('runtime_disposal.json',{'status':'PASS','observations':disposal,'scope':'actual unload with deliberately retained source/context references, empty detached cache payloads and no post-close recreation; no GC reachability claim'})
# Preserve source evidence without huge plaintext trace duplication.
for name,path in runs.items():
 if path.startswith('docs/'):continue
 dest=OUT/'runtime-runs'/name;dest.mkdir(parents=True,exist_ok=True)
 for f in (ROOT/path).glob('*.json'):
  if f.name=='task6b_locality_trace.json':
   with gzip.open(dest/(f.name+'.gz'),'wb') as z:z.write(f.read_bytes())
  else:shutil.copyfile(f,dest/f.name)
print(json.dumps({'pairedCommon12':pairs,'extended14Pair':extended,'canonicalExact':len(exact)},indent=2))
