# BLOCKER 5.4 icon verification

Reviewed on 10 November 2026 (Asia/Kolkata).

- Compiled the complete app against Android SDK 35, using the host JDK 21 compiler with Java 17 source/target compatibility. This host lacks `ct.sym`, so the build used equivalent source/target flags in place of the build script’s `--release 17` option.
- The APK is `app.blocker`, version code 9, version name 5.4. Android v2/v3 signatures and ZIP alignment were checked, with the original signing key preserved.
- Adaptive icons are provided for API 26+; API 33+ resources add the dedicated monochrome layer. The manifest references the adaptive normal and round launcher icons.
- The 108 dp vector foreground stays inside the central 66 dp safe circle. The logo’s bounds exceed 48 dp in both dimensions. Backgrounds are full bleed, with no baked-in mask or shadow.
- Rendered the actual vector paths with circle, rounded-square, square and squircle masks, in color and a sample monochrome tint. Small 24/32/48/64 px versions and a 512 px preview were visually inspected. These are vector renders, not screenshots from a physical Android launcher.
- Java source, existing tests, WebView assets and permission declarations match 5.3. No test cases or runtime dependencies were added. The prior 41 native/111 scene checks are recorded below and were not repeated for this icon-only change.
- The new icon was not reviewed on a physical phone. Launcher caching or themed-icon support can vary by launcher.

---

# BLOCKER 5.3 verification

Reviewed on 10 November 2026 (Asia/Kolkata). These are automated, simulated Android and browser-rendering checks, not a physical-phone certification.

## Native checks — 41 passed

`testDebugUnitTest` passed all 41 existing JUnit/Robolectric checks with Android API 35 and JDK 21. No new test cases are included in the delivered project; its existing Java test files are unchanged from 5.2.

- 7 timing tests: deadlines, lift reset, 3/5/10 minute holds, backwards time, unsupported durations, duration changes and formatting.
- 13 session/guard tests: permission prerequisites, saved choices, fixed per-session hold, expiry, history, managed-policy readback, failed release, preserving external restrictions, personal-admin cleanup, owner preservation and reboot recovery.
- 5 motion-policy tests: foreground, master preference, Battery Saver, animator scale zero and screen-off gates.
- 4 touch tests: moving outside, lifting, focus loss and multiple fingers. The fixture supplies the WebView frame and invokes the production touch handler; each cancellation starts from a confirmed hold.
- 9 guard-routing/service tests: Settings, installers, stores, essential controls, selected apps, PermissionController handling, manufacturer intent resolution, return from setup, repeated attempts and expiry before interception.
- 2 start/button tests: native permission flow without an extra setup confirmation; no button elevation, translation Z or state animator while pressed/selected.
- 1 sheet lifecycle test: dismissing an old sheet cannot unfreeze the scene behind a newer sheet.

## Exit walkthrough

A temporary review driver exercised the production activity and service through the existing test runner. It is excluded from the source archive and APK. The walkthrough confirmed:

- Inactive Exit finishes with no dialog or second button, leaves the service component disabled and preserves saved selections.
- Notifications/reminders are quiet after Exit; pending setup and its personal admin guard are cancelled.
- Active Exit keeps the session and guard active and opens one wait-or-hold guidance prompt.
- A completed hold releases access and closes without another prompt. Requested Exit also closes on timer expiry.
- When Android has not yet bound an enabled service, Exit waits quietly, lets the late connection call `disableSelf()`, and preserves another accessibility service’s grant.
- If teardown still has not arrived after three seconds, the app closes without trapping the user or falsely reporting that the outstanding grant has cleared. Inactive state cannot intercept apps.

The active-session prompt was rendered using Robolectric native graphics at 393 dp and inspected for spacing, rounded corners, text wrapping and a flat button. The broader duration/app-picker/plan/check-in native panels retain the 5.2 implementation, which had already been reviewed at 320 dp, 393 dp and enlarged text. Android-owned permission, keyboard and uninstall screens were not restyled.

Accessibility and device-admin responses use test doubles. Actual OS teardown timing and Android 13+ asynchronous notification-permission self-revocation require a device check; the walkthrough does not simulate permission revocation by claiming the real OS has performed it.

## Scene checks — 111 passed

`tools/verify_scene.cjs` rendered the bundled SVG, CSS, font and JavaScript in Chromium at 320, 393 and 640 CSS pixels. No JavaScript errors or horizontal overflow occurred; the maximum measured animated layers/elements was **8**. The existing active-header assertion now expects Exit to route to native session guidance. A compositor-frame settle before sampling a frozen transform avoids comparing a pre-pause frame with a committed pause frame.

Coverage includes Home entrance, timer/ring bounds, native-only unlock authority, hold progression, 400 ms ring drain, 1.5 s scene recovery, master freeze, reduced motion, interception timing, sunrise, seven history trees, check-in semantics, pause ending, lantern setup, jar and landscape preview.

A separate visual review rendered 40 ring states at device-pixel ratios 1, 2, 2.75 and 3: empty, tiny starting progress, quarter/half/later progress and early release. Thirty-two top/bottom seam regions at empty/early progress contained no unwanted bright pixels. The empty, early and quarter-progress renders and the active-session prompt were visually inspected. This review driver is not shipped.

## APK checks

- Release compiled against Android SDK 35; package `app.blocker`, version code `8`, version name `5.3`.
- Android v2/v3 signatures and 4-byte ZIP alignment verified.
- The supplied personal signing key is byte-for-byte unchanged from the input source. Signer certificate SHA-256: `71b3f3c4034b7565a639bce10682b37cba7ceb081774adea8f49dee2032a15cf`.
- Production files used for the native checks match the delivered source; the APK’s landscape assets match the reviewed working assets.
- No Internet, overlay, usage-access or wake-lock permission is declared. Accessibility does not request screen-content retrieval or gesture injection.

## Device acceptance still needed

Physical uninterrupted 3/5/10 minute holds, haptics, manufacturer accessibility/admin behavior, native permission delivery, app interception, uninstall routes, bank acceptance and battery consumption have not been tested on a phone.

On a phone, try Exit before starting: it should close without a new app-owned prompt. Start a one-minute session with an expendable blocked app; Exit must leave the session active and show wait-or-hold guidance. Wait for expiry and confirm closure. In a longer session, check early lift/second-finger reset, then finish a three-minute hold and confirm closure. After Exit, check that BLOCKER accessibility and personal admin are off before trying a bank app. Test the ring and animation-off setting at the phone’s display scale. During an active session, ordinary app-info, Settings, store and uninstall paths should return to the timer; emergency/phone controls must stay usable. After expiry or a full hold, those guarded paths must work again.

Android controls release timing and each bank controls its own security checks. The app cannot guarantee universal banking acceptance, removal of other apps’ permissions or external device-owner authority, an unbreakable uninstall ban, or zero battery drain.
