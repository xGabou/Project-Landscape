# Original sequential Task 3 verification runner. All Rights Reserved.
param([Parameter(Mandatory=$true)][string[]]$Names)
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
$profiles=@{
    'golden-24'=@('-PreproProfile=golden'); 'golden-2'=@('-PreproProfile=golden-scheduling','-PreproProcessors=2');
    'golden-48'=@('-PreproProfile=golden-scheduling','-PreproProcessors=48'); 'golden-repeat'=@('-PreproProfile=golden');
    'golden-tb'=@('-PreproProfile=golden','-PwithTerraBlender=true'); 'full'=@('-PreproProfile=full');
    'foundation'=@('-PreproProfile=foundation'); 'geography'=@('-PreproProfile=geography'); 'geography-final'=@('-PreproProfile=geography');
    'stage-final'=@('-PreproProfile=stage-extraction'); 'benchmark'=@('-PreproProfile=benchmark'); 'allocation'=@('-PreproProfile=allocation');
    'public-provider-benchmark'=@('-PreproProfile=public-provider-benchmark')
}
foreach($name in $Names){
    if(!$profiles.ContainsKey($name)){throw "Unknown gate $name"}
    if(Test-Path "docs/task3/evidence/$name"){throw "Refusing overwrite $name"}
    Write-Output "Starting $name"
    $ErrorActionPreference='Continue'
    & ./gradlew.bat runReproductionClient @($profiles[$name]) --console=plain *> "docs/task3/evidence/logs/$name.log"
    $code=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    if($code -ne 0){throw "$name Gradle failed: $code"}
    & "$PSScriptRoot/Collect-Run.ps1" -Name $name
    if($name -like 'golden-*'){
        $expected=Get-Content "docs/task1c/evidence/$name/tile_digests.json" -Raw | ConvertFrom-Json
        $actual=Get-Content "docs/task3/evidence/$name/tile_digests.json" -Raw | ConvertFrom-Json
        $index=@{};foreach($r in $expected){$index["$($r.seed):$($r.tileX):$($r.tileZ):$($r.order)"]=$r.sha256}
        if($actual.Count -ne $expected.Count){throw 'Tile count changed'}
        foreach($r in $actual){if($index["$($r.seed):$($r.tileX):$($r.tileZ):$($r.order)"] -cne $r.sha256){throw "Legacy tile changed in $name; STOP"}}
        if($name -eq 'golden-24'){& ./scripts/task2/Verify-Goldens.ps1 -CandidateRoot docs/task3/evidence -PrimaryOnly | Out-Null}
    }
    if($name -eq 'full'){& ./scripts/task1b/Verify-Repairs.ps1 -Run docs/task3/evidence/full -ThroughFix 11 -OutputDirectory docs/task3/evidence/task1b-verification}
    if($name -eq 'stage-final'){& "$PSScriptRoot/Verify-Stages.ps1" -Candidate docs/task3/evidence/stage-final}
    Write-Output "PASS $name"
}
