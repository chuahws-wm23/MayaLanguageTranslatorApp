# MayaLanguageTranslatorApp

Android Final Year Project application for Yucatec Maya ↔ English translation.

## Application

The fyp-development branch contains the complete application flow:

- account registration, login, logout and password reset
- typed Yucatec Maya ↔ English translation
- camera and gallery image input
- Google ML Kit Latin-script OCR
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

## Dataset

The full dictionary is prepared through the dataset scripts:

- dataset/scripts/convert_kaikki.py
- dataset/scripts/validate_dictionary.py
- dataset/scripts/import_firestore.py

See docs/DATA_SOURCES.md for the source and attribution notes.
