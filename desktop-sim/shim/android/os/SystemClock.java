package android.os;

/** Relógio monotônico, como no Android. */
public final class SystemClock {
    private static final long START = System.nanoTime();
    /** Relógio virtual para gravações quadro a quadro (-1 = tempo real). */
    public static volatile long fixedMs = -1L;

    private SystemClock() {
    }

    public static long uptimeMillis() {
        if (fixedMs >= 0L) {
            return fixedMs;
        }
        return (System.nanoTime() - START) / 1_000_000L + 10_000L;
    }
}
