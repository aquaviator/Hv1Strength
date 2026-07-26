# HUMAN STRENGTH — BRAND ASSET AUDIT & TECHNICAL REFERENCE

## 1. Executive Summary & Brand Identity
The **Human Strength** visual brand identity is strictly defined by the master artwork files in the project root:
- **`HV1 Icon.png`**: Master 1:1 square icon artwork. Features black/near-black background, thick white circular ring, white lifter figure, and white barbell. Contains **NO blue** inside the logo mark.
- **`HV1-Banner.png`**: Master landscape banner artwork. Defines the authentic typography hierarchy (`HUMAN V1`, `STRENGTH`, `TRAIN. TRACK. TRANSFORM.`) and premium black athletic aesthetic.

---

## 2. Canonical Master References (Project Root)
| Master File | Asset Type | Purpose / Description | Status |
| :--- | :--- | :--- | :---: |
| `HV1 Icon.png` | 1254x1254 PNG | Immutable master app icon artwork (all-white mark on dark background). | **`IMMUTABLE MASTER`** |
| `HV1-Banner.png` | 1983x793 PNG | Immutable master brand banner artwork. | **`IMMUTABLE MASTER`** |

---

## 3. Derived Android Runtime Asset Architecture

### A. Drawable Resources (`app/src/main/res/drawable/`)

| Resource Name | Format / Size | Source Reference | Usage / Surface |
| :--- | :--- | :--- | :--- |
| `@drawable/human_logo` | PNG (512x512) | Derived 1:1 from `HV1 Icon.png` | Standard icon mark for splash screen, settings card, workout dashboard, app info dialogs. |
| `@drawable/human_icon_master_derived` | WebP (512x512) | Derived 1:1 from `HV1 Icon.png` | High-fidelity master icon asset. |
| `@drawable/human_launcher_fg` | PNG (108x108) | Derived 1:1 from `HV1 Icon.png` | Adaptive icon foreground (72x72 mark centered in 108x108 canvas with safe zone padding). |
| `@drawable/human_brand_lockup_mobile` | PNG (800x541) | Derived directly from `HV1-Banner.png` | Welcome/Auth screen brand header (preserves exact "HUMAN V1", "STRENGTH", "TRAIN. TRACK. TRANSFORM." typography lockup). |
| `@drawable/human_banner_master_derived` | PNG (1200x480) | Derived directly from `HV1-Banner.png` | Full landscape banner asset. |
| `@drawable/ic_launcher_background` | XML Vector | `#0A0D10` | Solid dark background layer for launcher adaptive icons. |
| `@drawable/ic_launcher_foreground` | LayerList XML | `@drawable/human_launcher_fg` | References centered 108x108 raster foreground for adaptive icons. |
| `@drawable/ic_launcher_foreground_monochrome` | XML Vector | Android 13+ Spec | Material You monochrome icon vector using pure white paths. |
| `@drawable/ic_splash_icon` | LayerList XML | `@drawable/human_logo` | Centered 120dp all-white mark over dark canvas for system splash screen. |

### B. Mipmap Density Architecture (`app/src/main/res/mipmap-*/`)

| Mipmap Density | Sizing | Files (`ic_launcher.png` & `ic_launcher_round.png`) | Source Reference |
| :--- | :--- | :--- | :---: |
| `mipmap-mdpi` | 48 x 48 px | Derived directly from `HV1 Icon.png` | `HV1 Icon.png` |
| `mipmap-hdpi` | 72 x 72 px | Derived directly from `HV1 Icon.png` | `HV1 Icon.png` |
| `mipmap-xhdpi` | 96 x 96 px | Derived directly from `HV1 Icon.png` | `HV1 Icon.png` |
| `mipmap-xxhdpi` | 144 x 144 px | Derived directly from `HV1 Icon.png` | `HV1 Icon.png` |
| `mipmap-xxxhdpi` | 192 x 192 px | Derived directly from `HV1 Icon.png` | `HV1 Icon.png` |

---

## 4. Google Play Store Assets (`docs/`)

| Asset File | Size | Source Reference | Purpose |
| :--- | :--- | :--- | :--- |
| `docs/play_store_app_icon_512.png` | 512 x 512 px PNG | Derived 1:1 from `HV1 Icon.png` | Store Listing App Icon |
| `docs/play_store_feature_graphic_1024x500.png` | 1024 x 500 px PNG | Derived directly from `HV1-Banner.png` | Store Listing Feature Graphic |

---

## 5. Cleaned & Removed Legacy Assets

| Removed Asset | Reason | Replacement |
| :--- | :--- | :--- |
| `app/src/main/res/drawable/human_logo.xml` | Contained unapproved `#0066FF` blue barbell paths. | Replaced by `human_logo.png` (derived 1:1 from `HV1 Icon.png`). |
| `app/src/main/res/drawable/human_launcher.xml` | Contained unapproved `#0066FF` blue barbell paths. | Replaced by `human_launcher_fg.png` (derived 1:1 from `HV1 Icon.png`). |
| Compose text lockup in `WelcomeScreen.kt` | Created unapproved blue pill badge and arbitrary fonts. | Replaced by `@drawable/human_brand_lockup_mobile` derived from `HV1-Banner.png`. |
