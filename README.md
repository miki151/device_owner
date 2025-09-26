# Device Owner Control

This project provides a sample device owner (device policy controller) application for custom Android builds. It demonstrates how to control access to the Google Play Store while still permitting automatic updates and offers a secure PIN-gated interface for administrators.

## Features

- Provisionable as a **device owner** for full control of device policies.
- Blocks manual launches of the Google Play Store by redirecting market intents to an informational screen.
- Allows background Play Store updates to continue to run so managed apps stay up to date.
- Provides a simple admin UI secured by a PIN:
  - First launch requests the administrator to configure a PIN.
  - Subsequent launches require the PIN before policy changes can be made.
- Administrators can toggle the Play Store restriction, block or allow individual apps, and update the PIN.
- Stores the PIN using salted SHA-256 hashing.

## Project structure

- `app/src/main/java/com/example/deviceowner/admin` – Device admin receiver implementation.
- `app/src/main/java/com/example/deviceowner/policy` – Helper that applies or removes Play Store restrictions via the `DevicePolicyManager` API.
- `app/src/main/java/com/example/deviceowner/storage` – PIN persistence helper.
- `app/src/main/java/com/example/deviceowner/ui` – Activities and fragments for the user experience, including the blocking screen and admin console.

## Building

This repository uses the Android Gradle Plugin. Because the execution environment used to generate this project blocks access to Google's Maven repositories and Gradle distributions, the Gradle wrapper could not be generated automatically. Use a locally installed Gradle distribution that already has access to the required tooling to build the project, for example:

```bash
# From the project root
gradle assembleDebug
```

When running locally for the first time, make sure your Gradle installation can reach `https://maven.google.com` to download the Android Gradle Plugin and related dependencies.

## Provisioning as device owner

1. Build and install the application on a test device or emulator running your custom ROM.
2. Make the app the device owner (this usually requires a factory reset). Example using ADB while the device is in the setup wizard:
   ```bash
   adb shell dpm set-device-owner com.example.deviceowner/.admin.DeviceOwnerReceiver
   ```
3. Launch the **Device Owner Control** app and configure an administrator PIN when prompted.
4. Use the toggle within the app to enable or disable Play Store access. Without the PIN, users cannot change the restriction or open the Play Store.

## Notes

- The Play Store is blocked by registering persistent preferred activities that redirect market intents to the `PlayStoreBlockedActivity`. Removing the restriction clears these overrides.
- Auto updates are unaffected because the Play Store application package is still installed and able to run background services.
- The PIN is stored in shared preferences using salted SHA-256 hashes, preventing retrieval of the original PIN if the device is compromised.
