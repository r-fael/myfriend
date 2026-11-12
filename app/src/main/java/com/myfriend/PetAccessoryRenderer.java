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

    /**
     * Tufos do cabelo de palhaço {dy a partir da linha dos olhos, xMin, xMax} do lado esquerdo;
     * o direito é espelhado.
     */
    private static final int[][] CLOWN_TUFT = {
            {-7, -11, -9},
            {-6, -13, -8},
            {-5, -14, -8},
            {-4, -15, -9},
            {-3, -15, -9},
            {-2, -14, -9},
            {-1, -13, -10},
            {0, -11, -10},
    };

    private final PixelGrid g;
    private final TimeZone localTz = TimeZone.getDefault();

    /** Pixels do anel de bateria na borda (x, y), em ordem horária a partir do topo. */
    private static final int[][] RING = buildRing();

    public PetAccessoryRenderer(PixelGrid grid) {
        this.g = grid;
    }

    private static int[][] buildRing() {
        java.util.ArrayList<float[]> cells = new java.util.ArrayList<>();
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                double dx = x + 0.5 - GRID_SIZE / 2.0;
                double dy = y + 0.5 - GRID_SIZE / 2.0;
                double r = Math.sqrt(dx * dx + dy * dy);
                if (r >= 24.3 && r < 25.3) {
                    double angle = Math.atan2(dx, -dy); // 0 = topo, sentido horário
                    if (angle < 0) {
                        angle += 2 * Math.PI;
                    }
                    cells.add(new float[]{(float) angle, x, y});
                }
            }
        }
        java.util.Collections.sort(cells, (a, b) -> Float.compare(a[0], b[0]));
        int[][] ring = new int[cells.size()][];
        for (int i = 0; i < ring.length; i++) {
            ring[i] = new int[]{(int) cells.get(i)[1], (int) cells.get(i)[2]};
        }
        return ring;
    }

    /**
     * Bateria como um anel fininho na borda que vai encolhendo (sentido horário a partir do topo):
     * verde normal, amarelo em 30% ou menos, vermelho piscando em 15% ou menos.
     * Carregando: verde com um brilho correndo pelo anel.
     */
    public void drawBatteryRing(Canvas canvas, int batteryPct, boolean charging, long now) {
        Paint paint;
        if (charging || batteryPct > 30) {
            paint = g.greenPaint;
        } else if (batteryPct > 15) {
            paint = g.goldPaint;
        } else {
            paint = (now / 400L) % 2L == 0L ? g.redPaint : g.dimPaint;
        }
        int lit = Math.round(RING.length * Math.max(1, Math.min(100, batteryPct)) / 100f);
        int shine = charging ? (int) ((now / 40L) % RING.length) : -1;
        for (int i = 0; i < RING.length; i++) {
            Paint p = i < lit ? paint : g.ringTrackPaint;
            if (charging && Math.abs(i - shine) <= 1) {
                p = g.whitePaint;
            }
            g.px(canvas, RING[i][0], RING[i][1], p);
        }
    }

    /** Data DD/MM no mesmo lugar do relógio. */
    public void drawDateDDMM(Canvas canvas, int y, Paint paint) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int day = cal.get(java.util.Calendar.DAY_OF_MONTH);
        int month = cal.get(java.util.Calendar.MONTH) + 1;
        g.drawDigit3x5(canvas, 16, y, day / 10, paint);
        g.drawDigit3x5(canvas, 20, y, day % 10, paint);
        g.px(canvas, 26, y, g.cyanPaint);
        g.px(canvas, 26, y + 1, g.cyanPaint);
        g.px(canvas, 25, y + 2, g.cyanPaint);
        g.px(canvas, 24, y + 3, g.cyanPaint);
        g.px(canvas, 24, y + 4, g.cyanPaint);
        g.drawDigit3x5(canvas, 28, y, month / 10, paint);
        g.drawDigit3x5(canvas, 32, y, month % 10, paint);
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
        // HH:MM compacto e centralizado (x 17..33), deixando espaço à esquerda para a pilha.
        g.drawDigit3x5(canvas, 17, y, hours / 10, paint);
        g.drawDigit3x5(canvas, 21, y, hours % 10, paint);
        if ((now / 500L) % 2L == 0L) {
            g.px(canvas, 25, y + 1, g.cyanPaint);
            g.px(canvas, 25, y + 3, g.cyanPaint);
        }
        g.drawDigit3x5(canvas, 27, y, minutes / 10, paint);
        g.drawDigit3x5(canvas, 31, y, minutes % 10, paint);
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

    /**
     * Copo, maçã etc. que aparecem do lado do bichinho conforme o humor.
     *
     * @param cx   centro do corpinho
     * @param top  primeira linha da cabeça
     */
    public void drawMoodProps(Canvas canvas, int mood, int cx, int top,
                              boolean shopOpen, long actionStartTime, long now) {
        int fastBob = (now / 180L) % 2L == 0L ? 0 : -1;
        int slowBob = (now / 350L) % 2L == 0L ? 0 : -1;
        switch (mood) {
            case MOOD_DRINKING: {
                int level = 4 - Math.min(3, (int) (Math.max(0L, now - actionStartTime) / 550L));
                drawWaterCup(canvas, cx + 13, top + 7 + fastBob, level);
                break;
            }
            case MOOD_THIRSTY:
                if (!shopOpen) {
                    drawWaterCup(canvas, 41, 21 + slowBob, 3);
                    g.drawSprite5x5(canvas, 41, 14 + slowBob, SPRITE_WATER_DROP, g.cyanPaint);
                }
                break;
            case MOOD_EATING:
                drawApple(canvas, cx + 13, top + 8 + fastBob);
                break;
            case MOOD_HUNGRY:
                if (!shopOpen) {
                    drawApple(canvas, 41, 21 + slowBob);
                }
                break;
            case MOOD_GRUMPY:
                g.drawSprite5x5(canvas, cx + 11, top - 3, SPRITE_SPARKLE, g.redPaint);
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------- Acessórios da Loja
    //
    // Geometria do rosto (eixo de simetria em cx + 0.5):
    //   olhos ovais 5x6 centrados em (cx - 5, eyeY) e (cx + 6, eyeY), ocupando eyeY-3..eyeY+2;
    //   boca em eyeY + 6. Os chapéus apoiam a aba na linha "base" = eyeY - 5.

    /** hline espelhada no eixo do rosto: desenha os offsets do lado esquerdo e o espelho. */
    private void mirrorHline(Canvas canvas, int cx, int dx0, int dx1, int y, Paint paint) {
        g.hline(canvas, cx + dx0, cx + dx1, y, paint);
        g.hline(canvas, cx + 1 - dx1, cx + 1 - dx0, y, paint);
    }

    private void mirrorPx(Canvas canvas, int cx, int dx, int y, Paint paint) {
        g.px(canvas, cx + dx, y, paint);
        g.px(canvas, cx + 1 - dx, y, paint);
    }

    /** Faixa simétrica de meia-largura w (de cx+1-w até cx+w). */
    private void band(Canvas canvas, int cx, int halfWidth, int y, Paint paint) {
        g.hline(canvas, cx + 1 - halfWidth, cx + halfWidth, y, paint);
    }

    /**
     * Itens de cabeça.
     *
     * @param cx   centro do rosto (eixo de simetria em cx + 0.5)
     * @param eyeY linha central dos olhos
     */
    public void drawHeadItem(Canvas canvas, int cx, int eyeY, int item) {
        int base = eyeY - 5; // linha da aba, logo acima dos olhos

        switch (item) {
            case HEAD_SANTA_HAT:
                // Gorro mole caindo para a direita, com pompom pendurado.
                band(canvas, cx, 8, base, g.whitePaint);
                band(canvas, cx, 8, base - 1, g.whitePaint);
                g.hline(canvas, cx - 6, cx + 7, base - 2, g.redPaint);
                g.hline(canvas, cx - 5, cx + 6, base - 3, g.redPaint);
                g.hline(canvas, cx - 4, cx + 6, base - 4, g.redPaint);
                g.hline(canvas, cx - 2, cx + 6, base - 5, g.redPaint);
                g.hline(canvas, cx, cx + 7, base - 6, g.redPaint);
                g.hline(canvas, cx + 3, cx + 8, base - 7, g.redPaint);
                g.hline(canvas, cx + 6, cx + 9, base - 8, g.redPaint);
                g.rectPx(canvas, cx + 9, base - 7, cx + 11, base - 5, g.whitePaint);
                break;

            case HEAD_WITCH_HAT:
                band(canvas, cx, 10, base, g.purplePaint);
                band(canvas, cx, 7, base - 1, g.purplePaint);
                band(canvas, cx, 6, base - 2, g.orangePaint);
                g.hline(canvas, cx, cx + 1, base - 2, g.goldPaint); // fivela
                band(canvas, cx, 5, base - 3, g.purplePaint);
                band(canvas, cx, 4, base - 4, g.purplePaint);
                band(canvas, cx, 3, base - 5, g.purplePaint);
                g.hline(canvas, cx - 1, cx + 2, base - 6, g.purplePaint);
                g.hline(canvas, cx, cx + 3, base - 7, g.purplePaint);
                g.hline(canvas, cx + 2, cx + 4, base - 8, g.purplePaint);
                g.hline(canvas, cx + 4, cx + 5, base - 9, g.purplePaint); // ponta dobrada
                break;

            case HEAD_PUMPKIN:
                g.px(canvas, cx + 1, base - 9, g.greenPaint);
                g.hline(canvas, cx, cx + 1, base - 8, g.greenPaint);
                g.hline(canvas, cx + 2, cx + 3, base - 9, g.greenPaint); // folhinha
                band(canvas, cx, 4, base - 7, g.orangePaint);
                band(canvas, cx, 6, base - 6, g.orangePaint);
                for (int y = base - 5; y <= base - 1; y++) {
                    band(canvas, cx, 7, y, g.orangePaint);
                }
                band(canvas, cx, 6, base, g.orangePaint);
                // Gomos.
                for (int y = base - 6; y <= base; y++) {
                    mirrorPx(canvas, cx, -3, y, g.clownHairPaint);
                }
                // Carinha de abóbora.
                mirrorHline(canvas, cx, -4, -3, base - 4, g.goldPaint);
                mirrorPx(canvas, cx, -3, base - 5, g.goldPaint);
                g.hline(canvas, cx - 3, cx + 4, base - 2, g.goldPaint);
                g.px(canvas, cx - 1, base - 1, g.goldPaint);
                g.px(canvas, cx + 2, base - 1, g.goldPaint);
                break;

            case HEAD_DEVIL_HORNS:
                // Chifrinhos curvando para fora.
                mirrorHline(canvas, cx, -5, -3, base, g.redPaint);
                mirrorHline(canvas, cx, -6, -4, base - 1, g.redPaint);
                mirrorHline(canvas, cx, -7, -5, base - 2, g.redPaint);
                mirrorHline(canvas, cx, -8, -6, base - 3, g.redPaint);
                mirrorHline(canvas, cx, -8, -7, base - 4, g.redPaint);
                mirrorPx(canvas, cx, -8, base - 5, g.redPaint);
                break;

            case HEAD_CROWN:
                band(canvas, cx, 7, base, g.goldPaint);
                band(canvas, cx, 7, base - 1, g.goldPaint);
                band(canvas, cx, 7, base - 2, g.goldPaint);
                g.hline(canvas, cx, cx + 1, base - 1, g.redPaint);   // rubi
                mirrorPx(canvas, cx, -4, base - 1, g.cyanPaint);      // safiras
                // Três pontas, a do meio mais alta, com bolinhas.
                for (int y = base - 5; y <= base - 3; y++) {
                    mirrorHline(canvas, cx, -6, -5, y, g.goldPaint);
                }
                for (int y = base - 6; y <= base - 3; y++) {
                    g.hline(canvas, cx, cx + 1, y, g.goldPaint);
                }
                mirrorPx(canvas, cx, -6, base - 6, g.whitePaint);
                g.hline(canvas, cx, cx + 1, base - 7, g.whitePaint);
                break;

            case HEAD_WIZARD_HAT:
                band(canvas, cx, 10, base, g.deepBluePaint);
                band(canvas, cx, 6, base - 1, g.deepBluePaint);
                band(canvas, cx, 5, base - 2, g.deepBluePaint);
                band(canvas, cx, 4, base - 3, g.deepBluePaint);
                band(canvas, cx, 4, base - 4, g.deepBluePaint);
                band(canvas, cx, 3, base - 5, g.deepBluePaint);
                band(canvas, cx, 2, base - 6, g.deepBluePaint);
                g.hline(canvas, cx, cx + 2, base - 7, g.deepBluePaint);
                g.hline(canvas, cx + 1, cx + 3, base - 8, g.deepBluePaint);
                g.hline(canvas, cx + 2, cx + 4, base - 9, g.deepBluePaint);
                g.px(canvas, cx + 4, base - 10, g.deepBluePaint);
                // Estrelinhas.
                g.px(canvas, cx - 3, base - 2, g.goldPaint);
                g.px(canvas, cx + 2, base - 4, g.goldPaint);
                g.px(canvas, cx + 3, base - 1, g.goldPaint);
                g.px(canvas, cx, base - 6, g.goldPaint);
                break;

            case HEAD_HEADPHONES:
                // Arco por cima descendo até as conchas nas laterais dos olhos.
                band(canvas, cx, 6, base - 3, g.cyanPaint);
                mirrorHline(canvas, cx, -8, -6, base - 2, g.cyanPaint);
                mirrorPx(canvas, cx, -9, base - 1, g.cyanPaint);
                for (int y = base; y < eyeY - 2; y++) {
                    mirrorPx(canvas, cx, -10, y, g.cyanPaint);
                }
                for (int y = eyeY - 2; y <= eyeY + 2; y++) {
                    mirrorHline(canvas, cx, -12, -9, y, g.cyanPaint);
                }
                for (int y = eyeY - 1; y <= eyeY + 1; y++) {
                    mirrorPx(canvas, cx, -11, y, g.whitePaint);
                }
                break;

            case HEAD_HEADBAND:
                band(canvas, cx, 9, base, g.redPaint);
                band(canvas, cx, 9, base + 1, g.redPaint);
                g.hline(canvas, cx, cx + 1, base, g.whitePaint);
                g.px(canvas, cx + 10, base + 1, g.redPaint); // pontas amarradas
                g.px(canvas, cx + 11, base + 2, g.redPaint);
                g.px(canvas, cx + 10, base + 3, g.redPaint);
                break;

            case HEAD_BUNNY_EARS:
                band(canvas, cx, 6, base, g.whitePaint); // tiara
                // Orelha esquerda em pé.
                g.hline(canvas, cx - 4, cx - 3, base - 10, g.whitePaint);
                g.rectPx(canvas, cx - 5, base - 9, cx - 2, base - 1, g.whitePaint);
                g.rectPx(canvas, cx - 4, base - 8, cx - 3, base - 2, g.blushPaint);
                // Orelha direita dobrada.
                g.rectPx(canvas, cx + 3, base - 6, cx + 6, base - 1, g.whitePaint);
                g.rectPx(canvas, cx + 4, base - 5, cx + 5, base - 2, g.blushPaint);
                g.hline(canvas, cx + 4, cx + 7, base - 7, g.whitePaint);
                g.hline(canvas, cx + 6, cx + 9, base - 8, g.whitePaint);
                g.hline(canvas, cx + 8, cx + 9, base - 7, g.whitePaint);
                break;

            case HEAD_BIRTHDAY_HAT:
                // Chapéu de festa alto e listrado.
                Paint[] stripes = {g.cyanPaint, g.pinkPaint, g.goldPaint};
                int[] halfWidths = {4, 4, 3, 3, 2, 2, 1, 1};
                for (int i = 0; i < halfWidths.length; i++) {
                    band(canvas, cx, halfWidths[i], base - i, stripes[i % 3]);
                }
                band(canvas, cx, 2, base - 9, g.whitePaint); // pompom
                band(canvas, cx, 2, base - 10, g.whitePaint);
                g.hline(canvas, cx, cx + 1, base - 11, g.whitePaint);
                break;

            case HEAD_HALO:
                // Auréola: só o anel dourado flutuando.
                band(canvas, cx, 4, base - 7, g.goldPaint);
                mirrorHline(canvas, cx, -6, -4, base - 6, g.goldPaint);
                mirrorHline(canvas, cx, -6, -4, base - 5, g.goldPaint);
                band(canvas, cx, 4, base - 4, g.goldPaint);
                break;

            case HEAD_STRAW_HAT:
                band(canvas, cx, 11, base, g.goldPaint);
                mirrorPx(canvas, cx, -11, base - 1, g.orangePaint); // aba virada
                band(canvas, cx, 6, base - 1, g.goldPaint);
                for (int x = cx - 5; x <= cx + 6; x++) {
                    g.px(canvas, x, base - 2, (x & 1) == 0 ? g.redPaint : g.cyanPaint); // fita xadrez
                }
                band(canvas, cx, 6, base - 3, g.orangePaint);
                band(canvas, cx, 6, base - 4, g.goldPaint);
                band(canvas, cx, 5, base - 5, g.orangePaint);
                band(canvas, cx, 4, base - 6, g.goldPaint);
                break;

            case HEAD_CLOWN_HAIR:
                drawClownHair(canvas, cx, eyeY);
                break;

            case HEAD_PLUMBER_RED:
                drawPlumberCap(canvas, cx, base, g.capRedPaint, g.capRedDarkPaint, true);
                break;

            case HEAD_PLUMBER_GREEN:
                drawPlumberCap(canvas, cx, base, g.capGreenPaint, g.capGreenDarkPaint, false);
                break;

            default:
                break;
        }
    }

    /** Boné de encanador com a aba na frente e o emblema branco com a letra (M ou L). */
    private void drawPlumberCap(Canvas canvas, int cx, int base, Paint cap, Paint dark, boolean letterM) {
        band(canvas, cx, 9, base, dark);          // aba
        band(canvas, cx, 8, base - 1, cap);
        band(canvas, cx, 8, base - 2, cap);
        band(canvas, cx, 7, base - 3, cap);
        band(canvas, cx, 7, base - 4, cap);
        band(canvas, cx, 6, base - 5, cap);
        band(canvas, cx, 6, base - 6, cap);
        band(canvas, cx, 5, base - 7, cap);
        band(canvas, cx, 3, base - 8, cap);
        // Emblema: círculo branco 8x6 com a letra.
        band(canvas, cx, 3, base - 6, g.whitePaint);
        band(canvas, cx, 4, base - 5, g.whitePaint);
        band(canvas, cx, 4, base - 4, g.whitePaint);
        band(canvas, cx, 4, base - 3, g.whitePaint);
        band(canvas, cx, 4, base - 2, g.whitePaint);
        band(canvas, cx, 3, base - 1, g.whitePaint);
        if (letterM) {
            // "M" de 6 colunas: pernas, ombros e o "V" no meio.
            for (int y = base - 5; y <= base - 2; y++) {
                mirrorPx(canvas, cx, -2, y, dark);
            }
            mirrorPx(canvas, cx, -1, base - 4, dark);
            mirrorPx(canvas, cx, 0, base - 3, dark);
        } else {
            // "L".
            for (int y = base - 5; y <= base - 2; y++) {
                g.px(canvas, cx - 1, y, dark);
            }
            g.hline(canvas, cx, cx + 2, base - 2, dark);
        }
    }

    /** Cabelão estilo Bozo: careca em cima, dois tufos enormes laranja/vermelhos ao lado dos olhos. */
    private void drawClownHair(Canvas canvas, int cx, int eyeY) {
        for (int[] row : CLOWN_TUFT) {
            int y = eyeY + row[0];
            for (int dx = row[1]; dx <= row[2]; dx++) {
                // Mistura laranja e vermelho em xadrez para dar volume ao cabelo.
                Paint hair = ((dx + row[0]) & 3) == 0 ? g.clownRedPaint : g.clownHairPaint;
                mirrorPx(canvas, cx, dx, y, hair);
            }
        }
        // Pontinhas arrepiadas.
        mirrorPx(canvas, cx, -16, eyeY - 5, g.clownHairPaint);
        mirrorPx(canvas, cx, -16, eyeY - 2, g.clownRedPaint);
        mirrorPx(canvas, cx, -10, eyeY - 8, g.clownRedPaint);
    }

    /**
     * Itens de nariz, desenhados entre os olhos e a boca.
     *
     * @param cx   centro do rosto (eixo de simetria em cx + 0.5)
     * @param eyeY linha central dos olhos
     */
    public void drawNoseItem(Canvas canvas, int cx, int eyeY, int item) {
        int ny = eyeY + 2;
        switch (item) {
            case NOSE_CLOWN:
                // Bolinha vermelha com brilho.
                g.hline(canvas, cx, cx + 1, ny, g.clownRedPaint);
                g.hline(canvas, cx - 1, cx + 2, ny + 1, g.clownRedPaint);
                g.hline(canvas, cx - 1, cx + 2, ny + 2, g.clownRedPaint);
                g.hline(canvas, cx, cx + 1, ny + 3, g.clownRedPaint);
                g.px(canvas, cx, ny + 1, g.whitePaint);
                break;

            case NOSE_PIG:
                g.hline(canvas, cx - 1, cx + 2, ny, g.pinkPaint);
                g.hline(canvas, cx - 2, cx + 3, ny + 1, g.pinkPaint);
                g.hline(canvas, cx - 1, cx + 2, ny + 2, g.pinkPaint);
                g.px(canvas, cx - 1, ny + 1, g.blackPaint); // narinas
                g.px(canvas, cx + 2, ny + 1, g.blackPaint);
                break;

            case NOSE_CAT:
                // Narizinho rosa e bigodes de gato de cada lado.
                g.hline(canvas, cx, cx + 1, ny + 1, g.pinkPaint);
                g.hline(canvas, cx, cx + 1, ny + 2, g.blushPaint);
                mirrorHline(canvas, cx, -12, -9, ny, g.whitePaint);
                mirrorHline(canvas, cx, -12, -9, ny + 2, g.whitePaint);
                mirrorPx(canvas, cx, -13, ny - 1, g.whitePaint);
                mirrorPx(canvas, cx, -13, ny + 3, g.whitePaint);
                break;

            case NOSE_DISGUISE:
                // Narigão de disfarce com bigodão grosso embaixo.
                g.hline(canvas, cx, cx + 1, ny - 1, g.skinPaint);
                g.hline(canvas, cx, cx + 1, ny, g.skinPaint);
                g.hline(canvas, cx - 1, cx + 2, ny + 1, g.skinPaint);
                g.hline(canvas, cx - 2, cx + 3, ny + 2, g.skinPaint);
                g.px(canvas, cx - 1, ny + 2, g.skinShadePaint); // narinas
                g.px(canvas, cx + 2, ny + 2, g.skinShadePaint);
                g.px(canvas, cx + 1, ny, g.whitePaint);         // brilho
                band(canvas, cx, 5, ny + 3, g.bushyBrowPaint);
                band(canvas, cx, 4, ny + 4, g.bushyBrowPaint);
                mirrorPx(canvas, cx, -5, ny + 5, g.bushyBrowPaint);
                break;

            case NOSE_MUSTACHE:
                // Bigodão com as pontas enroladas para cima.
                g.hline(canvas, cx - 1, cx + 2, ny + 2, g.brownPaint);
                mirrorHline(canvas, cx, -4, -2, ny + 3, g.brownPaint);
                mirrorPx(canvas, cx, -5, ny + 2, g.brownPaint);
                mirrorPx(canvas, cx, -5, ny + 1, g.brownPaint);
                break;

            default:
                break;
        }
    }

    /**
     * Itens de olhos.
     *
     * @param lx   centro do olho esquerdo
     * @param rx   centro do olho direito
     * @param eyeY linha central dos olhos
     * @param cx   centro do rosto (para hastes e tiras)
     */
    public void drawEyesItem(Canvas canvas, int lx, int rx, int eyeY, int cx, int item) {
        switch (item) {
            case EYES_SUNGLASSES:
                drawGlassFrame(canvas, lx, eyeY, g.whitePaint, true);
                drawGlassFrame(canvas, rx, eyeY, g.whitePaint, true);
                g.hline(canvas, lx + 4, rx - 4, eyeY - 3, g.whitePaint); // ponte
                g.hline(canvas, cx - 10, lx - 4, eyeY - 3, g.whitePaint); // hastes
                g.hline(canvas, rx + 4, cx + 11, eyeY - 3, g.whitePaint);
                break;

            case EYES_PARTY_GLASSES:
                drawGlassFrame(canvas, lx, eyeY, g.goldPaint, false);
                drawGlassFrame(canvas, rx, eyeY, g.goldPaint, false);
                g.hline(canvas, lx + 4, rx - 4, eyeY - 3, g.goldPaint);
                // Estrelinhas em cima das lentes.
                for (int ex : new int[]{lx, rx}) {
                    g.px(canvas, ex, eyeY - 7, g.goldPaint);
                    g.hline(canvas, ex - 1, ex + 1, eyeY - 6, g.goldPaint);
                    g.px(canvas, ex, eyeY - 5, g.goldPaint);
                }
                break;

            case EYES_PATCH:
                drawEyePatch(canvas, lx, eyeY, cx);
                break;

            case EYES_DISGUISE:
                // Óculos redondos de aro grosso com sobrancelhas peludas por cima.
                for (int ex : new int[]{lx, rx}) {
                    g.hline(canvas, ex - 2, ex + 2, eyeY - 4, g.patchPaint);
                    g.hline(canvas, ex - 2, ex + 2, eyeY + 3, g.patchPaint);
                    for (int y = eyeY - 3; y <= eyeY + 2; y++) {
                        g.px(canvas, ex - 3, y, g.patchPaint);
                        g.px(canvas, ex + 3, y, g.patchPaint);
                    }
                    g.hline(canvas, ex - 3, ex + 3, eyeY - 6, g.bushyBrowPaint);
                    g.hline(canvas, ex - 2, ex + 2, eyeY - 7, g.bushyBrowPaint);
                }
                g.hline(canvas, lx + 4, rx - 4, eyeY - 2, g.patchPaint); // ponte
                break;

            case EYES_MONOCLE:
                g.hline(canvas, rx - 2, rx + 2, eyeY - 4, g.goldPaint);
                g.hline(canvas, rx - 2, rx + 2, eyeY + 3, g.goldPaint);
                for (int y = eyeY - 3; y <= eyeY + 2; y++) {
                    g.px(canvas, rx - 3, y, g.goldPaint);
                    g.px(canvas, rx + 3, y, g.goldPaint);
                }
                // Correntinha descendo.
                g.px(canvas, rx + 3, eyeY + 4, g.goldPaint);
                g.px(canvas, rx + 4, eyeY + 5, g.goldPaint);
                g.px(canvas, rx + 4, eyeY + 7, g.goldPaint);
                break;

            default:
                break;
        }
    }

    /** Armação de óculos em volta de um olho 5x6; se dark, a lente é escura com reflexo. */
    private void drawGlassFrame(Canvas canvas, int ex, int eyeY, Paint frame, boolean dark) {
        if (dark) {
            g.rectSolid(canvas, ex - 2, eyeY - 3, ex + 2, eyeY + 2, g.blackPaint);
            g.px(canvas, ex - 1, eyeY - 2, g.cyanPaint);
            g.px(canvas, ex, eyeY - 2, g.cyanPaint);
            g.px(canvas, ex - 1, eyeY - 1, g.cyanPaint);
        }
        g.hline(canvas, ex - 3, ex + 3, eyeY - 4, frame);
        for (int y = eyeY - 3; y <= eyeY + 1; y++) {
            g.px(canvas, ex - 3, y, frame);
            g.px(canvas, ex + 3, y, frame);
        }
        g.hline(canvas, ex - 2, ex + 2, eyeY + 2, frame);
    }

    /** Tapa-olho de pirata no olho esquerdo, com a tira passando na testa. */
    private void drawEyePatch(Canvas canvas, int lx, int eyeY, int cx) {
        // Tira: da lateral até o tapa-olho e dele subindo na diagonal.
        g.hline(canvas, cx - 10, lx - 3, eyeY - 1, g.patchStrapPaint);
        for (int i = 1; i <= 5; i++) {
            g.px(canvas, lx + 2 + i, eyeY - 3 - i, g.patchStrapPaint);
        }
        // Tapa-olho preto com borda cinza cobrindo o olho todo.
        g.rectSolid(canvas, lx - 2, eyeY - 3, lx + 2, eyeY + 2, g.blackPaint);
        g.hline(canvas, lx - 1, lx + 1, eyeY - 3, g.patchPaint);
        for (int y = eyeY - 2; y <= eyeY + 1; y++) {
            g.px(canvas, lx - 2, y, g.patchPaint);
            g.px(canvas, lx + 2, y, g.patchPaint);
        }
        g.hline(canvas, lx - 1, lx + 1, eyeY + 2, g.patchPaint);
        g.px(canvas, lx - 1, eyeY - 2, g.dimPaint); // brilhinho do couro
    }
}
