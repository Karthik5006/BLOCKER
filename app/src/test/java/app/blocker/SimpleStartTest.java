package app.blocker;
import android.app.role.RoleManager;
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

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class SimpleStartTest {
 @Before public void setup(){
  State.prefs(RuntimeEnvironment.getApplication()).edit().clear().commit();
 }
 @Test public void startWaitsForHomeRoleAndContinuesAfterSelection(){
  try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=controller.get();State.select(a,Set.of("com.brave.browser"));
   org.robolectric.shadows.ShadowRoleManager roles=Shadows.shadowOf(a.getSystemService(RoleManager.class));
   roles.addAvailableRole(RoleManager.ROLE_HOME);
   ReflectionHelpers.callInstanceMethod(a,"startBlocking");
   assertTrue(State.pending(a));assertFalse(State.active(a));
   assertNull(ReflectionHelpers.getField(a,"setupDialog"));
   Intent request=Shadows.shadowOf(a).getNextStartedActivityForResult().intent;
   assertTrue(Settings.ACTION_HOME_SETTINGS.equals(request.getAction()) ||
       "android.app.role.action.REQUEST_ROLE".equals(request.getAction()));
   assertFalse(State.active(a));
   roles.addHeldRole(RoleManager.ROLE_HOME);
   controller.pause().resume();
   assertTrue(State.active(a));
   assertFalse(State.pending(a));
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
