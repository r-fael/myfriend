package android.graphics;

/** Imitação mínima do Paint do Android (só cor). */
public class Paint {
    public enum Style { FILL, STROKE, FILL_AND_STROKE }

    private int color = 0xFF000000;

    public void setColor(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    public void setAntiAlias(boolean antiAlias) {
    }

    public void setStyle(Style style) {
    }
}
