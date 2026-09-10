"""Derive final Task 6B analysis from retained measurements; no world generation."""
import hashlib, json, pathlib, shutil, statistics, subprocess
ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/task6b/evidence'
def read(path): return json.loads((ROOT / path).read_text(encoding='utf-8-sig'))
def write(name, value): (OUT / name).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
def git(*args): return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()

performance = read('docs/task6b/evidence/full_chunk_performance.json')
profile_rows = []
for name, result in performance['runs'].items():
    for version in ('V1', 'legacy'):
        p = read(f'docs/task6b/evidence/profiles/{name}-{version}.json')
        profile_rows.append({'run': name, 'version': version,
            'newFullSeconds': result[version]['newFullWallSeconds'],
            **{k: v for k, v in p.items() if k not in ('topStacks', 'topParkStacksByBlockedThreadNanos', 'eventCounts')},
            'garbageCollections': p['eventCounts'].get('jdk.GarbageCollection', 0),
            'compilations': p['eventCounts'].get('jdk.Compilation', 0),
            'exactCacheMonitorClassBlockedThreadNanos': p['monitorBlockedThreadNanos'].get('com.gabou.atmospheregen.generation.cache.ExactCache', 0),
            'detail': f'profiles/{name}-{version}.json'})
acquisition = []
for version in ('baseline', 'optimized'):
    for index in (1, 2, 3):
        p = read(f'docs/task6b/evidence/acquisition/{version}-{index:02d}/jfr_summary.json')
        acquisition.append({'version': version, 'pair': index, 'executionSamples': p['executionSamples'], 'subsystemSamples': p['subsystemSamples']})
write('jfr_after_summary.json', {'scope': 'All seven retained FULL runs, V1 and legacy; all three acquisition pairs. Frozen original before summary is preserved separately.',
    'fullRuns': profile_rows, 'acquisitionPairs': acquisition,
    'methodology': 'First recognized subsystem from sampled stack leaf. Counts describe recorded execution samples, not exact CPU percentages. FULL recordings include output hashing and repeated quart microbenchmark between/after timed chunks; timings exclude those activities. Durations of parks/monitors/compilation sum across threads and cannot be divided by wall time as CPU cost. Original recording predates explicit 1 ms monitor threshold. Later recordings use 1 ms; installed JDK17 profile.jfc ThreadPark threshold is 10 ms (original JavaMonitorEnter also 10 ms).',
    'attributionCaveat': 'An ExactCache frame below a park includes loader waits on terrain as well as same-key followers. The monitor class identifies locking the ExactCache itself; call-path attribution is an upper bound including nested loader locks.'})

canonical = read('build/task6b-full-optimized-02/task6b_full_outputs.json')
geo = {(r['blockX'], r['blockZ']): r for r in canonical}
tail_rows = []
paths = {'baseline-original': 'docs/task6b/evidence/full-baseline', 'optimized-original': 'docs/task6b/evidence/runtime-final-01'}
for name in performance['runs']:
    path = paths.get(name, 'docs/task6b/evidence/runtime-runs/' + name)
    for r in read(path + '/task6b_full_chunks.json'):
        if r['version'] != 'V1': continue
        g = geo[r['blockX'], r['blockZ']]
        tail_rows.append({'run': name, 'index': r['index'], 'blockX': r['blockX'], 'blockZ': r['blockZ'],
            'landform': g['geography']['landform'], 'water': g['geography']['water'], 'surfaceBiome': g['surfaceBiome'],
            'wallSeconds': r['wallNanos']/1e9, 'processCpuSeconds': r['processCpuNanos']/1e9,
            'counterDeltas': {k: r['afterCounters'].get(k, 0)-v for k, v in r['beforeCounters'].items()}})
correlations = {}
for name in performance['runs']:
    rows = [r for r in tail_rows if r['run'] == name]
    correlations[name] = statistics.correlation([r['wallSeconds'] for r in rows], [r['counterDeltas']['tileGenerationCalls'] for r in rows])
write('full_tail_analysis.json', {'observations': tail_rows, 'wallVersusTileGenerationPearsonR': correlations,
    'interpretation': 'Broad cold canonical acquisition is directly associated with the tail: distant requests generate roughly 400-640 finalized tiles including climate-profile/neighbor requirements; adjacent requests generate zero to four. Point landform alone does not predict cost: verified mountain points can be cheap when nearby terrain is already acquired. This correlation does not isolate scheduling, compilation, or feature ordering as the cause of between-run speed changes.',
    'counterScope': 'Cumulative observer deltas bracket requests plus output hashing, whereas wall/process CPU timers bracket only getChunk. Terrain counts are useful workload observations, not exclusive per-stage timers.'})

