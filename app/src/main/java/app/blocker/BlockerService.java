package app.blocker;

import android.accessibilityservice.AccessibilityService;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityEvent;
import java.util.*;

public final class BlockerService extends AccessibilityService {
    private static BlockerService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GuardRoutes routes;
    private Set<String> selected = Collections.emptySet();
    private long lastRedirect = -700;
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if(State.active(BlockerService.this)) routes = new GuardRoutes(BlockerService.this);
        }
    };
    private final Runnable watch = new Runnable() {
        @Override public void run() {
            if (!State.active(BlockerService.this)) { if(State.expired(BlockerService.this))State.completed(BlockerService.this);shutdown();return; }
            scheduleDeadline();
        }
    };
    static void connectPending() {
        if(instance != null && State.pending(instance)) instance.beginSession();
    }
    @Override public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);filter.addDataScheme("package");
        registerReceiver(packages, filter);
    }
    @Override protected void onServiceConnected() {
        instance = this;
        beginSession();
    }
    private void beginSession() {
        instance = this;
        State.restoreClock(this);
        boolean starting = State.pending(this);
        if(starting) {
            State.activate(this);
            if(!State.active(this)) android.widget.Toast.makeText(this,"Session cancelled. Android did not confirm uninstall protection.",android.widget.Toast.LENGTH_LONG).show();
        }
        if (!State.active(this)) { if(State.expired(this))State.completed(this);shutdown();return; }
        routes = new GuardRoutes(this);
        selected = State.selected(this);
        Notices.session(this);
        Notices.sessionAlarm(this);
        handler.removeCallbacks(watch);
        scheduleDeadline();
        // Leave the accessibility setup page as soon as the session becomes active. A service
        // connection does not always produce another window event for the already-open Settings.
        if(starting) redirect(false);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!State.active(this)) { if(State.expired(this))State.completed(this);shutdown();return; }
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        String activity = event.getClassName() == null ? null : event.getClassName().toString();
        if(routes == null || !routes.shouldBlock(true, pkg, activity, selected)) return;
        redirect(true);
    }
    private void redirect(boolean intercepted) {
        // Never throttle HOME: two quick attempts must not leave the second blocked window open.
        performGlobalAction(GLOBAL_ACTION_HOME);
        long now = SystemClock.elapsedRealtime();
        if (now - lastRedirect < 700) return;
        lastRedirect = now;
        Intent intent = new Intent(this, MainActivity.class).putExtra("blocked", intercepted)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(intent); }
        catch (RuntimeException ignored) { /* Returning home remains the fallback. */ }
    }
    private void scheduleDeadline(){handler.removeCallbacks(watch);handler.postDelayed(watch,Math.max(1,State.remaining(this)));}
    static void complete(Context c){State.completed(c);exit(c);}
    static void exit(Context c) {
        State.stop(c);
        Notices.cancelSessionAlarm(c);
        UninstallGuard.release(c);
        c.getSystemService(NotificationManager.class).cancel(Notices.SESSION);
        if (instance != null) instance.shutdown();
        else if(BlockingAccess.enabled(c))BlockingAccess.awaitRelease(c);
        else BlockingAccess.disable(c);
    }
    private void shutdown() {
        State.stop(this);
        Notices.cancelSessionAlarm(this);
        UninstallGuard.release(this);
        handler.removeCallbacksAndMessages(null);
        getSystemService(NotificationManager.class).cancel(Notices.SESSION);
        disableSelf();
        BlockingAccess.disable(this);
    }
    @Override public boolean onUnbind(Intent intent) {
        if(State.active(this)) {
            Notices.sessionAlarm(this);
        } else {
            State.stop(this); Notices.cancelSessionAlarm(this); UninstallGuard.release(this);
        }
        handler.removeCallbacksAndMessages(null);
        getSystemService(NotificationManager.class).cancel(Notices.SESSION);
        if (instance == this) instance = null;
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        unregisterReceiver(packages);
        handler.removeCallbacksAndMessages(null);
        if (instance == this) instance = null;
        super.onDestroy();
    }
    @Override public void onInterrupt() { }
}
