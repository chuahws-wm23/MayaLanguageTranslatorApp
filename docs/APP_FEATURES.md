# Application Features

## Account

- Register with name, email and password
- Login and logout
- Password-reset email through Firebase Authentication
- Profile display-name update
- User and administrator roles

## Translation

### Text

- Yucatec Maya to English
- English to Yucatec Maya
- Keyboard apostrophe normalization
- Copy, listen and save actions

### Image

- Capture a photo with the device camera
- Choose an image from the gallery
- Extract Latin-script text using Google ML Kit
- Switch the full-width English ↔ Yucatec Maya photo direction bar
- Show supported source-language words and phrases in a **read-only** detected-text field
- Correct OCR mistakes separately using the Review OCR dialog before translation
- Show possible Yucatec spelling matches for review; do not automatically convert other Maya varieties
- Save successful vocabulary entries

### Voice

- English speech recognition through Android speech services
- Editable transcript
- English to Yucatec Maya translation
- Device text-to-speech playback

## Personal data

- Translation history with search, input-type filtering, ten entries per page, inline editable **Page N of M** controls above the list and user-controlled deletion (currently up to 100 recently loaded records); tapping a record opens polished details with **Translate again** in the original Text/Photo/Voice mode
- Browseable/searchable Maya-English dictionary
- Saved vocabulary with ten entries per page, inline editable **Page N of M** controls above the list, detailed word view with pronunciation, and **Translate again** (exact saved Yucatec Maya text to English)
- Usage totals for text, image and voice translation
- Feedback form
- Profile screen

## Administration

When a Firestore user profile has the admin role, the application opens a role-specific administrator console.

Administrators can:

- view registered user profiles and search accounts
- suspend and reactivate permitted user accounts
- browse and search dictionary entries
- add, edit and delete Firestore dictionary entries
- review submitted feedback and mark items as reviewed
- view system-wide user, translation-channel and feedback summary statistics
- switch to the normal user view for translator testing

Built-in fallback dictionary entries are displayed as read-only. Persistent dictionary management applies to Firestore entries.

## Development mode

Before Firebase is configured, the app uses session-only repositories and a small source-checked dictionary. This keeps the full interface testable while the real backend is still being configured.

The full dictionary should be imported to Firestore before final evaluation.
