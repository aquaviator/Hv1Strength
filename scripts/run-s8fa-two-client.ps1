$ErrorActionPreference = "Stop"

if (-not $env:FIRESTORE_EMULATOR_HOST -or -not $env:FIREBASE_AUTH_EMULATOR_HOST) {
    throw "S8F-A guard: Firebase emulator hosts are required"
}
if ($env:GCLOUD_PROJECT -eq "hv1-platform") {
    throw "S8F-A guard: production project is forbidden"
}

node functions/test/seed-planner-sync-emulator.js
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& .\gradlew.bat connectedDebugAndroidTest `
    '-Pandroid.testInstrumentationRunnerArguments.class=com.example.core.sync.PlannerTwoClientSyncInstrumentedTest' `
    --no-daemon --console=plain
exit $LASTEXITCODE
