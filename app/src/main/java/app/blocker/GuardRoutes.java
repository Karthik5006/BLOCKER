package app.blocker;

import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;
import java.util.*;

/**
 * Cached window routing, active only during the user's timed session.
 * Allows main Android Settings and normal settings navigation (Wi-Fi, Bluetooth, Display, Sound,
 * Battery, etc.) while blocking App Info, Manage Applications, Usage Access, Overlay Permission,
 * Device Admin, App Stores, and Package Installer / Uninstaller screens.
 */
final class GuardRoutes {
    private final Set<String> essential;
    private final Set<String> guardedPackages = new HashSet<>(Arrays.asList(
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.miui.packageinstaller", "com.miui.global.packageinstaller", "com.oplus.packageinstaller",
        "com.oneplus.packageinstaller", "com.samsung.android.packageinstaller",
        "com.miui.securitycenter", "com.coloros.safecenter", "com.oplus.safecenter",
        "com.vivo.permissionmanager", "com.iqoo.secure", "com.huawei.systemmanager",
        "com.android.vending", "com.sec.android.app.samsungapps", "com.xiaomi.mipicks",
        "com.heytap.market", "com.oppo.market", "com.vivo.appstore", "com.bbk.appstore",
        "com.huawei.appmarket", "com.amazon.venezia"));
    private final Set<ComponentName> guardedActivities = new HashSet<>();

    GuardRoutes(Context c) {
        essential = new HashSet<>(AppCatalog.essential(c));
        PackageManager pm = c.getPackageManager();
        Uri ownPackage = Uri.parse("package:" + c.getPackageName());

        // Guard specific Settings sub-activities that could disable permissions, force-stop, or uninstall BLOCKER,
        // without blocking the main Settings app or general SubSettings (Wi-Fi, Bluetooth, Display, Sound, etc.).
        for (String action : new String[]{
                Settings.ACTION_USAGE_ACCESS_SETTINGS,
                Settings.ACTION_APPLICATION_SETTINGS,
                Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
                Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS,
                DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN}) {
            collectActivityOnly(pm, new Intent(action));
        }
        collectActivityOnly(pm, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, ownPackage));
        collectActivityOnly(pm, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ownPackage));

        // Guard uninstaller and store packages/activities.
        collectPackageAndActivity(pm, new Intent(Intent.ACTION_DELETE, ownPackage));
        collectPackageAndActivity(pm, new Intent(Intent.ACTION_UNINSTALL_PACKAGE, ownPackage));
        collectPackageAndActivity(pm, new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + c.getPackageName())));

        essential.removeAll(guardedPackages);
    }

    private void collectActivityOnly(PackageManager pm, Intent intent) {
        for (ResolveInfo r : pm.queryIntentActivities(intent, 0)) {
            if (r.activityInfo == null || r.activityInfo.name == null) continue;
            // Never add the top-level Settings homepage or generic SubSettings wrapper here.
            String lower = r.activityInfo.name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".settings")
                    || lower.endsWith(".settingshomepageactivity")
                    || lower.endsWith(".subsettings")) {
                continue;
            }
            guardedActivities.add(new ComponentName(r.activityInfo.packageName, r.activityInfo.name));
        }
    }

    private void collectPackageAndActivity(PackageManager pm, Intent intent) {
        for (ResolveInfo r : pm.queryIntentActivities(intent, 0)) {
            if (r.activityInfo == null) continue;
            if (!"com.android.settings".equals(r.activityInfo.packageName)
                    && !"com.android.permissioncontroller".equals(r.activityInfo.packageName)
                    && !"com.google.android.permissioncontroller".equals(r.activityInfo.packageName)) {
                guardedPackages.add(r.activityInfo.packageName);
            }
            if (r.activityInfo.name != null) {
                guardedActivities.add(new ComponentName(r.activityInfo.packageName, r.activityInfo.name));
            }
        }
    }

    private boolean isProtectedActivity(String pkg, String className) {
        if (className == null) return false;
        String normalized = className.startsWith(".") ? pkg + className : className;
        if (guardedActivities.contains(new ComponentName(pkg, normalized))) return true;
        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.contains("uninstall")
            || lower.contains("deletepackage")
            || lower.contains("deviceadmin")
            || lower.contains("installedappdetails")
            || lower.contains("applicationdetails")
            || lower.contains("appinfo")
            || lower.contains("manageapplications")
            || lower.contains("appmanagement")
            || lower.contains("spaactivity")
            || lower.contains("spawrapper")
            || lower.contains("usageaccess")
            || lower.contains("drawoverlay")
            || lower.contains("overlaysettings")
            || lower.contains("specialaccess");
    }

    boolean shouldBlock(boolean active, String pkg, String className, Set<String> selected) {
        if (!active || pkg == null) return false;
        if (isProtectedActivity(pkg, className)) {
            return true;
        }
        if (guardedPackages.contains(pkg)) {
            return true;
        }
        if (essential.contains(pkg)) {
            return false;
        }
        return selected.contains(pkg);
    }
}
