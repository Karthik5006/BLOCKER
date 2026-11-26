package app.blocker;

import android.content.*;
import android.os.SystemClock;
import android.provider.Settings;
import java.time.LocalDate;
import java.util.*;

final class State {
    static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("blocker", Context.MODE_PRIVATE);
    }
    static int boot(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }
    static boolean sameBoot(Context c) { return prefs(c).getInt("boot", -2) == boot(c); }
    static boolean pending(Context c) {
        return prefs(c).getBoolean("pending", false) && sameBoot(c) &&
            SystemClock.elapsedRealtime() - prefs(c).getLong("setupAt", 0) < 600_000L;
    }
    static void restoreClock(Context c) {
        if (!prefs(c).getBoolean("active",false)) return;
        if (sameBoot(c)) {
            if(!prefs(c).contains("wallEnd")) {
                long left=Math.max(0,prefs(c).getLong("sessionEnd",0)-SystemClock.elapsedRealtime());
                prefs(c).edit().putLong("wallEnd",System.currentTimeMillis()+left).remove("unlockEnd").commit();
                Notices.sessionAlarm(c);
            }
            return;
        }
        long left=Math.max(0,prefs(c).getLong("wallEnd",0)-System.currentTimeMillis());
        prefs(c).edit().putInt("boot",boot(c)).putLong("sessionEnd",SystemClock.elapsedRealtime()+left).commit();
    }
    static boolean requested(Context c) { return prefs(c).getBoolean("active", false) && sameBoot(c); }
    static long remaining(Context c) {
        return sameBoot(c) ? Math.max(0,prefs(c).getLong("sessionEnd",0)-SystemClock.elapsedRealtime()) : 0;
    }
    static boolean expired(Context c) { return requested(c) && remaining(c) == 0; }
    static boolean active(Context c) { return requested(c) && remaining(c) > 0; }
    static void prepare(Context c, int minutes) {
        if (minutes < 1 || minutes > 1439) throw new IllegalArgumentException("Choose between 1 minute and 23 hours 59 minutes.");
        long hold = effectiveHold(holdDuration(c), minutes * 60_000L);
        prefs(c).edit().putBoolean("pending", true).putBoolean("active", false).putBoolean("scheduledSession", false).putBoolean("sessionRecorded",false).putBoolean("showComplete",false).putBoolean("exitAfterSession",false).putInt("jar",0)
            .putInt("boot",boot(c)).putLong("setupAt",SystemClock.elapsedRealtime())
            .putInt("duration",minutes).putLong("sessionHold",hold).remove("unlockEnd").remove("wallEnd").putLong("sessionEnd",0).commit();
        Notices.sessionAlarm(c);
    }
    static void activate(Context c) {
        if(!pending(c) || !UninstallGuard.acquire(c)) return;
        int minutes = prefs(c).getInt("duration", 25);
        long end=SystemClock.elapsedRealtime()+minutes*60_000L;
        prefs(c).edit().putBoolean("pending",false).putBoolean("active",true).putLong("sessionEnd",end)
            .putLong("wallEnd",System.currentTimeMillis()+minutes*60_000L).commit();
        BlockerNoticeGuard.sweep(c);
    }
    static boolean activateScheduled(Context c, int minutes) {
        if (minutes < 1 || minutes > 1439 || active(c)) return false;
        long hold = effectiveHold(holdDuration(c), minutes * 60_000L);
        long end = SystemClock.elapsedRealtime() + minutes * 60_000L;
        prefs(c).edit().putBoolean("pending", false).putBoolean("active", true).putBoolean("scheduledSession", true)
            .putBoolean("sessionRecorded", false).putBoolean("showComplete", false).putBoolean("exitAfterSession", false)
            .putInt("boot", boot(c)).putInt("duration", minutes).putLong("sessionHold", hold)
            .putLong("sessionEnd", end).putLong("wallEnd", System.currentTimeMillis() + minutes * 60_000L).commit();
        BlockerNoticeGuard.sweep(c);
        return true;
    }
    static void stop(Context c) {
        prefs(c).edit().putBoolean("active",false).putBoolean("pending",false).putBoolean("scheduledSession",false)
            .remove("unlockEnd").remove("wallEnd").putLong("sessionEnd",0).commit();
    }
    static long holdDuration(Context c){int m=prefs(c).getInt("holdMinutes",5);return (m==3||m==10?m:5)*60000L;}
    static long effectiveHold(long configuredMs, long windowMs) {
        if (windowMs <= 0 || windowMs > configuredMs) return configuredMs;
        long half = ((windowMs / 2L) / 5_000L) * 5_000L;
        return Math.max(10_000L, Math.min(configuredMs, half));
    }
    static long sessionHold(Context c){
        long base = prefs(c).getLong("sessionHold", 300_000L);
        if (base < 10_000L || base > 600_000L) base = 300_000L;
        return active(c) ? effectiveHold(base, remaining(c)) : base;
    }
    static String holdLabel(long ms) {
        long sec = Math.max(10L, Math.round(ms / 1000.0));
        if (sec < 60L) return sec + " seconds";
        long min = sec / 60L, rem = sec % 60L;
        if (rem == 0L) return min + (min == 1L ? " minute" : " minutes");
        return min + " min " + rem + " sec";
    }
    static boolean scheduleEnabled(Context c) { return prefs(c).getBoolean("schedOn", false); }
    static int scheduleMinutes(Context c) {
        int sh = prefs(c).getInt("schedStartH", 22), sm = prefs(c).getInt("schedStartM", 0);
        int eh = prefs(c).getInt("schedEndH", 7), em = prefs(c).getInt("schedEndM", 0);
        int start = sh * 60 + sm, end = eh * 60 + em;
        int diff = end > start ? (end - start) : (1440 - start + end);
        return Math.max(1, Math.min(1439, diff));
    }
    static String scheduleLabel(Context c) {
        if (!scheduleEnabled(c)) return "Off";
        return String.format(Locale.getDefault(), "%02d:%02d – %02d:%02d",
            prefs(c).getInt("schedStartH", 22), prefs(c).getInt("schedStartM", 0),
            prefs(c).getInt("schedEndH", 7), prefs(c).getInt("schedEndM", 0));
    }
    static void completed(Context c){
        if(!prefs(c).getBoolean("active",false)||prefs(c).getBoolean("sessionRecorded",false))return;
        String key="sessions_"+LocalDate.now();prefs(c).edit().putInt(key,prefs(c).getInt(key,0)+1).putBoolean("showComplete",true).putBoolean("sessionRecorded",true).commit();
    }
    static Set<String> selected(Context c) { return new HashSet<>(prefs(c).getStringSet("apps",Collections.emptySet())); }
    static void select(Context c,Set<String> set) { prefs(c).edit().putStringSet("apps",new HashSet<>(set)).apply(); }
    static String reason(Context c) { return prefs(c).getString("reason","I want my evenings back."); }
    static String step(Context c) { return prefs(c).getString("step","Charge my phone in the kitchen."); }
    static String clock(long ms) { return Deadline.display(ms,false); }
    static int[] week(Context c) {
        int free=0,logged=0;
        for(int i=0;i<7;i++) {
            String value=prefs(c).getString("day_"+LocalDate.now().minusDays(i),"");
            if(!value.isEmpty()) logged++;
            if(value.equals("free")) free++;
        }
        return new int[]{free,logged};
    }
}
