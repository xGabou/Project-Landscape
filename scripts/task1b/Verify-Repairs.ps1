# Original correctness/comparator checks. All Rights Reserved.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Run,
    [string]$Comparator = 'docs/task1/evidence/full-24-a',
    [int]$ThroughFix = 1,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
function Table([string]$dir, [string]$name) { return (Get-Content -LiteralPath (Join-Path $dir "$name.json") -Raw | ConvertFrom-Json) }
function Different($a, $b) { @($a.psobject.Properties | Where-Object {$_.Value -cne $b.($_.Name)} | ForEach-Object Name) }
$checks = [Collections.Generic.List[object]]::new()
function Check([string]$name, [bool]$pass) { $checks.Add([ordered]@{name=$name;pass=$pass}) }
Check 'real Forge harness completed' ((Table $Run 'completion')[0].status -eq 'PASS')
$cross = Table $Run 'cross_world_cache'
$sequences = @($cross | Where-Object {$null -ne $_.bContaminationFields})
Check 'cross-context sequences recompute for their owner' ($sequences.Count -ge 3 -and @($sequences | Where-Object {$_.bContaminationFields.Count -or (Different $_.a $_.aAgain).Count}).Count -eq 0)
$same = @($cross | Where-Object {$_.sequence -like 'same seed*'})
Check 'same seed distinct context identity is isolated' ($same.Count -eq 2 -and @($same | Where-Object {$_.bContaminationFields.Count -or $_.aToBFields.Count -eq 0}).Count -eq 0)
if ($ThroughFix -ge 2) {
    $mode=@($cross | Where-Object {$_.sequence -eq 'same context false-true climate'})
    $warm=@($cross | Where-Object {$_.sequence -eq 'same context tile becomes warm'})
    Check 'climate mode change refreshes cache' ($mode.Count -eq 1 -and $mode[0].differentFields.Count -eq 0)
    Check 'new tile refreshes same-position cache' ($warm.Count -eq 1 -and $warm[0].staleVsRefreshed.Count -eq 0 -and $warm[0].beforeVsStale.Count -gt 0)
    $semantics=@(Table $Run 'sampling_semantics')
    Check 'eviction regeneration and reverse climate mode refresh' ($semantics.Count -eq 3 -and @($semantics | Where-Object {$_.differentFields.Count -or $_.coldVsEvicted.Count -or $_.warmVsRegenerated.Count}).Count -eq 0)
}

