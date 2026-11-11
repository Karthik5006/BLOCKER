package app.blocker;

import android.app.*;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.util.*;

public final class BlockerService extends Service {
    private static final long POLL_INTERVAL_MS = 250L;
    private static BlockerService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GuardRoutes routes;
    private Set<String> selected = Collections.emptySet();
    private long lastRedirect = -700;
    private long lastQueryTime = 0;
    private String currentPkg;
    private String currentClass;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (State.active(BlockerService.this)) routes = new GuardRoutes(BlockerService.this);
        }
    };

    private final BroadcastReceiver screenState = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!State.active(BlockerService.this)) return;
            String action = i.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                handler.removeCallbacks(poll);
            } else if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                handler.removeCallbacks(poll);
                handler.post(poll);
            }
        }
    };

    private final Runnable watch = new Runnable() {
        @Override public void run() {
            if (!State.active(BlockerService.this)) {
                if (State.expired(BlockerService.this)) State.completed(BlockerService.this);
                shutdown();
                return;
            }
            scheduleDeadline();
        }
    };

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!State.active(BlockerService.this)) {
                if (State.expired(BlockerService.this)) State.completed(BlockerService.this);
                shutdown();
                return;
            }
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm != null && !pm.isInteractive()) {
                // Pause polling completely while screen is off; screenState receiver resumes on wake.
                return;
            }
            checkForeground();
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    static void connectPending(Context c) {
        BlockingAccess.prepare(c);
        if (instance != null && State.pending(c)) {
            instance.beginSession();
            return;
        }
        startServiceInternal(c);
    }

    static void ensureRunning(Context c) {
        if (!State.active(c) || !BlockingAccess.enabled(c)) return;
        BlockingAccess.prepare(c);
        if (instance == null) {
            startServiceInternal(c);
        }
    }

    static void interceptNow(Context c) {
        if (instance != null) {
            instance.redirect(true);
            return;
        }
        Intent home = new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(home); } catch (RuntimeException ignored) { }
        Intent intent = new Intent(c, MainActivity.class)
            .putExtra("blocked", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { c.startActivity(intent); } catch (RuntimeException ignored) { }
    }

    private static void startServiceInternal(Context c) {
        Intent intent = new Intent(c, BlockerService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(intent);
            else c.startService(intent);
        } catch (RuntimeException ignored) {
            try { c.startService(intent); } catch (RuntimeException ignoredFallback) { }
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkgFilter.addDataScheme("package");
        registerReceiver(packages, pkgFilter);

        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenState, screenFilter);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        instance = this;
        beginSession();
        return State.active(this) ? START_STICKY : START_NOT_STICKY;
    }

    void beginSession() {
        instance = this;
        State.restoreClock(this);
        boolean starting = State.pending(this);
        if (starting) {
            State.activate(this);
            if (!State.active(this)) {
                android.widget.Toast.makeText(
                    this,
                    "Session cancelled. Android did not confirm uninstall protection.",
                    android.widget.Toast.LENGTH_LONG
                ).show();
            }
        }
        if (!State.active(this)) {
            if (State.expired(this)) State.completed(this);
            shutdown();
            return;
        }
        routes = new GuardRoutes(this);
        selected = State.selected(this);
        lastQueryTime = System.currentTimeMillis() - 5_000L;
        Notification notification = Notices.sessionNotification(this);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notices.SESSION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(Notices.SESSION, notification);
            }
        } catch (RuntimeException ignored) {
            Notices.session(this);
        }
        Notices.sessionAlarm(this);
        handler.removeCallbacks(watch);
        scheduleDeadline();
        handler.removeCallbacks(poll);
        handler.post(poll);
        if (starting) redirect(false);
    }

    private void checkForeground() {
        UsageStatsManager usm = getSystemService(UsageStatsManager.class);
        if (usm == null) return;
        long now = System.currentTimeMillis();
        long start = lastQueryTime > 0 ? Math.max(now - 5_000L, lastQueryTime - 200L) : now - 5_000L;
        lastQueryTime = now;
        UsageEvents events = usm.queryEvents(start, now);
        UsageEvents.Event event = new UsageEvents.Event();
        String latestPkg = null;
        String latestClass = null;
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                latestPkg = event.getPackageName();
                latestClass = event.getClassName();
            }
        }
        if (latestPkg != null) {
            currentPkg = latestPkg;
            currentClass = latestClass;
        }
        if (currentPkg != null) {
            onWindowChanged(currentPkg, currentClass);
        }
    }

    void onWindowChanged(String pkg, String activity) {
        if (!State.active(this)) {
            if (State.expired(this)) State.completed(this);
            shutdown();
            return;
        }
        if (pkg == null) return;
        if (routes == null || !routes.shouldBlock(true, pkg, activity, selected)) return;
        redirect(true);
    }

    private void redirect(boolean intercepted) {
        Intent home = new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(home); } catch (RuntimeException ignored) { }

        long now = SystemClock.elapsedRealtime();
        if (now - lastRedirect < 700) return;
        lastRedirect = now;
        Intent intent = new Intent(this, MainActivity.class)
            .putExtra("blocked", intercepted)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(intent); }
        catch (RuntimeException ignored) { /* Returning home remains the fallback. */ }
    }

    private void scheduleDeadline() {
        handler.removeCallbacks(watch);
        handler.postDelayed(watch, Math.max(1, State.remaining(this)));
    }

    static void complete(Context c) {
        State.completed(c);
        exit(c);
    }

    static void exit(Context c) {
        State.stop(c);
        Notices.cancelSessionAlarm(c);
        UninstallGuard.release(c);
        c.getSystemService(NotificationManager.class).cancel(Notices.SESSION);
        if (instance != null) {
            instance.shutdown();
        } else {
            try { c.stopService(new Intent(c, BlockerService.class)); } catch (RuntimeException ignored) { }
            BlockingAccess.disable(c);
        }
    }

    private void shutdown() {
        State.stop(this);
        Notices.cancelSessionAlarm(this);
        UninstallGuard.release(this);
        handler.removeCallbacksAndMessages(null);
        try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (RuntimeException ignored) { }
        getSystemService(NotificationManager.class).cancel(Notices.SESSION);
        stopSelf();
        BlockingAccess.disable(this);
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onDestroy() {
        try { unregisterReceiver(packages); } catch (IllegalArgumentException ignored) { }
        try { unregisterReceiver(screenState); } catch (IllegalArgumentException ignored) { }
        handler.removeCallbacksAndMessages(null);
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
