package com.myfriend;

import static com.myfriend.PetConfig.*;
import static com.myfriend.PixelGrid.*;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.TimeZone;

/**
 * Desenha os acessórios da loja, objetos de cena (maçã, copo d'água, cocô) e indicadores do HUD.
 */
public final class PetAccessoryRenderer {

    /** Geometria pré-alocada dos tufos de cabelo do palhaço {dy, xMin, xMax}. */
    private static final int[][] CLOWN_TUFT = {
            {-12, -18, -15},
            {-11, -20, -14},
            {-10, -21, -13},
            {-9, -22, -13},
            {-8, -22, -13},
            {-7, -21, -13},
            {-6, -20, -14},
            {-5, -18, -15},
    };

    private final PixelGrid g;
    private final TimeZone localTz = TimeZone.getDefault();

    public PetAccessoryRenderer(PixelGrid grid) {
        this.g = grid;
    }

    // ---------------------------------------------------------------- Objetos e HUD

    public void drawClockHHMM(Canvas canvas, int y, long now, Paint paint) {
        long wall = System.currentTimeMillis();
        int minuteOfDay = (int) (((wall + localTz.getOffset(wall)) / 60_000L) % 1440L);
        if (minuteOfDay < 0) {
            minuteOfDay += 1440;
        }
        int hours = minuteOfDay / 60;
        int minutes = minuteOfDay % 60;
        g.drawDigit3x5(canvas, 13, y, hours / 10, paint);
        g.drawDigit3x5(canvas, 17, y, hours % 10, paint);
        if ((now / 500L) % 2L == 0L) {
            g.px(canvas, 25, y + 1, g.cyanPaint);
            g.px(canvas, 25, y + 3, g.cyanPaint);
        }
        g.drawDigit3x5(canvas, 31, y, minutes / 10, paint);
        g.drawDigit3x5(canvas, 35, y, minutes % 10, paint);
    }

    public void drawWaterCup(Canvas canvas, int x, int y, int level) {
        for (int row = 0; row < 5; row++) {
            g.px(canvas, x, y + row, g.whitePaint);
            g.px(canvas, x + 4, y + row, g.whitePaint);
        }
        g.hline(canvas, x + 1, x + 3, y + 5, g.whitePaint);
        int fill = Math.max(0, Math.min(4, level));
        for (int i = 0; i < fill; i++) {
            g.hline(canvas, x + 1, x + 3, y + 4 - i, i == fill - 1 ? g.cyanPaint : g.deepBluePaint);
        }
    }

    public void drawApple(Canvas canvas, int x, int y) {
        g.px(canvas, x + 2, y, g.greenPaint);
        g.px(canvas, x + 3, y, g.greenPaint);
        g.hline(canvas, x + 1, x + 3, y + 1, g.redPaint);
        g.hline(canvas, x, x + 4, y + 2, g.redPaint);
        g.hline(canvas, x, x + 4, y + 3, g.redPaint);
        g.hline(canvas, x + 1, x + 3, y + 4, g.redPaint);
        g.px(canvas, x + 1, y + 2, g.whitePaint);
    }

    public void drawPoop(Canvas canvas, int x, int y, long now) {
        g.px(canvas, x + 2, y, g.brownPaint);
        g.hline(canvas, x + 1, x + 3, y + 1, g.brownPaint);
        g.hline(canvas, x, x + 4, y + 2, g.brownPaint);
        g.hline(canvas, x, x + 4, y + 3, g.brownPaint);
        g.px(canvas, x + 1, y + 2, g.whitePaint);
        g.px(canvas, x + 3, y + 2, g.whitePaint);
        // Cheirinho verde subindo.
        int wiggle = (now / 400L) % 2L == 0L ? 0 : 1;
        g.px(canvas, x + 1 + wiggle, y - 2, g.greenPaint);
        g.px(canvas, x + 3 - wiggle, y - 3, g.greenPaint);
    }

