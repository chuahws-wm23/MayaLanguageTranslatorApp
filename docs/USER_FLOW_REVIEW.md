# User-flow regression review — 18–19 November 2026

## Fixes

- Development password reset previously returned success without sending anything. It now reports that reset emails are unavailable in development mode; Firebase-backed reset is unchanged.
- Reset-password content now scrolls when the keyboard or a smaller display reduces the available height.
- Login, registration and Profile → About now describe glyph scanning and saved scans. Removed descriptions advertising unavailable text, voice and Yucatec tools.

## Scope

Automated UI interactions on an Android API 29 emulator use the real app navigation, authentication/data view-models and development/in-memory repositories. The gallery response supplies a licensed reference image through a real content URI; it does not operate a system picker. Inference tests run the actual ONNX model. The CameraX check captures an actual virtual-camera frame. Firebase service tests use disposable Auth/Firestore emulators and the checked-in rules, independently of the Android UI.

## Results

Tested application commit: `bc4b7a7e0fb1e11429b7faf176109a8d85b2c14f`.

- **PASS — 180 JVM tests**, zero failures/errors/skips in the local XML results; CI unit/build job also passed.
- **PASS — 18 Android runtime tests**, zero failures/errors/skips, including the complete user journey.
- **PASS — 19 photo-evaluation tooling tests.** These validate evaluation tooling, not recognition accuracy.
- **PASS — two Firebase emulator integration scenarios.**
- **PASS — Dataset checks.**

[Android checks and screenshots](https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/44341410575) · [Firebase integration](https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/44341410598) · [Dataset checks](https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/44341426939).

Captured Home, scanner, Favourites, History, Dictionary, Profile, Activity and Feedback screens were inspected. This visual review covers the emulator's default phone size and text scale; it does not establish every screen size or accessibility configuration.

| User flow | Result and scope |
| --- | --- |
| Empty login, registration, login, development reset feedback | PASS — integrated journey |
| Single-reference photo → inference → History | PASS — integrated journey; reference drawing fixture |
| Favourite a scan → view favourite → retain after logout/login | PASS — integrated journey |
| Dictionary search | PASS — integrated journey |
| Edit profile → save → retain after logout/login | PASS — integrated journey |
| Drawer → Activity → Feedback → logout | PASS — integrated journey |
| Multiple-glyph region boxes and result selection | PASS — existing Android fixture tests |
| Dictionary artwork, alternate views and pagination | PASS — Android UI + JVM checks |
| History search, details, pagination, confirmed deletion | PASS — Android UI + JVM checks |
| Favourites search, details and confirmed removal | PASS — Android UI + repository checks |
| Similarity filter: one result only when >85%, percentage shown | PASS — Android UI + JVM checks |
| Unreadable input and changing the selected photo | PASS — Android UI checks |
| Profile settings, About and recovery callback | PASS — Android UI checks |
| Camera capture, decode, reopen and cancel | PASS — virtual-camera Android test; permission pre-granted |
| Auth/Firestore persistence, ownership, reset codes, admin backend operations | PASS — two Firebase emulator scenarios |

The integrated test waits for temporary save notifications to disappear before tapping buttons they could cover, matching a visible user interaction. Screenshot collection runs even on test failure.

## Observed usability issue

Temporary save/profile snackbars can cover controls near the lower edge for a few seconds. They time out normally, but an immediate tap through the banner does not activate the underlying button. This is why the integrated journey waits for notifications before tapping those controls. This remains a minor usability issue; it is not reported as a repository saving or feedback-submission failure.

## Outstanding checks and known limitations

- **Real-world identification remains unreliable on the existing public-photo probes.** No recognition accuracy or calibrated confidence is established. Similarity percentages describe visual similarity; a score above 85% does not guarantee the glyph or meaning is correct. No model changes were made in this regression pass.
- Physical camera permissions, denial/retry, rotation, different gallery providers, Android 35-specific behavior and actual phone layouts need device testing.
- Actual Firebase deployment/configuration, email delivery and physical cross-device persistence were not exercised. Development accounts remain session-only.
- Full administrator UI and the legacy administrator analytics counters were not certified by these checks.
