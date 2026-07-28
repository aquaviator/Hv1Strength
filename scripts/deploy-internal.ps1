param(
    [Parameter(Mandatory = $true)]
    [int]$PlayVersionCode,

    [string]$KeystorePath = "D:\App-Security-Keys\PlayStore\my-upload-key.jks",

    [switch]$SkipDebugBuild,

    [switch]$RunTests
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)

    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Fail {
    param([string]$Message)

    Write-Host ""
    Write-Host "DEPLOYMENT STOPPED: $Message" -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------
# Resolve repository root
# ------------------------------------------------------------

try {
    $repoRoot = (git rev-parse --show-toplevel 2>$null).Trim()
}
catch {
    Fail "This command must be run inside the Hv1Strength Git repository."
}

if (-not $repoRoot) {
    Fail "This command must be run inside the Hv1Strength Git repository."
}

Set-Location $repoRoot

Write-Step "Human Strength internal-release preflight"
Write-Host "Repository: $repoRoot"

# ------------------------------------------------------------
# Java
# ------------------------------------------------------------

Write-Step "Checking Java"

$androidStudioJbr = "C:\Program Files\Android\Android Studio\jbr"

$javaHomeValid =
    $env:JAVA_HOME -and
    (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME "bin\java.exe"))

if (-not $javaHomeValid) {
    if (Test-Path -LiteralPath (Join-Path $androidStudioJbr "bin\java.exe")) {
        $env:JAVA_HOME = $androidStudioJbr
    }
    else {
        Fail "Java was not found in JAVA_HOME or Android Studio JBR."
    }
}

$javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
$javaBin = Join-Path $env:JAVA_HOME "bin"

if ($env:Path -notlike "*$javaBin*") {
    $env:Path = "$javaBin;$env:Path"
}

if (-not (Test-Path -LiteralPath $javaExe)) {
    Fail "Java executable does not exist: $javaExe"
}

try {
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = New-Object System.Diagnostics.ProcessStartInfo

    $process.StartInfo.FileName = $javaExe
    $process.StartInfo.Arguments = "-version"
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    $process.StartInfo.CreateNoWindow = $true

    $started = $process.Start()

    if (-not $started) {
        Fail "Java process could not be started."
    }

    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()

    $process.WaitForExit()

    if ($process.ExitCode -ne 0) {
        Fail "Java exists but returned exit code $($process.ExitCode)."
    }

    # java -version normally writes its version information to STDERR.
    $javaOutput = if ($stderr) { $stderr } else { $stdout }
    $javaVersion = ($javaOutput -split "`r?`n" | Where-Object { $_ } | Select-Object -First 1)

    Write-Host "JAVA_HOME: $env:JAVA_HOME"
    Write-Host "Java:      $javaVersion"
}
catch {
    Fail "Java could not be executed from '$javaExe'. Error: $($_.Exception.Message)"
}
# ------------------------------------------------------------
# Git state
# ------------------------------------------------------------

Write-Step "Checking Git state"

git fetch origin

if ($LASTEXITCODE -ne 0) {
    Fail "git fetch origin failed."
}

$branch = (git branch --show-current).Trim()

if (-not $branch) {
    Fail "Could not determine the current Git branch."
}

if ($branch -notmatch '^Version\d+$') {
    Fail "Deployment must be run from a Version branch. Current branch: '$branch'."
}

$remoteBranch = "origin/$branch"

git rev-parse --verify $remoteBranch 2>$null | Out-Null

if ($LASTEXITCODE -ne 0) {
    Fail "Remote branch '$remoteBranch' does not exist."
}

$localHead = (git rev-parse HEAD).Trim()
$remoteHead = (git rev-parse $remoteBranch).Trim()

if ($localHead -ne $remoteHead) {
    Write-Host "Local:  $localHead"
    Write-Host "Remote: $remoteHead"
    Fail "Local $branch does not match $remoteBranch. Push or synchronise the branch first."
}

$trackedChanges = @(git status --porcelain --untracked-files=no)

