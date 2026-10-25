# Requirements Implementation Status

This file maps the Project I requirements to the current `fyp-development`
implementation. Functional implementation and evaluation evidence are tracked
separately: a feature can be implemented while still requiring device or
performance testing for the final Results chapter.

## Functional requirements

| ID | Requirement | Implementation |
| --- | --- | --- |
| FR01 | User Registration | Implemented with Firebase Authentication and user profiles. |
| FR02 | User Login | Implemented for users and administrators with role-based routing. |
| FR03 | Password Recovery | Implemented through Firebase password-reset email. |
| FR04 | Profile Management | Implemented for permitted profile fields. |
| FR05 | Text Input | Implemented for Maya and English text. |
| FR06 | Text Translation | Implemented with dictionary/phrase lookup in both directions. |
| FR07 | Image Selection | Implemented with CameraX capture and gallery selection. |
| FR08 | OCR Text Extraction | Implemented with Google ML Kit Latin text recognition. |
| FR09 | OCR Text Correction | Recognised text is editable before translation. |
| FR10 | Voice Input | Implemented using Android speech recognition. |
| FR11 | Speech-to-Text Conversion | Recognised English speech is displayed as editable text. |
| FR12 | Pronunciation Support | Implemented through device TTS when the requested voice is available. |
| FR13 | Translation History | Successful translations are stored with input type and timestamp. |
| FR14 | History Management | View, search, filter and confirmed deletion are implemented. Firestore deletion is restricted to the record owner. |
| FR15 | Dictionary Access | User-facing dictionary browse and Maya/English search are implemented. |
| FR16 | Saved Vocabulary | Save, search/filter and confirmed removal are implemented. |
| FR17 | Usage Analytics | Text, image and voice counters and activity summaries are implemented. |
| FR18 | Feedback Submission | Rating and written feedback are implemented. |
| FR19 | User Management | Administrators can search users and suspend/reactivate permitted accounts. Suspended accounts are blocked at login and signed out when status is loaded. |
| FR20 | Dictionary Management | Administrators can search, add, edit and delete Firestore dictionary entries. Built-in fallback entries are intentionally read-only. |
| FR21 | Feedback Management | Administrators can review feedback and mark it reviewed. |
| FR22 | System Reports | Administrator reports aggregate registered users, translation totals/channels, feedback totals and pending feedback. |

## Non-functional requirements

The architecture supports the NFRs, but some final compliance claims require
measured/manual evidence rather than code inspection alone.

- **NFR01 Usability:** role-specific navigation, readable labels, search/filter controls and confirmation dialogs are implemented.
- **NFR02 Performance:** final device timing must verify the Project I targets for text, image and voice processing.
- **NFR03 Reliability:** invalid input, OCR failure, camera permission denial, unavailable speech/TTS and repository failures have controlled UI responses. Final device regression testing remains required.
- **NFR04 Security:** Firebase Authentication, role checks and Firestore rules protect privileged operations.
- **NFR05 Privacy:** user records are scoped by authenticated user ID; administrator reads are explicitly role-gated.
- **NFR06 Maintainability:** UI, ViewModel/service and repository responsibilities remain separated using the existing MVVM/repository structure.
- **NFR07 Compatibility:** minSdk/targetSdk are defined; final emulator/physical-device and screen-size tests remain required.
- **NFR08 Scalability:** Firestore collections remain document-based and do not require schema redesign as users and records grow.
- **NFR09 Accessibility:** important icon actions have content descriptions and controls use Material components; final font-scale/TalkBack review remains required.
- **NFR10 Availability:** cloud/device-service errors are surfaced without intentional crashes; final offline testing remains required.

## Added automated checks

`RequirementRepositoryTest` covers:

- owner-scoped history deletion behaviour
- dictionary add/edit/search/delete behaviour
- administrator account-status updates
- system analytics aggregation across users

## Manual acceptance checks still required

Before final evaluation, perform the device/Firebase checks in
`docs/TESTING_STRATEGY.md`, including OCR accuracy, performance timing,
camera/microphone permission cases, offline behaviour, multiple screen sizes,
and Firestore access-control tests.
