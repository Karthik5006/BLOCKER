# BLOCKER 5.4

<img src="artwork/blocker-icon.png" width="96" height="96" alt="BLOCKER mountain and sun icon">

[Download the APK](releases/5.4/BLOCKER.apk?raw=1) · [Download the install ZIP](releases/5.4/BLOCKER-5.4-install.zip?raw=1)

A personal Android app for creating a little space from distracting apps. This update keeps package `app.blocker`, the supplied signing identity, and existing local preferences. Android 8 or later is required.

## What changed in 5.4

A new launcher icon matches the quiet landscape: rounded ivory mountains and a warm sun on deep pine green. Separate foreground and background vectors support circular, rounded-square, squircle and other launcher masks. Android 13+ gets a dedicated monochrome layer for themed icons. The icon is static, adds no runtime library or animation, and keeps the small leaf notification icon.

The 512-pixel artwork and editable SVG are in `artwork/`. The APK retains the existing signing identity and package so it can update 5.3 in place. Session, hold, Exit and permission logic are unchanged.

## What changed in 5.3

The hold ring no longer shows tiny bright seams at the top or bottom when progress is empty or just starting. Its two arcs stay hidden until needed; clipping removes the unwanted edge without adding an animation loop. Browser tap and selection decorations are disabled on the hold circle.

**Exit** now closes without a second confirmation or status screen when no session is active. It stops blocking, releases BLOCKER’s own accessibility access and personal-phone admin guard, cancels notifications and reminder alarms, and requests revocation of its notification permission on Android 13 or later. Saved app choices, plan and history remain. During a session, Exit shows a single wait-or-hold message and leaves the session and guard active. Completing the continuous hold closes the app automatically. If you requested Exit and wait instead, the app closes when the timer ends.

The flat native buttons, bounded press feedback, rounded inputs and responsive pop-ups from 5.2 are retained. System-owned Android permission and uninstall prompts retain Android’s UI.

## Use

1. On Focus, choose a session length in minutes (1–1,439) and an early-end hold of **3, 5, or 10 minutes**. The hold duration is fixed for that session.
2. Choose installed apps. Select all includes eligible installed and system apps. Choices and the system-app visibility setting persist. Discovery uses installed launchable activities, so a browser appears even if you have never opened it. The catalogue refreshes on returning to BLOCKER and on package installation, removal, or replacement.
3. Tap **Start blocking** and grant Android’s device-admin and accessibility permissions. There is no extra guard-mode or confirmation sheet. The session timer begins only after the applicable guard is confirmed and accessibility connects.
4. The countdown is the main display. Keep one finger inside the circle for the entire chosen hold to finish early. Lifting, moving outside, adding a finger, obscuring the touch, losing focus, or leaving the app resets progress. There is no tap or JavaScript shortcut to unlock.
5. At expiry or completed hold, BLOCKER stops redirecting apps, calls `disableSelf()` on its accessibility service and releases its guard. Ordinary timer expiry shows sunrise; a completed hold, or expiry after requesting Exit, closes the app. A completed session gets its own history tick; it does **not** automatically record a porn-free day.

Going Home or switching apps during an active session leaves blocking active. Exit can finish after expiry or the full hold. Android Settings and shared App info pages remain available during a session, including after Select all. Emergency/phone controls, launchers, the keyboard, System UI and ordinary permission prompts stay available. This app is a voluntary barrier, not an unbreakable device lockdown.

## Banking and uninstalling

**Exit** releases BLOCKER’s own access without sending you to Settings or showing another app-owned prompt. It checks Android’s accessibility grant and guard state while allowing up to three seconds for teardown, then removes its task. The service component is disabled once the grant clears and enabled again only for setup. If Android has not yet connected an already-granted service, BLOCKER leaves it available for the delayed callback to call `disableSelf()` immediately; inactive state never redirects apps. This avoids trapping you on a status screen or claiming that an uncleared grant is already off.

Android controls accessibility/admin teardown and runtime-permission revocation. Notification permission revocation is asynchronous after the app leaves the foreground; this app cannot silently revoke ordinary install-time permissions, other apps’ permissions or external device-owner authority. It declares no Internet, overlay, usage-access or wake-lock permission. Simply hiding an active session does not disable it. Banking compatibility still depends on Android completing release, other accessibility services and each bank’s own checks; it cannot be guaranteed.

