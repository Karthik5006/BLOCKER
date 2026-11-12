package app.blocker;
import android.app.admin.*;import android.content.*;
/** Personal-device admin prevents direct uninstallation and intercepts deactivation attempts during an active session. */
public final class UninstallGuard extends DeviceAdminReceiver {
 static final int UNKNOWN=-1,CLEAR=0,BLOCKED=1;
 static ComponentName component(Context c){return new ComponentName(c,UninstallGuard.class);}private static DevicePolicyManager policy(Context c){return c.getSystemService(DevicePolicyManager.class);}
 static boolean adminActive(Context c){return policy(c)!=null&&policy(c).isAdminActive(component(c));}
 static boolean owner(Context c){DevicePolicyManager d=policy(c);return d!=null&&(d.isDeviceOwnerApp(c.getPackageName())||d.isProfileOwnerApp(c.getPackageName()));}
 private static ComponentName caller(Context c){return owner(c)?component(c):null;}
 static boolean delegated(Context c){try{return policy(c)!=null&&policy(c).getDelegatedScopes(null,c.getPackageName()).contains(DevicePolicyManager.DELEGATION_BLOCK_UNINSTALL);}catch(SecurityException denied){return false;}}
 static int managedStatus(Context c){try{return policy(c)==null?UNKNOWN:policy(c).isUninstallBlocked(null,c.getPackageName())?BLOCKED:CLEAR;}catch(SecurityException denied){return UNKNOWN;}}
 static boolean managedBlocked(Context c){return managedStatus(c)==BLOCKED;}
 static boolean active(Context c){return adminActive(c)||State.prefs(c).getBoolean("ownsUninstallPolicy",false);}
 static boolean acquire(Context c){if(!State.prefs(c).getBoolean("managedSession",false))return adminActive(c);if(managedStatus(c)==BLOCKED)return true;if(!owner(c)&&!delegated(c))return false;if(!State.prefs(c).edit().putBoolean("ownsUninstallPolicy",true).commit())return false;try{policy(c).setUninstallBlocked(caller(c),c.getPackageName(),true);return managedStatus(c)==BLOCKED;}catch(SecurityException denied){return false;}}
 static void release(Context c){DevicePolicyManager d=policy(c);if(d==null)return;if(State.prefs(c).getBoolean("ownsUninstallPolicy",false)){try{d.setUninstallBlocked(caller(c),c.getPackageName(),false);}catch(SecurityException denied){/* Read back actual policy below. */}if(managedStatus(c)==CLEAR)State.prefs(c).edit().remove("ownsUninstallPolicy").commit();}
  if(adminActive(c)&&!owner(c))try{d.removeActiveAdmin(component(c));}catch(SecurityException denied){/* Keep actual status visible. */}
 }
 @Override public CharSequence onDisableRequested(Context c,Intent i){
  State.restoreClock(c);
  if(State.active(c)){
   BlockerService.interceptNow(c);
   return "Your session is active. Deactivating BLOCKER removes its administrator protection.";
  }
  return "Deactivating this administrator allows you to uninstall BLOCKER.";
 }
 @Override public void onDisabled(Context c,Intent i){State.prefs(c).edit().putBoolean("guardRevoked",State.active(c)).apply();}
}
