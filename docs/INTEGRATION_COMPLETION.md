# Integration completion: Firebase and camera

This supplements FEATURE_VALIDATION.md. Production accounts, production rules deployment and physical hardware are not modified by the tests.

## Completed engineering work

- User Activity reads Firestore server-side document counts instead of best-effort cached analytics. History deletion and repeated saves therefore reflect actual stored records. Aggregation queries avoid downloading all photo previews to compute totals. Administrator aggregate dashboards still use the legacy analytics collection and are not covered by this fix.
- Camera instructions no longer promise the removed crop/rotate screen. Camera initialization and lifecycle binding explicitly resume on the main dispatcher after awaiting the provider; the cold-start integration test exposed an off-main-thread preview access.
- Firebase emulator integration is reproducible with locked Node dependencies. It is isolated to `demo-maya-glyph-validation` and checks emulator hosts before running. No Google service credentials or live Firebase project are needed.
- Added a CameraX virtual-camera capture/reopen/cancel test to Android checks. The emulator now has an emulated back camera.

## Backend integration evidence

Run from the repository root:

```sh
npm --prefix validation/firebase ci --ignore-scripts
npm --prefix validation/firebase test
```

Two integration scenarios passed locally against real Firebase Authentication and Firestore emulators:

1. Registration, profile and scan persistence, incorrect password rejection, sign-in, password-reset code generation and completion, rejection of the old password, a separate authenticated client reading the stored scan, and signed-out read denial.
2. Owner-filtered queries, cross-account read/delete denial, client self-promotion denial, favourite retention after History deletion, authoritative History/favourite counts, user feedback status restrictions, admin dictionary create/edit/read/delete, admin feedback review, admin user listing, suspension blocking reads/writes and reactivation.

These test Firebase services and the repository's Firestore rules through the JavaScript SDK. They do not exercise Android Firebase repositories end-to-end or prove live email delivery. Emulator account data is disposable, and the admin bootstrap explicitly uses rules-disabled server context; the regular client is separately verified unable to promote itself.

## Android camera evidence

`CameraCaptureWorkflowTest` grants camera permission in its test environment, opens the real CameraX screen on API 29, captures a photo, decodes nonzero dimensions, reopens the camera and cancels. It validates the capture/file/disposal path using a virtual camera. It does not prove a physical device's permissions dialog, rotation, vendor camera behavior or glyph recognition accuracy. See the completed Android checks run for results.

## Still outstanding

- Real-photograph glyph identification: the existing source-described AJAW probe has known misses and no expert sign/box ground truth. No model weights or confidence thresholds were changed here.
- Production Firebase deployment, actual reset-email delivery, physical cross-device persistence and physical-camera checks remain manual release checks.
- Administrator UI end-to-end testing and legacy administrator analytics-counter consistency remain separate from the backend admin/rules scenarios above.

A passing integration suite must not be presented as proof that these outstanding items pass.
