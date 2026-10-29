# Project I Report Alignment Audit

This file is the implementation traceability checklist for the current
`fyp-development` branch. It compares the app against the Project I report,
including the formal functional requirements, use cases and Chapter 4 process
flows.

A code-complete requirement is not the same as an evaluated requirement.
Performance, compatibility, accessibility and offline behaviour still require
measured/manual evidence on a real device before the final Project II results
can be claimed.

## Functional requirements

| ID | Requirement | Current implementation status |
| --- | --- | --- |
| FR01 | User Registration | PASS — validated registration, Firebase Authentication, default `user` Firestore profile, success message, then return to sign-in. |
| FR02 | User Login | PASS — Firebase login, profile/role retrieval and role-based user/admin routing. Suspended accounts are blocked. |
| FR03 | Password Recovery | PASS — validated email and Firebase password-reset request with confirmation/error state. |
| FR04 | Profile Management | PASS — profile viewing, display-name update, profile photo selection, account information and password-reset entry point. |
| FR05 | Text Input | PASS — Maya or English manual input, editable before translation. |
| FR06 | Text Translation | PASS — Maya/English bidirectional dictionary/phrase translation, ambiguity handling and no-match state. |
| FR07 | Image Selection | PASS — CameraX capture and Android document/gallery selection. |
| FR08 | OCR Text Extraction | PASS — selected image is validated, resized/orientation-corrected and processed with ML Kit. The recognised visible text is shown without dictionary filtering. |
| FR09 | OCR Text Correction | PASS — OCR result remains editable before translation. |
| FR10 | Voice Input | PASS — microphone permission is checked/requested and English speech input is launched only when available. |
| FR11 | Speech-to-Text Conversion | PASS — Android Speech Recognition result is shown as editable text before translation; cancellation/silence/denial have controlled states. |
| FR12 | Pronunciation Support | PASS — device TTS is used for supported output/saved vocabulary, with visible unavailable state when the required voice is not installed. |
| FR13 | Translation History | PASS — successful translation records store source/target text, languages, input method and timestamp. |
| FR14 | History Management | PASS — own history can be viewed, searched, filtered, opened for complete details and deleted with confirmation. |
| FR15 | Dictionary Access | PASS — Maya/English search, browsing, sorting, sections and progressive Load More support. |
| FR16 | Saved Vocabulary | PASS — save, duplicate prevention, browse/search/filter, select for details/pronunciation and confirmed removal. |
| FR17 | Usage Analytics | PASS — text/photo/voice totals, numeric summaries, 7-day activity and charts. |
| FR18 | Feedback Submission | PASS — rating/comment validation and Firestore submission with `pending` status. |
| FR19 | User Management | PASS — authorised admin user list/search and suspend/reactivate operations; own admin account cannot be suspended. |
| FR20 | Dictionary Management | PASS — authorised admin search/add/edit/delete, duplicate validation, deletion confirmation and CSV bulk import with invalid/duplicate row reporting. |
| FR21 | Feedback Management | PASS — authorised admin review and status update with confirmation. |
| FR22 | System Reports | PASS — system-wide users/translations/feedback totals, method chart, numeric values and recent translation records. |

## Use cases

| Use case | Result | Implementation note |
| --- | --- | --- |
| UC-01 Register Account | PASS | Registration ends at sign-in as documented. |
| UC-02 Log In | PASS | Normal user/admin routing uses Firestore role. |
| UC-03 Reset Password | PASS | Firebase reset email flow. |
| UC-04 Manage Profile | PASS | View/edit permitted account information. |
| UC-05 Translate Typed Text | PASS | Input → validation → translation → result → optional pronunciation/history/analytics. |
| UC-06 Translate Image | PASS | Select/capture → validate/resize → OCR → review/edit → validate → translate. |
| UC-07 Translate Voice | PASS | Permission → speech recognition → review/edit → validate → translate. |
| UC-08 Play Pronunciation | PASS | Language availability check → TTS or unavailable state. |
| UC-09 View Translation History | PASS | List → select record → detail view, plus empty/error states. |
| UC-10 Manage Saved Vocabulary | PASS | Save → browse → select → pronounce/remove, duplicate and confirmation paths. |
| UC-11 View Usage Analytics | PASS | Numeric values and charts with zero state. |
| UC-12 Submit Feedback | PASS | Validated rating/comment stored as pending. |
| UC-13 Manage Users | PASS | Admin-only search/select/status management. |
| UC-14 Manage Dictionary Entries | PASS | Search/add/edit/delete/bulk import, validation and duplicate handling. |
| UC-15 Review Feedback | PASS | List/rating/comment/status review. |
| UC-16 View System Reports | PASS | System totals + method chart + recent translations; empty/error handling. |
| UC-17 Log Out | PASS | Firebase session ends, local app state clears and protected screens return to sign-in. |
| UC-18 Validate Translation Input | PASS | Blank, >500-character and non-letter input are rejected before processing. Direction is always represented by a valid enum. |
| UC-19 Process Translation | PASS | Repository lookup, success/alternatives/composed/no-match states; service errors do not create history. |
| UC-20 Extract Image Text | PASS | Image preparation/resizing → ML Kit → recognised text → user review. |
| UC-21 Recognise Speech | PASS | Microphone/recognizer flow returns editable recognised English or a retryable error state. |
| UC-22 Save Translation History | PASS | Result is displayed first; history failure does not remove the result. |
| UC-23 Update Usage Analytics | PASS | Analytics update follows successful history save and is best-effort; analytics failure does not remove result/history. |

