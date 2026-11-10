package app.blocker;

/** Elapsed-realtime deadlines, independent of changes to the wall clock. */
final class Deadline {
    static String display(long millis, boolean hours) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        if (hours) return String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", seconds/3600, seconds/60%60, seconds%60);
        return String.format(java.util.Locale.getDefault(), "%d:%02d", seconds/60, seconds%60);
    }
}
