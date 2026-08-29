# Pokemon Scanner Implementation Plan

## Goal

Build a private Android scanner that can read Pokemon GO detail and appraisal screens, save scanned Pokemon locally, and run an automatic scan loop using on-device screen capture plus an internal accessibility gesture service.

## Current Scope

- Private debug build only.
- No account login, packet inspection, backend service, or public release workflow.
- Screen content stays on device.
- Automatic mode starts from a Pokemon detail screen, opens Appraise, reads IV bars, saves the result, advances to the next Pokemon, and stops on repeats, failures, or the configured maximum.

## Screenshots Used

The screenshots in `screenshots/` were used to calibrate:

- Detail screen regions for name, CP, HP, favorite state, and screen detection.
- Appraisal overlay regions for the three IV bars.
- Bottom and side gesture targets for opening Appraise and advancing through the appraisal carousel.

The Appraise menu coordinate is still provisional because the folder does not yet include a screenshot with the Pokemon detail overflow menu open.

## Main Components

- `app`: Compose UI, MediaProjection permission flow, screen capture foreground service, saved inventory view.
- `automation-debug`: Accessibility service and automatic scan coordinator.
- `core-image`: OCR, screen classification, stable frame detection, appraisal bar reading.
- `core-domain`: scan state reducer, duplicate/repeat stop policy, CP/level helpers.
- `core-database`: Room database for saved Pokemon records and scan sessions.
- `core-model`: shared scan, detection, state, and automation models.

## Resources Needed

- Android device with Pokemon GO installed.
- Screen capture permission granted at scan start.
- Internal Pokemon Scanner accessibility service enabled.
- Local Android SDK in `.android-sdk/`.
- Local portable JDK 17 in `.jdk/`.
- More screenshots for calibration, especially:
  - Pokemon detail screen with the overflow menu open.
  - Appraisal screens for different screen sizes or aspect ratios.
  - Edge cases such as shiny, favorite, shadow/purified, alternate forms, and low CP Pokemon.

## Verification

- Build debug APK with `:app:assembleDebug`.
- Run domain unit tests with `:core-domain:test`.
- On-device verification still needs a physical phone because MediaProjection and accessibility gestures cannot be fully validated from desktop unit tests.
