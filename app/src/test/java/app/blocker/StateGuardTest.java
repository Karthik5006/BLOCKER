package app.blocker;
import android.app.admin.DevicePolicyManager;import android.content.*;import android.provider.Settings;import java.time.LocalDate;import java.time.Duration;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowSystemClock;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,shadows=StateGuardTest.Policy.class)
public class StateGuardTest {
 @Implements(DevicePolicyManager.class) public static class Policy {
  static boolean admin,owner,delegated,blocked,deny,denyRead,failApply;static int removals;
  @Implementation protected boolean isAdminActive(ComponentName c){return admin;}
  @Implementation protected boolean isDeviceOwnerApp(String p){return owner;}
  @Implementation protected boolean isProfileOwnerApp(String p){return false;}
  @Implementation protected List<String> getDelegatedScopes(ComponentName c,String p){return delegated?List.of(DevicePolicyManager.DELEGATION_BLOCK_UNINSTALL):List.of();}
  @Implementation protected boolean isUninstallBlocked(ComponentName c,String p){if(denyRead)throw new SecurityException();return blocked;}
  @Implementation protected void setUninstallBlocked(ComponentName c,String p,boolean v){if(deny||(!owner&&!delegated))throw new SecurityException();if(!failApply)blocked=v;}
  @Implementation protected void removeActiveAdmin(ComponentName c){removals++;admin=false;}
 }
 Context c;
 @Before public void setup(){c=RuntimeEnvironment.getApplication();State.prefs(c).edit().clear().commit();Settings.Global.putInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,9);Policy.admin=Policy.owner=Policy.delegated=Policy.blocked=Policy.deny=Policy.denyRead=Policy.failApply=false;Policy.removals=0;}
 @Test public void cannotStartWithoutAdmin(){State.prepare(c,25);State.activate(c);assertFalse(State.active(c));Policy.admin=true;State.activate(c);assertTrue(State.active(c));}
 @Test public void holdIsSnapshotAndChoicesPersist(){State.select(c,Set.of("com.brave.browser"));State.prefs(c).edit().putInt("holdMinutes",10).commit();State.prepare(c,25);Policy.admin=true;State.activate(c);State.prefs(c).edit().putInt("holdMinutes",3).commit();assertEquals(600000,State.sessionHold(c));State.stop(c);assertEquals(Set.of("com.brave.browser"),State.selected(c));assertEquals(180000,State.holdDuration(c));}
 @Test public void exactSessionExpiry(){Policy.admin=true;State.prepare(c,1);State.activate(c);ShadowSystemClock.advanceBy(Duration.ofMillis(59999));assertTrue(State.active(c));ShadowSystemClock.advanceBy(Duration.ofMillis(1));assertTrue(State.expired(c));}
 @Test public void abandonedSetupExpires(){State.prepare(c,1);ShadowSystemClock.advanceBy(Duration.ofMinutes(10));assertFalse(State.pending(c));State.activate(c);assertFalse(State.active(c));}
 @Test public void completionIsIdempotentAndNotACheckIn(){Policy.admin=true;State.prepare(c,25);State.activate(c);State.completed(c);State.completed(c);assertEquals(1,State.prefs(c).getInt("sessions_"+LocalDate.now(),0));assertEquals("",State.prefs(c).getString("day_"+LocalDate.now(),""));}
 @Test public void policyMustActuallyApply(){State.prefs(c).edit().putBoolean("managedSession",true).commit();Policy.delegated=true;Policy.failApply=true;State.prepare(c,25);State.activate(c);assertFalse(State.active(c));}
 @Test public void acquireAndReleaseVerified(){Policy.delegated=true;State.prefs(c).edit().putBoolean("managedSession",true).commit();assertTrue(UninstallGuard.acquire(c));assertTrue(Policy.blocked);UninstallGuard.release(c);assertFalse(Policy.blocked);assertFalse(UninstallGuard.active(c));}
 @Test public void externalRestrictionPreserved(){Policy.blocked=true;State.prefs(c).edit().putBoolean("managedSession",true).commit();assertTrue(UninstallGuard.acquire(c));assertFalse(State.prefs(c).getBoolean("ownsUninstallPolicy",false));UninstallGuard.release(c);assertTrue(Policy.blocked);}
 @Test public void deniedReleaseStaysVisible(){Policy.delegated=true;State.prefs(c).edit().putBoolean("managedSession",true).commit();assertTrue(UninstallGuard.acquire(c));Policy.deny=true;Policy.denyRead=true;UninstallGuard.release(c);assertTrue(UninstallGuard.active(c));assertEquals(UninstallGuard.UNKNOWN,UninstallGuard.managedStatus(c));}
 @Test public void failedReadbackIsNotSuccess(){Policy.delegated=true;State.prefs(c).edit().putBoolean("managedSession",true).commit();assertTrue(UninstallGuard.acquire(c));Policy.failApply=true;UninstallGuard.release(c);assertTrue(UninstallGuard.active(c));}
 @Test public void personalAdminReleased(){Policy.admin=true;UninstallGuard.release(c);assertFalse(Policy.admin);assertEquals(1,Policy.removals);}
 @Test public void ownerNotRemoved(){Policy.admin=true;Policy.owner=true;UninstallGuard.release(c);assertTrue(Policy.admin);assertEquals(0,Policy.removals);}
 @Test public void rebootRestoresDeadline(){Policy.admin=true;State.prepare(c,25);State.activate(c);long wall=State.prefs(c).getLong("wallEnd",0);Settings.Global.putInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,10);assertFalse(State.active(c));State.restoreClock(c);assertTrue(State.active(c));assertEquals(wall,State.prefs(c).getLong("wallEnd",0));}
}
