# Original Task 1C report tooling. All Rights Reserved.
param([string]$Run="$PSScriptRoot/../../docs/task1c/evidence/benchmark-final",[string]$Output="$PSScriptRoot/../../docs/task1c/evidence/benchmark_summary.json")
$ErrorActionPreference='Stop'
function Table($file){$a=Get-Content $file -Raw|ConvertFrom-Json;return $a}
function Quantile($values,$q){$s=@($values|Sort-Object);return $s[[Math]::Max(0,[Math]::Ceiling($s.Count*$q)-1)]}
function Stats($values){
    $values=@($values);$mean=($values|Measure-Object -Average).Average
    $squares=0.0;foreach($v in $values){$squares+=($v-$mean)*($v-$mean)}
    $sd=if($values.Count -gt 1){[Math]::Sqrt($squares/($values.Count-1))}else{0}
    return [ordered]@{n=$values.Count;mean=$mean;median=(Quantile $values .5);p95=(Quantile $values .95);p99=$(if($values.Count -ge 100){Quantile $values .99}else{$null});min=($values|Measure-Object -Minimum).Minimum;max=($values|Measure-Object -Maximum).Maximum;sampleStandardDeviation=$sd;coefficientOfVariationPercent=$(if($mean){100*$sd/$mean}else{0})}
}
$runs=@(Table "$Run/benchmark_runs.json"|Where-Object {!$_.warmup})
$latencies=@(Table "$Run/query_latencies.json")+@(Table "$Run/chunk_latencies.json")
$operations=@()
foreach($group in ($runs|Group-Object operation)){
    $point=$group.Name -in @('raw_heightmap_point','direct_approximate_point','canonical_warm_point','cached_opportunistic_point','warm_tile_lookup');$chunk=$group.Name -like 'FULL_*'
    $details=@{};$d=$group.Group[0].details;for($i=0;$i -lt $d.Count;$i+=2){$details[$d[$i]]=$d[$i+1]}
    $units=if($point){$details.queries}elseif($chunk){8}else{1}
    $perUnit=@($group.Group|ForEach-Object {$_.elapsedNs/$units})
    $individual=@($latencies|Where-Object {$_.operation -eq $group.Name -and !$_.warmup}|ForEach-Object {$_.nanoseconds})
    if(!$individual.Count){$individual=$perUnit}
    $repeatMeans=@($group.Group|Group-Object repeat|ForEach-Object {($_.Group.elapsedNs|Measure-Object -Average).Average/$units})
    $counters=[ordered]@{};foreach($row in $group.Group){foreach($p in $row.cacheObserverDelta.PSObject.Properties){if(!$counters.Contains($p.Name)){$counters[$p.Name]=0L};$counters[$p.Name]+=$p.Value}}
    $operations += [ordered]@{operation=$group.Name;unitsPerTimedBatch=$units;unit=$(if($point){'query'}elseif($chunk){'FULL chunk'}else{'tile'});
        measuredBatches=$group.Count;measuredRepetitions=@($group.Group.repeat|Select-Object -Unique).Count;
        throughputUnitsPerSecond=(Stats @($group.Group|ForEach-Object {1e9*$units/$_.elapsedNs}));
        latencyNs=(Stats $individual);repeatMeanNsPerUnit=(Stats $repeatMeans);batchMeanNsPerUnit=(Stats $perUnit);
        processAllocatedBytesPerUnit=(Stats @($group.Group|ForEach-Object {$_.processThreadAllocatedBytes/$units}));
        observerCounterTotals=$counters;attribution=$(if($chunk){'Total Minecraft FULL work plus prerequisite neighbors. Not isolated RTF terrain.'}else{'Harness operation; atomic cache observers and live client remain enabled.'})}
}
$metrics=@(Table "$Run/cache_metrics.json")
$probePath=Join-Path (Split-Path $Run -Parent) 'allocation/allocation_probe.json'
$probe=if(Test-Path $probePath){@(Table $probePath|Where-Object {!$_.warmup})}else{@()}
$summary=[ordered]@{schemaVersion=1;fixtureVersion=1;comparator='cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee';backend='LEGACY_RTF_V0';
    configurationFingerprint=@(Table "$Run/identity.json")[0].configurationFingerprint;identityReference='benchmark-final/identity.json';
    benchmarkRunReference='benchmark-final/benchmark_runs.json';environmentReference='benchmark-final/environment.json';machineReference='machine.json';
    percentilePolicy='nearest rank; p99 only when n>=100; 24 chunk latencies give coarse p95';operations=$operations;
    memory=[ordered]@{maximumObservedHeapUsedBytes=($metrics.heapUsedBytes|Measure-Object -Maximum).Maximum;maximumCachedEntries=($metrics.cachedEntries|Measure-Object -Maximum).Maximum;
        maximumPublishedCellCountUpperBound=($metrics.publishedCellCountUpperBound|Measure-Object -Maximum).Maximum;
        retainedCacheBytes=$null;reason='Heap observation is process-wide, not dominator retained size; no exact retained-size claim.';
        postShutdownLiveBorrows=@($metrics|Where-Object {$_.phase -like '*after shutdown'}|ForEach-Object {$_.cellPool.live+$_.chunkPool.live});
        allocationCaveat='ThreadMXBean sums all live JVM threads; includes render/client/background allocation and observer overhead. Not isolated allocations per generator.'};
    separateAllocationProbe=$(if($probe.Count){[ordered]@{reference='allocation/allocation_probe.json';seed='8675309';tileX=80;tileZ=-80;rtfWorkersAndCallerBytesPerTile=(Stats $probe.rtfWorkersAndCallerAllocatedBytes);timingComparableToPrimaryBenchmark=$false;scope='Existing 24 RTF executor workers plus caller; includes measurement overhead, excludes other Minecraft workers and renderer.'}}else{$null});
    futureGates=[ordered]@{extractionRegressionPercent=10;completePipelineTimeMemoryReviewPercent=25;hardFailureTimeMultiplier=2;variancePolicy='Compare like environment/protocol and confidence/variance; never apply sub-variance thresholds mechanically.'}}
[IO.File]::WriteAllText([IO.Path]::GetFullPath($Output),($summary|ConvertTo-Json -Depth 30)+"`n",[Text.UTF8Encoding]::new($false))
$operations|ForEach-Object {[pscustomobject]@{operation=$_.operation;medianNs=$_.latencyNs.median;p95Ns=$_.latencyNs.p95;medianThroughput=$_.throughputUnitsPerSecond.median;repeatCV=$_.repeatMeanNsPerUnit.coefficientOfVariationPercent}}