if ($trackedChanges.Count -gt 0) {
    Write-Host ""
    Write-Host "Tracked changes:"
    $trackedChanges | ForEach-Object { Write-Host "  $_" }

    Fail "Tracked working-tree changes exist. Commit or resolve them first."
}

Write-Host "Branch: $branch"
Write-Host "Remote: $remoteBranch"
Write-Host "Git: clean and synchronised"
Write-Host "HEAD: $localHead"

# Untracked files are reported but do not block deployment.
$untracked = @(git ls-files --others --exclude-standard)

if ($untracked.Count -gt 0) {
    Write-Host ""
    Write-Host "Untracked local files detected (not blocking):" -ForegroundColor Yellow

    $untracked | ForEach-Object {
        Write-Host "  $_"
    }
}

# ------------------------------------------------------------
# Validate Android PNG resources
# ------------------------------------------------------------

Write-Step "Validating Android PNG resources"

$pngFiles = @(
    Get-ChildItem ".\app\src\main\res" -Recurse -File -Filter *.png
)

if ($pngFiles.Count -eq 0) {
    Fail "No PNG resources were found under app/src/main/res."
}

foreach ($png in $pngFiles) {
    $bytes = [System.IO.File]::ReadAllBytes($png.FullName)

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

Write-Host "PNG validation: $($pngFiles.Count) valid"

# ------------------------------------------------------------
# Read Android release configuration
# ------------------------------------------------------------

Write-Step "Reading Android release configuration"

$buildGradlePath = ".\app\build.gradle.kts"

if (-not (Test-Path $buildGradlePath)) {
    Fail "Missing app/build.gradle.kts."
}

$buildGradle = Get-Content $buildGradlePath -Raw

$versionCodeMatch = [regex]::Match(
    $buildGradle,
    'versionCode\s*=\s*(\d+)'
)

$versionNameMatch = [regex]::Match(
    $buildGradle,
    'versionName\s*=\s*"([^"]+)"'
)

$targetSdkMatch = [regex]::Match(
    $buildGradle,
    'targetSdk\s*=\s*(\d+)'
)

if (-not $versionCodeMatch.Success) {
    Fail "Could not determine versionCode from app/build.gradle.kts."
}

if (-not $versionNameMatch.Success) {
    Fail "Could not determine versionName from app/build.gradle.kts."
}

if (-not $targetSdkMatch.Success) {
    Fail "Could not determine targetSdk from app/build.gradle.kts."
}

$versionCode = [int]$versionCodeMatch.Groups[1].Value
$versionName = $versionNameMatch.Groups[1].Value
$targetSdk = [int]$targetSdkMatch.Groups[1].Value

Write-Host "versionCode: $versionCode"
Write-Host "versionName: $versionName"
Write-Host "targetSdk:   $targetSdk"
Write-Host "Play code:   $PlayVersionCode"

if ($versionCode -le $PlayVersionCode) {
    Fail "Local versionCode $versionCode must be greater than Play versionCode $PlayVersionCode."
}

Write-Host "Version check: OK"

# ------------------------------------------------------------
# Release keystore
# ------------------------------------------------------------

Write-Step "Checking release signing"

# Explicit parameter takes precedence.
# Otherwise use KEYSTORE_PATH.
# Finally fall back to the known Human Strength key location.

if (-not $KeystorePath -and $env:KEYSTORE_PATH) {
    $KeystorePath = $env:KEYSTORE_PATH
}

if (-not $KeystorePath) {
    $KeystorePath = "D:\App-Security-Keys\PlayStore\my-upload-key.jks"
}

if (-not (Test-Path -LiteralPath $KeystorePath)) {
    Write-Host "Expected keystore: $KeystorePath"

    $keyRoot = "D:\App-Security-Keys"

    if (Test-Path $keyRoot) {
        $knownKeys = @(
            Get-ChildItem $keyRoot `
                -Recurse `
                -File `
                -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Extension -in ".jks", ".keystore"
            }
        )

        if ($knownKeys.Count -gt 0) {
            Write-Host ""
            Write-Host "Other keystore files found:"

            $knownKeys | ForEach-Object {
                Write-Host "  $($_.FullName)"
            }
        }
    }

    Fail "Release keystore does not exist at the configured path."
}

$resolvedKeystore = (Resolve-Path -LiteralPath $KeystorePath).Path
$env:KEYSTORE_PATH = $resolvedKeystore

Write-Host "Keystore: $resolvedKeystore"

if (-not $env:STORE_PASSWORD) {
    Fail "STORE_PASSWORD environment variable is not set."
}

if (-not $env:KEY_PASSWORD) {
    Fail "KEY_PASSWORD environment variable is not set."
}

# Password values are intentionally never printed.

Write-Host "Signing credentials: present"

# ------------------------------------------------------------
# Gradle wrapper
# ------------------------------------------------------------

Write-Step "Checking Gradle wrapper"

if (-not (Test-Path ".\gradlew.bat")) {
    Fail "gradlew.bat is missing."
}

& .\gradlew.bat --version

if ($LASTEXITCODE -ne 0) {
    Fail "Gradle wrapper validation failed."
}

# ------------------------------------------------------------
# Debug build
# ------------------------------------------------------------

if (-not $SkipDebugBuild) {
    Write-Step "Building debug APK"

    & .\gradlew.bat clean assembleDebug

    if ($LASTEXITCODE -ne 0) {
        Fail "assembleDebug failed."
    }

    Write-Host "Debug build: SUCCESS"
}
else {
    Write-Host ""
    Write-Host "Debug build: SKIPPED" -ForegroundColor Yellow
}

# ------------------------------------------------------------
# Optional unit tests
# ------------------------------------------------------------

if ($RunTests) {
    Write-Step "Running unit tests"

    & .\gradlew.bat testDebugUnitTest

    if ($LASTEXITCODE -ne 0) {
        Fail "Unit tests failed."
    }

    Write-Host "Unit tests: SUCCESS"
}
else {
    Write-Host ""
    Write-Host "Unit tests: not requested"
}

# ------------------------------------------------------------
# Release AAB
# ------------------------------------------------------------

Write-Step "Building signed release AAB"

& .\gradlew.bat bundleRelease

if ($LASTEXITCODE -ne 0) {
    Fail "bundleRelease failed."
}

$aabPath = ".\app\build\outputs\bundle\release\app-release.aab"

if (-not (Test-Path $aabPath)) {
    Fail "Release build completed but app-release.aab was not found."
}

$aabFile = Get-Item $aabPath

if ($aabFile.Length -le 0) {
    Fail "Generated AAB is empty."
}

# ------------------------------------------------------------
# Final Git sanity check
# ------------------------------------------------------------

Write-Step "Final Git sanity check"

$finalTrackedChanges = @(git status --porcelain --untracked-files=no)

if ($finalTrackedChanges.Count -gt 0) {
    Write-Host "Tracked files changed during deployment:"
    $finalTrackedChanges | ForEach-Object { Write-Host "  $_" }

    Fail "Deployment build changed tracked repository files."
}

Write-Host "Git remained clean during build"

# ------------------------------------------------------------
# Final summary
# ------------------------------------------------------------

Write-Step "Release ready"

Write-Host "Branch:       $branch"
Write-Host "Remote:       $remoteBranch"
Write-Host "Commit:       $localHead"
Write-Host "Version code: $versionCode"
Write-Host "Version name: $versionName"
Write-Host "Target SDK:   $targetSdk"
Write-Host "Play code:    $PlayVersionCode"
Write-Host "Keystore:     $resolvedKeystore"
Write-Host "AAB:          $($aabFile.FullName)"
Write-Host "Size:         $([math]::Round($aabFile.Length / 1MB, 2)) MB"
Write-Host "Created:      $($aabFile.LastWriteTime)"

Write-Host ""
Write-Host "READY FOR GOOGLE PLAY INTERNAL TESTING" -ForegroundColor Green
