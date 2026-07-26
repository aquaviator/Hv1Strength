# Google Play Privacy, Security & Data Safety Audit
**Application Name:** Human Strength (Human V1 Strength)  
**Package Name / Application ID:** `com.aistudio.humanstrength.kfqjza`  
**Audit Target:** Current Android Codebase (`main` branch)  
**Date of Audit:** July 2026  
**Audit Scope:** READ-ONLY technical verification of client-side implementation, data persistence, network permissions, billing verification, and compliance status against Google Play Console policy requirements.

---

## Executive Summary

This document provides a verified, codebase-backed technical foundation for:
1. Google Play Console **Data Safety Declarations**
2. **Human Strength Privacy Policy** content
3. **Data & Account Deletion Compliance** assessment
4. **Terms of Service & Support** status
5. **Permissions & Security Architecture** baseline

### Verification Classifications Used
- **`VERIFIED`**: Implemented in code and confirmed by direct source file evidence.
- **`NOT IMPLEMENTED`**: Absent from runtime implementation or UI workflows.
- **`UNCERTAIN / REQUIRES CONFIRMATION`**: Partial evidence found; requires external configuration or backend verification.

---

## 1. Google Play Console Data Safety Declarations

### A. Personal Information
| Data Type | Field / Source | Purpose in Codebase | Collected? | Transmitted to Cloud? | Classification | Evidence Source File |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| **Name** | `displayName` in `UserProfile` | User identity display & local personalization | Yes | Yes (when Google Auth active) | **`VERIFIED`** | `app/src/main/java/com/example/data/Entities.kt`<br>`app/src/main/java/com/example/data/AuthRepository.kt` |
| **Email Address** | `email` in `UserProfile` | Authentication identity & account matching | Yes | Yes (Firebase Auth) | **`VERIFIED`** | `app/src/main/java/com/example/data/Entities.kt`<br>`app/src/main/java/com/example/data/AuthRepository.kt` |
| **User Identifiers** | `id`, `googleUserId`, `firebaseUid`, `humanUserId` | Database primary key & multi-tenant row linking | Yes | Yes (Firestore / Sync) | **`VERIFIED`** | `app/src/main/java/com/example/data/Entities.kt`<br>`app/src/main/java/com/example/core/identity/HumanUserIdGenerator.kt` |
| **Date of Birth** | `dateOfBirth` in `UserProfile` | User fitness demographic profile | Optional | Yes (if synced) | **`VERIFIED`** | `app/src/main/java/com/example/data/Entities.kt`<br>`app/src/main/java/com/example/ui/screens/ProfileScreen.kt` |
| **Assigned Sex** | `sex` in `UserProfile` | User fitness demographic profile | Optional | Yes (if synced) | **`VERIFIED`** | `app/src/main/java/com/example/data/Entities.kt`<br>`app/src/main/java/com/example/ui/screens/ProfileScreen.kt` |

### B. Health and Fitness Data
| Data Type | Field / Source | Purpose in Codebase | Collected? | Transmitted to Cloud? | Classification | Evidence Source File |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| **Fitness History & Workout Logs** | `WorkoutSession`, `LoggedSet`, `Exercise`, `WorkoutTemplate` | Primary fitness tracking, progress analysis | Yes | Yes (Command queue sync) | **`VERIFIED`** | `app/src/main/java/com/example/data/Entities.kt`<br>`app/src/main/java/com/example/data/StrengthRepository.kt` |
| **Body Weight & Body Fat** | `BodyWeight` (`weight`, `bodyFat`, `leanMass`, `fatMass`, `bmi`) | Body composition tracking over time | Optional | Yes (Command queue sync) | **`VERIFIED`** | `app/src/main/java/com/example/data/Entities.kt`<br>`app/src/main/java/com/example/data/StrengthDao.kt` |
| **Anthropometric Measurements** | `TapeMeasurement` (`chest`, `waist`, `hips`, arms, thighs) | Body circumference progress tracking | Optional | Yes (Command queue sync) | **`VERIFIED`** | `app/src/main/java/com/example/data/Entities.kt` |

### C. Financial & Purchase Data
| Data Type | Field / Source | Purpose in Codebase | Collected? | Transmitted to Cloud? | Classification | Evidence Source File |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| **Purchase Tokens / Subscriptions** | Google Play Billing `Purchase` token | Server-side subscription entitlement verification | Yes | Yes (Sent to `/verifySubscription` Cloud Function) | **`VERIFIED`** | `app/src/main/java/com/example/billing/BillingManager.kt`<br>`app/src/main/java/com/example/billing/EntitlementVerificationClient.kt`<br>`functions/src/index.ts` |
| **Payment Info / Credit Cards** | N/A | Processed exclusively by Google Play | No | No | **`VERIFIED`** | Google Play Billing API handling (No PCI data collected in app) |

