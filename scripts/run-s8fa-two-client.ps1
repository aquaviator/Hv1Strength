$ErrorActionPreference = "Stop"

if (-not $env:FIRESTORE_EMULATOR_HOST -or -not $env:FIREBASE_AUTH_EMULATOR_HOST) {
    throw "S8F-A guard: Firebase emulator hosts are required"
}
if ($env:GCLOUD_PROJECT -eq "hv1-platform") {
    throw "S8F-A guard: production project is forbidden"
}

node functions/test/seed-planner-sync-emulator.js
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
& $adb reverse tcp:9099 tcp:9099
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $adb reverse tcp:8080 tcp:8080
if ($LASTEXITCODE -ne 0) { & $adb reverse --remove tcp:9099; exit $LASTEXITCODE }
try {
    & .\gradlew.bat connectedDebugAndroidTest `
        '-Pandroid.testInstrumentationRunnerArguments.class=com.example.core.sync.PlannerTwoClientSyncInstrumentedTest' `
        --no-daemon --console=plain
    $testExit = $LASTEXITCODE
} finally {
    & $adb reverse --remove tcp:9099 | Out-Null
    & $adb reverse --remove tcp:8080 | Out-Null
}
exit $testExit
