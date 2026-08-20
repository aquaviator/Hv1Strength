$ErrorActionPreference = 'Stop'
if ($env:FIRESTORE_EMULATOR_HOST -notin @('127.0.0.1:8080', 'localhost:8080')) {
    throw 'Loopback Firestore emulator is required'
}
$python = 'C:\Users\ANDYCL~1\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
& $python tools\demo_candidate_seed.py --package build\v36-publication-readiness --project demo-v36-device
if ($LASTEXITCODE -ne 0) { throw 'Demo candidate seed failed' }
& .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain '-Pandroid.testInstrumentationRunnerArguments.class=com.example.catalogue.V36CandidateFirestoreInstrumentedTest'
if ($LASTEXITCODE -ne 0) { throw 'V36 candidate device acceptance failed' }
