# BLOCKER for Android

The 5.5 source in this repository builds a focus launcher for Android 8 and later. Choose distractions, a session length and a continuous hold duration. Choose BLOCKER when Android asks for your Home app. While a session is active, opening a selected app from BLOCKER Home takes you to the timer. Other apps open normally. Notifications, links and Recents remain available. You can switch Home apps or uninstall BLOCKER in Android Settings.

The launcher uses your personal profile’s launchable activities. It runs without accessibility, overlay, usage access, device administration or a foreground monitoring service. Notifications are optional. Sessions, reminders and history are stored locally. When updating from the earlier blocker, choose your distractions again so a previous Select all choice does not unexpectedly include banking apps.

## Download an APK

| APK | What it does |
| --- | --- |
| [5.5 focus launcher](releases/5.5/BLOCKER-5.5-launcher.apk?raw=1) | Built from the launcher implementation in this repository. Select BLOCKER as your Home app to use it. |
| [5.5.1 final7](releases/5.5.1/BLOCKER-5.5.1-final7.apk?raw=1) | Separately supplied APK. Its manifest declares usage access, display over other apps, a foreground service and a device-admin receiver. |

Both APKs use the package name `app.blocker` and have different signing certificates. Android requires a fresh install to switch between them. Uninstalling clears local sessions, plans and history.

## Test a focus session

1. Open the 5.5 launcher, pick a distraction and start a two-minute session. Choose BLOCKER as the Home app when Android asks.
2. Press Home and tap the selected distraction to see the timer. Open an unselected app from Home to use it during the session.
3. After the timer expires, open the distraction from Home again. To switch launchers, go to **Settings → Apps → Default apps → Home app**.

## Build a personal test APK

Use JDK 17 or 21, Android SDK Platform 35 and Build Tools 35.0.0.

```sh
python3 tools/build.py --sdk /path/to/Android/Sdk
```

The APK is written to `app/build/outputs/apk/personal/BLOCKER.apk`. The build uses the local, Git-ignored `signing/personal-debug.keystore` and creates one if needed. An in-place update requires the same signing key. Files under `releases/5.4/` are older builds.
