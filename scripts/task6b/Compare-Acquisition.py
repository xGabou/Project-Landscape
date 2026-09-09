"""Task 6B exact paired acquisition comparison; no timings are discarded."""
import json, math, pathlib, shutil, statistics
ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/task6b/evidence'
PAIRS = [
 ('build/task6b-baseline-02', 'build/task6b-optimized-01'),
 ('build/task6b-frozen/build/task6b-baseline-03', 'build/task6b-optimized-02a'),
 ('build/task6b-frozen/build/task6b-baseline-04', 'build/task6b-optimized-03'),
]
SCENARIOS = ['sequential_generation','spawn_expansion','scattered_generation','interleaved_regions','climate_heavy']
def read(p): return json.loads((ROOT/p).read_text(encoding='utf-8-sig'))
def write(n,d): (OUT/n).write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
def stats(v):
 return {'runs':len(v),'median':statistics.median(v),'min':min(v),'max':max(v),'sampleStandardDeviation':statistics.stdev(v) if len(v)>1 else None,'p95':None,'p95Note':'Three independent runs do not support a reliable tail estimate.'}
allrows=[]
for name in SCENARIOS:
 rows=[]
 for index,(bp,ap) in enumerate(PAIRS,1):
  b,a=read(f'{bp}/{name}.json'),read(f'{ap}/{name}.json')
  assert b['outputs']==a['outputs'],(name,index,'raw output mismatch')
  assert b['queriesPerPass']==a['queriesPerPass']
  assert [r['name'] for r in b['measurements']]==[r['name'] for r in a['measurements']]
  bw=sum(r['wallNanos'] for r in b['measurements']);aw=sum(r['wallNanos'] for r in a['measurements'])
  rows.append({'pair':index,'baseline':bp,'optimized':ap,'baselineWallSeconds':bw/1e9,'optimizedWallSeconds':aw/1e9,'absoluteReductionSeconds':(bw-aw)/1e9,'wallReductionPercent':100*(1-aw/bw),'exactClimateComparisons':len(a['outputs']),'rawDoubleFieldsPerOutput':7,'allSevenRawDoubleBitsExact':True,'baselineSurface':b['surfaceCache'],'optimizedSurface':a['surfaceCache'],'baselineClimate':b['climateCache'],'optimizedClimate':a['climateCache']})
 result={'scenario':name,'scope':'cold-plus-warm canonical climate acquisition; not FULL chunk generation','pairs':rows,'baselineWallSeconds':stats([r['baselineWallSeconds'] for r in rows]),'optimizedWallSeconds':stats([r['optimizedWallSeconds'] for r in rows]),'pairedAbsoluteReductionSeconds':stats([r['absoluteReductionSeconds'] for r in rows]),'pairedReductionPercent':stats([r['wallReductionPercent'] for r in rows]),'allRawDoubleBitsExact':True}
 write(name+'.json',result);allrows.append(result)
for label,column in [('baseline',0),('optimized',1)]:
 runs=[]
 for index,pair in enumerate(PAIRS,1):
  source=pair[column];target=OUT/'acquisition'/f'{label}-{index:02d}';target.mkdir(parents=True,exist_ok=True)
  for name in SCENARIOS+['completion','microbenchmarks','jfr_summary']:
   shutil.copyfile(ROOT/source/(name+'.json'),target/(name+'.json'))
  runs.append({'pair':index,'source':source,'environment':read(f'{source}/completion.json'),'microbenchmarks':read(f'{source}/microbenchmarks.json'),'evidence':str(target.relative_to(ROOT)).replace('\\','/')})
 write(label+'_benchmark.json',{'scope':'acquisition, three fresh JVMs','productionHead':'a3140a5d7113137563d275cbaea05a8e94f3b23e' if column==0 else '4442abe76598ad351ff8350bcc2b7d036c548661','runs':runs})
write('paired_comparison.json',{'runs':3,'methodology':'Same JDK17.0.17+10, Forge data launcher, 8 available processors, 6GiB heap, seed 8675309 and accepted preset/config. Baseline then optimized for each pair, never concurrent. One cold and one warm pass per scenario, fresh world context per scenario, same fixed scenario and query ordering. JFR profile active. First pair retained from previous session; later pairs contemporary. No randomized order or confidence interval claim. All individual runs retained.','scenarios':allrows,'scope':'acquisition only; cold profile acquisition dominates; no FULL/TPS claim','allRawDoubleBitsExact':True})
for r in allrows: print(r['scenario'],r['baselineWallSeconds']['median'],r['optimizedWallSeconds']['median'],r['pairedReductionPercent']['median'])
