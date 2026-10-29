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
| FR08 | OCR Text Extraction | Implemented with image validation/resizing/orientation handling and Google ML Kit Latin text recognition. |
| FR09 | OCR Text Correction | Recognised text is editable before translation. |
| FR10 | Voice Input | Implemented with runtime microphone-permission handling and Android speech recognition. |
| FR11 | Speech-to-Text Conversion | Recognised English speech is displayed as editable text. |
| FR12 | Pronunciation Support | Implemented through device TTS with an explicit unavailable-audio state when the requested voice is not installed. |
| FR13 | Translation History | Successful translations are stored with input type and timestamp. |
| FR14 | History Management | View, search, filter, selectable detail view and confirmed deletion are implemented. Firestore deletion is restricted to the record owner. |
| FR15 | Dictionary Access | User-facing Maya/English search, sorting/sections and progressive Load More browsing are implemented. |
| FR16 | Saved Vocabulary | Save, duplicate prevention, search/filter, selectable details/pronunciation and confirmed removal are implemented. |
| FR17 | Usage Analytics | Text, image and voice counters and activity summaries are implemented. |
| FR18 | Feedback Submission | Rating/comment validation and Firestore submission with pending status are implemented. |
| FR19 | User Management | Administrators can search users and suspend/reactivate permitted accounts. Suspended accounts are blocked at login and signed out when status is loaded. |
| FR20 | Dictionary Management | Administrators can search, add, edit, delete and CSV bulk-import Firestore dictionary entries with duplicate/invalid-row validation. Built-in fallback entries are intentionally read-only. |
| FR21 | Feedback Management | Administrators can review feedback and mark it reviewed. |
| FR22 | System Reports | Administrator reports aggregate users/translations/feedback, show method charts and numeric values, and include recent translation records. |

## Non-functional requirements

The architecture supports the NFRs, but some final compliance claims require
measured/manual evidence rather than code inspection alone.

- **NFR01 Usability:** role-specific navigation, readable labels, search/filter controls and confirmation dialogs are implemented.
- **NFR02 Performance:** final device timing must verify the Project I targets for text, image and voice processing.
- **NFR03 Reliability:** invalid input, OCR failure, camera/microphone permission denial, unclear/cancelled speech, unavailable TTS and repository/service failures have controlled UI responses. Final device regression testing remains required.
- **NFR04 Security:** Firebase Authentication, role checks and Firestore rules protect privileged operations.
- **NFR05 Privacy:** user records are scoped by authenticated user ID; administrator reads are explicitly role-gated.
- **NFR06 Maintainability:** UI, ViewModel/service and repository responsibilities remain separated using the existing MVVM/repository structure.
- **NFR07 Compatibility:** minSdk/targetSdk are defined; final emulator/physical-device and screen-size tests remain required.
- **NFR08 Scalability:** Firestore collections remain document-based; dictionary browsing is progressive and system-report reads are bounded. Chapter 4's broader pagination wording should be revised or completed for saved vocabulary/feedback if literal design matching is required.
- **NFR09 Accessibility:** important icon actions have content descriptions and controls use Material components; final font-scale/TalkBack review remains required.
- **NFR10 Availability:** cloud/device-service errors are caught and surfaced; key data retrieval failures offer Retry actions. Final offline testing remains required.

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


## Full Project I alignment

See `docs/PROJECT_I_REPORT_ALIGNMENT.md` for the UC-01–UC-23 flow audit,
activity/sequence-diagram alignment, report numbering corrections, and the
remaining manual acceptance gate.
