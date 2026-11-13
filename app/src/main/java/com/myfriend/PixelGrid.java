package com.myfriend;

import static com.myfriend.PetConfig.GRID_SIZE;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Grade virtual de 52x52 "pixels" estilo LCD com paleta de cores e primitivas otimizadas.
 */
public final class PixelGrid {

    // ---------------------------------------------------------------- Sprites 5x5 / 3x5 (bits por linha)

    public static final int[] SPRITE_HEART = {10, 31, 31, 14, 4};
    public static final int[] SPRITE_BOLT = {6, 12, 31, 6, 12};
    public static final int[] SPRITE_MOON = {14, 24, 24, 24, 14};
    public static final int[] SPRITE_Z = {31, 2, 4, 8, 31};
    public static final int[] SPRITE_SPARKLE = {4, 14, 27, 14, 4};
    public static final int[] SPRITE_WATER_DROP = {4, 4, 14, 31, 14};
    public static final int[] SPRITE_SHOP_CROWN = {21, 31, 31, 31, 0};

    // Abas da loja
    public static final int[] SPRITE_TAB_CLOSE = {17, 10, 4, 10, 17};
    public static final int[] SPRITE_TAB_HAT = {14, 14, 14, 31, 0};
    public static final int[] SPRITE_TAB_NOSE = {14, 31, 31, 31, 14};
    public static final int[] SPRITE_TAB_EYE = {0, 14, 27, 14, 0};
    public static final int[] SPRITE_TAB_NECK = {17, 27, 31, 27, 17};
    public static final int[] SPRITE_TAB_BG = {4, 14, 31, 14, 10};
    /** Aba "segurar": espadinha na diagonal. */
    public static final int[] SPRITE_TAB_HOLD = {1, 2, 20, 8, 20};
    /** Tênis (passos do dia). */
    /** Marca de bravo (estilo mangá). */
    public static final int[] SPRITE_ANGER = {10, 27, 0, 27, 10};
    /** Olho de espiral (tonto), dois quadros. */
    public static final int[] SPRITE_SPIRAL_A = {31, 1, 13, 9, 15};
    public static final int[] SPRITE_SPIRAL_B = {31, 16, 22, 18, 30};

    private static final int[][] DIGITS_3X5 = {
            {7, 5, 5, 5, 7}, {2, 6, 2, 2, 7}, {7, 1, 7, 4, 7}, {7, 1, 7, 1, 7}, {5, 5, 7, 1, 1},
            {7, 4, 7, 1, 7}, {7, 4, 7, 5, 7}, {7, 1, 1, 1, 1}, {7, 5, 7, 5, 7}, {7, 5, 7, 1, 7},
    };

    // ---------------------------------------------------------------- Paleta de Cores

    public final Paint whitePaint = makePaint(0xFFFFFFFF);
    public final Paint dimPaint = makePaint(0xFF444444);
    public final Paint ringTrackPaint = makePaint(0xFF1C1C1C);
    // Cores do rostinho.
    // Coisas para segurar.
    public final Paint silverPaint = makePaint(0xFFCFD8DC);
    public final Paint stemPaint = makePaint(0xFF2E9E5B);
    public final Paint woodPaint = makePaint(0xFF5D4037);
    public final Paint conePaint = makePaint(0xFFD69A4E);
    public final Paint glassPaint = makePaint(0xFF90A4AE);
    // Fundos (bem apagadinhos para quase não gastar bateria no OLED).
    public final Paint starDimPaint = makePaint(0xFF4A4426);
    public final Paint starBrightPaint = makePaint(0xFFB8A64A);
    public final Paint rainPaint = makePaint(0xFF1F4F66);
    public final Paint snowPaint = makePaint(0xFF6E7C84);
    // De costas e modo tela desligada.
    public final Paint backHeadPaint = makePaint(0xFF2A2A2A);
    public final Paint ambientPaint = makePaint(0xFF8A8A8A);
    public final Paint ambientDimPaint = makePaint(0xFF505050);
    // Bonés de encanador e disfarce.
    public final Paint capRedPaint = makePaint(0xFFE53935);
    public final Paint capRedDarkPaint = makePaint(0xFFB71C1C);
    public final Paint capGreenPaint = makePaint(0xFF2ECC40);
    public final Paint capGreenDarkPaint = makePaint(0xFF1B7A2A);
    public final Paint skinPaint = makePaint(0xFFFFC7A8);
    public final Paint skinShadePaint = makePaint(0xFFE89A7A);
    public final Paint bushyBrowPaint = makePaint(0xFF6D4C41);
    public final Paint cyanPaint = makePaint(0xFF00E5FF);
    public final Paint deepBluePaint = makePaint(0xFF0091EA);
    public final Paint pinkPaint = makePaint(0xFFFF4081);
    public final Paint blushPaint = makePaint(0xFFFF80AB);
    public final Paint goldPaint = makePaint(0xFFFFD740);
    public final Paint redPaint = makePaint(0xFFFF5252);
    public final Paint greenPaint = makePaint(0xFF69F0AE);
    public final Paint orangePaint = makePaint(0xFFFFAB40);
    public final Paint lavenderPaint = makePaint(0xFF82B1FF);
    public final Paint purplePaint = makePaint(0xFFB388FF);
    public final Paint brownPaint = makePaint(0xFF8D6E63);
    public final Paint clownRedPaint = makePaint(0xFFFF1744);
    public final Paint clownHairPaint = makePaint(0xFFFF6D00);
    public final Paint blackPaint = makePaint(0xFF000000);