    public void drawBatteryBadge(Canvas canvas, int x, int y, int batteryPct, boolean charging, long now) {
        Paint fillPaint;
        if (charging) {
            fillPaint = g.greenPaint;
        } else if (batteryPct <= 15) {
            fillPaint = (now / 300L) % 2L == 1L ? g.whitePaint : g.redPaint; // pisca
        } else if (batteryPct <= 30) {
            fillPaint = g.orangePaint;
        } else {
            fillPaint = g.greenPaint;
        }

        // Contorno da pilha.
        g.hline(canvas, x, x + 5, y, g.whitePaint);
        g.hline(canvas, x, x + 5, y + 3, g.whitePaint);
        g.px(canvas, x, y + 1, g.whitePaint);
        g.px(canvas, x, y + 2, g.whitePaint);
        g.px(canvas, x + 5, y + 1, g.whitePaint);
        g.px(canvas, x + 5, y + 2, g.whitePaint);
        g.px(canvas, x + 6, y + 1, g.whitePaint);
        g.px(canvas, x + 6, y + 2, g.whitePaint);

        int bars = Math.max(1, Math.round(batteryPct / 100f * 4f));
        for (int i = 0; i < bars; i++) {
            g.px(canvas, x + 1 + i, y + 1, fillPaint);
            g.px(canvas, x + 1 + i, y + 2, fillPaint);
        }

        if (!charging && batteryPct <= 30) {
            // "!" de alerta.
            Paint alert = batteryPct <= 15 ? g.redPaint : g.goldPaint;
            g.px(canvas, x - 2, y, alert);
            g.px(canvas, x - 2, y + 1, alert);
            g.px(canvas, x - 2, y + 3, alert);
        } else if (charging) {
            // Raiozinho.
            g.px(canvas, x - 2, y, g.goldPaint);
            g.px(canvas, x - 3, y + 1, g.goldPaint);
            g.px(canvas, x - 2, y + 1, g.goldPaint);
            g.px(canvas, x - 3, y + 2, g.goldPaint);
        }
    }

    public void drawMiniStatBar(Canvas canvas, int x, int y, float value, Paint paint) {
        int filled = Math.round(value / 100f * 5f);
        for (int i = 0; i < 5; i++) {
            g.px(canvas, x + i, y, i < filled ? paint : g.dimPaint);
        }
    }

