package app.blocker;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, shadows={StateGuardTest.Policy.class,GuardRoutesTest.Access.class})
public class GuardRoutesTest {
    @Implements(AccessibilityService.class) public static class Access extends org.robolectric.shadows.ShadowAccessibilityService {
        static int homes; static boolean disabled;
        @Implementation protected boolean performGlobalAction(int action){if(action==AccessibilityService.GLOBAL_ACTION_HOME)homes++;return true;}
        @Implementation protected void disableSelf(){disabled=true;}
    }
    Context c;
    @Before public void setup(){
        c=RuntimeEnvironment.getApplication();State.prefs(c).edit().clear().commit();
        Settings.Global.putInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,9);
        StateGuardTest.Policy.admin=true;StateGuardTest.Policy.owner=false;StateGuardTest.Policy.delegated=false;
        StateGuardTest.Policy.blocked=false;StateGuardTest.Policy.deny=false;StateGuardTest.Policy.denyRead=false;
        StateGuardTest.Policy.failApply=false;Access.homes=0;Access.disabled=false;
    }
    void handler(Intent intent,String pkg,String name){
        ResolveInfo r=new ResolveInfo();r.activityInfo=new ActivityInfo();r.activityInfo.packageName=pkg;r.activityInfo.name=name;
        r.activityInfo.applicationInfo=new ApplicationInfo();r.activityInfo.applicationInfo.packageName=pkg;
        Shadows.shadowOf(c.getPackageManager()).addResolveInfoForIntent(intent,r);
    }
    @Test public void settingsInstallersAndStoresGuardedWithoutSelection(){
        GuardRoutes routes=new GuardRoutes(c);
        for(String p:List.of("com.android.settings","com.android.packageinstaller","com.google.android.packageinstaller","com.miui.securitycenter","com.android.vending","com.sec.android.app.samsungapps","com.heytap.market"))
            assertTrue(p,routes.shouldBlock(true,p,null,Set.of()));
    }
    @Test public void permissionSetupAndInactiveSessionsRemainOpen(){
        State.prepare(c,1);GuardRoutes routes=new GuardRoutes(c);
        assertFalse(routes.shouldBlock(State.active(c),"com.android.settings",null,Set.of("com.android.settings")));
        State.activate(c);assertTrue(routes.shouldBlock(State.active(c),"com.android.settings",null,Set.of()));
        State.stop(c);assertFalse(routes.shouldBlock(State.active(c),"com.android.vending",null,Set.of()));
    }
    @Test public void essentialNavigationCallsAndSelfSurviveSelectAll(){
        handler(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),"phone.launcher","phone.launcher.Home");
        handler(new Intent("android.intent.action.EMERGENCY_DIAL"),"phone.emergency","phone.emergency.Call");
        Settings.Secure.putString(c.getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD,"phone.keyboard/.Input");
        GuardRoutes routes=new GuardRoutes(c);
        for(String p:List.of(c.getPackageName(),"android","com.android.systemui","com.android.phone","com.android.server.telecom","phone.launcher","phone.emergency","phone.keyboard"))
            assertFalse(p,routes.shouldBlock(true,p,"anything.UninstallActivity",Set.of(p)));
    }
    @Test public void selectedAppsBlockAndOtherAppsStayAvailable(){
        GuardRoutes routes=new GuardRoutes(c);Set<String> selected=Set.of("com.brave.browser");
        assertTrue(routes.shouldBlock(true,"com.brave.browser",null,selected));
        assertFalse(routes.shouldBlock(true,"com.example.notes",null,selected));
    }
    @Test public void permissionControllerOnlyGuardsUninstallScreen(){
        GuardRoutes routes=new GuardRoutes(c);
        assertTrue(routes.shouldBlock(true,"com.google.android.permissioncontroller","com.android.packageinstaller.UninstallerActivity",Set.of()));
        assertFalse(routes.shouldBlock(true,"com.google.android.permissioncontroller","GrantPermissionsActivity",Set.of("com.google.android.permissioncontroller")));
        assertFalse(routes.shouldBlock(true,"com.android.permissioncontroller",null,Set.of()));
    }
    @Test public void resolvesManufacturerSettingsAndUninstaller(){
        handler(new Intent(Settings.ACTION_SETTINGS),"phone.config","phone.config.Main");
        handler(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+c.getPackageName())),"phone.remover","phone.remover.Confirm");
        GuardRoutes routes=new GuardRoutes(c);
        assertTrue(routes.shouldBlock(true,"phone.config",null,Set.of()));
        assertTrue(routes.shouldBlock(true,"phone.remover",null,Set.of()));
    }
    @Test public void resolvesManufacturerStore(){
        handler(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+c.getPackageName())),"phone.store","phone.store.Detail");
        assertTrue(new GuardRoutes(c).shouldBlock(true,"phone.store",null,Set.of()));
    }
    void window(BlockerService service,String pkg){
        AccessibilityEvent event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        event.setPackageName(pkg);service.onAccessibilityEvent(event);event.recycle();
    }
    @Test public void serviceLeavesSetupAndNeverThrottlesBlocking(){
        State.prepare(c,1);
        ServiceController<BlockerService> controller=Robolectric.buildService(BlockerService.class).create();
        try {
            BlockerService service=controller.get();ReflectionHelpers.callInstanceMethod(service,"onServiceConnected");
            assertTrue(State.active(c));assertEquals(1,Access.homes);
            Intent timer=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
            assertEquals(MainActivity.class.getName(),timer.getComponent().getClassName());assertFalse(timer.getBooleanExtra("blocked",false));
            window(service,"com.android.settings");window(service,"com.android.settings");
            assertEquals(3,Access.homes);
            window(service,c.getPackageName());assertEquals(3,Access.homes);
        } finally {controller.destroy();}
    }
    @Test public void expiryReleasesGuardBeforeAnyRedirect(){
        State.prepare(c,1);
        ServiceController<BlockerService> controller=Robolectric.buildService(BlockerService.class).create();
        try {
            BlockerService service=controller.get();ReflectionHelpers.callInstanceMethod(service,"onServiceConnected");
            ShadowSystemClock.advanceBy(Duration.ofMinutes(1));window(service,"com.android.settings");
            assertEquals(1,Access.homes);assertFalse(State.active(c));assertFalse(StateGuardTest.Policy.admin);assertTrue(Access.disabled);
        } finally {controller.destroy();}
    }
}
