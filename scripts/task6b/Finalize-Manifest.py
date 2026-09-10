"""Validate Task 6B completion and hash portable evidence without self-reference."""
import hashlib, json, pathlib, re, subprocess, sys
ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT/'docs/task6b/evidence'
def read(name): return json.loads((OUT/name).read_text(encoding='utf-8-sig'))
def write(name, value): (OUT/name).write_text(json.dumps(value, indent=2)+'\n', encoding='utf-8')
def git(*args): return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()
def log_text(path):
    raw = path.read_bytes()
    return raw.decode('utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig').replace('\r\n', '\n')
def digest(path):
    data = path.read_bytes()
    if path.suffix in ('.json', '.md', '.csv', '.log', '.patch', '.py', '.ps1', '.java'):
        data = data.replace(b'\r\n', b'\n')
    return hashlib.sha256(data).hexdigest()

if '--verify' in sys.argv:
    manifest = read('manifest.json')
    for entry in manifest['files']:
        path = ROOT/entry['path']
        assert path.exists() and digest(path) == entry['sha256'], entry['path']
        git('ls-files', '--error-unmatch', '--', entry['path'])
        if path.suffix == '.json': json.loads(path.read_text(encoding='utf-8-sig'))
    for path in (ROOT/'docs/task6b').glob('*.md'):
        for target in re.findall(r'\]\(([^)]+)\)', path.read_text(encoding='utf-8')):
            assert (path.parent/target).exists(), (path.name, target)
    assert manifest['productionSourceTree'] == git('rev-parse', 'HEAD:src/main')
    print(json.dumps({'status': 'PASS', 'verifiedTrackedFiles': len(manifest['files']), 'head': git('rev-parse', 'HEAD'), 'worktreeClean': not git('status', '--porcelain')}, indent=2))
    raise SystemExit(0)

assert git('branch', '--show-current') == 'task6b-performance'
assert not git('diff', 'ef75474', '--name-only', '--', 'src/main')
assert read('erosion_production_regression.json')['rawFieldComparisons'] == 33177600
assert read('canonical_determinism.json')['crossWorkerExact']
ownership = read('provider_instance_counts.json')
assert ownership['identityChecks'] and all(ownership[k] == 1 for k in ('geographyProviders', 'climateProviders', 'surfaceCaches', 'distanceFields'))
concurrency = read('cache_concurrency_telemetry.json')
assert all(concurrency[k] for k in ('failureRetry', 'recreatedWorld', 'inFlightDisposal', 'worldIsolation'))
assert [r['workers'] for r in concurrency['workerChecks']] == [1, 2, 8, 24, 48]
assert all(r['exact'] and r['singleFlightLoads'] == 1 for r in concurrency['workerChecks'])
for gate in ('geography_regression', 'climate_regression', 'biome_regression', 'legacy_comparator', 'runtime_disposal', 'save_reopen', 'terrablender_runtime'):
    assert read(gate+'.json')['status'] == 'PASS', gate
assert set(read('geography_regression.json')['digests'].values()) == {'c3930838fa0f75d1541c097838f00dfce5707e69a6f6dd12b2ca7b7e0dfdfee6'}
source = read('source_reuse.json')
assert source['holderReferenceIdentity'] and source['directHolderChecks'] == 20 and source['caveDelegationChecks'] == 9
assert read('task1b_regressions.json')['passed'] == read('task1b_regressions.json')['total'] == 64
paired = read('paired_comparison.json')
assert paired['runs'] == 3 and all(r['allRawDoubleBitsExact'] and len(r['pairs']) == 3 for r in paired['scenarios'])
full = read('full_output_equivalence.json')
assert full['status'] == 'PASS_CANONICAL_AND_STORED_BIOMES' and len(full['canonicalComparisons']) == 14
assert all(r['storedBiomeDigestExact'] for pair in full['fullDigestComparisons'] for r in pair['rows'])
snapshots = read('production_cache_metrics.json')['snapshots']
for r in snapshots:
    for cache in ('surface', 'climate', 'geography', 'winners'):
        c = r[cache]
        if 'capacityWaits' in c: assert c['capacityWaits'] == 0 and c['peakInFlight'] < c['capacity']
        assert c['failures'] == 0
for r in read('runtime_disposal.json')['observations']:
    if 'surface' in r:
        for k in ('surface', 'climate', 'geography', 'winners'): assert r[k]['entries'] == r[k]['inFlight'] == 0
        if 'distance' in r: assert r['distance']['entries'] == 0 and r['postCloseQueriesRejected']
    if r.get('case') == 'actual world unload': assert r['allClosed'] and r['allUnregistered'] and r['livePooledBorrows'] == 0
build = ROOT/'build/task6b-final-build.log'
assert 'BUILD SUCCESSFUL' in log_text(build)
assert 'BUILD SUCCESSFUL' in log_text(ROOT/'build/task6b-source-reuse-final.log')
write('build_verification.json', {'status': 'PASS', 'command': './gradlew.bat build --offline --console=plain',
    'sourceTree': git('rev-parse', 'HEAD:src/main'), 'log': 'final-build.log',
    'sourceIdentityCommand': './gradlew.bat runData -Ptask6bOutput=build/task6b-source-reuse-final -Ptask6bMode=ownership -Ptask6bSourceChecks=true --offline --console=plain',
    'warnings': 'Existing deprecated Java/Gradle API warnings; build and strengthened source identity check exit successfully.'})
