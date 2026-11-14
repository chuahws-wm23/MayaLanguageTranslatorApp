# Firebase Setup

The source code already contains Firebase repositories. Complete these steps locally to turn on persistent authentication and Firestore data.

## Android app

Register an Android app in Firebase using this package name:

com.chuahws.mayalanguageapp

Download google-services.json and place it in:

app/google-services.json

The file is ignored by Git.

## Gradle plugin

Open app/build.gradle.kts and uncomment:

id("com.google.gms.google-services")

Then sync Gradle.

## Authentication

In Firebase Console:

Authentication → Sign-in method → Email/Password → Enable

## Firestore

Create Cloud Firestore.

The application uses these collections:

- users
- dictionary
- translations
- savedVocabulary
- analytics
- feedback

Copy the repository file firestore.rules into the Firestore Rules editor and publish it.

## Dictionary

After Firebase works, process and import the full Yucatec Maya dictionary using the scripts under dataset/scripts.

Do not upload a Firebase Admin service-account JSON file to GitHub.
