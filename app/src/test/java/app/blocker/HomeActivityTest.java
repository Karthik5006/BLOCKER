package app.blocker;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.widget.LinearLayout;
import android.widget.ListView;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowSystemClock;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, shadows={HomeActivityTest.InstalledApps.class, HomeActivityTest.App.class})
public class HomeActivityTest {
    @Implements(LauncherActivityInfo.class)
    public static class App {
        private String name;
        @Implementation protected CharSequence getLabel() { return name; }
        @Implementation protected ComponentName getComponentName() { return new ComponentName(name, name + ".Main"); }
        @Implementation protected UserHandle getUser() { return Process.myUserHandle(); }
    }

    @Implements(LauncherApps.class)
    public static class InstalledApps {
        static ComponentName launched;
        private LauncherActivityInfo entry(String name) {
            LauncherActivityInfo info = Shadow.newInstanceOf(LauncherActivityInfo.class);
            ((App) Shadow.extract(info)).name = name;
            return info;
        }
        @Implementation protected List<LauncherActivityInfo> getActivityList(String pkg, UserHandle user) {
            return List.of(entry("bank"), entry("video"));
        }
        @Implementation protected void startMainActivity(ComponentName component, UserHandle user, Rect bounds, Bundle options) {
            launched = component;
        }
    }

    @Test public void launcherTapRoutesOnlySelectedAppsDuringSession() {
        try (var controller = Robolectric.buildActivity(HomeActivity.class).setup()) {
            HomeActivity home = controller.get();
            State.select(home, Set.of("video"));
            State.prepare(home, 1);
            State.startLauncherSession(home);
            LinearLayout layout = (LinearLayout) ((android.view.ViewGroup) home.findViewById(android.R.id.content)).getChildAt(0);
            ListView list = (ListView) layout.getChildAt(1);
            InstalledApps.launched = null;
            list.performItemClick(null, 1, 1);
            Intent timer = Shadows.shadowOf(home).getNextStartedActivity();
            assertEquals(MainActivity.class.getName(), timer.getComponent().getClassName());
            assertTrue(timer.getBooleanExtra("blocked", false));
            assertNull(InstalledApps.launched);
            list.performItemClick(null, 0, 0);
            assertEquals("bank", InstalledApps.launched.getPackageName());
            assertTrue(State.active(home));
            ShadowSystemClock.advanceBy(Duration.ofMinutes(1));
            list.performItemClick(null, 1, 1);
            assertEquals("video", InstalledApps.launched.getPackageName());
        }
    }
}
