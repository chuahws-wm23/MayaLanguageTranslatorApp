# MayaLanguageTranslatorApp

Final Year Project Android application for **Yucatec Maya ↔ English** text translation, image OCR, and English voice-to-Yucatec-Maya translation.

## Current implementation scope

This development branch follows the Project I design:

- Kotlin + Jetpack Compose
- MVVM-style presentation layer
- Firebase Authentication and Cloud Firestore
- Dictionary-based Yucatec Maya ↔ English translation
- Google ML Kit Latin-script OCR for modern Yucatec Maya text
- Android speech recognition / text-to-speech planned for later iterations
- Translation history, saved vocabulary, analytics, feedback and admin functions planned after the core translation flow

Ancient Maya hieroglyph recognition is **not** part of this implementation scope.

## Branches

- `main` — clean baseline
- `fyp-development` — active FYP development

## Android Studio setup

1. Clone the repository.
2. Checkout `fyp-development`.
3. Open the repository root in Android Studio.
4. Use JDK 17.
5. Let Android Studio sync Gradle.
6. Before enabling Firebase, create a Firebase project and register Android package:
   `com.chuahws.mayalanguageapp`
7. Download `google-services.json` from Firebase Console and place it in `app/`.
8. Never commit that file.

The starter currently uses an in-memory dictionary so it can be developed before Firebase is configured.

## Dataset pipeline

The initial dataset pipeline is designed around machine-readable **Yucatec Maya** lexical data.

Place raw JSONL at:

`dataset/raw/kaikki-yucatec-maya.jsonl`

Then run:

```bash
python dataset/scripts/convert_kaikki.py dataset/raw/kaikki-yucatec-maya.jsonl dataset/processed/maya_english_dictionary.csv
python dataset/scripts/validate_dictionary.py dataset/processed/maya_english_dictionary.csv
```

The converter marks automatically imported entries as `verified=false`. Do not treat automatically extracted dictionary data as gold-standard evaluation data until the evaluation subset has been manually checked against reliable linguistic sources.

## Firestore import

Install:

```bash
pip install -r dataset/requirements.txt
```

Set a Firebase Admin service account outside the repository:

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
```

Import:

```bash
python dataset/scripts/import_firestore.py dataset/processed/maya_english_dictionary.csv
```

## Development order

1. Account management + typed text translation
2. Image translation with ML Kit OCR and editable recognised text
3. English speech recognition + Yucatec Maya translation + pronunciation
4. Translation history + saved vocabulary + usage analytics
5. Feedback + administrator functions
6. Integration, security, performance and testing
