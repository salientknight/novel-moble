# novel-moble

Native Android companion for [novelWriter](https://novelwriter.io/), built with Kotlin and Jetpack Compose.

## Status
Initial Android scaffold. **Project import, editing, and saving are not implemented yet.** Do not use this build with your manuscript.

## Build
Install Android Studio (current stable), Android SDK Platform 35, and JDK 17. Open this repository as a Gradle project. Android Studio can install missing Gradle components. Use an Android emulator or physical device to run the `app` configuration.

If the Gradle wrapper is absent, run `gradle wrapper --gradle-version 8.9` using a local Gradle installation, or let Android Studio import the Gradle project. The wrapper executable and JAR should be committed after generating them.

## Roadmap
1. Validate novelWriter's current .nwx/.nwd format against upstream fixtures.
2. Read-only project browsing via Android Storage Access Framework.
3. Safe editing, atomic saves where supported, backup and conflict detection.
4. Sync interoperability and device testing.

No storage, network, or microphone permissions are requested by this scaffold.
