param(
    [Parameter(Mandatory = $true)]
    [int]$PlayVersionCode,

    [string]$KeystorePath =
        "D:\App-Security-Keys\PlayStore\my-upload-key.jks",

    [string]$CredentialsPath =
        "D:\App-Security-Keys\PlayStore\passwords.txt",

    [switch]$SkipDebugBuild,

    [switch]$RunTests,

    [switch]$VersionCheckOnly
)

$ErrorActionPreference = "Stop"
$script:VersionChangePending = $false
$script:VersionRollbackPath = $null
$script:VersionRollbackBytes = $null

trap {
    if (
        $script:VersionChangePending -and
        $script:VersionRollbackPath -and
        $null -ne $script:VersionRollbackBytes
    ) {
        [System.IO.File]::WriteAllBytes(
            $script:VersionRollbackPath,
            $script:VersionRollbackBytes
        )
        Write-Host ""
        Write-Host "Restored the original app/build.gradle.kts version after failure." -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "DEPLOYMENT STOPPED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

$versioningHelper =
    Join-Path $PSScriptRoot "release-versioning.ps1"

if (-not (Test-Path -LiteralPath $versioningHelper)) {
    throw "Missing release versioning helper: $versioningHelper"
}

. $versioningHelper

# ============================================================
# Helpers
# ============================================================

function Write-Step {
    param([string]$Message)

    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-OK {
    param([string]$Message)

    Write-Host $Message -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)

    Write-Host $Message -ForegroundColor Yellow
}

function Fail {
    param([string]$Message)

    throw $Message
}

# ============================================================
# Repository
# ============================================================

try {
    $repoRoot = (git rev-parse --show-toplevel 2>$null).Trim()
}
catch {
    Fail "This command must be run inside the Hv1Strength Git repository."
}

if (-not $repoRoot) {
    Fail "Could not determine the Git repository root."
}

Set-Location $repoRoot

Write-Step "Human Strength internal-release preflight"

Write-Host "Repository: $repoRoot"

# ============================================================
# Java
# ============================================================

Write-Step "Checking Java"

$androidStudioJbr =
    "C:\Program Files\Android\Android Studio\jbr"

$javaHomeValid =
    $env:JAVA_HOME -and
    (Test-Path -LiteralPath (
        Join-Path $env:JAVA_HOME "bin\java.exe"
    ))

if (-not $javaHomeValid) {

    $androidStudioJava =
        Join-Path $androidStudioJbr "bin\java.exe"

    if (Test-Path -LiteralPath $androidStudioJava) {
        $env:JAVA_HOME = $androidStudioJbr
    }
    else {
        Fail "Java was not found in JAVA_HOME or Android Studio JBR."
    }
}

$javaExe =
    Join-Path $env:JAVA_HOME "bin\java.exe"

$javaBin =
    Join-Path $env:JAVA_HOME "bin"

if (-not (Test-Path -LiteralPath $javaExe)) {
    Fail "Java executable does not exist: $javaExe"
}

if ($env:Path -notlike "*$javaBin*") {
    $env:Path = "$javaBin;$env:Path"
}

try {

    $process =
        New-Object System.Diagnostics.Process

    $process.StartInfo =
        New-Object System.Diagnostics.ProcessStartInfo

    $process.StartInfo.FileName = $javaExe
    $process.StartInfo.Arguments = "-version"
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    $process.StartInfo.CreateNoWindow = $true

    if (-not $process.Start()) {
        Fail "Java process could not be started."
    }

    $stdout =
        $process.StandardOutput.ReadToEnd()

    $stderr =
        $process.StandardError.ReadToEnd()

    $process.WaitForExit()

    if ($process.ExitCode -ne 0) {
        Fail "Java returned exit code $($process.ExitCode)."
    }

    # java -version normally writes to STDERR.
    $javaOutput =
        if ($stderr) { $stderr }
        else { $stdout }

    $javaVersion =
        $javaOutput `
        -split "`r?`n" |
        Where-Object { $_ } |
        Select-Object -First 1

    Write-Host "JAVA_HOME: $env:JAVA_HOME"
    Write-Host "Java:      $javaVersion"
}
catch {
    Fail "Java could not be executed. $($_.Exception.Message)"
}

Write-OK "Java check: OK"

# ============================================================
# Git
# ============================================================

Write-Step "Checking Git state"

git fetch origin

if ($LASTEXITCODE -ne 0) {
    Fail "git fetch origin failed."
}

$branch =
    (git branch --show-current).Trim()

if (-not $branch) {
    Fail "Could not determine the current Git branch."
}

# Allows Version3, Version4, Version5 etc.
if ($branch -notmatch '^Version\d+$') {
    Fail "Deployment must run from a Version branch. Current branch: '$branch'."
}

$remoteBranch =
    "origin/$branch"

git rev-parse --verify $remoteBranch 2>$null |
    Out-Null

if ($LASTEXITCODE -ne 0) {
    Fail "Remote branch '$remoteBranch' does not exist."
}

$localHead =
    (git rev-parse HEAD).Trim()

$remoteHead =
    (git rev-parse $remoteBranch).Trim()

if ($localHead -ne $remoteHead) {

    Write-Host "Local:  $localHead"
    Write-Host "Remote: $remoteHead"

    Fail "Local $branch does not match $remoteBranch. Push or synchronise first."
}

# Ignore untracked local tooling, but tracked files must be clean.
$trackedChanges =
    @(git status --porcelain --untracked-files=no)

if ($trackedChanges.Count -gt 0) {

    Write-Host ""
    Write-Host "Tracked changes:"

    $trackedChanges |
        ForEach-Object {
            Write-Host "  $_"
        }

    Fail "Tracked working-tree changes exist. Commit or restore them first."
}

Write-Host "Branch: $branch"
Write-Host "Remote: $remoteBranch"
Write-Host "HEAD:   $localHead"

Write-OK "Git: clean and synchronised"

$untracked =
    @(git ls-files --others --exclude-standard)

if ($untracked.Count -gt 0) {

    Write-Host ""
    Write-Warn "Untracked local files detected (not blocking):"

    $untracked |
        ForEach-Object {
            Write-Host "  $_"
        }
}

# ============================================================
# PNG integrity
# ============================================================

Write-Step "Validating Android PNG resources"

$pngFiles =
    @(
        Get-ChildItem `
            ".\app\src\main\res" `
            -Recurse `
            -File `
            -Filter *.png
    )

if ($pngFiles.Count -eq 0) {
    Fail "No PNG resources were found under app/src/main/res."
}

foreach ($png in $pngFiles) {

    $bytes =
        [System.IO.File]::ReadAllBytes(
            $png.FullName
        )

    $valid =
        $bytes.Length -ge 8 -and
        $bytes[0] -eq 0x89 -and
        $bytes[1] -eq 0x50 -and
        $bytes[2] -eq 0x4E -and
        $bytes[3] -eq 0x47 -and
        $bytes[4] -eq 0x0D -and
        $bytes[5] -eq 0x0A -and
        $bytes[6] -eq 0x1A -and
        $bytes[7] -eq 0x0A

    if (-not $valid) {
        Fail "Invalid/corrupt PNG detected: $($png.FullName)"
    }
}

Write-OK "PNG validation: $($pngFiles.Count) valid"

# ============================================================
# Android release configuration
# ============================================================

Write-Step "Reading Android release configuration"

$buildGradlePath =
    ".\app\build.gradle.kts"

if (-not (Test-Path -LiteralPath $buildGradlePath)) {
    Fail "Missing app/build.gradle.kts."
}

$buildGradle =
    Get-Content `
        -LiteralPath $buildGradlePath `
        -Raw

$versionCodeMatches =
    [regex]::Matches(
        $buildGradle,
        '(?m)^\s*versionCode\s*=\s*(\d+)\s*(?://.*)?$'
    )

$versionNameMatches =
    [regex]::Matches(
        $buildGradle,
        '(?m)^\s*versionName\s*=\s*"([^"]+)"\s*(?://.*)?$'
    )

$targetSdkMatch =
    [regex]::Match(
        $buildGradle,
        'targetSdk\s*=\s*(\d+)'
    )

if ($versionCodeMatches.Count -ne 1) {
    Fail "Expected exactly one versionCode declaration, found $($versionCodeMatches.Count)."
}

if ($versionNameMatches.Count -ne 1) {
    Fail "Expected exactly one versionName declaration, found $($versionNameMatches.Count)."
}

if (-not $targetSdkMatch.Success) {
    Fail "Could not determine targetSdk."
}

$versionCode =
    [int]$versionCodeMatches[0].Groups[1].Value

$versionName =
    $versionNameMatches[0].Groups[1].Value

$targetSdk =
    [int]$targetSdkMatch.Groups[1].Value

$versionDecision =
    Get-ReleaseVersionDecision `
        -PlayVersionCode $PlayVersionCode `
        -LocalVersionCode $versionCode `
        -LocalVersionName $versionName

$targetVersionCode =
    $versionDecision.TargetVersionCode

$targetVersionName =
    $versionDecision.TargetVersionName

Write-Host "Current Play version:  $PlayVersionCode"
Write-Host "Current local version: $versionCode / $versionName"
Write-Host "Target release version: $targetVersionCode"
Write-Host "Target version name:     $targetVersionName"
Write-Host "Target SDK:              $targetSdk"

Write-OK "Version decision: OK"

if ($VersionCheckOnly) {
    Write-Host ""
    Write-OK "Version check only: no files changed and no build started."
    exit 0
}

if ($versionDecision.RequiresUpdate) {
    $updatedBuildGradle =
        Set-AndroidReleaseVersion `
            -BuildGradle $buildGradle `
            -VersionCode $targetVersionCode `
            -VersionName $targetVersionName

    $resolvedBuildGradlePath =
        (Resolve-Path -LiteralPath $buildGradlePath).Path

    $script:VersionRollbackPath =
        $resolvedBuildGradlePath

    $script:VersionRollbackBytes =
        [System.IO.File]::ReadAllBytes(
            $resolvedBuildGradlePath
        )

    [System.IO.File]::WriteAllText(
        $resolvedBuildGradlePath,
        $updatedBuildGradle,
        [System.Text.UTF8Encoding]::new($false)
    )

    $script:VersionChangePending = $true
    Write-OK "Prepared release version $targetVersionCode / $targetVersionName"
}
else {
    $updatedBuildGradle = $buildGradle
    Write-OK "Local project already contains release version $targetVersionCode / $targetVersionName; no rewrite needed."
}

# ============================================================
# Keystore
# ============================================================

Write-Step "Checking release signing"

# Explicit KEYSTORE_PATH environment variable can override
# the default or parameter value.
if ($env:KEYSTORE_PATH) {
    $KeystorePath = $env:KEYSTORE_PATH
}

if (-not (
    Test-Path `
        -LiteralPath $KeystorePath
)) {

    Write-Host "Expected keystore:"
    Write-Host "  $KeystorePath"

    $keyRoot =
        "D:\App-Security-Keys"

    if (Test-Path -LiteralPath $keyRoot) {

        $knownKeys =
            @(
                Get-ChildItem `
                    $keyRoot `
                    -Recurse `
                    -File `
                    -ErrorAction SilentlyContinue |
                Where-Object {
                    $_.Extension -in ".jks", ".keystore"
                }
            )

        if ($knownKeys.Count -gt 0) {

            Write-Host ""
            Write-Host "Keystore files found:"

            $knownKeys |
                ForEach-Object {
                    Write-Host "  $($_.FullName)"
                }
        }
    }

    Fail "Release keystore does not exist at the configured path."
}

$resolvedKeystore =
    (Resolve-Path `
        -LiteralPath $KeystorePath).Path

$env:KEYSTORE_PATH =
    $resolvedKeystore

Write-Host "Keystore: $resolvedKeystore"

# ============================================================
# Signing credentials
# ============================================================

Write-Step "Checking signing credentials"

if (
    -not $env:STORE_PASSWORD -or
    -not $env:KEY_PASSWORD
) {

    if (-not (
        Test-Path `
            -LiteralPath $CredentialsPath
    )) {
        Fail "Signing credentials file does not exist: $CredentialsPath"
    }

    foreach (
        $line in
        Get-Content -LiteralPath $CredentialsPath
    ) {

        $trimmed =
            $line.Trim()

        if (
            -not $trimmed -or
            $trimmed.StartsWith("#")
        ) {
            continue
        }

        # Supports:
        #
        # STORE_PASSWORD=value
        #
        if (
            $trimmed -match
            '^STORE_PASSWORD\s*=\s*(.*)$'
        ) {

            $value =
                $matches[1].Trim()

            $value =
                $value.Trim('"').Trim("'")

            $env:STORE_PASSWORD =
                $value

            continue
        }

        # Supports:
        #
        # KEY_PASSWORD=value
        #
        if (
            $trimmed -match
            '^KEY_PASSWORD\s*=\s*(.*)$'
        ) {

            $value =
                $matches[1].Trim()

            $value =
                $value.Trim('"').Trim("'")

            $env:KEY_PASSWORD =
                $value

            continue
        }

        # Supports:
        #
        # $env:STORE_PASSWORD = "value"
        #
        if (
            $trimmed -match
            '^\$env:STORE_PASSWORD\s*=\s*["''](.*)["'']$'
        ) {

            $env:STORE_PASSWORD =
                $matches[1]

            continue
        }

        # Supports:
        #
        # $env:KEY_PASSWORD = "value"
        #
        if (
            $trimmed -match
            '^\$env:KEY_PASSWORD\s*=\s*["''](.*)["'']$'
        ) {

            $env:KEY_PASSWORD =
                $matches[1]

            continue
        }
    }
}

