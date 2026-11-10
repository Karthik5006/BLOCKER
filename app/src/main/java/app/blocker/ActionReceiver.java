package app.blocker;
import android.content.*;
public final class ActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        State.restoreClock(c);
        if ("session-end".equals(intent.getAction()) && (State.expired(c) || (State.prefs(c).getBoolean("pending",false)&&!State.pending(c)))) {if(State.expired(c))BlockerService.complete(c);else BlockerService.exit(c);}
        if ("remind".equals(intent.getAction())) Notices.remind(c);
    }
}
