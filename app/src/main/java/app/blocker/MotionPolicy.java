package app.blocker;
import android.content.*;import android.database.ContentObserver;import android.os.*;import android.provider.Settings;
/** Event-driven motion controls: no background polling. */
final class MotionPolicy {
 private final Context c;private final Runnable changed;private boolean foreground;
 private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){changed.run();}};
 private final ContentObserver observer=new ContentObserver(new Handler(Looper.getMainLooper())){public void onChange(boolean self){changed.run();}};
 MotionPolicy(Context c,Runnable changed){this.c=c;this.changed=changed;IntentFilter f=new IntentFilter();f.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_USER_PRESENT);c.registerReceiver(receiver,f);c.getContentResolver().registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer);}
 void foreground(boolean value){foreground=value;changed.run();}
 boolean allowed(){PowerManager p=c.getSystemService(PowerManager.class);return foreground&&State.prefs(c).getBoolean("graphics",true)&&p!=null&&p.isInteractive()&&!p.isPowerSaveMode()&&Settings.Global.getFloat(c.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1)>0;}
 void close(){c.unregisterReceiver(receiver);c.getContentResolver().unregisterContentObserver(observer);}
}
