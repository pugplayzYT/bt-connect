# BT Connect

A small Windows WPF Bluetooth server and Android companion app. The computer
advertises a dedicated Bluetooth service; phones find compatible servers and tap
to connect. Both apps show the number of connected phones. The computer and phones
can chat, and the computer can broadcast its microphone to every connected phone.
Phones automatically play the broadcast and show **Broadcasting audio**, while
chat stays visible below it. Stopping audio returns the display to **Chat only**.

## Use

1. Windows 10/11 x64: enable Bluetooth, open `BT-Connect-Windows-x64.exe`, and click
   **Start server**. A Bluetooth Classic adapter with the Microsoft Bluetooth
   stack is required. Allow microphone access for desktop apps in Windows Settings.
2. Install the Android APK on an Android 8+ phone. Grant Nearby devices permission
   (Android 12+) or Location permission (Android 8–11; Location must also be on for
   discovery). Android does not need microphone permission.
3. Tap **Find servers**. The list contains computers advertising the BT Connect
   UUID, not every Bluetooth device. Tap one to connect and accept pairing prompts.
   Pair the phone and computer through OS Bluetooth Settings first if connection
   or service discovery fails.
4. Send a message or click **Broadcast microphone** on the computer. Turn up the
   phone's media volume. A phone joining an active broadcast starts playback too.
5. Stop broadcasting to return to chat, or disconnect. Closing the computer server
   disconnects phones and restores its previous Bluetooth discoverability mode.

The Android client operates while its screen is in the foreground. Leaving the
app disconnects it and releases playback/audio focus; tap a server to reconnect.
This version uses the computer's default microphone (not system audio) and streams
mono 16 kHz PCM speech. Chat history is in memory only and limited to 500 desktop /
200 phone messages. Device counts are connections, not authenticated identities.
Bluetooth bandwidth and adapter limits determine the practical number of phones.

## Build and test

Prerequisites: .NET SDK pinned by `global.json`, JDK 17 or 21, Android SDK platform
35 / build-tools 35.0.0. Gradle 8.11.1 is pinned and checksum verified by the wrapper.

```sh
dotnet test desktop/BtConnect.Core.Tests/BtConnect.Core.Tests.csproj -c Release -p:RestoreLockedMode=true
dotnet build desktop/BtConnect.Server/BtConnect.Server.csproj -c Release -p:RestoreLockedMode=true
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The WPF source can be cross-compiled on Linux; execution requires Windows.
On Windows, build a standalone EXE with its .NET runtime bundled:

```sh
dotnet publish desktop/BtConnect.Server/BtConnect.Server.csproj -c Release -p:RestoreLockedMode=true -o artifacts/windows
```

The EXE may extract bundled native libraries to the user's temporary directory.
The debug APK at `android/app/build/outputs/apk/debug/app-debug.apk` is signed for
development; it is not the production release APK. Windows builds are not
Authenticode signed, so Windows may show a publisher warning.

Cloud setup: run `bash scripts/install-cloud.sh`, then `source scripts/cloud-env.sh`.
This installs tools and caches beneath `/workspace`, respecting the read-only home
directory, and uses the injected proxy for Gradle without disabling TLS checks.
Use the existing checkout; a separate Git worktree is unnecessary.

## Releases

Downloads are under [GitHub Releases](https://github.com/pugplayzYT/bt-connect/releases).
Tags such as `v0.1.0-preview.1` publish a **development preview** with the standalone
Windows EXE/ZIP, a development-signed Android APK, and checksums. Both platforms'
checks must pass before publication. Preview signing keys can change between runs;
uninstall an older development APK if Android rejects the update. Production tags
exclude preview tags and use the persistent signing configuration below.

The GitHub Actions release workflow tests both platforms, builds a self-contained
Windows x64 EXE and ZIP, builds and verifies a signed Android release APK, then
attaches all three and `SHA256SUMS.txt` to one GitHub Release. Publication happens
only when both builds succeed. CI on main / pull requests also runs tests and
uploads development artifacts without publishing a release.

Before pushing the first production version tag, configure these **GitHub repository Actions
secrets** (never commit a keystore or put passwords in chat):

- `ANDROID_KEYSTORE_BASE64`: base64 of your persistent release keystore.
- `ANDROID_KEYSTORE_PASSWORD`: the keystore password.
- `ANDROID_KEY_ALIAS`: the signing key alias.
- `ANDROID_KEY_PASSWORD`: the signing key password.

Create and back up the keystore locally using `keytool -genkeypair` with RSA 3072
or stronger. Keep the same key for future APK updates. Release builds deliberately
fail if these secrets are missing; they never fall back to the debug signing key.
Push a tag such as `v0.1.0` to run the release workflow. `versionName` comes from
the tag and `versionCode` uses the release workflow's increasing run number.
Re-run failed builds on the same tag before a release is published; after publication
use a new version tag for changes. The workflow does not overwrite an existing release.

If Windows reports that it cannot access a Bluetooth Classic adapter, turn Bluetooth
on in Settings > Bluetooth & devices. If the switch is missing, check Device Manager
and the adapter driver; a computer without built-in Bluetooth needs a suitable USB
adapter. Startup errors identify whether radio discovery, discoverability, or the
RFCOMM service failed. A successful build cannot verify hardware availability.

## Validation

Automated tests cover shared wire fixtures, fragmented and concatenated reads,
invalid/truncated payloads, Unicode limits, count changes, multiple receivers,
audio transitions, late joins, cancellation, and client playback state.
They do not substitute for real Bluetooth / microphone / speaker testing.
See [hardware checks](docs/hardware-validation.md) and [wire protocol](docs/protocol.md).
