# Glyph integration validation

The Android source and exact bundled model were checked on a temporary staging branch before this integration was applied to fyp-development.

GitHub Actions run: https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43473438115

- Command: `gradle testDebugUnitTest assembleDebug --stacktrace`
- Result: BUILD SUCCESSFUL
- JUnit totals: 64 tests, 0 failures, 0 errors, 0 skipped.
- Includes 17 new model/crop software checks.
- Model SHA-256: `d4ed697fbb7dd3552f6efac0e58e6c29e872b9b60832dba8d5f6a19bd94a4094`.
- Final app code and asset are unchanged from that validated staging version. Documentation is updated; the temporary model-transfer workflow is excluded.

These checks confirm compilation, APK assembly and the listed unit tests. They do not establish recognition accuracy, runtime performance on a phone, camera/device compatibility or visual UI correctness on every screen size.

See GLYPH_ANDROID_INTEGRATION.md for physical-device acceptance checks and experimental limitations.
