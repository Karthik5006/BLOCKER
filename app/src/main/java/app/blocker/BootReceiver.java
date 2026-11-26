package app.blocker;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        State.restoreClock(c);
        if (State.active(c)) {
            Notices.sessionAlarm(c);
            Notices.session(c);
        } else if (State.expired(c)) {
            State.finishSession(c);
        } else {
            State.end(c);
        }
        Notices.schedule(c);
    }
}
