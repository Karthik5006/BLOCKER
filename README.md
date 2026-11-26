# BLOCKER 5.5.1 final7

BLOCKER is a personal focus timer for Android 8 and later. Choose the apps you want to pause, set a session length, and start the timer. During a session, BLOCKER detects when a selected app is in the foreground and shows a blocking screen. To end a session early, hold the on-screen control for the configured duration.

[Download the 5.5.1 final7 APK](releases/5.5.1/BLOCKER-5.5.1-final7.apk?raw=1)

## Android permissions and banking apps

This version uses Usage Access, Display over other apps, and a foreground service. On a personal phone, setup also requests device administrator access. During a session, the app can intercept system screens for app management and administrator deactivation. The APK declares no accessibility service. Compatibility with HSBC, ICICI, and other banking apps requires testing on the device.

## Build and verification

Use JDK 17 or 21, Android SDK Platform 35, and Build Tools 35.0.0.

```sh
python3 tools/build.py --sdk /path/to/Android/Sdk
./gradlew testDebugUnitTest
```

The build writes `app/build/outputs/apk/personal/BLOCKER.apk`. It uses the local signing key in `signing/personal-debug.keystore` and creates one if needed. Android requires the same signing key for in-place updates.

The rebuild from this source matched the published APK’s compiled code, manifest, resources, and assets. The local rebuild used a different signing key. Of 36 unit tests, 35 passed. In `SimpleStartTest`, the setup flow opened overlay settings while the assertion expected usage access settings.

Published APK SHA-256: `00f55e20e4462f2b61b5e3e603c72dc4faaeb0d53d270f569f4123e0d65c8894`.

## Earlier focus launcher

Version 5.5 pauses selected apps when launched from BLOCKER Home. Its APK uses a different signing key from 5.5.1 final7. Switching between these APKs requires uninstalling the installed version, which clears its local data.

[Download the 5.5 launcher APK](releases/5.5/BLOCKER-5.5-launcher.apk?raw=1) · [Browse the 5.5 launcher source](https://github.com/Karthik5006/BLOCKER/tree/a9abece7e49d90c43a111ce357aec47177a1ff28)
