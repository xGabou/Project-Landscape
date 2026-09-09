param([switch]$Resume)
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
$cases=@(
 @{name='baseline-04';root="$root/build/task6b-frozen";task='runData';args=@('-Ptask6bOutput=build/task6b-baseline-04')},
 @{name='optimized-03';root=$root;task='runData';args=@('-Ptask6bOutput=build/task6b-optimized-03')},
 @{name='full-baseline-03';root="$root/build/task6b-frozen";task='runReproductionClient';args=@('-PreproProfile=task6b','-PreproProcessors=8','-PwithTerraBlender=true')},
 @{name='full-optimized-02';root=$root;task='runReproductionClient';args=@('-PreproProfile=task6b','-PreproProcessors=8','-PwithTerraBlender=true')},
 @{name='full-workers-24';root=$root;task='runReproductionClient';args=@('-PreproProfile=task6b','-PreproProcessors=24','-PwithTerraBlender=true')},
 @{name='full-workers-48';root=$root;task='runReproductionClient';args=@('-PreproProfile=task6b','-PreproProcessors=48','-PwithTerraBlender=true')},
 @{name='full-baseline-04';root="$root/build/task6b-frozen";task='runReproductionClient';args=@('-PreproProfile=task6b','-PreproProcessors=8','-PwithTerraBlender=true')},
 @{name='legacy-runtime-24';root=$root;task='runReproductionClient';args=@('-PreproProfile=golden','-PreproProcessors=24','-PreproSeeds=8675309','-PwithTerraBlender=false')},
 @{name='legacy-runtime-tb';root=$root;task='runReproductionClient';args=@('-PreproProfile=golden','-PreproProcessors=24','-PreproSeeds=8675309','-PwithTerraBlender=true')},
 @{name='task1b-final';root=$root;task='runReproductionClient';args=@('-PreproProfile=full','-PreproProcessors=24','-PwithTerraBlender=false')}
)
foreach($case in $cases){
 $marker="$root/build/task6b-$($case.name)-complete.json"
 if(Test-Path -LiteralPath $marker){if($Resume){continue}else{throw "Existing completion $marker"}}
 $log="$root/build/task6b-$($case.name)-resume.log"
 if(Test-Path -LiteralPath $log){$log="$root/build/task6b-$($case.name)-resume-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()).log"}
 Set-Location $case.root
 $start=[DateTime]::UtcNow
 Write-Output "Starting $($case.name)"
 $ErrorActionPreference='Continue'
 & ./gradlew.bat $case.task @($case.args) --offline --console=plain *> $log
 $code=$LASTEXITCODE
 $ErrorActionPreference='Stop'
 if($code -ne 0){throw "$($case.name) failed ($code): $log"}
 $result=[ordered]@{name=$case.name;root=$case.root;arguments=$case.args;startedUtc=$start.ToString('o');finishedUtc=[DateTime]::UtcNow.ToString('o');log=$log;exitCode=$code}
 if($case.task -eq 'runReproductionClient'){
  $pass=Get-Item 'run/task1a/task1a-pass.txt'
  if($pass.LastWriteTimeUtc -lt $start){throw 'Stale runtime marker'}
  $run=(Get-Content -LiteralPath $pass.FullName -Raw).Trim()
  if($run -notmatch '^reproduction-[0-9]+$'){throw 'Invalid runtime marker'}
  $destination="$root/build/task6b-$($case.name)"
  if(Test-Path -LiteralPath $destination){throw "Refusing overwrite $destination"}
  Copy-Item -LiteralPath "$($case.root)/run/task1a/evidence/$run" -Destination $destination -Recurse
  $result['evidence']=$destination
 }
 [IO.File]::WriteAllText($marker,($result|ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))
 Write-Output "Completed $($case.name)"
}
