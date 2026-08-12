function Test-InternalReleaseBranch {
    param([Parameter(Mandatory = $true)][string]$Branch)

    $Branch -match '^Version\d+$' -or
        $Branch -match '^release/strength-v\d+-rc\d+$'
}

function Assert-InternalReleaseTrackedChanges {
    param(
        [string[]]$ChangedFiles,
        [switch]$AllowVersionFile
    )

    $allowed = @('app/diagnostic.txt')
    if ($AllowVersionFile) { $allowed += 'app/build.gradle.kts' }
    $unexpected = @($ChangedFiles | Where-Object { $_ -notin $allowed })
    if ($unexpected.Count -gt 0) {
        throw "Unexpected tracked changes: $($unexpected -join ', ')."
    }
}

function Assert-SigningEnvironment {
    param([hashtable]$Environment)

    if (-not $Environment.STORE_PASSWORD) {
        throw 'STORE_PASSWORD must be supplied by the inherited process environment.'
    }
    if (-not $Environment.KEY_PASSWORD) {
        throw 'KEY_PASSWORD must be supplied by the inherited process environment.'
    }
}

function Assert-DiagnosticUnchanged {
    param(
        [Parameter(Mandatory = $true)][string]$ExpectedHash,
        [Parameter(Mandatory = $true)][string]$ActualHash
    )

    if ($ExpectedHash -ne $ActualHash) {
        throw 'app/diagnostic.txt changed during the internal release build.'
    }
}

function Invoke-WithFileRollback {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][scriptblock]$Action
    )

    $original = [System.IO.File]::ReadAllBytes($Path)
    try {
        & $Action
    }
    catch {
        [System.IO.File]::WriteAllBytes($Path, $original)
        throw
    }
}
