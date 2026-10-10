# Firebase Setup

The repository does not contain Firebase secrets. Complete these steps locally before testing real authentication and Firestore.

## 1. Create the Firebase project

Create a Firebase project for the FYP and register an Android application with package name:

`com.chuahws.mayalanguageapp`

The package name must match `applicationId` in `app/build.gradle.kts`.

## 2. Add the local Android configuration

Download `google-services.json` and place it at:

`app/google-services.json`

The file is ignored by Git and must not be committed.

## 3. Enable the Google Services plugin

In `app/build.gradle.kts`, uncomment:

```kotlin
id("com.google.gms.google-services")
```

The root project already declares the plugin version.

## 4. Enable authentication

In Firebase Console, enable Email/Password under Authentication providers.

## 5. Create Cloud Firestore

Create Cloud Firestore and use development rules only for the initial local setup. Replace them with user-scoped and role-based rules before system testing.

Expected collections:

- `users`
- `dictionary`
- `translations`
- `savedVocabulary`
- `analytics`
- `feedback`

## 6. Confirm backend selection

When Firebase is configured correctly, the login screen will no longer show the development-mode message. The application will automatically select:

- `FirebaseAuthRepository`
- `FirestoreUserProfileRepository`
- `FirestoreDictionaryRepository`

Without Firebase configuration, it uses in-memory development repositories so the UI can still be opened and reviewed.

## Security reminder

Never commit:

- `google-services.json`
- Firebase Admin service-account JSON
- keystores or signing passwords
- API secrets
