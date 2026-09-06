# Original Task 1A relational reproduction checks. All Rights Reserved.
# These assert that inherited defects are observable, NOT that their output is correct.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$FullRun,
    [string[]]$SchedulingRuns = @(),
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
function Read-Table([string]$directory, [string]$name) {
    return (Get-Content -LiteralPath (Join-Path $directory ($name+'.json')) -Raw | ConvertFrom-Json)
}
$checks = [System.Collections.Generic.List[object]]::new()
function Reproduction([string]$name, [bool]$observed) {
    $checks.Add([pscustomobject][ordered]@{name=$name; kind='inherited reproduction, not desired correctness'; pass=$observed})
}
$completion = @(Read-Table $FullRun 'completion')
Reproduction 'harness completed in real Forge' ($completion[0].status -eq 'PASS')
$seeds = @(Read-Table $FullRun 'seed_collisions')
$collision = @($seeds | Where-Object sameLow32)
Reproduction 'distinct long seeds collide on all recorded RTF fields' ($collision.Count -gt 0 -and @($collision | Where-Object {$_.differentFields.Count -ne 0}).Count -eq 0)
$cached = @(Read-Table $FullRun 'cached_uncached')
$changed = @($cached | Where-Object {$_.directVsFiltered.Count -gt 0})
Reproduction 'direct and filtered disagree' ($changed.Count -gt 0)
Reproduction 'warm repeat agrees with same filtered path' (@($cached | Where-Object {$_.filteredVsWarm.Count -gt 0}).Count -eq 0)
$eviction = @(Read-Table $FullRun 'eviction_reload')
Reproduction 'drop restores direct path and reload restores filtered path' (@($eviction | Where-Object {$_.directVsAfterDrop.Count -gt 0 -or $_.filteredVsRegenerated.Count -gt 0}).Count -eq 0)
$cross = @(Read-Table $FullRun 'cross_world_cache')
Reproduction 'same-thread A-B-A observes A in B' (@($cross | Where-Object {$_.sequence -eq 'A-B-A' -and $_.bContaminationFields.Count -gt 0 -and $_.aToBFields.Count -eq 0}).Count -eq 1)
Reproduction 'reverse order contamination' (@($cross | Where-Object {$_.sequence -eq 'B-A-B' -and $_.bContaminationFields.Count -gt 0}).Count -eq 1)
Reproduction 'sampleClimate omitted from cell cache key' (@($cross | Where-Object {$_.sequence -eq 'same context false-true climate' -and $_.differentFields.Count -gt 0}).Count -eq 1)
Reproduction 'same-position cell cache ignores tile becoming warm' (@($cross | Where-Object {$_.sequence -eq 'same context tile becomes warm' -and $_.beforeVsStale.Count -eq 0 -and $_.staleVsRefreshed.Count -gt 0}).Count -eq 1)
$noise = @(Read-Table $FullRun 'noise_cache')
Reproduction 'same noise cache ignores changed compute seed' (@($noise | Where-Object {$_.case -eq 'same instance P changing compute seed' -and $_.cached -ne $_.uncached -and $_.newGraph -eq $_.uncached}).Count -gt 0)
$noiseBits = @(Read-Table $FullRun 'noise_cache_bits')
Reproduction 'registered dormant erosion/ridge graphs return non-finite values' (@($noiseBits | Where-Object {($_.key -eq 'reterraforged:terrain/erosion' -or $_.key -eq 'reterraforged:terrain/ridges') -and -not $_.finite}).Count -eq 6)
$bounds = @(Read-Table $FullRun 'tile_bounds')
$aliases = @($bounds | Where-Object {-not $_.axisInBounds -and -not $_.absent})
Reproduction 'horizontal out-of-bounds aliases valid row' ($aliases.Count -gt 0)
$life = @(Read-Table $FullRun 'lifecycle')
Reproduction 'retained tile storage reused' (@($life | Where-Object {$_.case -eq '64 drops then pool reuse' -and $_.sameArray -and $_.readerStillReferencesRecycledCell -and $_.lazyEntryStillReturns}).Count -eq 1)
Reproduction 'stale close changes new owner' (@($life | Where-Object {$_.case -eq 'stale Tile.close after pool reborrow' -and $_.replacementChanged}).Count -eq 1)
Reproduction 'TTL removal bypasses pool release' (@($life | Where-Object {$_.case -eq 'TTL poll forced timestamp only' -and $_.removed -and $_.resourceStillOpen -and $_.poolItems -eq 0}).Count -eq 1)
Reproduction 'retained worker reader sees recycled data' (@($life | Where-Object {$_.case -eq 'worker retained reader across release' -and $_.changed}).Count -eq 1)
Reproduction 'forced failure does not return allocated arrays to pool' (@($life | Where-Object {$_.case -eq 'test-only forced filter failure after allocation' -and $_.returnedCellArrays -eq 0 -and $_.error}).Count -eq 1)
$closed=@(Read-Table $FullRun 'closed_cache_ownership')
Reproduction 'closed context caches remain globally retained' ($closed[0].globalManagerStillContainsFirstClosedCache)
$resources = @(Read-Table $FullRun 'cell_resources')
Reproduction 'proper nested resources survive exceptions' (@($resources | Where-Object {$_.depth -and (-not $_.unique -or -not $_.outerValuesPreserved -or -not $_.threadLocalClosed)}).Count -eq 0)
Reproduction 'fallback pool double close aliases borrows' (@($resources | Where-Object {$_.subsequentBorrowAliases}).Count -eq 1)
$missing = @(Read-Table $FullRun 'missing_context')
Reproduction 'missing preset produces deferred NPE' (@($missing | Where-Object {$_.case -eq 'initialized with empty preset registry' -and $_.error -match 'NullPointerException'}).Count -eq 1)
$vegetation = @(Read-Table $FullRun 'vegetation_disabled')
Reproduction 'disabled custom features fail real registry bootstrap' ($vegetation[0].bootstrap -eq 'failed' -and $vegetation[0].exceptionTree -match 'Unreferenced key.*placed_feature')
$orders = @()
foreach ($run in (@($FullRun) + $SchedulingRuns)) { $orders += @(Read-Table $run 'generation_order') }
$orderSummary = @($orders | Group-Object tileX,tileZ | ForEach-Object {
    [ordered]@{tile=$_.Name;observations=$_.Count;workers=@($_.Group.workers | Sort-Object -Unique);distinctHashes=@($_.Group.digest.hash | Sort-Object -Unique).Count}
})
Reproduction 'same tile output stable across tested orders and workers' (@($orderSummary | Where-Object {$_.distinctHashes -ne 1}).Count -eq 0)
$geometry = @(Read-Table $FullRun 'tile_geometry')
$geometrySummary = @($geometry | Group-Object size,border,batch | ForEach-Object {
    [ordered]@{settings=$_.Name;samples=$_.Count;changed=@($_.Group | Where-Object {$_.differentFromBaseline.Count}).Count;fields=@($_.Group.differentFromBaseline | Sort-Object -Unique)}
})
Reproduction 'batch partition alone unchanged' (@($geometry | Where-Object {$_.size -eq 3 -and $_.border -eq 1 -and $_.differentFromBaseline.Count}).Count -eq 0)
Reproduction 'tile geometry changes inherited filtered output' (@($geometry | Where-Object {$_.size -ne 3 -and $_.differentFromBaseline.Count}).Count -gt 0)
$overlap = @(Read-Table $FullRun 'tile_overlap')
Reproduction 'overlapping halos disagree' (@($overlap | Where-Object {$_.differentFields.Count}).Count -gt 0)
$chunks = @(Read-Table $FullRun 'generated_chunks')
$chunkPairs = @()
foreach ($a in @($chunks | Where-Object {$_.seed -eq 8675309})) {
    $b = @($chunks | Where-Object {$_.seed -eq 4303642605 -and $_.x -eq $a.x -and $_.z -eq $a.z})[0]
    if ($null -eq $b) { continue }
    $heightDifferences = 0
    for ($i=0; $i -lt $a.surfaceHeights.Count; $i++) { if ($a.surfaceHeights[$i] -ne $b.surfaceHeights[$i]) {$heightDifferences++} }
    $cellFields=@($a.exact.psobject.Properties | Where-Object {$_.Value -cne $b.exact.($_.Name)} | ForEach-Object Name)
    $chunkPairs += [ordered]@{x=$a.x;z=$a.z;differentSurfaceColumns=$heightDifferences;equalBlocks=$a.blocksSHA256 -eq $b.blocksSHA256;equalBlockBiomes=$a.biomesSHA256 -eq $b.biomesSHA256;equalStoredQuartBiomes=$a.storedQuartBiomesSHA256 -eq $b.storedQuartBiomesSHA256;equalBarrier=$a.barrierNoise -eq $b.barrierNoise;equalVeinToggle=$a.veinToggle -eq $b.veinToggle;equalFinalDensity=$a.finalDensity -eq $b.finalDensity;differentExactCellFields=$cellFields}
}
$counters = @(Read-Table $FullRun 'observer_counters')
Reproduction 'Forge queue/drop hooks run before harness interventions' ($counters[0].counters.queueCalls -gt 0 -and $counters[0].counters.dropCalls -gt 0)
$summary = [ordered]@{
    kind='Task 1A relational observations; no terrain golden baseline'
    fullRun=(Split-Path $FullRun -Leaf)
    schedulingRuns=@($SchedulingRuns | ForEach-Object {Split-Path $_ -Leaf})
    tests=$checks
    seedPairs=@($seeds | Group-Object seedA,seedB | ForEach-Object {[ordered]@{pair=$_.Name;points=$_.Count;different=@($_.Group|Where-Object {$_.differentFields.Count}).Count}})
    cache=[ordered]@{samples=$cached.Count;changed=$changed.Count;fieldCounts=@($cached.directVsFiltered|Group-Object|Sort-Object Name|Select-Object Name,Count);warmDifferences=@($cached|Where-Object {$_.filteredVsWarm.Count}).Count}
    crossWorld=@($cross | Select-Object sequence,bContaminationFields,aToBFields,differentFields)
    aliasCases=$aliases
    tileGeometry=$geometrySummary
    tileOverlap=[ordered]@{samples=$overlap.Count;changed=@($overlap|Where-Object {$_.differentFields.Count}).Count;fields=@($overlap.differentFields|Sort-Object -Unique)}
    concurrency=$orderSummary
    chunkSeedPairs=$chunkPairs
    postChunk=[ordered]@{samples=$chunks.Count;cached=@($chunks|Where-Object postChunkWasCached).Count;differentFromExact=@($chunks|Where-Object {$_.postChunkVsExact.Count}).Count;differentFromDirect=@($chunks|Where-Object {$_.postChunkVsDirect.Count}).Count}
    noiseRegisteredGraphDifferences=@($noise|Where-Object contaminated)
}
$json=($summary|ConvertTo-Json -Depth 20).Replace("`r`n","`n")
if ($OutputDirectory) {
    $dir=New-Item -ItemType Directory -Path $OutputDirectory -Force
    [IO.File]::WriteAllText((Join-Path $dir.FullName 'summary.json'), $json+"`n", [Text.UTF8Encoding]::new($false))
}
$checks | Format-Table -AutoSize
if (@($checks | Where-Object {-not $_.pass}).Count) { throw 'Reproduction observations changed or coverage missing. Investigate; do not update terrain goldens.' }
Write-Output ('PASS: '+$checks.Count+' relational reproduction checks')
