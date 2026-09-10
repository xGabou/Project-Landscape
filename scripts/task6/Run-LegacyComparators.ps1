# Task 6 fresh legacy comparator orchestration. Original ARR code.
param([string]$Output='build/task6-legacy-current',[switch]$Resume)
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $taskRoot
$destination=[IO.Path]::GetFullPath((Join-Path $taskRoot $Output))
if(!$destination.StartsWith($taskRoot+[IO.Path]::DirectorySeparatorChar)){throw 'Output must stay inside the task worktree'}
if(Test-Path -LiteralPath $destination){if(!$Resume){throw "Refusing existing evidence directory without -Resume: $destination"}}
else{New-Item -ItemType Directory -Path $destination | Out-Null}
$cases=@(
    @{name='golden-24';profile='golden';processors=24;tb=$false},
    @{name='golden-2';profile='golden-scheduling';processors=2;tb=$false},
    @{name='golden-48';profile='golden-scheduling';processors=48;tb=$false},
    @{name='golden-repeat';profile='golden';processors=24;tb=$false},
    @{name='golden-tb';profile='golden';processors=24;tb=$true}
)
foreach($case in $cases){
    $saved=Join-Path $destination $case.name
    if(Test-Path -LiteralPath $saved){
        if(!$Resume){throw "Existing suite $saved"}
        $completion=Get-Content -LiteralPath (Join-Path $saved 'completion.json') -Raw | ConvertFrom-Json
        if(@($completion | Where-Object {$_.status -eq 'PASS'}).Count -ne 1){throw "Incomplete saved suite $saved"}
        Write-Output "Retaining completed suite $($case.name)"
        continue
    }
    $started=[DateTime]::UtcNow
    $arguments=@('runReproductionClient',"-PreproProfile=$($case.profile)","-PreproProcessors=$($case.processors)",'--offline','--console=plain')
    if($case.tb){$arguments+='-PwithTerraBlender=true'}
    Write-Output "Starting fresh $($case.name)"
    $ErrorActionPreference='Continue'
    $logPath=Join-Path $destination "$($case.name).log"
    if(Test-Path -LiteralPath $logPath){$logPath=Join-Path $destination "$($case.name)-retry-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()).log"}
    & "$taskRoot/gradlew.bat" @arguments *> $logPath
    $exitCode=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    if($exitCode -ne 0){throw "$($case.name) failed with exit $exitCode; inspect its log"}
    $marker=Get-Item 'run/task1a/task1a-pass.txt'
    if($marker.LastWriteTimeUtc -lt $started){throw 'Stale runtime completion marker'}
    $run=(Get-Content -LiteralPath $marker.FullName -Raw).Trim()
    if($run -notmatch '^reproduction-[0-9]+$'){throw 'Invalid runtime marker'}
    Copy-Item -LiteralPath (Join-Path "$taskRoot/run/task1a/evidence" $run) -Destination (Join-Path $destination $case.name) -Recurse
    Write-Output "$($case.name) finished: $run"
}
& "$taskRoot/scripts/task2/Verify-Goldens.ps1" -CandidateRoot $destination
