# Feature validation and remaining release checks

Latest user-flow run: see [USER_FLOW_REVIEW.md](USER_FLOW_REVIEW.md) for 180 passing JVM tests, 18 passing Android runtime tests, two passing Firebase emulator scenarios, fixes and remaining limitations. Older counts below describe earlier runs.

This pass removes the Learn Maya hub, lessons, quizzes, number playground, progress display, and their entry points. It restores the earlier Activity layout. Existing scanner result wording (“Closest reference found”), region selection, dictionary pages, History and Profile improvements remain.

## Fixes from validation

- Account changes immediately clear the old profile, history, favourites and loading state. Sign-out cancels outstanding data work; a session version prevents a delayed refresh from repopulating signed-out state.
- Saved-item and History removals reject items belonging to another account. Profile, feedback and removal callbacks ignore stale account results.
- Deleting a History item refreshes the underlying Activity totals.
- Registration, login and password reset trim email addresses. Validation rejects repeated `@`, whitespace inside addresses, empty domain labels and malformed domain-label boundaries. Names are trimmed before registration. Blank passwords are rejected; valid passwords are preserved verbatim.

## Automated coverage

| Feature | Evidence and scope |
| --- | --- |
| Account inputs | AuthValidationTest: malformed reset addresses, trimmed valid addresses, repeated reset clicks, registration name/password/confirmation rules. Fake authentication repository; no email is sent. |
| Account state | AppDataValidationTest: immediate clearing on switch, delayed refresh after logout, ownership checks, profile validation, feedback double-submit/failure/retry, and refreshed totals after deletion. |
| Single glyph scanner | GlyphReferenceWorkflowTest: actual licensed reference retrieval, alternatives, photo changes, exact artwork, unreadable input and correct user-facing candidate wording. Reference-only tests do not establish field-photo accuracy. |
| Multiple glyph scanner | Region boxes and controls select the same result, detected reference panels and review controls, source-image coordinates and reading-order unit tests. Automatic segmentation remains experimental for natural inscriptions. |
| Dictionary | Large reference images, alternate-view dialog, syllable/word views, source-resolution checks and paging unit tests. |
| History | Saved image and candidate details, search, ten-item pagination, page reset/clamping after deletion. Repository tests cover account isolation and separate favourite retention. |
| Favourites | New runtime search/detail/confirmation/removal flow. Repository tests cover deterministic favourite identity, repeated saving and independence from History deletion. |
| Profile | Runtime name editing, photo settings, password-recovery callback, About and sign-out; light/dark screenshots. Profile image handling uses account-specific local preferences. |
| Feedback | New runtime rating-required and success-reset flow. View-model tests cover validation, repeated clicks, failure and retry. |
| Activity | Previous screen restored; History deletion now reloads totals. It uses the existing repository analytics, including legacy record types. |
| Admin | Existing repository tests cover dictionary CRUD and user status changes. This pass does not establish end-to-end Firebase administrator functionality. |

The Android workflow runs the active JVM and emulator suites and assembles the debug APK. Removed-feature tests are removed rather than being reported as checks of active functionality.

## Checks still required before claiming release completeness

- Test with the project's configured Firebase instance: real account registration, login, reset-email delivery, permission rules, session restoration and cross-device persistence. CI uses development/in-memory repositories when credentials are absent.
- Test CameraX on a physical phone, including permission denial, capture cancellation, rotation and gallery providers. An emulator/reference fixture does not validate hardware camera quality.
- Validate real-world photographs against a held-out, expert-labelled dataset. Current public-photo probes have known misses; neither high similarity nor passing reference tests is a measured identification confidence.
- Verify administrator operations against the intended production data. Admin vocabulary editing does not edit the bundled TWKM reference artwork.

These are limitations of available evidence, not claims that those workflows have been verified successfully.

## Local verification

`testDebugUnitTest assembleDebug assembleDebugAndroidTest`: passed, 177 JVM tests with zero failures/errors. `lintDebug`: passed, zero errors and 39 warnings (dependency updates, Compose modifier conventions, unused resources, KTX suggestions and package visibility in the retired voice screen). Emulator results are recorded by the Android checks workflow; no claim of a passing test is based on compilation alone.

## Integration follow-up

See [INTEGRATION_COMPLETION.md](INTEGRATION_COMPLETION.md) for the added Auth/Firestore emulator scenarios, CameraX virtual-camera check and live Activity counter fix. Emulator service checks narrow the earlier integration gaps but do not certify physical devices, production configuration, actual email delivery, or real-photo identification.
