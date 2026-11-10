package app.blocker;
import android.content.*;import android.os.*;import android.provider.Settings;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import static org.junit.Assert.*;import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class MotionPolicyTest {
 Context c;PowerManager power;MotionPolicy motion;
 @Before public void setup(){c=RuntimeEnvironment.getApplication();State.prefs(c).edit().clear().commit();power=c.getSystemService(PowerManager.class);shadowOf(power).setIsInteractive(true);shadowOf(power).setIsPowerSaveMode(false);Settings.Global.putFloat(c.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1);motion=new MotionPolicy(c,()->{});}
 @After public void close(){motion.close();}
 @Test public void foregroundOnly(){assertFalse(motion.allowed());motion.foreground(true);assertTrue(motion.allowed());motion.foreground(false);assertFalse(motion.allowed());}
 @Test public void masterSwitch(){motion.foreground(true);State.prefs(c).edit().putBoolean("graphics",false).commit();assertFalse(motion.allowed());}
 @Test public void batterySaver(){motion.foreground(true);shadowOf(power).setIsPowerSaveMode(true);assertFalse(motion.allowed());}
 @Test public void animatorScaleZero(){motion.foreground(true);Settings.Global.putFloat(c.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,0);assertFalse(motion.allowed());}
 @Test public void screenOff(){motion.foreground(true);shadowOf(power).setIsInteractive(false);assertFalse(motion.allowed());}
}
