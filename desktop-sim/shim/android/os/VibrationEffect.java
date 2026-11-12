package android.os;

/** Efeito de vibração: guarda a duração total para o simulador "tremer" a tela. */
public class VibrationEffect {
    public static final int DEFAULT_AMPLITUDE = -1;

    public final long totalMs;

    private VibrationEffect(long totalMs) {
        this.totalMs = totalMs;
    }

    public static VibrationEffect createOneShot(long millis, int amplitude) {
        return new VibrationEffect(millis);
    }

    public static VibrationEffect createWaveform(long[] timings, int repeat) {
        long sum = 0;
        for (long t : timings) {
            sum += t;
        }
        return new VibrationEffect(sum);
    }
}