    public void drawMoodProps(Canvas canvas, int mood, int lookOffsetX, int offsetY,
                              boolean shopOpen, long actionStartTime, long now) {
        int fastBob = (now / 180L) % 2L == 0L ? 0 : -1;
        int slowBob = (now / 350L) % 2L == 0L ? 0 : -1;
        switch (mood) {
            case MOOD_DRINKING: {
                int level = 4 - Math.min(3, (int) (Math.max(0L, now - actionStartTime) / 550L));
                drawWaterCup(canvas, lookOffsetX + 33, offsetY + 29 + fastBob, level);
                break;
            }
            case MOOD_THIRSTY:
                if (!shopOpen) {
                    drawWaterCup(canvas, 39, 13 + slowBob, 3);
                    g.drawSprite5x5(canvas, 33, 14 + slowBob, SPRITE_WATER_DROP, g.cyanPaint);
                }
                break;
            case MOOD_EATING:
                drawApple(canvas, lookOffsetX + 33, offsetY + 30 + fastBob);
                break;
            case MOOD_HUNGRY:
                if (!shopOpen) {
                    drawApple(canvas, 39, 13 + slowBob);
                }
                break;
            case MOOD_GRUMPY:
                g.drawSprite5x5(canvas, lookOffsetX + 37, offsetY + 11, SPRITE_SPARKLE, g.redPaint);
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------- Acessórios da Loja

    /**
     * @param cx     centro horizontal do rosto
     * @param eyeY   linha central dos olhos
     * @param mouthY linha da boca
     */
    public void drawAccessory(Canvas canvas, int cx, int eyeY, int mouthY, int item) {
        int top = eyeY - 9; // logo acima dos olhos
        int lx = cx - 8;    // centro do olho esquerdo
        int rx = cx + 9;    // centro do olho direito

        switch (item) {
            case ITEM_SANTA_HAT:
                g.hline(canvas, cx - 11, cx + 11, top + 1, g.whitePaint);
                g.hline(canvas, cx - 12, cx + 12, top + 2, g.whitePaint);
                g.hline(canvas, cx - 9, cx + 9, top, g.redPaint);
                g.hline(canvas, cx - 8, cx + 9, top - 1, g.redPaint);
                g.hline(canvas, cx - 6, cx + 10, top - 2, g.redPaint);
                g.hline(canvas, cx - 3, cx + 11, top - 3, g.redPaint);
                g.hline(canvas, cx + 1, cx + 12, top - 4, g.redPaint);
                g.rectPx(canvas, cx + 12, top - 2, cx + 14, top, g.whitePaint); // pompom
                break;

            case ITEM_HALLOWEEN_WITCH:
                g.hline(canvas, cx - 14, cx + 14, top + 2, g.purplePaint);
                g.hline(canvas, cx - 11, cx + 11, top + 1, g.purplePaint);
                g.hline(canvas, cx - 8, cx + 8, top, g.orangePaint);
                g.px(canvas, cx - 1, top, g.goldPaint);
                g.px(canvas, cx, top, g.goldPaint);
                g.px(canvas, cx + 1, top, g.goldPaint);
                g.hline(canvas, cx - 7, cx + 7, top - 1, g.purplePaint);
                g.hline(canvas, cx - 5, cx + 5, top - 2, g.purplePaint);
                g.hline(canvas, cx - 3, cx + 4, top - 3, g.purplePaint);
                g.hline(canvas, cx - 1, cx + 5, top - 4, g.purplePaint);
                g.hline(canvas, cx + 2, cx + 6, top - 5, g.purplePaint);
                break;

            case ITEM_HALLOWEEN_PUMPKIN:
                g.hline(canvas, cx - 1, cx + 1, top - 5, g.greenPaint);
                g.px(canvas, cx, top - 6, g.greenPaint);
                g.hline(canvas, cx - 6, cx + 6, top - 4, g.orangePaint);
                g.rectPx(canvas, cx - 8, top - 3, cx + 8, top + 1, g.orangePaint);
                g.hline(canvas, cx - 6, cx + 6, top + 2, g.orangePaint);
                g.px(canvas, cx - 4, top - 2, g.goldPaint);
                g.px(canvas, cx - 3, top - 2, g.goldPaint);
                g.px(canvas, cx + 3, top - 2, g.goldPaint);
                g.px(canvas, cx + 4, top - 2, g.goldPaint);
                g.hline(canvas, cx - 3, cx + 3, top, g.goldPaint);
                break;

            case ITEM_DEVIL_HORNS:
                g.hline(canvas, cx - 10, cx - 7, top + 1, g.redPaint);
                g.hline(canvas, cx - 11, cx - 8, top, g.redPaint);
                g.hline(canvas, cx - 12, cx - 9, top - 1, g.redPaint);
                g.hline(canvas, cx - 12, cx - 10, top - 2, g.redPaint);
                g.px(canvas, cx - 11, top - 3, g.redPaint);
                g.hline(canvas, cx + 7, cx + 10, top + 1, g.redPaint);
                g.hline(canvas, cx + 8, cx + 11, top, g.redPaint);
                g.hline(canvas, cx + 9, cx + 12, top - 1, g.redPaint);
                g.hline(canvas, cx + 10, cx + 12, top - 2, g.redPaint);
                g.px(canvas, cx + 11, top - 3, g.redPaint);
                break;

            case ITEM_SUNGLASSES:
                g.hline(canvas, lx - 6, rx + 6, eyeY - 3, g.whitePaint);
                drawSunglassLens(canvas, lx, eyeY);
                drawSunglassLens(canvas, rx, eyeY);
                break;

            case ITEM_CROWN:
                g.hline(canvas, cx - 8, cx + 8, top + 1, g.goldPaint);
                g.hline(canvas, cx - 8, cx + 8, top, g.goldPaint);
                g.hline(canvas, cx - 8, cx + 8, top - 1, g.goldPaint);
                g.rectPx(canvas, cx - 8, top - 4, cx - 6, top - 2, g.goldPaint);
                g.rectPx(canvas, cx - 1, top - 5, cx + 1, top - 2, g.goldPaint);
                g.rectPx(canvas, cx + 6, top - 4, cx + 8, top - 2, g.goldPaint);
                g.px(canvas, cx, top, g.redPaint);
                g.px(canvas, cx - 5, top, g.cyanPaint);
                g.px(canvas, cx + 5, top, g.cyanPaint);
                break;

            case ITEM_WIZARD_HAT:
                g.hline(canvas, cx - 13, cx + 13, top + 2, g.deepBluePaint);
                g.hline(canvas, cx - 9, cx + 9, top + 1, g.deepBluePaint);
                g.hline(canvas, cx - 7, cx + 7, top, g.deepBluePaint);
                g.hline(canvas, cx - 5, cx + 5, top - 1, g.deepBluePaint);
                g.hline(canvas, cx - 4, cx + 4, top - 2, g.deepBluePaint);
                g.hline(canvas, cx - 2, cx + 3, top - 3, g.deepBluePaint);
                g.hline(canvas, cx, cx + 3, top - 4, g.deepBluePaint);
                g.px(canvas, cx - 3, top, g.goldPaint);
                g.px(canvas, cx + 2, top - 2, g.goldPaint);
                g.px(canvas, cx + 4, top + 1, g.goldPaint);
                break;

            case ITEM_BOWTIE: {
                int by = mouthY + 6;
                g.rectPx(canvas, cx - 5, by - 1, cx - 2, by + 1, g.redPaint);
                g.rectPx(canvas, cx + 2, by - 1, cx + 5, by + 1, g.redPaint);
                g.px(canvas, cx - 5, by - 2, g.redPaint);
                g.px(canvas, cx - 5, by + 2, g.redPaint);
                g.px(canvas, cx + 5, by - 2, g.redPaint);
                g.px(canvas, cx + 5, by + 2, g.redPaint);
                g.rectPx(canvas, cx - 1, by - 1, cx + 1, by + 1, g.whitePaint);
                break;
            }

            case ITEM_HEADPHONES:
                g.hline(canvas, cx - 14, cx + 14, top, g.cyanPaint);
                g.hline(canvas, cx - 15, cx - 13, top + 1, g.cyanPaint);
                g.hline(canvas, cx + 13, cx + 15, top + 1, g.cyanPaint);
                g.rectPx(canvas, cx - 17, eyeY - 4, cx - 14, eyeY + 3, g.cyanPaint);
                g.rectPx(canvas, cx + 14, eyeY - 4, cx + 17, eyeY + 3, g.cyanPaint);
                g.rectPx(canvas, cx - 16, eyeY - 2, cx - 15, eyeY + 1, g.whitePaint);
                g.rectPx(canvas, cx + 15, eyeY - 2, cx + 16, eyeY + 1, g.whitePaint);
                break;

            case ITEM_HEADBAND:
                g.hline(canvas, cx - 12, cx + 12, top + 1, g.redPaint);
                g.hline(canvas, cx - 12, cx + 12, top + 2, g.redPaint);
                g.px(canvas, cx, top + 1, g.whitePaint);
                g.px(canvas, cx + 13, top + 2, g.redPaint);
                g.px(canvas, cx + 14, top + 3, g.redPaint);
                g.px(canvas, cx + 13, top + 4, g.redPaint);
                break;

            case ITEM_EASTER_BUNNY:
                g.rectPx(canvas, cx - 8, top - 7, cx - 4, top + 1, g.whitePaint);
                g.hline(canvas, cx - 7, cx - 5, top - 8, g.whitePaint);
                g.rectPx(canvas, cx - 7, top - 6, cx - 5, top, g.blushPaint);
                g.rectPx(canvas, cx + 4, top - 6, cx + 8, top + 1, g.whitePaint);
                g.rectPx(canvas, cx + 5, top - 5, cx + 7, top, g.blushPaint);
                g.hline(canvas, cx + 6, cx + 10, top - 7, g.whitePaint); // orelha dobrada
                g.hline(canvas, cx + 8, cx + 11, top - 6, g.whitePaint);
                g.rectPx(canvas, cx + 9, top - 5, cx + 11, top - 4, g.whitePaint);
                break;

            case ITEM_BIRTHDAY_HAT:
                g.hline(canvas, cx - 7, cx + 7, top + 1, g.cyanPaint);
                g.hline(canvas, cx - 6, cx + 6, top, g.pinkPaint);
                g.hline(canvas, cx - 5, cx + 5, top - 1, g.goldPaint);
                g.hline(canvas, cx - 4, cx + 4, top - 2, g.cyanPaint);
                g.hline(canvas, cx - 3, cx + 3, top - 3, g.pinkPaint);
                g.hline(canvas, cx - 2, cx + 2, top - 4, g.goldPaint);
                g.hline(canvas, cx - 1, cx + 1, top - 5, g.cyanPaint);
                g.rectPx(canvas, cx - 1, top - 7, cx + 1, top - 6, g.whitePaint);
                g.px(canvas, cx, top - 8, g.goldPaint);
                break;

            case ITEM_CARNIVAL:
                g.rectPx(canvas, cx - 1, top - 7, cx + 1, top, g.pinkPaint);
                g.px(canvas, cx, top - 8, g.goldPaint);
                g.hline(canvas, cx - 6, cx - 3, top - 4, g.cyanPaint);
                g.hline(canvas, cx - 8, cx - 4, top - 3, g.cyanPaint);
                g.hline(canvas, cx - 7, cx - 3, top - 2, g.cyanPaint);
                g.hline(canvas, cx + 3, cx + 6, top - 4, g.greenPaint);
                g.hline(canvas, cx + 4, cx + 8, top - 3, g.greenPaint);
                g.hline(canvas, cx + 3, cx + 7, top - 2, g.greenPaint);
                g.hline(canvas, cx - 9, cx + 9, top + 1, g.goldPaint);
                g.px(canvas, cx, top + 1, g.purplePaint);
                g.px(canvas, cx - 4, top + 1, g.cyanPaint);
                g.px(canvas, cx + 4, top + 1, g.pinkPaint);
                break;

            case ITEM_VALENTINE_HALO:
                g.hline(canvas, cx - 8, cx + 8, top - 3, g.goldPaint);
                g.hline(canvas, cx - 10, cx - 8, top - 2, g.goldPaint);
                g.hline(canvas, cx + 8, cx + 10, top - 2, g.goldPaint);
                g.hline(canvas, cx - 8, cx + 8, top - 1, g.goldPaint);
                g.drawSprite5x5(canvas, cx - 2, top - 8, SPRITE_HEART, g.pinkPaint);
                break;

            case ITEM_FESTA_JUNINA:
                g.hline(canvas, cx - 14, cx + 14, top + 2, g.goldPaint);
                g.px(canvas, cx - 15, top + 1, g.orangePaint);
                g.px(canvas, cx + 15, top + 1, g.orangePaint);
                g.hline(canvas, cx - 12, cx + 12, top + 1, g.orangePaint);
                for (int x = cx - 8; x <= cx + 8; x++) {
                    g.px(canvas, x, top, (x & 1) == 0 ? g.redPaint : g.cyanPaint); // fita xadrez
                }
                g.hline(canvas, cx - 8, cx + 8, top - 1, g.goldPaint);
                g.hline(canvas, cx - 8, cx + 8, top - 2, g.orangePaint);
                g.hline(canvas, cx - 7, cx + 7, top - 3, g.goldPaint);
                g.hline(canvas, cx - 5, cx + 5, top - 4, g.goldPaint);
                break;

            case ITEM_NEW_YEAR_STARS:
                g.hline(canvas, lx + 4, rx - 4, eyeY - 1, g.goldPaint);
                drawStarFrame(canvas, lx, eyeY, -6, 5, -5, 4, -4, 3);
                drawStarFrame(canvas, rx, eyeY, -5, 6, -4, 5, -3, 4);
                break;

            case ITEM_CLOWN:
                drawClown(canvas, cx, eyeY);
                break;

            default:
                break;
        }
    }

    private void drawSunglassLens(Canvas canvas, int ex, int eyeY) {
        g.rectPx(canvas, ex - 5, eyeY - 2, ex + 5, eyeY + 2, g.blackPaint);
        g.hline(canvas, ex - 5, ex + 5, eyeY - 3, g.whitePaint);
        g.hline(canvas, ex - 4, ex + 4, eyeY + 3, g.whitePaint);
        g.px(canvas, ex - 5, eyeY - 2, g.whitePaint);
        g.px(canvas, ex + 5, eyeY - 2, g.whitePaint);
        g.px(canvas, ex - 3, eyeY - 1, g.cyanPaint); // reflexo
        g.px(canvas, ex - 2, eyeY, g.cyanPaint);
    }

    private void drawStarFrame(Canvas canvas, int ex, int eyeY,
                               int barL, int barR, int armL, int armR, int footL, int footR) {
        g.px(canvas, ex, eyeY - 6, g.goldPaint);
        g.hline(canvas, ex + barL, ex + barR, eyeY - 3, g.goldPaint);
        g.px(canvas, ex + armL, eyeY - 2, g.goldPaint);
        g.px(canvas, ex + armR, eyeY - 2, g.goldPaint);
        g.px(canvas, ex + footL, eyeY + 3, g.goldPaint);
        g.px(canvas, ex + footR, eyeY + 3, g.goldPaint);
    }

    /**
     * Palhaço: nariz vermelho redondo entre os olhos e a boca, e o cabelão estilo Bozo —
     * careca em cima, com dois tufos laranja/vermelhos enormes saindo pelos lados.
     */
    private void drawClown(Canvas canvas, int cx, int eyeY) {
        for (int[] row : CLOWN_TUFT) {
            int y = eyeY + row[0];
            for (int dx = row[1]; dx <= row[2]; dx++) {
                // Mistura laranja e vermelho em xadrez para dar volume ao cabelo.
                Paint hair = ((dx + row[0]) & 3) == 0 ? g.clownRedPaint : g.clownHairPaint;
                g.px(canvas, cx + dx, y, hair);
                g.px(canvas, cx + 1 - dx, y, hair);
            }
        }
        // Pontinhas arrepiadas.
        g.px(canvas, cx - 23, eyeY - 10, g.clownHairPaint);
        g.px(canvas, cx + 24, eyeY - 10, g.clownHairPaint);
        g.px(canvas, cx - 23, eyeY - 7, g.clownRedPaint);
        g.px(canvas, cx + 24, eyeY - 7, g.clownRedPaint);
        g.px(canvas, cx - 17, eyeY - 13, g.clownRedPaint);
        g.px(canvas, cx + 18, eyeY - 13, g.clownRedPaint);

        // Nariz vermelho (bolinha 4x4) com brilho.
        int ny = eyeY + 4;
        g.hline(canvas, cx, cx + 1, ny, g.clownRedPaint);
        g.hline(canvas, cx - 1, cx + 2, ny + 1, g.clownRedPaint);
        g.hline(canvas, cx - 1, cx + 2, ny + 2, g.clownRedPaint);
        g.hline(canvas, cx, cx + 1, ny + 3, g.clownRedPaint);
        g.px(canvas, cx, ny + 1, g.whitePaint);
    }
}
