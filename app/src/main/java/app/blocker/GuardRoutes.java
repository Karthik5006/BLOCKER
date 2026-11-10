package app.blocker;

import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;
import java.util.*;

/** Cached window routing, active only during the user's timed session. No screen-content access. */
final class GuardRoutes {
    private final Set<String> essential;
    private final Set<String> guarded = new HashSet<>(Arrays.asList(
        "com.android.settings", "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.miui.packageinstaller", "com.oplus.packageinstaller", "com.samsung.android.packageinstaller",
        "com.miui.securitycenter", "com.coloros.safecenter", "com.oplus.safecenter",
        "com.vivo.permissionmanager", "com.iqoo.secure", "com.huawei.systemmanager",
        "com.android.vending", "com.sec.android.app.samsungapps", "com.xiaomi.mipicks",
        "com.heytap.market", "com.oppo.market", "com.vivo.appstore", "com.bbk.appstore",
        "com.huawei.appmarket", "com.amazon.venezia"));
    private final Set<ComponentName> uninstallActivities = new HashSet<>();

    GuardRoutes(Context c) {
        essential = AppCatalog.essential(c);
        PackageManager pm = c.getPackageManager();
        Uri ownPackage = Uri.parse("package:" + c.getPackageName());
        for (String action : new String[]{Settings.ACTION_SETTINGS, Settings.ACTION_SECURITY_SETTINGS,
                Settings.ACTION_ACCESSIBILITY_SETTINGS, DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN})
            collect(pm, new Intent(action), false);
        collect(pm, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ownPackage), false);
        collect(pm, new Intent(Intent.ACTION_DELETE, ownPackage), true);
        collect(pm, new Intent(Intent.ACTION_UNINSTALL_PACKAGE, ownPackage), true);
        collect(pm, new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + c.getPackageName())), false);
    }

    private void collect(PackageManager pm, Intent intent, boolean uninstall) {
        for (ResolveInfo r : pm.queryIntentActivities(intent, 0)) {
            if(r.activityInfo == null) continue;
            guarded.add(r.activityInfo.packageName);
            if(uninstall) uninstallActivities.add(new ComponentName(r.activityInfo.packageName, r.activityInfo.name));
        }
    }

    boolean shouldBlock(boolean active, String pkg, String className, Set<String> selected) {
        if(!active || pkg == null) return false;
        if(essential.contains(pkg)) {
            // Some Android versions host uninstall screens in PermissionController. Its ordinary
            // permission prompts must remain available, as must Home, calls, keyboard and System UI.
            if(!pkg.equals("com.android.permissioncontroller") && !pkg.equals("com.google.android.permissioncontroller")) return false;
            if(className == null) return false;
            String normalized = className.startsWith(".") ? pkg + className : className;
            return uninstallActivities.contains(new ComponentName(pkg, normalized))
                || normalized.toLowerCase(Locale.ROOT).contains("uninstall");
        }
        return guarded.contains(pkg) || selected.contains(pkg);
    }
}
