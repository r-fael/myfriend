package android.os;

/** Vibração simulada: o simulador treme a janelinha enquanto "vibra". */
public class Vibrator {
    public volatile long vibrateUntil = 0L;

    public boolean hasVibrator() {
        return true;
    }

    public void vibrate(VibrationEffect effect) {
        vibrateUntil = System.currentTimeMillis() + Math.min(400L, effect.totalMs);
    }
}
