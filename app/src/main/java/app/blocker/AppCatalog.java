package app.blocker;

import android.content.*;
import android.content.pm.*;
import android.provider.Settings;
import android.telecom.TelecomManager;
import java.util.*;

final class AppCatalog {
    static final class Entry {
        final String name, pkg;
        final boolean system, essential;
        Entry(String name, String pkg, boolean system, boolean essential) {
            this.name = name; this.pkg = pkg; this.system = system; this.essential = essential;
        }
    }
    static Set<String> essential(Context c) {
        Set<String> safe = new HashSet<>(Arrays.asList(c.getPackageName(), "android",
            "com.android.systemui", "com.android.phone", "com.android.server.telecom",
            "com.android.emergency", "com.android.permissioncontroller", "com.google.android.permissioncontroller"));
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo r : c.getPackageManager().queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY))
            safe.add(r.activityInfo.packageName);
        String keyboard = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (keyboard != null) {
            ComponentName component = ComponentName.unflattenFromString(keyboard);
            if (component != null) safe.add(component.getPackageName());
        }
        TelecomManager tm = c.getSystemService(TelecomManager.class);
        if (tm != null && tm.getDefaultDialerPackage() != null) safe.add(tm.getDefaultDialerPackage());
        for (ResolveInfo r : c.getPackageManager().queryIntentActivities(new Intent("android.intent.action.EMERGENCY_DIAL"), 0))
            if(r.activityInfo != null) safe.add(r.activityInfo.packageName);
        return safe;
    }
    static List<Entry> load(Context c) {
        Set<String> safe = essential(c);
        PackageManager pm = c.getPackageManager();
        Map<String, Entry> result = new HashMap<>();
        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(launch, 0)) {
            ApplicationInfo a = r.activityInfo.applicationInfo;
            String pkg = a.packageName;
            result.put(pkg, new Entry(a.loadLabel(pm).toString(), pkg,
                (a.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0,
                safe.contains(pkg)));
        }
        List<Entry> entries = new ArrayList<>(result.values());
        entries.sort(Comparator.comparing(e -> e.name.toLowerCase(Locale.getDefault())));
        return entries;
    }
}
