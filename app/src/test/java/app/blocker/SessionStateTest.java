package app.blocker;
import android.app.admin.DevicePolicyManager;import android.content.*;import android.provider.Settings;import java.time.LocalDate;import java.time.Duration;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowSystemClock;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class SessionStateTest {
 Context c;
 @Before public void setup(){c=RuntimeEnvironment.getApplication();State.prefs(c).edit().clear().commit();Settings.Global.putInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,9);State.restoreClock(c);}
 @Test public void holdIsSnapshotAndChoicesPersist(){State.select(c,Set.of("com.brave.browser"));State.prefs(c).edit().putInt("holdMinutes",10).commit();State.prepare(c,25);State.activate(c);State.prefs(c).edit().putInt("holdMinutes",3).commit();assertEquals(600000,State.sessionHold(c));State.stop(c);assertEquals(Set.of("com.brave.browser"),State.selected(c));assertEquals(180000,State.holdDuration(c));}
 @Test public void exactSessionExpiry(){State.prepare(c,1);State.activate(c);ShadowSystemClock.advanceBy(Duration.ofMillis(59999));assertTrue(State.active(c));ShadowSystemClock.advanceBy(Duration.ofMillis(1));assertTrue(State.expired(c));}
 @Test public void abandonedSetupExpires(){State.prepare(c,1);ShadowSystemClock.advanceBy(Duration.ofMinutes(10));assertFalse(State.pending(c));State.activate(c);assertFalse(State.active(c));}
 @Test public void completionIsIdempotentAndNotACheckIn(){State.prepare(c,25);State.activate(c);State.completed(c);State.completed(c);assertEquals(1,State.prefs(c).getInt("sessions_"+LocalDate.now(),0));assertEquals("",State.prefs(c).getString("day_"+LocalDate.now(),""));}
 @Test public void rebootRestoresDeadline(){State.prepare(c,25);State.activate(c);long wall=State.prefs(c).getLong("wallEnd",0);Settings.Global.putInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,10);assertFalse(State.active(c));State.restoreClock(c);assertTrue(State.active(c));assertEquals(wall,State.prefs(c).getLong("wallEnd",0));}
}