### D. App Info & Diagnostics
| Data Type | Field / Source | Purpose in Codebase | Collected? | Transmitted to Cloud? | Classification | Evidence Source File |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| **Local Crash Logs** | File `crashLogFile` (`MainActivity.kt`) | Local crash recovery & diagnosis on app restart | Local File | No (Deleted on next startup) | **`VERIFIED`** | `app/src/main/java/com/example/MainActivity.kt` |
| **Third-Party Telemetry / Analytics** | N/A | None integrated (No Firebase Analytics, Crashlytics, Mixpanel) | No | No | **`VERIFIED`** | `app/build.gradle.kts` (Dependencies review) |

### E. Device or Other Identifiers
| Data Type | Field / Source | Purpose in Codebase | Collected? | Transmitted to Cloud? | Classification | Evidence Source File |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| **Advertising ID (AAID)** | N/A | Not requested or accessed | No | No | **`VERIFIED`** | `app/src/main/AndroidManifest.xml` (No `AD_ID` permission) |
| **Hardware Serial / IMEI** | N/A | Not requested or accessed | No | No | **`VERIFIED`** | `app/src/main/AndroidManifest.xml` |

---

## 2. Security & Data Protection Architecture

| Security Mechanism | Codebase Implementation | Technical Verification Detail | Classification | Evidence Source File |
| :--- | :--- | :--- | :---: | :--- |
| **Data in Transit Encryption** | HTTPS / TLS 1.2+ for Google Play Billing & Firebase Cloud Functions | `EntitlementVerificationClient.kt` executes HTTPS calls; Firebase SDK handles encrypted RPCs. | **`VERIFIED`** | `app/src/main/java/com/example/billing/EntitlementVerificationClient.kt` |
| **Data at Rest Encryption** | Standard SQLite Room DB on app internal storage | Database file `strength_database` resides in private app sandboxed storage (`/data/data/com.aistudio.humanstrength.kfqjza/databases/`). SQLCipher is not implemented. | **`VERIFIED`** | `app/src/main/java/com/example/data/StrengthDatabase.kt` |
| **App Integrity Protection** | Firebase App Check | Initialized via `DebugAppCheckProviderFactory` / `PlayIntegrityAppCheckProviderFactory` in application context. | **`VERIFIED`** | `app/src/main/java/com/example/HumanStrengthApplication.kt` |
| **Offline Isolation Mode** | Local-first SQLite fallback | When Firebase is unconfigured or offline, data remains local with `id="offline"` and `isOfflineUser=true`. | **`VERIFIED`** | `app/src/main/java/com/example/data/AuthRepository.kt`<br>`app/src/main/java/com/example/HumanStrengthApplication.kt` |

---

## 3. Account & Data Deletion Compliance (Google Play Policy)

Google Play mandates that apps allowing account creation must provide an in-app path and a web link for users to request account deletion and data purge.

```
[Google Play Policy Requirement]
  ├── 1. In-App Account & Data Deletion
  └── 2. Web-Based Data Deletion Request Page
```

### Codebase Audit Findings

#### A. In-App Data Deletion
- **Local Workout Data Purge**: **`VERIFIED`**  
  - **Location**: `ProfileScreen.kt` ("Delete local workout data").
  - **Action**: Triggers `viewModel.deleteLocalWorkoutData()`, which calls Room DAO queries to execute `DELETE FROM workout_session`, `DELETE FROM logged_set`, `DELETE FROM body_weight`, `DELETE FROM tape_measurement`, and `DELETE FROM workout_template`.
  - **Source Code**: `app/src/main/java/com/example/ui/screens/ProfileScreen.kt` (lines 1111–1141) & `app/src/main/java/com/example/data/StrengthDao.kt`.

- **In-App Cloud Account Deletion**: **`NOT IMPLEMENTED`**  
  - **Observation**: `ProfileScreen.kt` contains a "Sign Out of Account" action, but does **NOT** provide a dedicated "Delete Account" button or flow to delete the user's account from Firebase Authentication and purge user data from Cloud Firestore.
  - **Source Code**: `app/src/main/java/com/example/ui/screens/ProfileScreen.kt` (lines 703–715).

