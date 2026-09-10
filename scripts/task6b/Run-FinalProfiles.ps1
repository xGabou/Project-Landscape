param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
$runs=[ordered]@{
 'baseline-original'='docs/task6b/evidence/full-baseline'
 'baseline-repeat'='build/task6b-full-baseline-03'
 'baseline-extended'='build/task6b-full-baseline-04'
 'optimized-original'='docs/task6b/evidence/runtime-final-01'
 'optimized-extended'='build/task6b-full-optimized-02'
 'optimized-24'='build/task6b-full-workers-24'
 'optimized-48'='build/task6b-full-workers-48'
}
$out=New-Item -ItemType Directory -Force -Path "$root/docs/task6b/evidence/profiles"
foreach($run in $runs.GetEnumerator()) {
 foreach($profile in (Get-Content "$($run.Value)/task6b_profiles.json" -Raw | ConvertFrom-Json)) {
  $destination="$($out.FullName)/$($run.Key)-$($profile.version).json"
  if(Test-Path -LiteralPath $destination){continue}
  if(!(Test-Path -LiteralPath $profile.path)){throw "Missing retained recording $($profile.path)"}
  $ErrorActionPreference='Continue'
  & ./gradlew.bat task6bJfrSummary "-Ptask6bJfr=$($profile.path)" "-Ptask6bSummary=$destination" --offline --console=plain
  $code=$LASTEXITCODE
  $ErrorActionPreference='Stop'
  if($code -ne 0){throw "JFR summary failed: $($run.Key) $($profile.version)"}
 }
}
