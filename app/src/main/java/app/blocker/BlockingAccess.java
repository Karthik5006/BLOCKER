package app.blocker;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

/** Checks Usage Access + Overlay permissions and manages the BlockerService component state. */
final class BlockingAccess {
    private static ComponentName component(Context c) {
        return new ComponentName(c, BlockerService.class);
    }

    static boolean disabled(Context c) {
        return c.getPackageManager().getComponentEnabledSetting(component(c)) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    static boolean usageEnabled(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        if (ops == null) return false;
        int mode = Build.VERSION.SDK_INT >= 29
            ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
            : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return c.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static boolean overlayEnabled(Context c) {
        return Settings.canDrawOverlays(c);
    }

    static boolean enabled(Context c) {
        return usageEnabled(c) && overlayEnabled(c);
    }

    static Intent nextPermissionIntent(Context c) {
        if (!usageEnabled(c)) {
            return new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        }
        return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.getPackageName()));
    }

    static void prepare(Context c) {
        if (!State.pending(c) && !State.active(c)) return;
        c.getPackageManager().setComponentEnabledSetting(
            component(c),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        );
    }

    static void disable(Context c) {
        if (State.active(c) || State.pending(c) || disabled(c)) return;
        c.getPackageManager().setComponentEnabledSetting(
            component(c),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        );
    }

    static void awaitRelease(Context c) {
        disable(c);
    }

    static void releaseRuntimePermissions(Context c) {
        // POST_NOTIFICATIONS is the app's only runtime permission. Android
        // applies this revocation asynchronously after the app has left the foreground.
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            try {
                c.revokeSelfPermissionOnKill(Manifest.permission.POST_NOTIFICATIONS);
            } catch (SecurityException ignored) {
                /* Android owns runtime permission revocation. */
            }
        }
    }
}
