# HUMAN STRENGTH — BRAND ASSET AUDIT & TECHNICAL REFERENCE

## 1. Executive Summary & Brand Identity
The **Human Strength** visual brand identity is defined by a high-contrast, premium athletic performance design language.
- **Brand Identity**: HUMAN V1 STRENGTH
- **Tagline**: `TRAIN. TRACK. TRANSFORM.`
- **Primary Palette**:
  - Dark Canvas: `#0A0D10`
  - Elevated Surfaces: `#12161A` / `#1A2026`
  - Electric Blue Accent: `#0072FF`
  - Supporting Blue: `#338BFF`
  - Primary Crisp Text: `#FFFFFF`
  - Secondary Slate Text: `#94A3B8`
  - Subtle Borders: `#262A30`
- **Core Symbol**: Circular badge containing a white athletic lifter silhouette pulling a heavy deadlift, with electric blue barbell accent bar and weight plates.

---

## 2. Canonical Master References (Root Repository)
The following files at the repository root serve as the immutable visual sources of truth for the Human Strength brand:

| File Name | Asset Type | Purpose / Description | Status |
| :--- | :--- | :--- | :---: |
| `HV1 Icon.png` | 1176x1176 PNG | Canonical master launcher icon image. Dark background `#0A0D10`, white circular badge, white lifter, electric blue barbell. | **`CANONICAL`** |
| `HV1-Banner.png` | 2880x1620 PNG | Canonical master marketing banner reference. Deep dark background, electric blue accents, brand typography & tagline. | **`CANONICAL`** |
| `human_logo_master.svg` | 512x512 SVG | Master scalable vector source for the circular Human mark. | **`CANONICAL`** |

---

## 3. Derived Android Runtime Asset Architecture

### A. Vector & Drawable Resources (`app/src/main/res/drawable/`)

| Resource Name | Type | Purpose / Usage | Source Reference | Status |
| :--- | :--- | :--- | :--- | :---: |
| `@drawable/human_logo` | `VectorDrawable` (200x200dp) | Standardized internal UI logo for headers, welcome screen, settings card, and about dialogs. | Derived from `human_logo_master.svg` | **`CONSISTENT`** |
| `@drawable/human_launcher` | `VectorDrawable` (108x108dp) | Scaled (0.66x) and padded vector foreground for Android launcher adaptive icon compliance (66% safe zone). | Derived from `HV1 Icon.png` | **`CONSISTENT`** |
| `@drawable/ic_launcher_background` | `VectorDrawable` | Solid `#0A0D10` dark background layer for launcher adaptive icons. | Derived from `HV1 Icon.png` | **`CONSISTENT`** |
| `@drawable/ic_launcher_foreground` | `LayerList` | References `@drawable/human_launcher` centered inside adaptive icon bounds. | Adaptive Icon Spec | **`CONSISTENT`** |
| `@drawable/ic_launcher_foreground_monochrome` | `VectorDrawable` (108x108dp) | Android 13+ themed monochrome icon vector using pure white paths. | Material You Spec | **`CONSISTENT`** |
| `@drawable/ic_splash_icon` | `LayerList` | Clean centered 120dp `@drawable/human_logo` over dark background for system splash screen. | Android Splash Spec | **`CONSISTENT`** |

### B. Launcher Mipmap Architecture (`app/src/main/res/mipmap/`)

| Mipmap Density | Files | Purpose | Status |
| :--- | :--- | :--- | :---: |
| `mipmap-anydpi-v26` | `ic_launcher.xml`, `ic_launcher_round.xml` | Adaptive icon xml definition referencing background, foreground, monochrome layers. | **`CONSISTENT`** |
| `mipmap-mdpi` | `ic_launcher.webp`, `ic_launcher_round.webp` | 48x48px fallback raster launcher icons. | **`CONSISTENT`** |
| `mipmap-hdpi` | `ic_launcher.webp`, `ic_launcher_round.webp` | 72x72px fallback raster launcher icons. | **`CONSISTENT`** |
| `mipmap-xhdpi` | `ic_launcher.webp`, `ic_launcher_round.webp` | 96x96px fallback raster launcher icons. | **`CONSISTENT`** |
| `mipmap-xxhdpi` | `ic_launcher.webp`, `ic_launcher_round.webp` | 144x144px fallback raster launcher icons. | **`CONSISTENT`** |
| `mipmap-xxxhdpi` | `ic_launcher.webp`, `ic_launcher_round.webp` | 192x192px fallback raster launcher icons. | **`CONSISTENT`** |

---

## 4. Brand Color Tokens & Theme Architecture

