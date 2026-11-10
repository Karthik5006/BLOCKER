package app.blocker;

/** Tracks a single uninterrupted foreground touch. No progress is persisted. */
final class ContinuousHold {
    static final long DURATION = 300_000L;
    private long start = -1;
    private long duration=DURATION;
    void duration(long v){if(v!=180000&&v!=300000&&v!=600000)throw new IllegalArgumentException("Invalid hold");cancel();duration=v;}
    long duration(){return duration;}
    boolean holding(){return start>=0;}
    void begin(long now) { start = now; }
    void cancel() { start = -1; }
    long left(long now) {
        if (start < 0) return -1;
        if(now<start){cancel();return -1;}
        return Math.max(0, duration - (now - start));
    }
    boolean takeCompletion(long now) {
        if (left(now) != 0) return false;
        cancel();
        return true;
    }
}
