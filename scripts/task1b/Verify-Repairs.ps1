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