Brand colors are consolidated centrally in `app/src/main/java/com/example/ui/theme/Color.kt` and `DesignTokens.kt`:

```kotlin
// Brand Core Accent
val HumanElectricBlue = Color(0xFF0072FF)          // Primary Electric Blue (#0072FF)
val HumanSupportingBlue = Color(0xFF338BFF)        // Supporting Blue (#338BFF)
val HumanElectricBluePressed = Color(0xFF0052D4)   // Active/Pressed State
val HumanElectricBlueMuted = Color(0xFF003399)     // Secondary Subtle State

// Dark Theme Canvas Tokens
val HumanDarkBackground = Color(0xFF0A0D10)         // Near-black graphite canvas (#0A0D10)
val HumanDarkSurface = Color(0xFF12161A)            // Elevated card container (#12161A)
val HumanDarkSurfaceElevated = Color(0xFF1A2026)    // Lighter surface container (#1A2026)
val HumanDarkOnBackground = Color(0xFFFFFFFF)       // Primary crisp text (#FFFFFF)
val HumanDarkOnSurfaceVariant = Color(0xFF94A3B8)   // Secondary slate text (#94A3B8)
val HumanDarkOutline = Color(0xFF262A30)            // Subtle graphite border outline
```

> **Consolidation Note**: Legacy cyan `#00E5FF` has been removed as a core brand color and consolidated into the canonical electric blue palette (`#0072FF` / `#338BFF`).

---

## 5. Application Surface Mapping

### A. Welcome & Onboarding Screen (`WelcomeScreen.kt`)
- **Branded Header**: Replaced legacy `human_banner.xml` image with a clean, responsive Compose layout featuring:
  - Scaled `@drawable/human_logo` circular mark (80.dp)
  - "HUMAN V1" title in crisp white bold typography
  - "STRENGTH" badge container in `#0072FF` electric blue
  - "TRAIN. TRACK. TRANSFORM." tagline in electric blue with letter spacing
- **Responsive Layout**: Adjusts padding dynamically for small mobile displays, ensuring Google Sign-In and Continue Offline actions remain fully visible and accessible without scrolling off screen.

### B. Internal Application Headers & Surfaces
- **Workout Dashboard (`WorkoutScreen.kt`)**: Standardized header brand badge to `@drawable/human_logo`. Removed direct usage of `human_launcher` as an internal UI logo.
- **Settings Screen (`SettingsScreen.kt`)**: Displays canonical `@drawable/human_logo` alongside app versioning and open-source license information.
- **Profile Screen (`ProfileScreen.kt`)**: Utilizes consolidated surface colors (`#12161A` / `#1A2026`) and electric blue accent indicators.

### C. System Splash Screen & Window Background
- **Background**: `#0A0D10` (no light theme flash during cold boot).
- **Icon**: Centered `@drawable/ic_splash_icon` (120dp `@drawable/human_logo`).

---

## 6. Google Play Store Asset Mapping

To prepare assets for Google Play Console submission:

| Store Asset | Required Dimensions | Recommended Master Source | Conversion / Processing Guidelines |
| :--- | :--- | :--- | :--- |
| **App Icon** | 512 x 512 px (PNG 32-bit with alpha or solid bg) | `HV1 Icon.png` / `human_logo_master.svg` | Export `human_logo_master.svg` to 512x512 PNG with solid `#0A0D10` dark background and no rounded corner mask (Google Play applies launcher mask dynamically). |
| **Feature Graphic** | 1024 x 500 px (PNG 24-bit or JPEG) | `HV1-Banner.png` | Crop/reframe `HV1-Banner.png` to 1024x500 aspect ratio, keeping "HUMAN V1 STRENGTH" and circular lifter mark centered in the focal area. |
| **Phone Screenshots** | Min 1080px shortest side (16:9 / 19.5:9) | App UI Screenshots | Capture live screens on dark `#0A0D10` theme showing active workout logger, progress charts, and routine builder. |

---

## 7. Cleanup & Removed Legacy Assets

| Legacy Asset | Action Taken | Reason |
| :--- | :---: | :--- |
| `human_banner.xml` | **`REMOVED`** | Replaced in `WelcomeScreen.kt` with responsive Compose brand header. |
| Legacy cyan `#00E5FF` | **`CONSOLIDATED`** | Consolidated to canonical `#0072FF` electric blue in `Color.kt`. |

---

## 8. Preserved Domain Assets (Non-Brand Content)
The following assets are domain exercise graphics or functional icons and are intentionally preserved:
- Exercise thumbnail vector drawables and muscle highlight diagrams.
- Material Symbol functional icons (`ic_back_arrow`, `ic_settings`, etc.).