# Compare valid independently generated terrain paths; never bless contaminated query values.
if ($ThroughFix -ge 6) {
    $resources=@(Table $Run 'cell_resources')
    Check 'fallback double close cannot alias independent borrows' (@($resources | Where-Object {$_.case -eq 'isolated fallback pool double close' -and -not $_.subsequentBorrowAliases}).Count -eq 1)
    Check 'stale fallback close cannot release new borrow' (@($resources | Where-Object {$_.case -eq 'stale fallback close after reborrow' -and $_.independentOpenBorrows}).Count -eq 1)
    Check 'normal nested and exception paths remain safe' (@($resources | Where-Object {$_.depth}).Count -eq 4 -and @($resources | Where-Object {$_.depth -and (-not $_.unique -or -not $_.outerValuesPreserved -or -not $_.threadLocalClosed)}).Count -eq 0)
    Check 'normal concurrent samples stay independent' (@($resources | Where-Object {$_.case -eq 'concurrent normal resources' -and $_.distinctCells -eq 4}).Count -eq 1)
    Check 'concurrent nested fallback borrows stay independent' (@($resources | Where-Object {$_.case -eq 'concurrent nested fallback' -and $_.distinctCells -eq 4}).Count -eq 1)
    Check 'foreign thread cannot release thread-local fallback' (@($resources | Where-Object {$_.case -eq 'foreign thread release' -and $_.stillOpen -and $_.error -match 'borrowing thread'}).Count -eq 1)
}
if ($ThroughFix -ge 3) {
    $bounds=@(Table $Run 'tile_bounds')
    Check 'independent tile axes reject all invalid coordinates' ($bounds.Count -ge 11 -and @($bounds | Where-Object {$_.axisInBounds -eq $_.absent}).Count -eq 0)
}
if ($ThroughFix -ge 4) {
    $life=@(Table $Run 'lifecycle')
    $reuse=@($life | Where-Object {$_.case -eq '64 drops then pool reuse'})
    Check 'published storage is not recycled' ($reuse.Count -eq 1 -and -not $reuse[0].sameArray -and (Different $reuse[0].before $reuse[0].afterReuse).Count -eq 0 -and (Different $reuse[0].before $reuse[0].afterRelease).Count -eq 0)
    Check 'retained worker sees original sample' (@($life | Where-Object {$_.case -eq 'worker retained reader across release' -and -not $_.changed}).Count -eq 1)
    Check 'stale close cannot corrupt replacement' (@($life | Where-Object {$_.case -eq 'stale Tile.close after pool reborrow' -and -not $_.replacementChanged}).Count -eq 1)
    Check 'published close is repeatable and retains readers' (@($life | Where-Object {$_.case -eq 'repeated published close' -and $_.unchanged}).Count -eq 1)
    Check 'detached publication releases workspace pool storage' (@($life | Where-Object {$_.case -eq 'detached publication cost' -and $_.unchanged -and $_.workspacePoolItems -gt 0}).Count -eq 1)
}
if ($ThroughFix -ge 5) {
    $life=@(Table $Run 'lifecycle');$disposed=@(Table $Run 'disposal')
    Check 'expiry closes the removed snapshot' (@($life | Where-Object {$_.case -eq 'TTL poll forced timestamp only' -and $_.removed -and -not $_.resourceStillOpen}).Count -eq 1)
    Check 'filter failure returns workspace arrays' (@($life | Where-Object {$_.case -eq 'test-only forced filter failure after allocation' -and $_.returnedCellArrays -eq 1}).Count -eq 1)
    Check 'closed test caches unregistered' (-not (Table $Run 'closed_cache_ownership')[0].globalManagerStillContainsFirstClosedCache)
    Check 'capacity eviction disposes unread future and preserves reader' (@($disposed | Where-Object {$_.case -eq 'capacity eviction without lazy get' -and $_.firstClosed -and $_.readerUnchanged}).Count -eq 1)
    Check 'stale entry cannot remove replacement' (@($disposed | Where-Object {$_.case -eq 'identity conditional removal' -and -not $_.wrongEntryRemoved -and $_.replacementPresent}).Count -eq 1)
    Check 'pending future disposed on completion after close' (@($disposed | Where-Object {$_.case -eq 'pending completion after shutdown' -and $_.closed}).Count -eq 1)
    Check 'context replacement retires old cache and refreshes sampler' (@($disposed | Where-Object {$_.case -eq 'RandomState context replacement' -and $_.oldClosed -and $_.distinctContext -and $_.sameSample}).Count -eq 1)
    Check 'cancelled real generation drains both pools' (@($disposed | Where-Object {$_.case -eq 'cancelled real tile workspaces' -and $_.cancelled -gt 0 -and $_.cells.live -eq 0 -and $_.chunks.live -eq 0}).Count -eq 1)
    Check 'stale workspace close cannot release replacement borrow' (@($disposed | Where-Object {$_.case -eq 'stale workspace close after pool reuse' -and $_.unchanged -and $_.cells.live -eq 0 -and $_.chunks.live -eq 0}).Count -eq 1)
    Check 'array pool releases once even when full' (@($disposed | Where-Object {$_.case -eq 'array handles and full pool' -and $_.staleHandleSafe -and $_.allClosed -and $_.statistics.live -eq 0 -and $_.statistics.borrowed -eq 3 -and $_.statistics.returned -eq 3}).Count -eq 1)
    $unloads=@($disposed | Where-Object {$_.case -eq 'actual world unload'})
    Check 'real world/server shutdown unregisters caches and drains pools' ($unloads.Count -eq 3 -and @($unloads | Where-Object {-not $_.allClosed -or -not $_.allUnregistered -or $_.livePooledBorrows -ne 0}).Count -eq 0)
}
$comparisons = [Collections.Generic.List[object]]::new()
foreach ($name in @('cached_uncached','seed_collisions','generation_order','generated_chunks')) {
    $before = Table $Comparator $name; $after = Table $Run $name
    Check "$name fixture count preserved" ($before.Count -eq $after.Count)
    $changes = [Collections.Generic.List[object]]::new()
    for ($i=0; $i -lt [Math]::Min($before.Count,$after.Count); $i++) {
        $a=$before[$i]; $b=$after[$i]
        switch ($name) {
            'cached_uncached' { $fields=@(Different $a.direct $b.direct) + @(Different $a.filtered $b.filtered) }
            'seed_collisions' { $fields=@(Different $a.a $b.a) + @(Different $a.b $b.b) }
            'generation_order' { $fields=@(); if ($a.digest.hash -cne $b.digest.hash) {$fields=@('digest')} }
            'generated_chunks' { $fields=@(Different $a.exact $b.exact); if ($a.storedQuartBiomesSHA256 -cne $b.storedQuartBiomesSHA256) {$fields+= 'storedQuartBiomesSHA256'} }
        }
        if ($fields.Count) { $changes.Add([ordered]@{row=$i;point=$b.point;seed=$b.seed;x=$b.x;z=$b.z;tileX=$b.tileX;tileZ=$b.tileZ;fields=$fields}) }
    }
    $comparisons.Add([ordered]@{table=$name;count=$after.Count;changed=$changes.Count;changes=$changes})
    Check "$name canonical comparator unchanged" ($changes.Count -eq 0)
}
$cached = Table $Run 'cached_uncached'
Check 'same warm path repeats' (@($cached | Where-Object {$_.filteredVsWarm.Count}).Count -eq 0)
Check 'eviction and regeneration retain each path semantics' (@(Table $Run 'eviction_reload' | Where-Object {$_.directVsAfterDrop.Count -or $_.filteredVsRegenerated.Count}).Count -eq 0)
$summary = [ordered]@{throughFix=$ThroughFix;run=(Split-Path $Run -Leaf);comparator=$Comparator;checks=$checks;comparisons=$comparisons;
    crossWorld=$sequences;cachedDirect=[ordered]@{count=$cached.Count;different=@($cached | Where-Object {$_.directVsFiltered.Count}).Count;fields=@($cached.directVsFiltered | Group-Object | Sort-Object Name | Select-Object Name,Count)}}
$dir = New-Item -ItemType Directory -Path $OutputDirectory -Force
[IO.File]::WriteAllText((Join-Path $dir.FullName 'verification.json'), ($summary | ConvertTo-Json -Depth 30).Replace("`r`n","`n")+"`n", [Text.UTF8Encoding]::new($false))
$checks | ForEach-Object {[pscustomobject]$_} | Format-Table -AutoSize
if (@($checks | Where-Object {-not $_.pass}).Count) { throw 'Correctness/comparator check failed; see verification.json' }
