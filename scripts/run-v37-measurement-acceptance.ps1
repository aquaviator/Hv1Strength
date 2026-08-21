param([string]$Serial = "emulator-5554")
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$python = if ($env:PYTHON) { $env:PYTHON } elseif (Get-Command py -ErrorAction SilentlyContinue) { "py" } else { "python" }
$out = Join-Path $root "build\v37-measurement-architecture"
$first = Join-Path $out "determinism-a"
$second = Join-Path $out "determinism-b"

Push-Location $root
try {
    & $python tools\measurement_catalogue_audit.py --source build\v36-publication-readiness\release-exercises.json --output $first
    & $python tools\measurement_catalogue_audit.py --source build\v36-publication-readiness\release-exercises.json --output $second
    if ((Get-FileHash "$first\measurement-profile-candidate.json").Hash -ne (Get-FileHash "$second\measurement-profile-candidate.json").Hash) {
        throw "Measurement candidate is not deterministic"
    }
    & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
    adb -s $Serial install -r app\build\outputs\apk\debug\app-debug.apk
    adb -s $Serial install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
    $before = adb -s $Serial shell settings get system font_scale
    try {
        foreach ($scale in @("1.0", "1.5", "2.0")) {
            adb -s $Serial shell settings put system font_scale $scale
            $result = adb -s $Serial shell am instrument -w `
                -e class com.example.measurement.V37MeasurementPersistenceInstrumentedTest `
                com.aistudio.humanstrength.kfqjza.test/androidx.test.runner.AndroidJUnitRunner
            if ($LASTEXITCODE -ne 0 -or $result -notmatch "OK \(2 tests\)") { throw "V37 instrumentation failed at font scale $scale`n$result" }
        }
    } finally {
        adb -s $Serial shell settings put system font_scale $before
        adb -s $Serial shell settings put system accelerometer_rotation 1
        adb -s $Serial shell svc wifi enable
        adb -s $Serial shell svc data enable
    }
} finally { Pop-Location }
