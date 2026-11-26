package app.blocker;
import android.content.*;
public final class ActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (intent == null) return;
        State.restoreClock(c);
        String action = intent.getAction();
        if ("session-end".equals(action) && (State.expired(c) || (State.prefs(c).getBoolean("pending", false) && !State.pending(c)))) {
            if (State.expired(c)) BlockerService.complete(c);
            else BlockerService.exit(c);
        } else if ("keep-alive".equals(action) && State.active(c)) {
            BlockerService.ensureRunning(c);
        } else if ("schedule-start".equals(action)) {
            Notices.scheduleBlock(c);
            if (State.scheduleEnabled(c) && !State.active(c) && !State.selected(c).isEmpty() && BlockingAccess.enabled(c)) {
                if (State.activateScheduled(c, State.scheduleMinutes(c))) {
                    Notices.session(c);
                    Notices.sessionAlarm(c);
                    BlockerService.ensureRunning(c);
                }
            }
        } else if ("remind".equals(action)) {
            Notices.remind(c);
        }
    }
}
