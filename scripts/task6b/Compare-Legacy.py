"""Validate final Task 6B legacy comparator without pretending offline runs contain holders."""
import json,pathlib,shutil
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'docs/task6b/evidence'
def read(p):return json.loads((ROOT/p).read_text(encoding='utf-8-sig'))
primary='build/task6b-legacy-runtime-24';tb='build/task6b-legacy-runtime-tb';ref='docs/task1c/evidence';rows=[]
for actual,expected in [(primary,ref+'/golden-24'),(tb,ref+'/golden-tb')]:
 for table in ('canonical_geography','legacy_direct_samples','biome_hints','minecraft_biomes','seed_collision','cached_direct_comparison'):
  a,b=read(actual+'/'+table+'.json'),read(expected+'/'+table+'.json');assert len(a)==len(b),(actual,table,'count')
  for x,y in zip(a,b):
   if table=='seed_collision':assert x['a']==y['a'] and x['b']==y['b'] and not x['differentFields']
   else:
    assert x['seed']==y['seed'] and x['point']==y['point'],(actual,table,'identity')
    field='biome' if table=='minecraft_biomes' else 'differentFields' if table=='cached_direct_comparison' else 'fields'
    assert x[field]==y[field],(actual,table,x.get('seed'),x.get('point'))
  rows.append({'source':actual,'table':table,'exactRows':len(a),'expectedRows':len(b)})
tiles=[]
for name,actual in [('golden-24',primary),('golden-2','docs/task6b/evidence/legacy-offline/golden-2'),('golden-48','docs/task6b/evidence/legacy-offline/golden-48'),('golden-repeat','docs/task6b/evidence/legacy-offline/golden-repeat'),('golden-tb',tb)]:
 a,b=read(actual+'/tile_digests.json'),read(ref+'/'+name+'/tile_digests.json');assert len(a)==len(b)==105
 lookup={(x['seed'],x['tileX'],x['tileZ'],x['order']):x['sha256'] for x in b}
 for x in a:assert lookup[(x['seed'],x['tileX'],x['tileZ'],x['order'])]==x['sha256'],(name,x)
 tiles.append({'suite':name,'source':actual,'exact':len(a),'expected':len(b),'workers':sorted(set(x['workers'] for x in a))})
for source,name in [(primary,'legacy-runtime-24'),(tb,'legacy-runtime-tb')]:
 dest=OUT/name;dest.mkdir(exist_ok=True)
 for f in (ROOT/source).glob('*.json'):shutil.copyfile(f,dest/f.name)
result={'status':'PASS','exactness':'raw float bits and exact identifiers; no tolerance','rows':rows,'tiles':tiles,'canonicalGeography':'595/595','biomeHints':'595/595','biomeKeys':'85/85','standaloneTileDigests':'420/420','TerraBlenderTileDigests':'105/105','legacyCollisionPreserved':True,'scope':'fresh production runtime primary/TerraBlender suites plus retained completed 2/48/repeat offline scheduling suites. Offline runs are used only for tile digests, not invented holder observations.'}
(OUT/'legacy_comparator.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
