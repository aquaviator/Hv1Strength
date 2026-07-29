function Get-ReleaseVersionDecision {
    param(
        [Parameter(Mandatory = $true)]
        [int]$PlayVersionCode,

        [Parameter(Mandatory = $true)]
        [int]$LocalVersionCode,

        [Parameter(Mandatory = $true)]
        [string]$LocalVersionName
    )

    if ($PlayVersionCode -lt 1) {
        throw "PlayVersionCode must be at least 1."
    }

    if ($PlayVersionCode -eq [int]::MaxValue) {
        throw "PlayVersionCode cannot be incremented because it is already Int32.MaxValue."
    }

    $expectedLocalVersionName = "$LocalVersionCode.0"
    if (
        $LocalVersionName -notmatch '^\d+\.0$' -or
        $LocalVersionName -ne $expectedLocalVersionName
    ) {
        throw "Local versionName '$LocalVersionName' does not match the expected numeric convention '$expectedLocalVersionName'."
    }

    $nextVersionCode = $PlayVersionCode + 1
    $nextVersionName = "$nextVersionCode.0"

    if ($LocalVersionCode -lt $PlayVersionCode) {
        throw "Local versionCode $LocalVersionCode is behind Play versionCode $PlayVersionCode."
    }

    if ($LocalVersionCode -gt $nextVersionCode) {
        throw "Local versionCode $LocalVersionCode is more than one version ahead of Play versionCode $PlayVersionCode. Review the unreleased version before deploying."
    }

    [pscustomobject]@{
        PlayVersionCode = $PlayVersionCode
        LocalVersionCode = $LocalVersionCode
        LocalVersionName = $LocalVersionName
        TargetVersionCode = $nextVersionCode
        TargetVersionName = $nextVersionName
        RequiresUpdate = $LocalVersionCode -eq $PlayVersionCode
    }
}

function Set-AndroidReleaseVersion {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BuildGradle,

        [Parameter(Mandatory = $true)]
        [int]$VersionCode,

        [Parameter(Mandatory = $true)]
        [string]$VersionName
    )

    $versionCodePattern = '(?m)^(\s*versionCode\s*=\s*)\d+(\s*(?://.*)?)$'
    $versionNamePattern = '(?m)^(\s*versionName\s*=\s*)"[^"]+"(\s*(?://.*)?)$'
    $versionCodeMatches = [regex]::Matches($BuildGradle, $versionCodePattern)
    $versionNameMatches = [regex]::Matches($BuildGradle, $versionNamePattern)

    if ($versionCodeMatches.Count -ne 1) {
        throw "Expected exactly one versionCode declaration, found $($versionCodeMatches.Count)."
    }

    if ($versionNameMatches.Count -ne 1) {
        throw "Expected exactly one versionName declaration, found $($versionNameMatches.Count)."
    }

    $updated = [regex]::Replace(
        $BuildGradle,
        $versionCodePattern,
        { param($match) "$($match.Groups[1].Value)$VersionCode$($match.Groups[2].Value)" }
    )

    [regex]::Replace(
        $updated,
        $versionNamePattern,
        { param($match) "$($match.Groups[1].Value)`"$VersionName`"$($match.Groups[2].Value)" }
    )
}
