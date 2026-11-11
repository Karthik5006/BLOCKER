package app.blocker;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, shadows={StateGuardTest.Policy.class})
public class GuardRoutesTest {
    Context c;
    @Before public void setup(){
        c=RuntimeEnvironment.getApplication();State.prefs(c).edit().clear().commit();
        Settings.Global.putInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,3);
        StateGuardTest.Policy.admin=true;
        StateGuardTest.Policy.owner=StateGuardTest.Policy.delegated=StateGuardTest.Policy.blocked=false;
        StateGuardTest.Policy.deny=StateGuardTest.Policy.denyRead=StateGuardTest.Policy.failApply=false;
        StateGuardTest.Policy.removals=0;
        handler(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),"android.launcher","android.launcher.Home");
        handler(new Intent(Settings.ACTION_SETTINGS),"com.android.settings","com.android.settings.Settings");
        handler(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+c.getPackageName())),"com.google.android.permissioncontroller","com.android.packageinstaller.UninstallerActivity");
    }
    void handler(Intent intent,String pkg,String cls){
        ActivityInfo a=new ActivityInfo();a.packageName=pkg;a.name=cls;a.applicationInfo=new ApplicationInfo();a.applicationInfo.packageName=pkg;
        ResolveInfo r=new ResolveInfo();r.activityInfo=a;
        Shadows.shadowOf(c.getPackageManager()).addResolveInfoForIntent(intent,r);
    }
    @Test public void blocksSelectedSettingsAndUninstallWhilePreservingEssentials(){
        GuardRoutes routes=new GuardRoutes(c);
        Set<String> chosen=Set.of("com.brave.browser","android.launcher");
        assertTrue(routes.shouldBlock(true,"com.brave.browser",null,chosen));
        assertTrue(routes.shouldBlock(true,"com.android.settings",null,chosen));
        assertTrue(routes.shouldBlock(true,"com.android.vending",null,chosen));
        assertTrue(routes.shouldBlock(true,"com.miui.securitycenter",null,chosen));
        assertFalse(routes.shouldBlock(true,"android.launcher",null,chosen));
        assertFalse(routes.shouldBlock(true,c.getPackageName(),null,chosen));
        assertFalse(routes.shouldBlock(false,"com.brave.browser",null,chosen));
    }
    @Test public void blocksOnlyUninstallInsidePermissionController(){
        GuardRoutes routes=new GuardRoutes(c);
        assertFalse(routes.shouldBlock(true,"com.google.android.permissioncontroller","com.android.permissioncontroller.permission.ui.GrantPermissionsActivity",Set.of()));
        assertTrue(routes.shouldBlock(true,"com.google.android.permissioncontroller","com.android.packageinstaller.UninstallerActivity",Set.of()));
        assertTrue(routes.shouldBlock(true,"com.android.permissioncontroller",".UninstallAppProgress",Set.of()));
    }
    @Test public void resolvesCustomSettingsPackage(){
        handler(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+c.getPackageName())),"vendor.settings","vendor.settings.AppDetails");
        assertTrue(new GuardRoutes(c).shouldBlock(true,"vendor.settings",null,Set.of()));
    }
    @Test public void resolvesManufacturerStore(){
        handler(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+c.getPackageName())),"phone.store","phone.store.Detail");
        assertTrue(new GuardRoutes(c).shouldBlock(true,"phone.store",null,Set.of()));
    }
    void window(BlockerService service,String pkg){
        service.onWindowChanged(pkg, null);
    }
    @Test public void serviceLeavesSetupAndNeverThrottlesBlocking(){
        State.prepare(c,1);
        ServiceController<BlockerService> controller=Robolectric.buildService(BlockerService.class).create();
        try {
            BlockerService service=controller.get();
            service.beginSession();
            assertTrue(State.active(c));
            Intent first=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
            assertEquals(Intent.ACTION_MAIN,first.getAction());
            Intent timer=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
            assertEquals(MainActivity.class.getName(),timer.getComponent().getClassName());assertFalse(timer.getBooleanExtra("blocked",false));
            window(service,"com.android.settings");
            assertEquals(Intent.ACTION_MAIN,Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity().getAction());
            window(service,"com.android.settings");
            assertEquals(Intent.ACTION_MAIN,Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity().getAction());
        } finally {controller.destroy();}
    }
    @Test public void expiryReleasesGuardBeforeAnyRedirect(){
        State.prepare(c,1);
        ServiceController<BlockerService> controller=Robolectric.buildService(BlockerService.class).create();
        try {
            BlockerService service=controller.get();
            service.beginSession();
            ShadowSystemClock.advanceBy(Duration.ofMinutes(1));window(service,"com.android.settings");
            assertFalse(State.active(c));assertFalse(StateGuardTest.Policy.admin);assertTrue(BlockingAccess.disabled(c));
        } finally {controller.destroy();}
    }
}
