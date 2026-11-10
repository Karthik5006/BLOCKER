package app.blocker;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Button;
import java.util.Set;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,shadows=StateGuardTest.Policy.class)
public class SimpleStartTest {
 @Before public void setup(){
  State.prefs(RuntimeEnvironment.getApplication()).edit().clear().commit();
  StateGuardTest.Policy.admin=StateGuardTest.Policy.owner=StateGuardTest.Policy.delegated=StateGuardTest.Policy.blocked=false;
  StateGuardTest.Policy.deny=StateGuardTest.Policy.denyRead=StateGuardTest.Policy.failApply=false;
 }
 @Test public void startAsksAndroidDirectlyAndWaitsForPermissions(){
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=controller.get();State.select(a,Set.of("com.brave.browser"));
   ReflectionHelpers.callInstanceMethod(a,"startBlocking");
   assertTrue(State.pending(a));assertFalse(State.active(a));
   assertNull(ReflectionHelpers.getField(a,"setupDialog"));
   Intent request=Shadows.shadowOf(a).getNextStartedActivityForResult().intent;
   assertEquals(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN,request.getAction());
   assertEquals(UninstallGuard.component(a),request.getParcelableExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN));
   StateGuardTest.Policy.admin=true;
   ReflectionHelpers.callInstanceMethod(a,"onActivityResult",from(int.class,20),from(int.class,-1),from(Intent.class,null));
   assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS,Shadows.shadowOf(a).getNextStartedActivity().getAction());
   assertFalse(State.active(a));
  }
 }
 @Test public void allNativeButtonsStayFlatWhenPressedAndSelected(){
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=controller.get();
   for(boolean primary:new boolean[]{false,true}){
    Button b=ReflectionHelpers.callInstanceMethod(a,"button",from(String.class,"5 min"),from(boolean.class,primary),from(Runnable.class,null));
    for(boolean selected:new boolean[]{false,true}){
     ReflectionHelpers.callInstanceMethod(a,"selectChip",from(Button.class,b),from(boolean.class,selected));
     for(boolean pressed:new boolean[]{false,true}){
      b.setPressed(pressed);b.jumpDrawablesToCurrentState();
      assertNull(b.getStateListAnimator());assertEquals(0,b.getElevation(),0);assertEquals(0,b.getTranslationZ(),0);assertNull(b.getOutlineProvider());
     }
    }
   }
  }
 }
}
