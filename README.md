# MayaLanguageTranslatorApp

Android Final Year Project application for Yucatec Maya ↔ English translation.

## Application

The fyp-development branch contains the application flow:

- account registration, login, logout and password reset
- typed Yucatec Maya ↔ English translation
- camera and gallery image input
- Google ML Kit Latin-script OCR
- Yucatec Maya OCR post-filtering
- English speech recognition
- device text-to-speech playback
- translation history
- saved vocabulary
- usage analytics
- profile editing
- feedback submission
- administrator feedback review
- Firebase Authentication and Cloud Firestore repositories
- development-mode repositories for testing before Firebase setup

The main mobile navigation is:

Home · Translate · History · Saved · Profile

## Scope

The implementation targets modern Yucatec Maya written in Latin orthography.

Ancient Maya hieroglyph recognition is outside the current application scope.

## Branches

- main — clean baseline
- fyp-development — active FYP application branch

## Run in Android Studio

1. Checkout fyp-development.
2. Pull the latest changes.
3. Sync Gradle.
4. Run the app configuration on an emulator or Android device.
5. Before Firebase is configured, create a temporary development account.
6. Use the sample dictionary to test translation.
7. For persistent data, follow docs/FIREBASE_SETUP.md.

## Translation engines

The current Android implementation uses verified dictionary / phrase lookup.

An experimental direct **Yucatec Maya ↔ English neural machine translation** pipeline has now been added under `ml/`.

It is designed as a future fallback for text that is not found in the verified dictionary. The model is not connected to the Android app until it has been trained and evaluated.

See:

- `ml/README.md`
- `docs/NMT_EXPERIMENT.md`

## Development sample dictionary

A small source-checked dictionary is included so the interface can be tested before Firestore is ready.

Example English searches:

- water
- house
- tree
- moon
- green
- turtle
- papaya
- person
- cloud
- stone
- forest
- book
- make
- understand
- with

This sample is not the final dataset. See docs/DATA_SOURCES.md.

## Firebase collections

The application expects:

- users
- dictionary
- translations
- savedVocabulary
- analytics
- feedback

Security rules are included in firestore.rules.

Firebase configuration files and Admin service-account credentials must not be committed to Git.

## Dictionary dataset

The full dictionary is prepared through the dataset scripts:

- dataset/scripts/convert_kaikki.py
- dataset/scripts/validate_dictionary.py
- dataset/scripts/import_firestore.py

See docs/DATA_SOURCES.md for the source and attribution notes.


## Dataset quality workflow

The repository now separates three concepts:

1. **Source data** — machine-readable Yucatec Maya data downloaded from Kaikki / English Wiktionary.
2. **Application dictionary** — cleaned CSV imported to Firestore. Automatically imported rows remain `verified=false`.
3. **Evaluation set** — a manually reviewed subset used to report translation accuracy/coverage. Only rows explicitly marked `verified=true` are scored.

Build the dataset locally with:

```bash
python dataset/scripts/download_kaikki.py
python dataset/scripts/convert_kaikki.py dataset/raw/kaikki-yucatec-maya.jsonl dataset/processed/maya_english_dictionary.csv
python dataset/scripts/validate_dictionary.py dataset/processed/maya_english_dictionary.csv --report dataset/processed/validation_report.json
python dataset/scripts/prepare_evaluation_sample.py dataset/processed/maya_english_dictionary.csv dataset/evaluation/manual_evaluation.csv --size 200
```

After manually checking the evaluation CSV, score only the approved rows:

```bash
python dataset/scripts/evaluate_dictionary.py dataset/processed/maya_english_dictionary.csv dataset/evaluation/manual_evaluation.csv
```

A GitHub Actions workflow also builds and validates the current public Kaikki dataset and publishes the processed CSV as a workflow artifact.
