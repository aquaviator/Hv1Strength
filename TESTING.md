# Human V1 Strength Test Pass

Use this pass before every internal-testing release.

## Build gate

- `testDebugUnitTest` passes
- `assembleDebug` passes
- `bundleRelease` passes with the approved upload key
- No private keystore, `.env`, `local.properties`, or service-account file is committed

## Brand gate

- Launcher icon has no clipping on square and round launchers
- Splash uses dedicated splash artwork
- Welcome uses `human_banner`
- Settings/About uses `human_logo`
- No Compose screen uses `human_launcher`

## Functional gate

- Offline onboarding
- Google authentication
- Reactive Room-backed profile
- Routine create/edit/delete
- Active workout resume after process restart
- Actual set values stored separately from suggested targets
- History and personal-best calculations
- Weight/body-fat entry and derived metrics
- Search and filters
- JSON export/import
- Command queue retry and sync recovery

## Athlete-side UX gate

Perform the complete workout flow while holding the phone in one hand. Fail the screen when it requires horizontal scanning, precision tapping, repeated keyboard entry, or viewing multiple editable sets as a table.
