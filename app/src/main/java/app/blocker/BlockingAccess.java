package app.blocker;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;

/** The service is available for setup, active blocking or finishing its own teardown. */
final class BlockingAccess {
    private static ComponentName component(Context c){return new ComponentName(c,BlockerService.class);}
    static boolean disabled(Context c){return c.getPackageManager().getComponentEnabledSetting(component(c))==PackageManager.COMPONENT_ENABLED_STATE_DISABLED;}
    static boolean enabled(Context c){
        for(AccessibilityServiceInfo info:c.getSystemService(AccessibilityManager.class).getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)){
            if(info.getResolveInfo()==null||info.getResolveInfo().serviceInfo==null)continue;
            android.content.pm.ServiceInfo service=info.getResolveInfo().serviceInfo;
            if(component(c).equals(new ComponentName(service.packageName,service.name)))return true;
        }
        // A disabled component can disappear from the manager list before Android clears its
        // saved grant. Wait for disableSelf() to clear that grant as well when possible.
        String granted=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(granted!=null)for(String entry:granted.split(":"))if(component(c).equals(ComponentName.unflattenFromString(entry)))return true;
        return false;
    }
    static void prepare(Context c){
        if(!State.pending(c))return;
        c.getPackageManager().setComponentEnabledSetting(component(c),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
    }
    static void disable(Context c){
        if(State.active(c)||State.pending(c)||disabled(c))return;
        c.getPackageManager().setComponentEnabledSetting(component(c),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
    }
    static void awaitRelease(Context c){
        // If teardown races a service bind, allow its onServiceConnected() callback to run.
        // With no pending/active session it immediately calls disableSelf(), without a dialog.
        if(!State.active(c)&&!State.pending(c)&&disabled(c))
            c.getPackageManager().setComponentEnabledSetting(component(c),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
    }
    static void releaseRuntimePermissions(Context c){
        // POST_NOTIFICATIONS is the app's only dangerous/runtime permission. Android
        // applies this revocation asynchronously after the app has left the foreground.
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
            try{c.revokeSelfPermissionOnKill(Manifest.permission.POST_NOTIFICATIONS);}catch(SecurityException ignored){/* Android owns runtime permission revocation. */}
    }
}
