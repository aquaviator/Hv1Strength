$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'release-versioning.ps1')
. (Join-Path $PSScriptRoot 'internal-release-policy.ps1')

$passed = 0
function Assert-True([bool]$Value, [string]$Message) {
    if (-not $Value) { throw $Message }
    $script:passed++
}
function Assert-Throws([scriptblock]$Action, [string]$Pattern) {
    try { & $Action; throw 'Expected an exception.' }
    catch {
        if ($_.Exception.Message -eq 'Expected an exception.' -or $_.Exception.Message -notmatch $Pattern) { throw }
    }
    $script:passed++
}

$d = Get-ReleaseVersionDecision -PlayVersionCode 28 -LocalVersionCode 28 -LocalVersionName '28.0'
Assert-True ($d.TargetVersionCode -eq 29 -and $d.TargetVersionName -eq '29.0' -and $d.RequiresUpdate) '28 must advance to 29/29.0.'
$d = Get-ReleaseVersionDecision -PlayVersionCode 28 -LocalVersionCode 29 -LocalVersionName '29.0'
Assert-True (-not $d.RequiresUpdate -and $d.TargetVersionCode -eq 29) 'Existing local 29 must be accepted.'
Assert-Throws { Get-ReleaseVersionDecision -PlayVersionCode 28 -LocalVersionCode 29 -LocalVersionName '29.1' } 'numeric convention'
Assert-True (Test-InternalReleaseBranch 'release/strength-v29-rc1') 'Release-candidate branch must be accepted.'
Assert-True (Test-InternalReleaseBranch 'Version3') 'Version branch must remain accepted.'
Assert-Throws { Assert-InternalReleaseTrackedChanges @('app/diagnostic.txt', 'README.md') } 'Unexpected tracked changes'
Assert-InternalReleaseTrackedChanges @('app/diagnostic.txt'); $passed++
Assert-InternalReleaseTrackedChanges @('app/diagnostic.txt', 'app/build.gradle.kts') -AllowVersionFile; $passed++
Assert-Throws { Assert-DiagnosticUnchanged 'before' 'after' } 'changed during'
Assert-Throws { Assert-SigningEnvironment @{} } 'STORE_PASSWORD'
Assert-Throws { Assert-SigningEnvironment @{ STORE_PASSWORD = 'present' } } 'KEY_PASSWORD'
$captured = (& { Assert-SigningEnvironment @{ STORE_PASSWORD = 'secret-one'; KEY_PASSWORD = 'secret-two' }; 'ok' } | Out-String)
Assert-True ($captured -notmatch 'secret-one|secret-two') 'Credential values must not be printed.'
Assert-True ((Get-Command Test-InternalReleaseBranch).Name -eq 'Test-InternalReleaseBranch') 'Local policy checks must not require a remote.'

$temp = Join-Path ([IO.Path]::GetTempPath()) "hv1-release-rollback-$([guid]::NewGuid()).txt"
try {
    [IO.File]::WriteAllText($temp, 'before')
    Assert-Throws { Invoke-WithFileRollback $temp { [IO.File]::WriteAllText($temp, 'after'); throw 'build failed' } } 'build failed'
    Assert-True ((Get-Content $temp -Raw) -eq 'before') 'Failed build must restore the version file.'
} finally { Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue }

Write-Host "Internal release policy tests passed: $passed"
