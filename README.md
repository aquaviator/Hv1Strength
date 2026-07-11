# Human V1 - Strength

Human V1 Strength is an offline-first Android strength-training companion built with Kotlin, Jetpack Compose, Material 3, Room, Firebase Authentication, Firestore, WorkManager, and Firebase AI.

## Product rule

Design every screen for an athlete standing beside a machine between sets. Room is the source of truth; cloud services provide authentication, backup, and synchronisation.

## Open and run

### Requirements

- Android Studio Quail or newer
- JDK 21
- Android SDK 36, including Build Tools 36.0.0
- An Android 7.0+ device or emulator

### Debug build

1. Open this repository root in Android Studio.
2. Allow Gradle sync to finish.
3. Confirm `app/google-services.json` belongs to Firebase project `hv1-platform` and package `com.aistudio.humanstrength.kfqjza`.
4. Create `.env` from `.env.example` only when an AI API key is required.
5. Select the `app` run configuration and a device that shows as **Online**.
6. Run the debug build. Android supplies the standard debug keystore automatically.

Command line:

```bash
./gradlew clean testDebugUnitTest assembleDebug
```

Windows PowerShell or Command Prompt:

```powershell
.\gradlew.bat clean testDebugUnitTest assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release bundle

Release signing is intentionally supplied through environment variables and is never committed.

Required variables:

```text
KEYSTORE_PATH
STORE_PASSWORD
KEY_PASSWORD
```

The upload alias is `upload`.

```bash
./gradlew bundleRelease --no-configuration-cache
```

The AAB is written to:

```text
app/build/outputs/bundle/release/app-release.aab
```

Before upload, verify the certificate:

```bash
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
```

Google Play currently expects the approved upload certificate, not an arbitrary newly-created keystore.

## Architecture

```text
Compose UI
  -> ViewModel / StateFlow
  -> Repositories
  -> Room
  -> Command Queue
  -> WorkManager Sync Engine
  -> Firestore
```

The UI must not read or write Firestore directly.

## Branding assets

- Adaptive launcher icon: `mipmap-*` and `drawable/ic_launcher_*`
- Splash artwork: `drawable/ic_splash_icon.xml`
- In-app logo: `drawable-nodpi/human_logo.png`
- In-app banner: `drawable-nodpi/human_banner.png`

Do not load launcher artwork as an in-app Compose image.

## Smoke-test checklist

1. Fresh install opens the branded welcome screen without crashing.
2. Continue offline creates an `offline` Room profile.
3. Google sign-in creates or updates the authenticated Room profile and displays its real name, email, initials, and photo.
4. Create a routine, add exercises, configure intent, save, reopen, and edit it.
5. Start a workout, log actual weight, reps, and RPE, complete it, and verify history.
6. Restart in aeroplane mode and confirm routines, active data, history, and progress remain available.
7. Restore connectivity and inspect the command queue/sync debug screen.
8. Export JSON, import it into a clean test install, and verify the restored records.
9. Check all primary controls with one hand and ensure touch targets remain at least 56dp.

## Current database

Room schema version: **7**

Application version is sourced from `BuildConfig.VERSION_NAME` in the Settings screen.
