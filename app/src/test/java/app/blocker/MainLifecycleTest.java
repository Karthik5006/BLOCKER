package app.blocker;
import android.app.Dialog;import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import org.robolectric.android.controller.ActivityController;import org.robolectric.util.ReflectionHelpers;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class MainLifecycleTest {
 @Test public void dismissingOldSheetDoesNotUnfreezeNewSheet(){
  try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=c.get();Dialog first=new Dialog(a),second=new Dialog(a);
   ReflectionHelpers.callInstanceMethod(a,"nativeSheetShown",ReflectionHelpers.ClassParameter.from(Dialog.class,first));
   ReflectionHelpers.callInstanceMethod(a,"nativeSheetShown",ReflectionHelpers.ClassParameter.from(Dialog.class,second));
   ReflectionHelpers.callInstanceMethod(a,"nativeSheetClosed",ReflectionHelpers.ClassParameter.from(Dialog.class,first));
   assertTrue((Boolean)ReflectionHelpers.getField(a,"sheetVisible"));
   ReflectionHelpers.callInstanceMethod(a,"nativeSheetClosed",ReflectionHelpers.ClassParameter.from(Dialog.class,second));
   assertFalse((Boolean)ReflectionHelpers.getField(a,"sheetVisible"));
  }
 }
}