if (-not $env:STORE_PASSWORD) {
    Fail "STORE_PASSWORD was not found in the environment or credentials file."
}

if (-not $env:KEY_PASSWORD) {
    Fail "KEY_PASSWORD was not found in the environment or credentials file."
}

# Never print secret values.
Write-OK "Signing credentials: present"

# ============================================================
# Gradle wrapper
# ============================================================

Write-Step "Checking Gradle wrapper"

if (-not (
    Test-Path `
        -LiteralPath ".\gradlew.bat"
)) {
    Fail "gradlew.bat is missing."
}

& .\gradlew.bat --version

if ($LASTEXITCODE -ne 0) {
    Fail "Gradle wrapper validation failed."
}

Write-OK "Gradle wrapper: OK"

# ============================================================
# Debug build
# ============================================================

if (-not $SkipDebugBuild) {

    Write-Step "Building debug APK"

    & .\gradlew.bat clean assembleDebug

    if ($LASTEXITCODE -ne 0) {
        Fail "assembleDebug failed."
    }

    Write-OK "Debug build: SUCCESS"
}
else {

    Write-Host ""
    Write-Warn "Debug build: SKIPPED"
}

# ============================================================
# Optional tests
# ============================================================

if ($RunTests) {

    Write-Step "Running unit tests"

    & .\gradlew.bat testDebugUnitTest

    if ($LASTEXITCODE -ne 0) {
        Fail "Unit tests failed."
    }

    Write-OK "Unit tests: SUCCESS"
}
else {

    Write-Host ""
    Write-Host "Unit tests: not requested"
}

# ============================================================
# Release bundle
# ============================================================

Write-Step "Building signed release AAB"

& .\gradlew.bat bundleRelease

if ($LASTEXITCODE -ne 0) {
    Fail "bundleRelease failed."
}

$aabPath =
    ".\app\build\outputs\bundle\release\app-release.aab"

if (-not (
    Test-Path `
        -LiteralPath $aabPath
)) {
    Fail "Release build completed but app-release.aab was not found."
}

