$ErrorActionPreference = "Stop"

if (-not $env:FIRESTORE_EMULATOR_HOST -or -not $env:FIREBASE_AUTH_EMULATOR_HOST) {
    throw "S8F-B guard: Firebase emulator hosts are required"
}
if ($env:GCLOUD_PROJECT -eq "hv1-platform") {
    throw "S8F-B guard: production project is forbidden"
}

Push-Location functions
try {
    npm.cmd run build
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    node test/account-deletion-emulator.js
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
