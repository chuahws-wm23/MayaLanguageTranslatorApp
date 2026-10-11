# UI/UX Audit and Improvement Plan

## Current architecture

The app is a single-activity Jetpack Compose application using Kotlin, Material 3 and ViewModels with StateFlow. Dependencies are created through a small manual dependency container in `AppDependencies`.

Data access is separated behind repositories. When Firebase is available the app uses Firebase Authentication and Cloud Firestore; otherwise it falls back to session-only in-memory repositories. Translation is dictionary-based and uses a composite repository so the core phrase set can work alongside the Firestore dictionary. Image input uses Google ML Kit OCR. Voice input uses Android speech recognition.

The current screen set is:

- Login, registration and password reset
- Home
- Text translation
- Image translation
- Voice translation
- Saved vocabulary
- Translation history
- Usage analytics
- Feedback
- Profile
- Administrator feedback review

## Main findings

### High priority

1. **Back navigation is inconsistent**
   - Main content is switched with an enum rather than a navigation back stack.
   - Android system Back can exit the activity instead of returning from a feature screen to Home.
   - Fix: add explicit system-back handling without replacing the existing navigation architecture.

2. **Some top-bar actions look interactive but do nothing**
   - The Home menu and notification icons currently have empty actions.
   - Effect: users can tap controls with no response.
   - Fix: remove nonfunctional affordances until there is a real destination or feature.

3. **Authentication forms need better mobile input behaviour**
   - Passwords cannot be revealed temporarily.
   - IME actions, keyboard dismissal and keyboard-safe spacing are limited.
   - Fix: reusable password field, proper Next/Done actions and IME padding.

4. **Backend errors can leak technical wording**
   - Firebase exceptions can surface directly through ViewModel error text.
   - Fix: map common authentication/network failures to plain-language messages.

5. **Saved-vocabulary deletion is immediate**
   - A single accidental tap deletes the item.
   - Fix: add a confirmation dialog before removal.

6. **Password reset from Profile can fail silently**
   - A restored Firebase session may not populate the editable email field, and auth messages are not surfaced from the main shell.
   - Fix: hydrate auth state from the current user and surface auth success/error messages through the app snackbar.

7. **App refresh performs Firestore reads sequentially**
   - Profile, history, saved vocabulary, feedback and analytics are loaded one after another.
   - Fix: run independent reads concurrently while keeping the existing repository APIs.

### Medium priority

1. **Design tokens are only partly centralised**
   - Colours are centralised, but typography, shapes and spacing are mostly default or hard-coded.
   - Fix: establish reusable typography, shape and spacing tokens.

2. **Fixed card heights can be fragile with font scaling**
   - Home cards use fixed heights.
   - Fix: prefer minimum heights and allow content to grow.

3. **History search has weak empty-state feedback**
   - “No translations yet” is shown even when records exist but a search has no matches.
   - Fix: distinguish empty history from empty search results and add a clear-search action.

4. **Feedback submission can feel abrupt**
   - Rating/comment controls need clearer validation and keyboard-safe spacing.
   - Fix: disable submission until required input is valid and show concise supporting text.

5. **Voice and TTS depend on device services**
   - Speech recogniser/TTS availability varies by emulator and device.
   - Fix: handle unavailable services gracefully instead of failing unexpectedly.

6. **Saved screen naming is slightly confusing**
   - The bottom destination is labelled “Dictionary” while the actual screen contains saved vocabulary.
   - Fix: label the destination according to its current function unless a full browseable dictionary screen is added later.

### Low priority

- Subtle screen transitions and motion
- Additional skeleton states
- Full string-resource localisation pass
- Tablet-specific two-pane layouts

## Implementation plan

### Group 1 — design system and navigation
- Centralise typography, shapes and spacing.
- Enable edge-to-edge rendering.
- Add correct Android system Back behaviour.
- Remove dead top-bar actions.
- Show non-blocking loading progress when user data refreshes.

### Group 2 — forms and authentication
- Add reusable password input with visibility control.
- Improve IME actions and keyboard behaviour.
- Prevent duplicate auth submissions.
- Map common Firebase errors to useful messages.
- Fix password-reset feedback from Profile.

### Group 3 — content screens and QoL
- Improve History search and empty states.
- Add Saved vocabulary delete confirmation.
- Improve Feedback validation.
- Improve device-service failure handling for voice/TTS.
- Preserve all current Firebase field names, Firestore collections and security rules.

## Risk controls

This pass does **not** change Firestore collection names, document field names, API contracts or security rules. It also does not replace the current MVVM/repository structure.

History deletion is intentionally not added in this pass because current Firestore rules deny translation deletion. Adding that function would require an explicit security-rule and workflow decision.
