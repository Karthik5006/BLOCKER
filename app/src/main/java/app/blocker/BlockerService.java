package app.blocker;

import android.app.*;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.*;

public final class BlockerService extends Service {
    private static final long POLL_INTERVAL_MS = 180L;
    private static BlockerService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GuardRoutes routes;
    private Set<String> selected = Collections.emptySet();
    private boolean foregroundStarted;
    private long lastRedirect = -1000L;
    private long lastBlockedEventTime = -1L;
    private String lastBlockedPkg;
    private long mainResumedAt;
    private String currentPkg;
    private String currentClass;
    private long currentEventTime;
    private long lastQueryEnd;
    private View shieldView;
    private TextView shieldClock;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (State.active(BlockerService.this)) routes = new GuardRoutes(BlockerService.this);
        }
    };

    private final BroadcastReceiver screenState = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!State.active(BlockerService.this)) return;
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                hideShield();
                handler.removeCallbacks(poll);
            } else {
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
                hideShield();
                return;
            }
            checkForeground();
            if (shieldView != null && shieldClock != null) {
                shieldClock.setText(State.clock(State.remaining(BlockerService.this)) + " remaining in your session");
            }
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    static void connectPending(Context c) {
        BlockingAccess.prepare(c);
        State.restoreClock(c);
        if (State.pending(c)) {
            State.activate(c);
            if (State.active(c)) State.prefs(c).edit().putBoolean("justActivated", true).commit();
        }
        if (instance != null) instance.beginSession();
        else startServiceInternal(c);
    }

    static void ensureRunning(Context c) {
        if (!State.active(c) || !BlockingAccess.enabled(c)) return;
        BlockingAccess.prepare(c);
        if (instance == null) startServiceInternal(c);
    }

    static void interceptNow(Context c) {
        if (instance != null) {
            instance.showShield();
            instance.lastRedirect = -1000L;
            instance.redirect(true);
            return;
        }
        Intent intent = new Intent(c, MainActivity.class)
            .putExtra("blocked", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { c.startActivity(intent); } catch (RuntimeException ignored) { }
    }

    static void onMainActivityResumed() {
        if (instance != null) {
            long now = System.currentTimeMillis();
            instance.mainResumedAt = now;
            instance.currentPkg = instance.getPackageName();
            instance.currentClass = MainActivity.class.getName();
            instance.currentEventTime = now;
            instance.lastQueryEnd = now;
            instance.hideShield();
        }
    }

    private static void startServiceInternal(Context c) {
        Intent intent = new Intent(c, BlockerService.class);
        try { c.startForegroundService(intent); }
        catch (RuntimeException ignored) {
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

    private void promoteForeground() {
        if (foregroundStarted) return;
        Notification notification = Notices.sessionNotification(this);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notices.SESSION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(Notices.SESSION, notification);
            }
            foregroundStarted = true;
        } catch (RuntimeException ignored) {
            Notices.session(this);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        instance = this;
        promoteForeground();
        beginSession();
        return State.active(this) ? START_STICKY : START_NOT_STICKY;
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        if (State.active(this)) {
            Notices.sessionAlarm(this);
            Notices.keepAlive(this);
        }
        super.onTaskRemoved(rootIntent);
    }

    void beginSession() {
        instance = this;
        State.restoreClock(this);
        boolean starting = State.pending(this) || State.prefs(this).getBoolean("justActivated", false);
        if (State.pending(this)) {
            State.activate(this);
            if (!State.active(this)) {
                android.widget.Toast.makeText(
                    this,
                    "Session cancelled. Android did not confirm uninstall protection.",
                    android.widget.Toast.LENGTH_LONG
                ).show();
            }
        }
        State.prefs(this).edit().remove("justActivated").commit();
        if (!State.active(this)) {
            if (State.expired(this)) State.completed(this);
            shutdown();
            return;
        }
        BlockingAccess.prepare(this);
        routes = new GuardRoutes(this);
        selected = State.selected(this);
        promoteForeground();
        Notices.sessionAlarm(this);
        handler.removeCallbacks(watch);
        scheduleDeadline();
        handler.removeCallbacks(poll);
        handler.post(poll);
        if (starting && !MainActivity.isInForeground()) {
            redirect(false);
        }
    }

    private void checkForeground() {
        if (MainActivity.isInForeground()) {
            currentPkg = getPackageName();
            currentClass = MainActivity.class.getName();
            hideShield();
            return;
        }
        UsageStatsManager usm = getSystemService(UsageStatsManager.class);
        if (usm == null) return;
        long now = System.currentTimeMillis();
        if (now < lastQueryEnd) {
            currentPkg = null;
            currentClass = null;
            currentEventTime = 0;
            lastQueryEnd = 0;
            hideShield();
        }
        long since = lastQueryEnd == 0 ? now - 60_000L : Math.max(now - 15_000L, lastQueryEnd - 250L);
        UsageEvents events = usm.queryEvents(since, now);
        if (events == null) {
            hideShield();
            return;
        }
        lastQueryEnd = now;
        UsageEvents.Event event = new UsageEvents.Event();
        String latestPkg = currentPkg;
        String latestClass = currentClass;
        long latestTime = currentEventTime;
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            long ts = event.getTimeStamp();
            if (ts < mainResumedAt) continue;
            int type = event.getEventType();
            if (type == UsageEvents.Event.ACTIVITY_RESUMED && ts >= latestTime) {
                latestPkg = event.getPackageName();
                latestClass = event.getClassName();
                latestTime = ts;
            } else if (type == UsageEvents.Event.ACTIVITY_PAUSED
                    && event.getPackageName() != null
                    && event.getPackageName().equals(latestPkg)
                    && (latestClass == null || latestClass.equals(event.getClassName()))
                    && ts > latestTime) {
                latestPkg = null;
                latestClass = null;
                latestTime = ts;
            }
        }
        currentPkg = latestPkg;
        currentClass = latestClass;
        currentEventTime = latestTime;
        if (currentPkg == null || getPackageName().equals(currentPkg)) {
            hideShield();
            return;
        }
        if (routes == null || !routes.shouldBlock(currentPkg, currentClass, selected)) {
            hideShield();
            return;
        }
        long elapsed = SystemClock.elapsedRealtime() - lastRedirect;
        if (currentEventTime > 0
                && currentEventTime == lastBlockedEventTime
                && currentPkg.equals(lastBlockedPkg)) {
            // Only fall back to the overlay shield if MainActivity failed to come to the front after 550ms.
            if (elapsed >= 550L) showShield();
            return;
        }
        if (elapsed < 400L) return;
        lastBlockedEventTime = currentEventTime;
        lastBlockedPkg = currentPkg;
        redirect(true);
    }

    void onWindowChanged(String pkg, String activity) {
        if (!State.active(this)) {
            if (State.expired(this)) State.completed(this);
            shutdown();
            return;
        }
        if (pkg == null || routes == null || !routes.shouldBlock(pkg, activity, selected)) {
            hideShield();
            return;
        }
        redirect(true);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void showShield() {
        if (!Settings.canDrawOverlays(this) || shieldView != null) return;
        WindowManager wm = getSystemService(WindowManager.class);
        if (wm == null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(28), dp(28), dp(28));
        box.setBackgroundColor(0xff142a25);

        TextView title = new TextView(this);
        title.setText("That can wait. Breathe.");
        title.setTextColor(0xfff4eddd);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView sub = new TextView(this);
        sub.setText(State.clock(State.remaining(this)) + " remaining in your session");
        sub.setTextColor(0xffb2c2b5);
        sub.setTextSize(15);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(10), 0, dp(28));
        shieldClock = sub;
        box.addView(sub);

        Button back = new Button(this);
        back.setText("Back to timer");
        back.setAllCaps(false);
        back.setTextSize(15);
        back.setTextColor(0xff142a25);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xffc3d6ad);
        btnBg.setCornerRadius(dp(18));
        back.setBackground(btnBg);
        back.setPadding(dp(24), dp(14), dp(24), dp(14));
        back.setOnClickListener(v -> {
            currentPkg = getPackageName();
            currentClass = MainActivity.class.getName();
            hideShield();
            lastRedirect = -1000L;
            redirect(true);
        });
        box.addView(back, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE
        );
        params.gravity = Gravity.CENTER;
        try {
            wm.addView(box, params);
            shieldView = box;
        } catch (RuntimeException ignored) { }
    }

    private void hideShield() {
        if (shieldView == null) return;
        WindowManager wm = getSystemService(WindowManager.class);
        if (wm != null) {
            try { wm.removeViewImmediate(shieldView); }
            catch (RuntimeException ignored) {
                try { wm.removeView(shieldView); } catch (RuntimeException ignored2) { }
            }
        }
        shieldView = null;
        shieldClock = null;
    }

    private void redirect(boolean intercepted) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRedirect < 250L) return;
        lastRedirect = now;
        Intent intent = new Intent(this, MainActivity.class)
            .putExtra("blocked", intercepted)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(intent);
        } catch (RuntimeException fallbackToHome) {
            Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(home); } catch (RuntimeException ignored) { }
        }
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
        hideShield();
        foregroundStarted = false;
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
        hideShield();
        foregroundStarted = false;
        try { unregisterReceiver(packages); } catch (IllegalArgumentException ignored) { }
        try { unregisterReceiver(screenState); } catch (IllegalArgumentException ignored) { }
        handler.removeCallbacksAndMessages(null);
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
