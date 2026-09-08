# Original Task 2 sequential verification runner. All Rights Reserved.
param([Parameter(Mandatory=$true)][string[]]$Names)
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
$profiles=@{
    'golden-48'=@('-PreproProfile=golden-scheduling','-PreproProcessors=48');
    'golden-repeat'=@('-PreproProfile=golden');
    'golden-tb'=@('-PreproProfile=golden','-PwithTerraBlender=true');
    'foundation-tb-final'=@('-PreproProfile=foundation','-PwithTerraBlender=true');
    'full-regression'=@('-PreproProfile=full');
    'benchmark-final'=@('-PreproProfile=benchmark');
    'allocation'=@('-PreproProfile=allocation')
}
foreach($name in $Names){
    if(!$profiles.ContainsKey($name)){throw "Unknown measurement $name"}
    if(Test-Path "docs/task2/evidence/$name"){throw "Evidence already exists: $name"}
    $arguments=@('runReproductionClient')+$profiles[$name]+@('--console=plain')
    Write-Output "Starting $name"
    $ErrorActionPreference='Continue'
    & "$root/gradlew.bat" @arguments *> "$root/docs/task2/evidence/logs/$name.log"
    $code=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    if($code -ne 0){throw "$name failed with Gradle exit $code"}
    & "$PSScriptRoot/Collect-Run.ps1" -Name $name
    if($name -like 'golden-*'){
        $actual=Get-Content "docs/task2/evidence/$name/tile_digests.json" -Raw | ConvertFrom-Json
        $expected=Get-Content "docs/task1c/evidence/$name/tile_digests.json" -Raw | ConvertFrom-Json
        $index=@{};foreach($row in $expected){$index["$($row.seed):$($row.tileX):$($row.tileZ):$($row.order)"]=$row.sha256}
        if($actual.Count -ne $expected.Count){throw "$name tile count changed"}
        foreach($row in $actual){if($index["$($row.seed):$($row.tileX):$($row.tileZ):$($row.order)"] -cne $row.sha256){throw "$name legacy tile changed; stop"}}
        Write-Output "$name exact tile comparator PASS ($($actual.Count))"
    }
    if($name -eq 'full-regression'){
        & "$root/scripts/task1b/Verify-Repairs.ps1" -Run "docs/task2/evidence/$name" -ThroughFix 11 -OutputDirectory 'docs/task2/evidence/task1b-verification'
    }
}
