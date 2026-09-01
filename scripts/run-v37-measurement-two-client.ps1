$ErrorActionPreference = "Stop"
if (-not $env:FIRESTORE_EMULATOR_HOST -or -not $env:FIREBASE_AUTH_EMULATOR_HOST) {
    throw "V37 guard: Firestore and Auth emulator hosts are required"
}
if ($env:GCLOUD_PROJECT -eq "hv1-platform") {
    throw "V37 guard: production project is forbidden"
}
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path -LiteralPath $adb)) { throw "Android SDK adb was not found" }
node functions/test/seed-planner-sync-emulator.js
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $adb reverse tcp:9099 tcp:9099
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $adb reverse tcp:8080 tcp:8080
if ($LASTEXITCODE -ne 0) { & $adb reverse --remove tcp:9099; exit $LASTEXITCODE }
try {
    & .\gradlew.bat :app:connectedDebugAndroidTest `
        '-Pandroid.testInstrumentationRunnerArguments.class=com.example.core.sync.V37MeasurementTwoClientSyncInstrumentedTest' `
        --no-daemon --console=plain
    $testExit = $LASTEXITCODE
} finally {
    & $adb reverse --remove tcp:9099 | Out-Null
    & $adb reverse --remove tcp:8080 | Out-Null
}
exit $testExit