When no session is active, **Focus → Session setup → Uninstall BLOCKER** releases access and the personal-phone guard before opening Android’s uninstall confirmation. Uninstalling removes the local plan and history.

On a personal phone, device admin makes Android require deactivation before uninstalling. BLOCKER receives a targeted callback when you request deactivation of its own administrator. Shared Android App info screens remain accessible for all apps. Normal permissions cannot guarantee an unbreakable timed uninstall ban. On managed devices, uninstall restrictions require device/profile-owner authority or a delegated uninstall-control scope. BLOCKER verifies a restriction before starting and verifies a clear policy before declaring its own policy released. A pre-existing administrator restriction is not removed. Device/profile owners are never removed as ordinary admins. Installing this APK does not provision device management.

## Natural landscape and battery controls

Home is quiet dusk, setup has a lantern, sessions begin at pine-green night, holding cross-fades the valley toward dawn, and completion is sunrise. The tree grows with the same finger used for the hold. Quarter-progress haptics follow system haptic preferences.

Open **My plan → Edit plan → Animated landscape** to freeze motion immediately. The preview obeys the same setting. Ambient motion also freezes in Battery Saver, at animator scale zero, with system reduced motion, when the screen is off, and while BLOCKER is in the background. Native buttons and time chips have zero elevation in both resting and pressed states; selected chips use a flat moss fill and a fine outline. Native sheets use XML window animations; the scene is an offline WebView with CSS transform/opacity animations. Backgrounding pauses the WebView’s JavaScript timers too.

The UI countdown updates once a second only while a session or pause needs it. Hold feedback runs at 10 Hz only during a finger press. The accessibility service schedules one deadline callback instead of a half-second watchdog. Guard routes and selected apps are cached; package-change broadcasts refresh the routes without polling. Rapid blocked-window events always return Home, while duplicate timer launches are throttled. There is no continuous polling service, animation frame loop in JavaScript, network access, overlay permission, or partial wake lock. The screen stays awake only during a hold. Session expiry and optional daily reminders use alarms; Android may delay alarm delivery during sleep, so the deadline is also checked before redirecting an app.

Actual battery use depends on the phone, WebView and screen brightness. The implementation reduces work; it does not claim zero drain or measured power savings. Disable animations for the least visual work.

## My plan and history

An optional five-tap firefly jar is available on Home between sessions; a new session resets the jar. The ten-minute pause has a growing pine sprig and a plain text ending. Check-ins use native leaf chips. Weekly history shows seven trees: full trees for explicit porn-free entries, bare branches for a slip, muted branches for unlogged days. Delete confirmation is plain. Reminders are opt-in and notifications have a static leaf icon. Exit pauses reminders and cancels their alarms; explicitly saving My plan resumes the saved reminder preference. Reopening the app alone does not restart reminders.

The plan, selections, preferences and history use private local SharedPreferences. Android backup is disabled. The accessibility service receives app-window events and cannot retrieve screen content; it does not request passwords, screen text or gesture injection. The bundled Outfit font’s SIL Open Font License is included.

## Build and verify

Use JDK 17 or 21, Android SDK platform 35 and build tools 35.0.0. Java source and bytecode target 17.

```sh
python3 tools/build.py --sdk /path/to/Android/Sdk
# APK: app/build/outputs/apk/personal/BLOCKER.apk

./gradlew testDebugUnitTest assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

The signing key is excluded from Git. To update the existing APK in place, keep the original `signing/personal-debug.keystore` locally. A newly generated key cannot update an APK signed by the old key. Use a separate private release key for public distribution. This package is a personal build; public store distribution requires a separate review of accessibility policy and disclosures.

For scene checks, install Playwright and its Chromium browser, then run `node tools/verify_scene.cjs`. Optionally set `BROWSER_PATH` to a Chromium executable and `QA_SCREENSHOTS` to an output directory. See `VERIFICATION.md` and `docs/MOTION.md` for the validation scope and exact animation map.

Android references: [device admin](https://developer.android.com/work/device-admin), [uninstall policy](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setUninstallBlocked(android.content.ComponentName,java.lang.String,boolean)), [disableSelf](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#disableSelf()), [WebView lifecycle](https://developer.android.com/reference/android/webkit/WebView#pauseTimers()), [self-revocation of runtime permissions](https://developer.android.com/training/permissions/requesting#remove-access).
