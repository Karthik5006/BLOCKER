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
    private long lastRedirect = -1000L;
    private long lastBlockedEventTime = -1L;
    private String lastBlockedPkg;
    private String currentPkg;
    private String currentClass;
    private long currentEventTime;
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
            String action = i.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                hideShield();
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
            if (State.active(c)) {
                State.prefs(c).edit().putBoolean("justActivated", true).commit();
            }
        }
        if (instance != null) {
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

    static void onMainActivityResumed() {
        if (instance != null) {
            instance.currentPkg = instance.getPackageName();
            instance.currentClass = MainActivity.class.getName();
            instance.hideShield();
        }
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
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
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
        long lookback = currentPkg == null ? 60_000L : 15_000L;
        UsageEvents events = usm.queryEvents(now - lookback, now);
        UsageEvents.Event event = new UsageEvents.Event();
        String latestPkg = null;
        String latestClass = null;
        long latestTime = -1L;
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                latestPkg = event.getPackageName();
                latestClass = event.getClassName();
                latestTime = event.getTimeStamp();
            }
        }
        if (latestPkg != null) {
            currentPkg = latestPkg;
            currentClass = latestClass;
            currentEventTime = latestTime;
        }
        if (currentPkg == null || getPackageName().equals(currentPkg)) {
            hideShield();
            return;
        }
        if (routes == null || !routes.shouldBlock(true, currentPkg, currentClass, selected)) {
            hideShield();
            return;
        }
        // Immediately cover the blocked app or uninstall/settings screen with the persistent shield
        // so it cannot be interacted with even if OEM background activity launch rules delay MainActivity.
        showShield();
        long elapsed = SystemClock.elapsedRealtime() - lastRedirect;
        if (currentEventTime > 0
                && currentEventTime == lastBlockedEventTime
                && currentPkg.equals(lastBlockedPkg)) {
            return;
        }
        if (elapsed < 500L) {
            return;
        }
        lastBlockedEventTime = currentEventTime;
        lastBlockedPkg = currentPkg;
        onWindowChanged(currentPkg, currentClass);
    }

    void onWindowChanged(String pkg, String activity) {
        if (!State.active(this)) {
            if (State.expired(this)) State.completed(this);
            shutdown();
            return;
        }
        if (pkg == null) return;
        if (routes == null || !routes.shouldBlock(true, pkg, activity, selected)) {
            hideShield();
            return;
        }
        showShield();
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
            lastRedirect = -1000L;
            redirect(true);
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        box.addView(back, bp);

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
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
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
        try { unregisterReceiver(packages); } catch (IllegalArgumentException ignored) { }
        try { unregisterReceiver(screenState); } catch (IllegalArgumentException ignored) { }
        handler.removeCallbacksAndMessages(null);
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