## Activity and sequence diagram alignment

The implemented process order now matches the Project I process diagrams:

- **Authentication:** register/login/reset → Firebase → profile/role → correct dashboard.
- **Typed translation:** input → direction → validation → dictionary translation → result → history + analytics → optional pronunciation/save.
- **Image translation:** camera/gallery → image validation/down-sizing → OCR → editable recognised text → translation → history + analytics.
- **Voice translation:** microphone permission → Android speech recognition → editable recognised text → English-to-Maya translation → history + analytics.
- **History / vocabulary:** load user-owned records → select/details → delete/remove with confirmation where applicable.
- **Administrator:** role gate → manage users / dictionary / feedback / reports.
- **Sequence flow:** UI → ViewModel/service → repository/Firebase/ML Kit/speech/TTS → UI response follows the same high-level interaction order.

## Non-functional requirements

| ID | Status | Evidence / remaining work |
| --- | --- | --- |
| NFR01 Usability | CODE ALIGNED | Consistent Material navigation, sidebar/bottom navigation, readable labels, clear input/output and confirmation dialogs. Final user evaluation still belongs in Project II. |
| NFR02 Performance | NEEDS MEASURED EVIDENCE | Image preparation is bounded and async; dictionary is progressively loaded. Real-device timing must still prove ~2 s typed and ~5 s image/voice under stable internet. |
| NFR03 Reliability | CODE ALIGNED | Invalid input, OCR failure, unclear/cancelled voice, denied permissions and repository/service failures have controlled UI/error paths rather than intentional crashes. Regression/device tests remain required. |
| NFR04 Security | CODE ALIGNED | Firebase Authentication, role checks and Firestore rules gate privileged operations. |
| NFR05 Privacy | CODE ALIGNED | Firestore rules scope user-owned profile/history/saved/analytics/feedback records to the authenticated owner; admins have explicit role-gated access. |
| NFR06 Maintainability | CODE ALIGNED | Compose UI, ViewModels/services and repository/data layers are separated. |
| NFR07 Compatibility | NEEDS DEVICE EVIDENCE | Android min/target SDK and responsive Compose UI are defined; test at least two screen sizes and a physical Android phone. |
| NFR08 Scalability | CODE ALIGNED | Firestore document collections, dictionary progressive loading and bounded report retrieval support growth without schema redesign. Larger-volume stress evidence is still optional Project II evidence. |
| NFR09 Accessibility | NEEDS MANUAL REVIEW | Material touch controls and content descriptions are used; final font-scale and TalkBack review is still required. |
| NFR10 Availability | CODE ALIGNED / NEEDS OFFLINE TEST | Cloud/service failures are caught and surfaced, with retry actions for key retrieval flows. A real offline test is still required. |

## Important Project I report corrections

The **implementation now follows the intended behaviours**, but the report itself
contains old requirement numbering in several use-case tables. The formal
FR01–FR22 table should remain the source of truth.

Examples that should be corrected in the report before final submission:

- UC-05 currently references “FR05 – Typed Text Translation”; formal FR05 is
  **Text Input** and FR06 is **Text Translation**.
- UC-06 references old FR06 “Image Translation”; the formal table separates
  **FR07 Image Selection, FR08 OCR Extraction, FR09 OCR Correction**, with FR06
  still covering translation.
- UC-07/UC-08/UC-09/UC-10/UC-11/UC-12 use old sequential FR numbers; under the
  final formal table they map mainly to FR10–FR18.
- UC-13/UC-14/UC-15/UC-16 map to formal **FR19/FR20/FR21/FR22**, not the older
  FR13/FR14/FR15/FR16 labels shown in those use-case tables.
- The UC-14 table caption says **“UC-17: Manage Dictionary Entries”** even
  though the Use Case ID is UC-14.
- The UC-17 Logout table contains a duplicated/corrupted Trigger line.

These are documentation traceability issues, not missing application
functionality.

## Design-model differences to document or update in the report

The functional/process diagrams align, but a few structural diagrams should be
treated as conceptual unless they are updated to the final code:

1. The implementation uses a single `UserProfile` with a `role` field and
   role-based routing rather than literal Kotlin `User` and
   `Administrator : User` inheritance.
2. `SavedVocabularyItem` keeps `entryId` plus denormalised display fields
   (Maya/English/pronunciation/POS) for resilient rendering; the report describes
   a stricter reference-only saved-vocabulary model.
3. Pronunciation is implemented by the reusable Compose `SpeakButton`/Android
   TTS integration rather than a standalone domain `PronunciationService`
   class.

Do not add unused classes only to make the source code resemble a diagram.
Either describe those diagrams as conceptual design, or revise the final report
diagrams to mirror the implemented model.

## Final manual acceptance gate

Before declaring the whole FYP “100% passed”, record evidence for:

1. typed translation response time under stable internet;
2. camera/gallery OCR on a real phone, including invalid/too-small/unclear image;
3. microphone Allow and Deny, clear speech, silence/cancel and unavailable speech;
4. TTS available and unavailable voice state;
5. user A cannot read/delete user B history/saved/feedback/analytics;
6. normal user cannot access admin writes;
7. suspend → login blocked → reactivate → login works;
8. dictionary add/edit/duplicate/delete and CSV import;
9. admin report totals/chart/recent translations;
10. offline Firebase/service error states;
11. at least two screen sizes, larger font scale and basic TalkBack/content-description review.

Once those measured/manual checks are captured, the implementation can be
reported as fully verified against the Project I requirements.
