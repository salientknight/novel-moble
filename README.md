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

## USB synchronization launcher (Linux)

The launcher uses ADB on demand and exits after novelWriter closes. It never runs a background service.

Register each project **once**, after its phone copy has been transferred and its sync baseline initialized:

```bash
python3 tools/novelwriter_launcher.py add "/home/dlefebvre/Nexus Story"
python3 tools/novelwriter_launcher.py list
```

Launch novelWriter through the wrapper:

```bash
python3 tools/novelwriter_launcher.py run
```

The wrapper checks for exactly one authorized Android device, synchronizes registered projects before opening novelWriter, waits for novelWriter to exit, and synchronizes again. When no phone is connected, novelWriter opens normally and no synchronization is attempted. If a connected device produces a sync error, the launcher refuses to start novelWriter, avoiding accidental editing of stale files. On a post-exit error, both devices retain their copies for recovery.

The launcher **does not automatically replace your desktop launcher**. Use this command (or configure a desktop shortcut pointing to it) to get automatic synchronization. Do not run another novelWriter instance concurrently, and close Novel Mobile's editor before synchronizing. The project must have a valid sync baseline; do not initialize synchronization blindly on a valuable manuscript.

For projects whose names contain spaces, the ADB sync helper quotes shell paths correctly. Keep USB debugging enabled and authorized on your Pixel. No cloud is required.

### Current limitations

- This is an early implementation and has not been end-to-end tested with the launcher yet.
- Only the project XML, ToC.txt, and content Markdown files are synchronized; other project metadata and attachments are not synchronized.
- Project registration is explicit, not automatic discovery.
- Android changes not saved in Novel Mobile are not synchronized.
- The desktop app's single-instance behavior may affect whether the launcher can wait for its exit; close other novelWriter instances first.
- The existing sync script does not guarantee transactional rollback if an ADB transfer fails partway through. Maintain independent backups.
