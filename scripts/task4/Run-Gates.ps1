# Original Task 4 sequential real-Forge gate runner. All Rights Reserved.
param([Parameter(Mandatory=$true)][string[]]$Names)
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
New-Item -ItemType Directory -Force docs/task4/evidence/logs | Out-Null
$profiles=@{
 'golden-24'=@('-PreproProfile=golden'); 'golden-2'=@('-PreproProfile=golden-scheduling','-PreproProcessors=2');
 'golden-48'=@('-PreproProfile=golden-scheduling','-PreproProcessors=48'); 'golden-repeat'=@('-PreproProfile=golden');
 'golden-tb'=@('-PreproProfile=golden','-PwithTerraBlender=true'); 'full'=@('-PreproProfile=full');
 'foundation'=@('-PreproProfile=foundation'); 'geography'=@('-PreproProfile=geography');
 'v1-terrain'=@('-PreproProfile=task4-terrain','-PreproSeeds=8675309');
 'v1-terrain-2'=@('-PreproProfile=task4-terrain','-PreproSeeds=8675309','-PreproProcessors=2')
}
foreach($name in $Names){
 if(!$profiles.ContainsKey($name)){throw "Unknown gate $name"}
 if(Test-Path "docs/task4/evidence/$name"){throw "Refusing overwrite $name"}
 Write-Output "Starting $name"
 $ErrorActionPreference='Continue'
 & ./gradlew.bat runReproductionClient @($profiles[$name]) --console=plain *> "docs/task4/evidence/logs/$name.log"
 $code=$LASTEXITCODE
 $ErrorActionPreference='Stop'
 if($code -ne 0){throw "$name Gradle failed: $code"}
 & "$PSScriptRoot/Collect-Run.ps1" -Name $name
 if($name -eq 'golden-24'){& ./scripts/task2/Verify-Goldens.ps1 -CandidateRoot docs/task4/evidence -PrimaryOnly | Out-Null}
 if($name -eq 'full'){& ./scripts/task1b/Verify-Repairs.ps1 -Run docs/task4/evidence/full -ThroughFix 11 -OutputDirectory docs/task4/evidence/task1b-verification}
 Write-Output "PASS $name"
}
