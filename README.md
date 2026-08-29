# Gift Access Assistant

Gift Access Assistant is an independent Android accessibility tool for people with motor, dexterity, or other disabilities who need help managing gifts in Pokémon GO. A run starts only after the user presses **Start**, grants Android screen-capture consent, and has enabled the app's accessibility service.

The app does not request Pokémon GO credentials, inspect game memory/files/network traffic, use private APIs, require root, conceal automation, or upload captured frames. It is not affiliated with or endorsed by Niantic, Pokémon, Nintendo, or The Pokémon Company.

> Third-party UI automation may be restricted by Pokémon GO's current terms. Review the current rules before use. Keep guided mode enabled after every game or device-layout update until the complete flow has been recalibrated.

## Current verification status

- The project compiles and produces a debug APK.
- JVM unit tests cover workflow transitions, stop conditions, retries, low confidence, unexpected dialogs, fake recognizer/executor ports, bag-full recovery, and protected-item deletion rules.
- OCR cue tests cover the supplied gift-flow states.
- An Android instrumentation fixture suite runs the real ML Kit pipeline over the supplied screenshots.
- The supplied 1080 × 2340 portrait screenshots cover map/home, Friends, sort options, gift/friendship sorted lists, received gift postcard, friend detail, gift picker, gift composition, sent state, and rewards.
- Live gestures, MediaProjection behavior, accessibility-node availability, sort-arrow detection, and color-based send eligibility still require a physical-device guided run.
- Item deletion is intentionally safety-locked: without calibrated discard-dialog quantity fixtures, the recognizer returns no verified quantity and the reducer refuses to confirm deletion.

Guided dry-run mode is enabled by default. It places a non-touchable yellow accessibility overlay around the intended target and does not tap.

## Architecture and toolchain

This repository was an existing Pokémon appraisal scanner. Its useful multi-module boundaries, on-device MediaProjection capture, ML Kit dependency, Hilt setup, and Room support were retained and adapted.

- Kotlin 2.0.21 and Java 17
- Gradle 8.9 and Android Gradle Plugin 8.7.3
- compile/target SDK 35, minimum SDK 26
- Jetpack Compose and Material 3
- Preferences DataStore for settings, onboarding status, and the sanitized local log
- ML Kit on-device text recognition

Modules:

- `app`: Compose UI, onboarding, permission launchers, settings/log persistence, and the MediaProjection foreground service.
- `core-model`: automation states, observations, safe targets, counters, settings, and cleanup item catalog.
- `core-domain`: pure reducer, stop/recovery decisions, protected-item policy, and fakeable recognition/gesture ports.
- `core-image`: OCR/screen recognition, normalized target extraction, visual heuristics, calibration profile loading, and fixture tests.
- `automation-debug`: accessibility-node inspection, gesture execution, manual-touch detection, guided overlay, and coordinator.
- `core-database`: retained local scanner database code; gift-assistant logs use DataStore and contain no friend names.

The workflow is an explicit reducer using the requested states, including verifying, navigation, sorting, friend scanning, opening, results, sending, bag cleanup, pause/recovery/error, and completion. The coordinator always recognizes a stable screen before requesting the next action.

## Build

Open the project in Android Studio with JDK 17 and Android SDK 35, or use the checked-in local toolchain:

```powershell
$env:JAVA_HOME=(Resolve-Path '.jdk\jdk-17.0.19+10').Path
& '.\.gradle-bootstrap\gradle-8.9\bin\gradle.bat' :core-domain:test :core-image:testDebugUnitTest :app:assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Useful verification commands:

```powershell
& '.\.gradle-bootstrap\gradle-8.9\bin\gradle.bat' :app:lintDebug
& '.\.gradle-bootstrap\gradle-8.9\bin\gradle.bat' :core-image:assembleDebugAndroidTest
```

The screenshot fixture test needs an Android device or emulator:

```powershell
& '.\.gradle-bootstrap\gradle-8.9\bin\gradle.bat' :core-image:connectedDebugAndroidTest
```

The Android test APK embeds the local `screenshots/` fixtures, which can include trainer names. Do not distribute that test APK.

## Install and first-run setup

1. Install `app-debug.apk` from Android Studio or with `adb install -r`.
2. Open Gift Access Assistant and read the automation/terms warning.
3. Open Android accessibility settings and enable **Gift Access Assistant**. The service is package-filtered to Pokémon GO.
4. Allow notifications so the foreground notification can expose Pause and Stop.
5. Leave **Guided dry-run mode** enabled.
6. Put Pokémon GO on the main map in portrait orientation.
7. Return to this app, acknowledge the run checkbox, and press **Start guided run**.
8. Grant Android's screen-capture prompt. Pokémon GO opens and the intended target is highlighted without being tapped.
9. Make that action manually, return to the app if necessary, and press Resume to validate the next state.
10. Only consider live mode after every state in the applicable workflow has passed guided validation.

Each Start requests fresh MediaProjection consent. Pause stops gesture progress while retaining capture so Resume can recheck the screen. Stop ends the run and capture. Manual touch detected inside Pokémon GO pauses the run immediately.

## Permissions

- **Accessibility service**: reads supported node text/content descriptions/bounds when Pokémon GO exposes them, performs user-requested gestures, displays the guided target overlay, and detects manual touch.
- **Screen capture / MediaProjection**: supplies on-device frames when usable accessibility nodes are absent.
- **Foreground service**: keeps capture visible and user-controlled.
- **Notifications**: displays persistent Pause and Stop controls while capture is active.

No overlay permission, storage permission, account credential, root access, or network permission is requested by the app.

## Item-cleanup safety

Enabled by default, pending confirmation of the Nanab wording:

- Potion
- Super Potion
- Hyper Potion
- Revive
- Razz Berry
- Nanab Berry

Always protected in the domain policy:

- Max Potion
- Max Revive
- Golden Razz Berry
- Silver Pinap Berry
- every unknown item
- every item not selected in Settings

`Razz Berry` and `Golden Razz Berry` use exact-name matching. A discard is allowed only when the recognized dialog item matches the pending item and both selected and available quantities are verified, equal, and greater than zero.

Please confirm that “napnap berry” means **Nanab Berry** before treating the provisional default as final.

## Calibration

See [docs/CALIBRATION.md](docs/CALIBRATION.md) for the exact capture matrix and profile format. The app automatically loads a matching file-backed profile from `files/calibration/<profile-id>/`.

An example is in [docs/calibration-profile-example](docs/calibration-profile-example). Targets are normalized from 0 to 1 and can be supplied by accessibility bounds, OCR bounds, a calibrated profile, or a visual template. Accessibility nodes take priority.

## Privacy and logs

Friend names may exist transiently in the OCR engine, but observations expose only session-scoped hashes and generic safe cues. Fingerprints and screenshots are not persisted by the gift workflow. The local log contains state/action messages only, is capped at 200 entries, and can be cleared in the app.

## Troubleshooting

- **Start opens accessibility settings**: enable Gift Access Assistant, then return and Start again.
- **Recognition pauses**: check Diagnostics. Capture the exact full screen with system bars, game version, language, and device/display settings.
- **Wrong or shifted target**: stop, re-enable guided mode, and create a matching calibration profile.
- **After a game update**: treat all layouts as unsupported until guided validation passes.
- **Network/system dialog**: resolve it manually; Resume only from a supported stable screen.
- **Bag cleanup pauses**: expected until all item-row and discard quantity fixtures are calibrated. Never bypass the safety check.
