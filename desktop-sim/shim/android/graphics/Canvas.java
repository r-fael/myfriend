package android.graphics;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;

/** Imitação mínima do Canvas do Android, desenhando com Java2D (só para o simulador de PC). */
public class Canvas {
    private final Graphics2D g;
    private final int width;
    private final int height;
    private final ArrayDeque<AffineTransform> stack = new ArrayDeque<>();

    public Canvas(Graphics2D g, int width, int height) {
        this.g = g;
        this.width = width;
        this.height = height;
    }

    public void drawColor(int color) {
        AffineTransform saved = g.getTransform();
        g.setTransform(new AffineTransform());
        g.setColor(new Color(color, true));
        g.fillRect(0, 0, width, height);
        g.setTransform(saved);
    }

    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        g.setColor(new Color(paint.getColor(), true));
        g.fill(new Rectangle2D.Float(left, top, right - left, bottom - top));
    }

    public int save() {
        stack.push(g.getTransform());
        return stack.size();
    }

    public void scale(float sx, float sy, float px, float py) {
        g.translate(px, py);
        g.scale(sx, sy);
        g.translate(-px, -py);
    }

    public void translate(float dx, float dy) {
        g.translate(dx, dy);
    }

    public void restore() {
        if (!stack.isEmpty()) {
            g.setTransform(stack.pop());
        }
    }
}
