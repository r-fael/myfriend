package android.os;

/** Relógio monotônico, como no Android. */
public final class SystemClock {
    private static final long START = System.nanoTime();

    private SystemClock() {
    }

    public static long uptimeMillis() {
        return (System.nanoTime() - START) / 1_000_000L + 10_000L;
    }
}