(OUT/'final-build.log').write_text(log_text(build), encoding='utf-8', newline='\n')
(OUT/'source-reuse-final.log').write_text(log_text(ROOT/'build/task6b-source-reuse-final.log'), encoding='utf-8', newline='\n')

commits = [{'sha': line.split(' ', 1)[0], 'subject': line.split(' ', 1)[1]}
    for line in git('log', '--reverse', '--format=%H %s', 'a3140a5..HEAD').splitlines()]
write('task6b_commits.json', {'commitsThroughEvidenceParent': commits,
    'scope': 'Task 6B branch commits after baseline a3140a5. Final documentation/manifest commit necessarily follows this inventory; obtain complete final list with git log --reverse --format=%H a3140a5..task6b-performance. No squash.',
    'productionTree': git('rev-parse', 'HEAD:src/main')})
write('final_summary.json', {
    'status': 'COMPLETE_SCOPED_STABILIZATION', 'generationSemanticsUnchanged': True, 'phase2FeatureWorkStarted': False,
    'baselineProductionHead': git('rev-parse', 'a3140a5'), 'productionSourceTree': git('rev-parse', 'HEAD:src/main'),
    'evidenceParentHead': git('rev-parse', 'HEAD'),
    'erosionExactFieldComparisons': 33177600,
    'correctnessGates': {g: 'PASS' for g in ['erosion', 'canonical worker/reference tiles', 'Task4 geography', 'Task5 climate', 'Task6 biomes', 'FULL canonical semantics and stored biomes', 'Holder identity and caves', 'ownership', 'generic cache concurrency', 'actual runtime unload', 'save/reopen', 'TerraBlender', 'Task1B 64/64', 'legacy comparator', 'build']},
    'requestedProcessors': [1, 2, 8, 24, 48], 'actualTerrainWorkers': [2, 2, 8, 24, 48],
    'acquisitionPairs': 3, 'medianPairedAcquisitionReductionsPercent': {r['scenario']: r['pairedReductionPercent']['median'] for r in paired['scenarios']},
    'fullPerformance': read('full_chunk_performance.json'),
    'legacyV1Comparison': read('legacy_v1_full_comparison.json'),
    'productionCapacityWaits': 0, 'peakInFlight': {'surface': 9, 'climate': 49, 'geography': 8, 'winners': 49},
    'cacheCapacities': {'surface': 1024, 'climate': 4096, 'geography': 4096, 'winners': 4096, 'distance': 32},
    'surfaceArrayPayloadExactBytes': 134217728, 'fiveCompletedCachesEstimatedBytes': read('cache_memory_summary.json')['estimatedFiveCompletedCachesBytesAtAllCapacities'],
    'stopTask6BOptimizing': True, 'recommendDedicatedTerrainTask6C': True, 'task6CStarted': False,
    'historicalPlus25PercentLegacyGoalMet': False,
    'phase2MayBegin': 'Correctness prerequisites preserved; Phase 1 performance acceptance requires explicitly deferring the unmet +25% target. Phase 2 was not started or automatically authorized.',
    'limitations': ['Small fixed corpora, few repeats and ordered runs', 'Exact cause of optimized FULL execution-cost variance not isolated; GC and ExactCache monitor costs do not explain it', 'Terrain noise/filtering and broad acquisition dominate; IslandModel high-worker contention remains', 'Known FULL block/feature nondeterminism, with exact canonical and stored-biome gates', 'JFR sampling/thresholds; blocking durations overlap across threads', 'Estimated cache payload excludes terrain cache, pools, pending work and unmodeled overhead', 'Payload clearing demonstrated with retained references; no GC reachability claim', 'No true one-terrain-worker test, TPS claim or total retained-heap measurement'],
    'reports': ['docs/task6b/TASK_6B_WORLDGEN_PERFORMANCE.md', 'docs/task6b/PERFORMANCE_COMPARISON.md', 'docs/task6b/CACHE_ARCHITECTURE.md'],
    'treeScope': 'Final clean-tree check applies to task6b-performance after the final commit. Unrelated main-worktree changes and frozen harness adaptations remain preserved.'})

raw_profiles = []
for f in sorted(OUT.rglob('task6b_profiles.json')):
    for p in json.loads(f.read_text(encoding='utf-8-sig')):
        path = pathlib.Path(p['path'])
        assert path.exists(), path
        raw_profiles.append({'reference': f.relative_to(OUT).as_posix(), 'version': p['version'], 'localPath': str(path), 'bytes': path.stat().st_size, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
files = []
for folder in (ROOT/'docs/task6b', ROOT/'scripts/task6b'):
    for f in sorted(folder.rglob('*')):
        if not f.is_file() or f == OUT/'manifest.json': continue
        files.append({'path': f.relative_to(ROOT).as_posix(), 'sha256': digest(f)})
write('manifest.json', {'status': 'PASS', 'productionSourceTree': git('rev-parse', 'HEAD:src/main'),
    'hashMethod': 'SHA-256; text CRLF normalized to LF for cross-platform checkout stability, binary exact. Manifest excludes itself; final Git commit is reported externally to avoid self-reference.',
    'files': files, 'retainedLocalFullJfrRecordings': raw_profiles,
    'preservation': 'Original acquisition/equivalence evidence and failed/partial local runs retained. source-reuse-initial preserves earlier observations superseded by strengthened Holder-reference test.'})
print(json.dumps({'status': 'PASS', 'files': len(files), 'rawFullProfiles': len(raw_profiles), 'productionSourceTree': git('rev-parse', 'HEAD:src/main')}, indent=2))
