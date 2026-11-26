package app.blocker;

import android.content.*;
import android.provider.Settings;
import android.service.notification.*;
import java.util.Set;

/** Optional listener that silently dismisses notifications from blocked apps during active sessions. */
public final class BlockerNoticeGuard extends NotificationListenerService {
    private static volatile BlockerNoticeGuard activeInstance;

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        activeInstance = this;
        sweep(this);
    }

    @Override public void onListenerDisconnected() {
        if (activeInstance == this) activeInstance = null;
        super.onListenerDisconnected();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !State.active(this)) return;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) return;
        if (State.selected(this).contains(pkg)) {
            try { cancelNotification(sbn.getKey()); }
            catch (RuntimeException ignored) { }
        }
    }

    static void sweep(Context c) {
        BlockerNoticeGuard guard = activeInstance;
        if (guard == null || !State.active(c)) return;
        Set<String> blocked = State.selected(c);
        if (blocked.isEmpty()) return;
        try {
            StatusBarNotification[] list = guard.getActiveNotifications();
            if (list == null) return;
            for (StatusBarNotification sbn : list) {
                if (sbn != null && blocked.contains(sbn.getPackageName()) && !c.getPackageName().equals(sbn.getPackageName())) {
                    guard.cancelNotification(sbn.getKey());
                }
            }
        } catch (RuntimeException ignored) { }
    }

    static boolean enabled(Context c) {
        String flat = new ComponentName(c, BlockerNoticeGuard.class).flattenToString();
        String list = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return list != null && list.contains(flat);
    }
}
