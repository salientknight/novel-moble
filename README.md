# Novel Mobile

An experimental, community-oriented Android companion for [novelWriter](https://novelwriter.io/), with optional USB synchronization for Linux.

> **Early development / use at your own risk.** Back up your novelWriter projects before trying this software. The editor and sync tooling are not yet production-hardened. This is an independent project, not an official novelWriter release.

## Why this exists

This project started with a straightforward problem: I was writing a novel on Linux with novelWriter, and I wanted to keep writing when I was away from my desk—especially from my Android phone.

novelWriter offered a useful way to organize a manuscript, but it didn't give me a native Android writing experience. I didn't want to move chapters manually between apps, depend on a cloud service, or risk losing writing because I forgot to synchronize.

So Novel Mobile began as a small experiment: read a novelWriter project on Android, dictate into a scene, save it in its original format, and carry those changes back to Linux. That experiment became a native Android app and a small USB synchronization launcher.

The goal isn't to replace novelWriter or build another giant writing suite. It's to remove friction between the places where writing happens.

## What works today

- Native Android app built with Kotlin and Jetpack Compose
- Open a local novelWriter project folder using Android's document picker
- Browse its project tree and edit document files
- Dictate using the Android keyboard's voice input
- Save changes, with a local recovery copy before replacement
- Simple prose mode that hides metadata and the structural heading for supported scenes
- Fallback to source editing for documents containing markup the prose editor cannot safely handle
- Optional Linux ADB sync script with a last-sync baseline, conflict detection, and backup copies
- A Linux launcher that checks for a connected, authorized phone, syncs before opening novelWriter, and syncs again after it closes
- A script to install a clickable desktop entry on Linux

## What it does *not* do yet

- Full WYSIWYG editing of novelWriter formatting
- Reliable automatic synchronization merely upon plugging in a phone
- Cloud synchronization
- Automatic project discovery/registration on the Linux side
- Complete synchronization of every project asset, attachment, and metadata file
- Guaranteed atomic saves or rollback of interrupted transfers
- Proven conflict-free operation when the same project is open for editing on both devices

Do not edit the same scene on both devices before synchronizing. Save and close active editors before syncing. Keep independent, versioned backups.

## Android build

Requirements: Android SDK Platform 35, JDK 17, and an Android development environment with Gradle support. This repository uses Android Gradle Plugin 8.13.2 and the Gradle 8.13 wrapper.

From the repository root:

\`\`\`bash
./gradlew assembleDebug
\`\`\`

To install the debug APK on a USB-connected, authorized Android phone:

\`\`\`bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
\`\`\`

On the phone, place an **extracted copy** of a novelWriter project under Documents, open Novel Mobile, and select the folder containing \`nwProject.nwx\`.

## Linux USB synchronization

Requirements: Python 3, Android SDK Platform Tools (\`adb\`), a USB-authorized Android device, and an existing novelWriter project on both devices.

**Important:** The synchronization script tracks a baseline. For an existing phone project with edits, initialization imports the phone version of differing files and backs up both copies. Inspect your project and keep a separate backup before initializing.

Example using a project named \`Example Novel\`:

\`\`\`bash
python3 tools/sync_usb.py "$HOME/Example Novel" --initialize-from-phone
python3 tools/novelwriter_launcher.py add "$HOME/Example Novel"
python3 tools/install_desktop_launcher.py
\`\`\`

After that, launch **novelWriter (Auto Sync)** from the Linux application launcher. It attempts to sync registered projects when the phone is connected and authorized, opens novelWriter, then syncs again after novelWriter exits.

You can also run a manual sync:

\`\`\`bash
python3 tools/sync_usb.py "$HOME/Example Novel"
\`\`\`

If no authorized device is connected, the launcher can start novelWriter without syncing. A failed sync with a connected device blocks startup. The desktop entry currently opens a terminal so errors are visible.

## Project format

The implementation currently targets novelWriter projects with an \`nwProject.nwx\` XML index and \`content/*.md\` documents. The Android app is not a general-purpose Markdown-to-rich-text converter. Files with unsupported formatting are shown in source mode rather than silently transforming markup.

## Contributing and project status

Bug reports, compatibility tests, accessibility feedback, and pull requests are welcome once the repository is public. Particularly useful contributions include round-trip formatting tests, interrupted-transfer recovery, and Android accessibility improvements.

This is a small independent project built to solve a practical writing problem. Please treat the synchronization tools as experimental until they have broader testing.

## AI-Assisted Development

Novel Mobile was developed through collaboration between a human software developer and OpenAI's ChatGPT.

The project's goals, architecture, requirements, and development direction were guided by the developer, while AI was used extensively to generate code, troubleshoot issues, and accelerate implementation.

This project demonstrates how AI-assisted development can turn a practical idea into working software with relatively little manual coding.

As with any software, AI-generated code may contain errors. Contributions, code reviews, testing, and improvements from the open-source community are welcome.

## License and Disclaimer

**Novel Mobile is free and open-source software, released under the MIT License.**

This project was created to make writing more accessible and convenient. It is shared freely with the hope that others will find it useful, improve upon it, and contribute to its continued development.

You are welcome to use, modify, fork, and redistribute Novel Mobile under the terms of the [MIT License](LICENSE). If you build upon this project, please preserve the original copyright and license notices as required by that license.

**Use at your own risk.** Novel Mobile is provided "as is," without warranties or guarantees of any kind. Although protecting your writing is a development priority, software errors and synchronization failures are possible. Always maintain independent backups of your manuscripts.

Contributions, improvements, and bug reports are welcome.