    // Tapa-olho: borda e tira cinza-claro para aparecer no fundo preto.
    public final Paint patchPaint = makePaint(0xFF9E9E9E);
    public final Paint patchStrapPaint = makePaint(0xFF9E9E9E);

    // ---------------------------------------------------------------- Métricas

    private float cellSize = 8f;
    private float originX = 0f;
    private float originY = 0f;
    private float cellDrawSize = 7f;

    private static Paint makePaint(int color) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(false);
        return paint;
    }

    public void updateMetrics(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        cellSize = Math.min(width, height) / (float) GRID_SIZE;
        originX = (width - cellSize * GRID_SIZE) * 0.5f;
        originY = (height - cellSize * GRID_SIZE) * 0.5f;
        // Deixa um vãozinho entre os pixels para dar cara de tela de LCD.
        cellDrawSize = cellSize - (cellSize > 5f ? 1.2f : 0.6f);
    }

    public void px(Canvas canvas, int gx, int gy, Paint paint) {
        float left = originX + gx * cellSize;
        float top = originY + gy * cellSize;
        canvas.drawRect(left, top, left + cellDrawSize, top + cellDrawSize, paint);
    }

    /** Pixel cheio (sem o vãozinho), usado para "apagar" com preto. */
    public void pxSolid(Canvas canvas, int gx, int gy, Paint paint) {
        float left = originX + gx * cellSize;
        float top = originY + gy * cellSize;
        canvas.drawRect(left, top, left + cellSize, top + cellSize, paint);
    }

    /** Bloco contínuo sem vão (1 única draw call na GPU), usado na pupila dos olhos. */
    public void rectSolid(Canvas canvas, int x0, int y0, int x1, int y1, Paint paint) {
        float left = originX + x0 * cellSize;
        float top = originY + y0 * cellSize;
        float right = originX + (x1 + 1) * cellSize;
        float bottom = originY + (y1 + 1) * cellSize;
        canvas.drawRect(left, top, right, bottom, paint);
    }

    /** Linha vertical de pixels (y0 até y1, inclusive). */
    public void vline(Canvas canvas, int x, int y0, int y1, Paint paint) {
        for (int y = y0; y <= y1; y++) {
            px(canvas, x, y, paint);
        }
    }

    public void hline(Canvas canvas, int x0, int x1, int y, Paint paint) {
        float top = originY + y * cellSize;
        float bottom = top + cellDrawSize;
        float left = originX + x0 * cellSize;
        float size = cellDrawSize;
        float step = cellSize;
        for (int x = x0; x <= x1; x++) {
            canvas.drawRect(left, top, left + size, bottom, paint);
            left += step;
        }
    }

    public void rectPx(Canvas canvas, int x0, int y0, int x1, int y1, Paint paint) {
        for (int y = y0; y <= y1; y++) {
            hline(canvas, x0, x1, y, paint);
        }
    }

    public void drawSprite5x5(Canvas canvas, int x, int y, int[] rows, Paint paint) {
        for (int row = 0; row < 5; row++) {
            int rowBits = rows[row];
            if (rowBits == 0) {
                continue;
            }
            int gy = y + row;
            for (int col = 0; col < 5; col++) {
                if (((rowBits >> (4 - col)) & 1) != 0) {
                    px(canvas, x + col, gy, paint);
                }
            }
        }
    }

    public void drawDigit3x5(Canvas canvas, int x, int y, int digit, Paint paint) {
        if (digit < 0 || digit > 9) {
            return;
        }
        int[] rows = DIGITS_3X5[digit];
        for (int row = 0; row < 5; row++) {
            int rowBits = rows[row];
            int gy = y + row;
            for (int col = 0; col < 3; col++) {
                if (((rowBits >> (2 - col)) & 1) != 0) {
                    px(canvas, x + col, gy, paint);
                }
            }
        }
    }

    public void drawNumber3x5(Canvas canvas, int x, int y, int number, Paint paint) {
        int n = Math.max(0, Math.min(999, number));
        if (n >= 100) {
            drawDigit3x5(canvas, x, y, n / 100, paint);
            drawDigit3x5(canvas, x + 4, y, (n / 10) % 10, paint);
            drawDigit3x5(canvas, x + 8, y, n % 10, paint);
        } else if (n >= 10) {
            drawDigit3x5(canvas, x, y, n / 10, paint);
            drawDigit3x5(canvas, x + 4, y, n % 10, paint);
        } else {
            drawDigit3x5(canvas, x, y, n, paint);
        }
    }
}
