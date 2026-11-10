package app.blocker;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.util.Calendar;

final class Notices {
    private static PendingIntent endIntent(Context c) {
        return PendingIntent.getBroadcast(c,4,new Intent(c,ActionReceiver.class).setAction("session-end"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void sessionAlarm(Context c) {
        long end;
        if(State.pending(c)) end=State.prefs(c).getLong("setupAt",0)+600_000L;
        else if(State.active(c)) end=android.os.SystemClock.elapsedRealtime()+State.remaining(c);
        else return;
        c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,end,endIntent(c));
    }
    static void cancelSessionAlarm(Context c) { c.getSystemService(AlarmManager.class).cancel(endIntent(c)); }

    static final int SESSION = 40;
    static void channels(Context c) {
        NotificationManager n = c.getSystemService(NotificationManager.class);
        n.createNotificationChannel(new NotificationChannel("session", "Active blocking", NotificationManager.IMPORTANCE_LOW));
        n.createNotificationChannel(new NotificationChannel("plan", "My daily plan", NotificationManager.IMPORTANCE_DEFAULT));
    }
    static void session(Context c) {
        channels(c);
        PendingIntent open = PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(c, "session")
            .setSmallIcon(app.blocker.R.drawable.ic_leaf).setContentTitle("A little space is protected")
            .setContentText("Your selected apps can wait. Open your quiet timer.").setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setOngoing(true)
            .setOnlyAlertOnce(true).setShowWhen(false).build();
        try { c.getSystemService(NotificationManager.class).notify(SESSION, notification); }
        catch (SecurityException ignored) { }
    }
    static void schedule(Context c) {
        PendingIntent reminder = PendingIntent.getBroadcast(c, 3, new Intent(c, ActionReceiver.class).setAction("remind"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm = c.getSystemService(AlarmManager.class);
        alarm.cancel(reminder);
        if (!State.prefs(c).getBoolean("remind", false)||State.prefs(c).getBoolean("alertsPaused",false)) return;
        Calendar time = Calendar.getInstance();
        time.set(Calendar.HOUR_OF_DAY, State.prefs(c).getInt("hour", 22));
        time.set(Calendar.MINUTE, State.prefs(c).getInt("minute", 30));
        time.set(Calendar.SECOND, 0);
        time.set(Calendar.MILLISECOND, 0);
        if (time.getTimeInMillis() <= System.currentTimeMillis()) time.add(Calendar.DATE, 1);
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(), reminder);
    }
    static void remind(Context c) {
        if (!State.prefs(c).getBoolean("remind", false)||State.prefs(c).getBoolean("alertsPaused",false)) return;
        channels(c);
        PendingIntent open = PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            c.getSystemService(NotificationManager.class).notify(41, new Notification.Builder(c, "plan")
                .setSmallIcon(app.blocker.R.drawable.ic_leaf).setContentTitle("Time for your plan")
                .setContentText("Take a little space for yourself.").setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).build());
        } catch (SecurityException ignored) { }
        schedule(c);
    }
    static void quietExit(Context c){
        State.prefs(c).edit().putBoolean("alertsPaused",true).apply();
        cancelSessionAlarm(c);schedule(c);c.getSystemService(NotificationManager.class).cancelAll();
    }
}
