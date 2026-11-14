# MayaLanguageTranslatorApp

Android Final Year Project application focused on recognising supported Maya glyph drawings and showing proposed English meanings. Existing modern Yucatec Maya text tools are retained separately.

## Application

The fyp-development branch contains the application flow:

- account registration, login, logout and password reset
- camera and gallery input for experimental offline Maya glyph recognition
- single-glyph crop selection, EXIF orientation handling and manual rotation
- bundled TinyGlyphNet v1 model with unverified suggestions and a review fallback
- typed Yucatec Maya ↔ English dictionary/phrase lookup
- English speech recognition
- device text-to-speech playback
- translation history with search/filter/delete
- searchable dictionary browsing
- saved vocabulary
- usage analytics
- profile editing
- feedback submission
- role-specific administrator console
- administrator user management
- administrator dictionary management
- administrator feedback review
- administrator system reports
- Firebase Authentication and Cloud Firestore repositories
- development-mode repositories for testing before Firebase setup

The main mobile navigation is:

Home · Saved · History · Profile, with translation and learning screens accessible through Home and the sidebar.

## Scope

New images selected through **Photo translation** now use the **Maya glyph recognition** screen, not Latin-script text OCR.

TinyGlyphNet v1 supports five experimental reference categories: Deer (CHIJ), Hawk (MUWAAN), Jaguar (HIX), Star (EK') and Cacao (KAKAW), plus an OTHER category. KAKAW is treated as a whole-block example. This is not a general ancient Maya inscription translator. Proposed readings need specialist review, and real-world recognition accuracy has not been established.

The retained text, voice and dictionary features target modern Yucatec Maya written in Latin orthography. Those features and their stored text records are separate from glyph recognition. Older photo History items can still replay their original recognised text. New glyph scans are not automatically stored as verified dictionary entries or written into the existing text-history schema.

See `docs/GLYPH_ANDROID_INTEGRATION.md` and `docs/glyph_v1_training_report.json` for runtime details, provenance, limitations and phone-testing requirements. Earlier OCR-specific documents describe the previous photo workflow.

## Branches

- main — clean baseline
- fyp-development — active FYP application branch

## Run in Android Studio

1. Check that local changes are saved, then checkout fyp-development.
2. Pull the latest changes with `git pull --ff-only origin fyp-development`.
3. Sync Gradle.
4. Run the app configuration on an emulator or Android device.
5. Before Firebase is configured, create a temporary development account.
6. Open Home > Photo translation, choose or capture an image, crop one glyph and tap **Recognise glyph**.
7. For persistent account/text data, follow `docs/FIREBASE_SETUP.md`.

The 29,420-byte glyph model is bundled in `app/src/main/assets/maya_glyph_v1.mgl`. No separate model copy, Python installation, training dataset or model server is required on the phone. Recognition runs offline; account and Firestore features still have their existing connectivity requirements.

## Translation engines

The glyph screen uses custom Kotlin inference with the bundled TinyGlyphNet v1 weights. Outputs are labelled as possible, unverified meanings, not validated translations. Model scores are not shown as accuracy percentages.

The retained text/voice implementation uses verified dictionary / phrase lookup.

An experimental direct **Yucatec Maya ↔ English neural machine translation** pipeline is available under `ml/`. It is designed as a future fallback for text not found in the verified dictionary. That text model is not connected to the Android app until it has been trained and evaluated; it is separate from the bundled image-recognition model.

See:

- `ml/README.md`
- `docs/NMT_EXPERIMENT.md`
- `docs/GLYPH_ANDROID_INTEGRATION.md`

## Development sample dictionary

A small source-checked dictionary is included so the text interface can be tested before Firestore is ready.

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

The modern Yucatec Maya dictionary is prepared through the dataset scripts:

- dataset/scripts/convert_kaikki.py
- dataset/scripts/validate_dictionary.py
- dataset/scripts/import_firestore.py

See docs/DATA_SOURCES.md for the source and attribution notes. These text-dictionary scripts do not train the glyph recogniser.

## Dataset quality workflow

The repository separates three concepts for the text dictionary:

1. **Source data** — machine-readable Yucatec Maya data downloaded from Kaikki / English Wiktionary.
2. **Application dictionary** — cleaned CSV imported to Firestore. Automatically imported rows remain `verified=false`.
3. **Evaluation set** — a manually reviewed subset used to report translation accuracy/coverage. Only rows explicitly marked `verified=true` are scored.

Build the dictionary locally with:

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

A GitHub Actions workflow builds and validates the public Kaikki dataset and publishes the processed CSV as a workflow artifact. It does not establish glyph-recognition accuracy.