$aabFile =
    Get-Item `
        -LiteralPath $aabPath

if ($aabFile.Length -le 0) {
    Fail "Generated AAB is empty."
}

Write-OK "Release bundle: SUCCESS"

# ============================================================
# Final Git sanity check
# ============================================================

Write-Step "Final Git sanity check"

$stagedChanges =
    @(git diff --cached --name-only)

if ($stagedChanges.Count -gt 0) {
    Fail "Deployment unexpectedly staged tracked files: $($stagedChanges -join ', ')."
}

$finalTrackedFiles =
    @(git diff --name-only)

if ($versionDecision.RequiresUpdate) {
    if (
        $finalTrackedFiles.Count -ne 1 -or
        $finalTrackedFiles[0] -ne "app/build.gradle.kts"
    ) {
        Fail "Unexpected tracked files changed during deployment: $($finalTrackedFiles -join ', ')."
    }

    $finalBuildGradle =
        [System.IO.File]::ReadAllText(
            $script:VersionRollbackPath
        )

    if ($finalBuildGradle -ne $updatedBuildGradle) {
        Fail "app/build.gradle.kts contains changes beyond the intended release version update."
    }

    Write-OK "Git contains only the expected uncommitted release version change."
}
elseif ($finalTrackedFiles.Count -gt 0) {
    Fail "Deployment changed tracked repository files: $($finalTrackedFiles -join ', ')."
}
else {
    Write-OK "Git remained clean; the target release version was already present."
}

# ============================================================
# Release summary
# ============================================================

Write-Step "Release ready"

Write-Host ""
Write-Host "Branch:       $branch"
Write-Host "Remote:       $remoteBranch"
Write-Host "Commit:       $localHead"
Write-Host "Version code: $targetVersionCode"
Write-Host "Version name: $targetVersionName"
Write-Host "Target SDK:   $targetSdk"
Write-Host "Play code:    $PlayVersionCode"
Write-Host "Keystore:     $resolvedKeystore"
Write-Host "AAB:          $($aabFile.FullName)"
Write-Host "Size:         $([math]::Round($aabFile.Length / 1MB, 2)) MB"
Write-Host "Created:      $($aabFile.LastWriteTime)"

Write-Host ""
Write-OK "Release version prepared: $targetVersionCode / $targetVersionName"
if ($versionDecision.RequiresUpdate) {
    Write-Warn "Tracked release version change pending commit: app/build.gradle.kts"
}
else {
    Write-Host "Release version was already committed before this build."
}
Write-Host ""
Write-Host "READY FOR GOOGLE PLAY INTERNAL TESTING" `
    -ForegroundColor Green