verification = read('build/task6b-task1b-verification/verification.json')
assert len(verification['checks']) == 64 and all(c['pass'] for c in verification['checks'])
write('task1b_regressions.json', {'status': 'PASS', 'passed': 64, 'total': 64,
    'source': 'task1b-final', 'validator': 'scripts/task1b/Verify-Repairs.ps1 -ThroughFix 15', 'verification': verification})
dest = OUT / 'task1b-final'; dest.mkdir(exist_ok=True)
for f in (ROOT / 'build/task6b-task1b-final').glob('*.json'): shutil.copyfile(f, dest/f.name)

runtime = read('docs/task6b/evidence/runtime-final-01/task6_runtime.json')
fingerprints = read('docs/task6b/evidence/runtime-final-01/terrablender_fingerprint.json')
assert len(runtime) == 2 and {r['reopened'] for r in runtime} == {True, False}
assert runtime[0]['biomeDigest'] == runtime[1]['biomeDigest'] and all(r['sourceCodecRoundtrip'] for r in runtime)
assert all(r['stableAfterInitialization'] and r['registeredRuleChangeDetected'] and r['fixtureRestored'] for r in fingerprints)
assert len({r['fingerprint'] for r in fingerprints}) == 1
changes = git('diff', '--name-only', 'cee8f11', 'HEAD', '--', 'src/main').splitlines()
assert set(changes) == {'src/main/java/com/gabou/atmospheregen/generation/cache/ExactCache.java', 'src/main/java/com/gabou/atmospheregen/biome/ClimateBiomeSource.java'}
write('evidence_provenance.json', {'reviewHead': git('rev-parse', 'HEAD'), 'productionSourceTree': git('rev-parse', 'HEAD:src/main'),
    'lastProductionChange': git('log', '-1', '--format=%H', '--', 'src/main'),
    'lastSemanticImplementation': git('rev-parse', 'cee8f11'), 'laterProductionFiles': changes,
    'laterProductionChangeReview': '4442abe adds capacityWaits/peakInFlight counters and a read-only distanceCacheStats accessor. Cache policy, loaders, generation equations and lifecycle behavior remain identical to cee8f11. Current resume changes are harness/analysis/documentation only.',
    'retainedGatesValid': ['erosion 33177600 fields', 'Task4', 'Task5', 'Task6', 'worker-reference determinism', 'three acquisition pairs', 'save/reopen TerraBlender lifecycle'],
    'runtimeLifecycleSource': 'runtime-final-01: reproduction-1788977426740, finished 2026-09-09 14:17 EDT, after cee8f11; subsequent 4442abe telemetry is nonsemantic',
    'sourceIdentityGate': 'source-reuse-final reruns strengthened d102ce2 reference-identity assertion',
    'task1bRun': read('build/task6b-task1b-final-complete.json')})

# Capture the precise frozen harness diff including its untracked output validator.
patch = git('-C', 'build/task6b-frozen', 'diff') + '\n'
output = ROOT/'build/task6b-frozen/src/reproduction/java/baseline/reproduction/task6b/FullOutputEvidence.java'
lines = output.read_text().splitlines()
patch += '--- /dev/null\n+++ b/src/reproduction/java/baseline/reproduction/task6b/FullOutputEvidence.java\n@@ -0,0 +1,' + str(len(lines)) + ' @@\n' + ''.join('+'+line+'\n' for line in lines)
(OUT/'frozen_harness_final.patch').write_text(patch, encoding='utf-8')
assert not git('-C', 'build/task6b-frozen', 'diff', '--name-only', '--', 'src/main')
markers = []
for f in sorted((ROOT/'build').glob('task6b-*-complete.json')):
    markers.append({'path': f.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(f.read_bytes()).hexdigest(), 'run': json.loads(f.read_text(encoding='utf-8-sig'))})
write('completed_run_markers.json', markers)
print(json.dumps({'task1b': '64/64', 'profileRuns': len(profile_rows), 'tailCorrelations': correlations}, indent=2))
