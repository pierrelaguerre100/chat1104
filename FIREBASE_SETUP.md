# Firebase setup

The Android project already includes the Firebase Authentication, Realtime Database, and Cloud Storage SDKs. Its Android application ID is `com.example.chat1104`.

## Connect the Android app

1. In the Firebase console, open the `chatapp-pierre` project.
2. In **Project settings**, add or select the Android app with package name `com.example.chat1104`.
3. Download the current `google-services.json` file.
4. Replace `app/google-services.json` with the downloaded file.
5. Sync the Gradle project and rebuild the app.

Do not manually add a database URL to `google-services.json`. After Realtime Database is created, download a new file from Firebase instead.

## Enable the services used by the app

### Authentication

1. Open **Build** > **Authentication** > **Sign-in method**.
2. Enable **Email/Password**.
3. Save the setting.

Without this, the Create account button fails with a Firebase `operation-not-allowed` error.

### Realtime Database

1. Open **Build** > **Realtime Database**.
2. Create the database in your preferred location.
3. For development, choose test mode only temporarily. Before release, restrict rules so users can access only their own `/users/<uid>` record.
4. Download and replace `app/google-services.json` again after creation.

The app saves profiles below `/users/<Firebase Authentication UID>`.

### Cloud Storage

1. Open **Build** > **Storage**.
2. Create the default bucket.
3. Configure rules to allow an authenticated user to write only their own file under `/profile_images/<uid>.jpg`.

Cloud Storage is optional for registration: if it is not enabled, the account is still created and the photo is skipped.

## Verify

Run the app, create an account with a valid email address and a password of at least six characters, then check **Authentication** > **Users** in Firebase Console. A successful profile also appears in Realtime Database under `/users`.
