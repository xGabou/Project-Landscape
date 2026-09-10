param([string]$Group='offline')
$ErrorActionPreference='Continue'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
function Invoke-Gate($name,$task,$arguments){
 $log="$root/build/task6c-$name.log"
 if(Test-Path "$root/build/task6c-$name-complete.json"){Write-Output "Already completed $name";return}
 if(Test-Path $log){throw "Inspect prior incomplete log before retry: $log"}
 $started=[DateTime]::UtcNow
 & ./gradlew.bat $task @arguments --offline --console=plain *> $log
 if($LASTEXITCODE -ne 0){throw "Gate failed: $name"}
 $evidence=$null
 if($task -eq 'runReproductionClient'){
  $marker=Get-Item run/task1a/task1a-pass.txt
  if($marker.LastWriteTimeUtc -lt $started){throw "Stale marker $name"}
  $run=(Get-Content $marker.FullName -Raw).Trim()
  if($run -notmatch '^reproduction-[0-9]+$'){throw 'Invalid marker'}
  $evidence="$root/build/task6c-$name"
  if(Test-Path $evidence){throw "Refusing existing evidence $evidence"}
  Copy-Item -LiteralPath "$root/run/task1a/evidence/$run" -Destination $evidence -Recurse
 }
 @{name=$name;startedUtc=$started.ToString('o');finishedUtc=[DateTime]::UtcNow.ToString('o');task=$task;arguments=$arguments;evidence=$evidence}|ConvertTo-Json -Depth 8|Set-Content "$root/build/task6c-$name-complete.json"
 Write-Output "Completed $name"
}
if($Group -eq 'offline'){
 foreach($workers in @(1,2,8,24,48)){Invoke-Gate "final-determinism-$workers" runData @('-Ptask6bMode=determinism',"-Ptask6bWorkers=$workers","-Ptask6bOutput=build/task6c-final-determinism-$workers")}
 Invoke-Gate 'final-ownership' runData @('-Ptask6bMode=ownership','-Ptask6bSourceChecks=true','-Ptask6cMetrics=true','-Ptask6bOutput=build/task6c-final-ownership')
 Invoke-Gate 'final-task4' task4Survey @('-Ptask4Output=build/task6c-final-task4')
 Invoke-Gate 'final-task5' task5ClimateSurvey @('-Ptask5Output=build/task6c-final-task5')
 Invoke-Gate 'final-task6' task6BiomeSurvey @('-Ptask6ChecksOnly=true','-Ptask6Output=build/task6c-final-task6')
 Invoke-Gate 'final-cache' task6bCacheChecks @('-Ptask6bOutput=build/task6c-final-cache')
 Invoke-Gate 'final-legacy' runData @('-Ptask6bMode=legacy','-Ptask6bOutput=build/task6c-final-legacy')
}
if($Group -eq 'runtime'){
 Invoke-Gate 'final-full-8' runReproductionClient @('-PreproProfile=task6b','-PreproProcessors=8','-PwithTerraBlender=true','-Ptask6bFinal=true')
 Invoke-Gate 'final-task1b' runReproductionClient @('-PreproProfile=full','-PreproProcessors=24','-PwithTerraBlender=false')
 Invoke-Gate 'final-legacy-tb' runReproductionClient @('-PreproProfile=golden','-PreproProcessors=24','-PreproSeeds=8675309','-PwithTerraBlender=true')
}
if($Group -eq 'build'){Invoke-Gate 'final-build' build @()}
exit 0
