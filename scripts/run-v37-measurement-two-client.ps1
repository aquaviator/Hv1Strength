$ErrorActionPreference = "Stop"
if (-not $env:FIRESTORE_EMULATOR_HOST -or -not $env:FIREBASE_AUTH_EMULATOR_HOST) {
    throw "V37 guard: Firestore and Auth emulator hosts are required"
}
if ($env:GCLOUD_PROJECT -eq "hv1-platform") {
    throw "V37 guard: production project is forbidden"
}
node functions/test/seed-planner-sync-emulator.js
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& .\gradlew.bat :app:connectedDebugAndroidTest `
    '-Pandroid.testInstrumentationRunnerArguments.class=com.example.core.sync.V37MeasurementTwoClientSyncInstrumentedTest' `
    --no-daemon --console=plain
exit $LASTEXITCODE
