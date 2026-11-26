package app.blocker;

import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;
import java.util.*;

/**
 * Foreground routing active only during a timed session.
 * Keeps all unselected apps (including banking apps), Quick Settings, and the top-level Settings
 * homepage open, while blocking selected apps, package installers, Samsung Settings Search,
 * and Settings sub-screens (SubSettings/App Info/Device Admin) where uninstall/force-stop live.
 */
final class GuardRoutes {
    private final Set<String> essential;
    private final Set<String> guardedPackages = new HashSet<>(Arrays.asList(
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.miui.packageinstaller", "com.miui.global.packageinstaller",
        "com.oplus.packageinstaller", "com.oneplus.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.securitycenter", "com.coloros.safecenter", "com.oplus.safecenter",
        "com.vivo.permissionmanager", "com.iqoo.secure", "com.huawei.systemmanager"
    ));
    private final Set<ComponentName> guardedActivities = new HashSet<>();

    GuardRoutes(Context c) {
        this.essential = new HashSet<>(AppCatalog.essential(c));
        PackageManager pm = c.getPackageManager();
        Uri ownPackage = Uri.parse("package:" + c.getPackageName());

        for (String action : new String[]{
                Settings.ACTION_USAGE_ACCESS_SETTINGS,
                Settings.ACTION_APPLICATION_SETTINGS,
                Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
                Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS,
                DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN}) {
            collect(pm, new Intent(action), false);
        }
        collect(pm, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, ownPackage), false);
        collect(pm, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ownPackage), false);
        collect(pm, new Intent(Intent.ACTION_DELETE, ownPackage), true);
        collect(pm, new Intent(Intent.ACTION_UNINSTALL_PACKAGE, ownPackage), true);

        essential.removeAll(guardedPackages);
    }

    private void collect(PackageManager pm, Intent intent, boolean isInstaller) {
        for (ResolveInfo r : pm.queryIntentActivities(intent, 0)) {
            if (r.activityInfo == null) continue;
            String pkg = r.activityInfo.packageName;
            String cls = r.activityInfo.name;
            if (isInstaller
                    && !"com.android.settings".equals(pkg)
                    && !"com.android.permissioncontroller".equals(pkg)
                    && !"com.google.android.permissioncontroller".equals(pkg)) {
                guardedPackages.add(pkg);
            }
            if (cls != null && !cls.endsWith(".Settings") && !cls.endsWith(".SettingsHomepageActivity")) {
                guardedActivities.add(new ComponentName(pkg, cls));
            }
        }
    }

    boolean shouldBlock(String pkg, String className, Set<String> selected) {
        if (pkg == null) return false;
        if (guardedPackages.contains(pkg)) return true;
        if (className != null) {
            String normalized = className.startsWith(".") ? pkg + className : className;
            if (guardedActivities.contains(new ComponentName(pkg, normalized))) return true;
            String lower = normalized.toLowerCase(Locale.ROOT);
            if ("com.android.settings".equals(pkg)
                    || "com.android.permissioncontroller".equals(pkg)
                    || "com.google.android.permissioncontroller".equals(pkg)) {
                if (lower.contains("subsettings")
                        || lower.contains("uninstall")
                        || lower.contains("deletestaged")
                        || lower.contains("deletepackage")
                        || lower.contains("deviceadmin")
                        || lower.contains("installedappdetails")
                        || lower.contains("appinfo")
                        || lower.contains("manageapplications")
                        || lower.contains("spaactivity")
                        || lower.contains("usageaccess")
                        || lower.contains("drawoverlay")
                        || lower.contains("overlaysettings")) {
                    return true;
                }
            }
        }
        return !essential.contains(pkg) && selected.contains(pkg);
    }
}