#### B. Cloud Firestore Server Cascade Deletion
- **Soft Delete Sync Commands**: **`VERIFIED`**  
  - Individual session/measurement deletions place `WorkoutDeleted` or `MeasurementDeleted` commands into the `sync_command` table with a `deletedAt` timestamp for soft-deletion syncing.
  - **Source Code**: `app/src/main/java/com/example/data/StrengthRepository.kt` & `app/src/main/java/com/example/data/Entities.kt`.

- **Cloud Account Purge Function**: **`NOT IMPLEMENTED`**  
  - Neither the Android app nor the `functions/` Cloud Functions codebase contains an automated user account purge handler (e.g., a Firebase Auth `onUserDeleted` trigger) to purge cloud user data upon account removal.
  - **Source Code**: `functions/src/index.ts`.

#### C. Web Data Deletion Request URL
- **Web Portal Link**: **`NOT IMPLEMENTED`**  
  - No web URL is linked inside the application or manifest for external web-based account deletion requests.
  - **Source Code**: `app/src/main/AndroidManifest.xml` & `app/src/main/java/com/example/ui/screens/ProfileScreen.kt`.

---

## 4. Terms of Service, Support & Documentation Audit

| Requirement | Current Status in Codebase | Implementation Details | Classification | Evidence Source File |
| :--- | :--- | :--- | :---: | :--- |
| **Help & Support Portal** | UI Placeholder | Clicking "Help & Support" triggers a Toast saying "Support portal is coming soon!". | **`NOT IMPLEMENTED`** | `app/src/main/java/com/example/ui/screens/ProfileScreen.kt` (lines 580–598) |
| **Privacy Policy Link** | UI Placeholder | Clicking "Privacy Policy" triggers a Toast saying "Privacy Policy is coming soon!". | **`NOT IMPLEMENTED`** | `app/src/main/java/com/example/ui/screens/ProfileScreen.kt` (lines 601–620) |
| **Terms of Service Link** | UI Placeholder | Clicking "Terms of Service" triggers a Toast saying "Terms of Service are coming soon!". | **`NOT IMPLEMENTED`** | `app/src/main/java/com/example/ui/screens/ProfileScreen.kt` (lines 622–640) |
| **Open Source License Notice** | Implemented | `SettingsScreen.kt` displays "License: Open-source under MIT". | **`VERIFIED`** | `app/src/main/java/com/example/ui/screens/SettingsScreen.kt` (lines 360–365) |

---

## 5. System Permissions Audit

The application manifest (`AndroidManifest.xml`) declares only the following system permissions:

```xml
<uses-permission android.permission.INTERNET />
<uses-permission android.permission.VIBRATE />
```

| Permission | Declared in Manifest? | Purpose in Codebase | Classification | Evidence Source File |
| :--- | :---: | :--- | :---: | :--- |
| `android.permission.INTERNET` | Yes | Required for Firebase Auth, Firestore sync, and Play Billing verification. | **`VERIFIED`** | `app/src/main/AndroidManifest.xml` |
| `android.permission.VIBRATE` | Yes | Required for rest timer countdown vibration alerts during workouts. | **`VERIFIED`** | `app/src/main/AndroidManifest.xml` |
| Dangerous Hardware / Privacy Permissions | No | No Camera, Location, Contacts, Microphone, or External Storage permissions are declared or requested. | **`VERIFIED`** | `app/src/main/AndroidManifest.xml` |

---

## 6. Actionable Summary for Google Play Console Submission

### Play Console Data Safety Form Inputs
When filling out the Google Play Console Data Safety section based on the current codebase state:

1. **Data Collection & Sharing**:
   - Check **YES** for data collection.
   - Declare **Personal Info**: Name, Email address, User IDs, Date of Birth, Sex.
   - Declare **Health & Fitness**: Fitness info, Body measurements, Workout logs.
   - Declare **Financial Info**: Purchase history (Subscription status tokens via Google Play Billing).

2. **Security Practices**:
   - Data is encrypted in transit (**YES** via HTTPS/TLS).
   - Local storage uses standard Android sandboxed internal storage.

3. **Account Deletion Readiness Roadmap**:
   - To achieve 100% Google Play policy compliance before public release, the following items must be implemented:
     1. Add an in-app "Delete Account" action under `ProfileScreen.kt` that removes user profile and triggers cloud account cleanup.
     2. Host a web-based data deletion request page URL to place in the Google Play Console Data Safety listing.
     3. Provide hosted URL links for Privacy Policy, Terms of Service, and Support documentation in `ProfileScreen.kt`.
