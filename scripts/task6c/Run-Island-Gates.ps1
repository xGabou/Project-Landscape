$ErrorActionPreference='Continue'
Set-Location (Resolve-Path "$PSScriptRoot/../..").Path
$cases=@(
 @{name='island-macro-01';mode='macro-determinism'},
 @{name='island-determinism-01';mode='determinism'},
 @{name='island-ownership-01';mode='ownership'},
 @{name='island-experiment-02';mode='island-experiment'}
)
foreach($case in $cases){
 $output="build/task6c-$($case.name)"
 if(Test-Path $output){throw "Refusing existing evidence $output"}
 & ./gradlew.bat runData "-Ptask6bMode=$($case.mode)" "-Ptask6bOutput=$output" --offline --console=plain *> "$output.log"
 if($LASTEXITCODE -ne 0){throw "Failed $output"}
 Write-Output "Completed $output"
}
exit 0
