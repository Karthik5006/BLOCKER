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
        handler(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+c.getPackageName())),"com.android.settings","com.android.settings.applications.InstalledAppDetails");
        handler(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+c.getPackageName())),"com.google.android.permissioncontroller","com.android.packageinstaller.UninstallerActivity");
    }
    void handler(Intent intent,String pkg,String cls){
        ActivityInfo a=new ActivityInfo();a.packageName=pkg;a.name=cls;a.applicationInfo=new ApplicationInfo();a.applicationInfo.packageName=pkg;
        ResolveInfo r=new ResolveInfo();r.activityInfo=a;
        Shadows.shadowOf(c.getPackageManager()).addResolveInfoForIntent(intent,r);
    }
    @Test public void sharedAppInfoRemainsAvailableWithOldSelectAllChoices() {
        handler(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+c.getPackageName())),
                "com.android.settings", "com.android.settings.SubSettings");
        GuardRoutes routes = new GuardRoutes(AppCatalog.essential(c));
        Set<String> selected = Set.of("com.android.settings", "com.brave.browser", "android.launcher");
        assertFalse(routes.shouldBlock("com.android.settings", selected));
        assertFalse(routes.shouldBlock("android.launcher", selected));
        assertFalse(routes.shouldBlock("com.android.vending", selected));
        assertTrue(routes.shouldBlock("com.brave.browser", selected));
    }

    @Test public void serviceLeavesSetupAndRedirectsDirectlyToTimer(){
        State.select(c,Set.of("com.brave.browser"));State.prepare(c,1);
        ServiceController<BlockerService> controller=Robolectric.buildService(BlockerService.class).create();
        try {
            BlockerService service=controller.get();
            service.beginSession();
            assertTrue(State.active(c));
            Intent timer=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
            assertEquals(MainActivity.class.getName(),timer.getComponent().getClassName());assertFalse(timer.getBooleanExtra("blocked",false));
            ShadowSystemClock.advanceBy(Duration.ofMillis(300));
            service.onWindowChanged("com.brave.browser");
            Intent blocked=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
            assertEquals(MainActivity.class.getName(),blocked.getComponent().getClassName());assertTrue(blocked.getBooleanExtra("blocked",false));
        } finally {controller.destroy();}
    }
    @Test public void expiryReleasesGuardBeforeAnyRedirect(){
        State.select(c,Set.of("com.brave.browser"));State.prepare(c,1);
        ServiceController<BlockerService> controller=Robolectric.buildService(BlockerService.class).create();
        try {
            BlockerService service=controller.get();
            service.beginSession();
            ShadowSystemClock.advanceBy(Duration.ofMinutes(1));service.onWindowChanged("com.brave.browser");
            assertFalse(State.active(c));assertFalse(StateGuardTest.Policy.admin);assertTrue(BlockingAccess.disabled(c));
        } finally {controller.destroy();}
    }
}
