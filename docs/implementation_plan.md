# Gift Access Assistant implementation summary

## Implemented

- Accessible Compose/Material 3 onboarding, run controls, settings, diagnostics, logs, permissions, troubleshooting, and calibration guidance.
- Explicit pure workflow reducer with safe recovery and terminal conditions.
- Node-first accessibility recognition and click execution, on-device MediaProjection + ML Kit OCR fallback, normalized targets, stable-frame waits, configurable timeouts/retries/delays, and guided overlays.
- Persistent active-run notification with Pause and Stop.
- Immediate pause on detected manual touch.
- Session-only friend fingerprints and sanitized local logging.
- Exact protected-item policy and verified-full-quantity requirement.
- File-backed calibration profiles and screenshot instrumentation fixtures.
- Unit coverage for normal transitions, stop conditions, protected items, bag recovery, low confidence, retries, unexpected dialogs, and fake ports.

## Physical-device work remaining

- Run the full supplied path in guided mode on the source 1080 × 2340 device.
- Capture and calibrate every missing state listed in `docs/CALIBRATION.md`.
- Run `:core-image:connectedDebugAndroidTest` on a device/emulator with ML Kit available.
- Validate notification actions, manual-touch pause, capture revocation, font/display scaling, and at least one additional aspect ratio.

The app stays conservative when a required state is missing: it pauses or errors instead of tapping an uncertain control. Item deletion cannot pass without item-name and full-quantity verification.
