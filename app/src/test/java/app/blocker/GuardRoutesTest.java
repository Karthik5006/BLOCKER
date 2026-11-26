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
@Config(sdk=35)
public class GuardRoutesTest {
    Context c;
    @Before public void setup(){
        c=RuntimeEnvironment.getApplication();State.prefs(c).edit().clear().commit();
        Settings.Global.putInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,3);
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

}
