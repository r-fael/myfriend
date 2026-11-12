package com.myfriend.sim;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import com.myfriend.PetClock;
import com.myfriend.PetEngine;
import com.myfriend.PetSignals;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Simulador de PC do myfriend: roda o mesmo PetEngine do relógio numa janelinha redonda.
 *
 * Mouse = toque, rodinha = coroa. Teclas: setas cima/baixo = bateria, C = carregador,
 * M = trocar humor, S = +100 passos, G = +1000 passos, A = tela desligada, W = app/watchface, N = notificação,
 * D = chacoalhar, H = +1 hora, U = +1 dia, R = apagar e acender a tela, 0 = hora real.
 */
public final class DesktopSim extends JPanel {
    private static final int SCREEN = 432;   // resolução do Galaxy Watch 6 Classic
    private static final int MARGIN = 24;
    private static final int HELP_HEIGHT = 46;
    private static final int WIDTH_EXTRA = 260;

    private final Context context;
    private final PetEngine engine;
    private final BufferedImage frame = new BufferedImage(SCREEN, SCREEN, BufferedImage.TYPE_INT_ARGB);
    private boolean ambient = false;
    private boolean watchFace = true;
    /** Contador de passos simulado (como o do sistema: só cresce). */
    private float stepCounter = 1000f;
    /** Humor sendo mostrado pela tecla M (só no simulador). */
    private int moodIndex = -1;
    private long moodShownUntil = 0L;
    private static final long MOOD_PREVIEW_MS = 4000L;
    private static final String[] MOOD_NAMES = {
        "feliz", "amado", "com fome", "com sono", "dormindo", "triste", "bravo", "surpreso",
        "comendo", "piscadinha", "com sede", "bebendo", "banho", "bateria fraca", "bocejo",
        "espreguiçando", "animado", "tonto", "mensagem", "emburrado", "comemorando", "carente"
    };

    private DesktopSim(File prefsFile) {
        context = new Context(prefsFile);
        engine = new PetEngine(context);
        engine.setWatchFaceMode(watchFace);
        engine.updateGridMetrics(SCREEN, SCREEN);
        engine.onResume();
        engine.onStepCounterTotal(stepCounter);

        setPreferredSize(new Dimension(SCREEN + 2 * MARGIN + WIDTH_EXTRA, SCREEN + 2 * MARGIN + HELP_HEIGHT));
        setBackground(new Color(0x2B2B2B));
        setFocusable(true);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                touch(MotionEvent.ACTION_DOWN, e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                touch(MotionEvent.ACTION_MOVE, e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                touch(MotionEvent.ACTION_UP, e);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                engine.handleRotaryScroll(e.getWheelRotation() > 0 ? 1f : -1f);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                shiftDown = e.isShiftDown();
                onKey(e.getKeyCode());
            }
        });

        // ~60 quadros por segundo.
        new Timer(16, e -> repaint()).start();
    }

    private void touch(int action, MouseEvent e) {
        requestFocusInWindow();
        float x = e.getX() - MARGIN;
        float y = e.getY() - MARGIN;
        if (action == MotionEvent.ACTION_DOWN && (x < 0 || y < 0 || x >= SCREEN || y >= SCREEN)) {
            return;
        }
        engine.handleTouch(new MotionEvent(action, x, y), SCREEN, SCREEN);
    }

    private boolean shiftDown = false;

    /** Força um humor no motor via reflexão, sem adicionar nada ao app do relógio. */
    private void previewMood(int mood) {
        try {
            java.lang.reflect.Method start = PetEngine.class.getDeclaredMethod(
                    "startAction", int.class, long.class, long.class);
            start.setAccessible(true);
            start.invoke(engine, mood, android.os.SystemClock.uptimeMillis(), MOOD_PREVIEW_MS);
            moodShownUntil = System.currentTimeMillis() + MOOD_PREVIEW_MS;
        } catch (ReflectiveOperationException ex) {
            ex.printStackTrace();
        }
    }

    private void onKey(int code) {
        android.os.BatteryManager battery = context.battery();
        switch (code) {
            case KeyEvent.VK_UP:
                battery.level = Math.min(100, battery.level + 5);
                break;
            case KeyEvent.VK_DOWN:
                battery.level = Math.max(1, battery.level - 5);
                break;
            case KeyEvent.VK_C:
                battery.charging = !battery.charging;
                break;
            case KeyEvent.VK_S:
                stepCounter += 10;
                engine.onStepCounterTotal(stepCounter); // passos "ao vivo" (anima)
                stepCounter += 90;
                engine.onStepCounterTotal(stepCounter);
                return;
            case KeyEvent.VK_G:
                stepCounter += 1000;
                engine.onStepCounterTotal(stepCounter);
                return;
            case KeyEvent.VK_A:
                ambient = !ambient;
                if (ambient) {
                    engine.onPause();
                } else {
                    engine.onResume();
                }
                return;
            case KeyEvent.VK_N:
                PetSignals.lastNotificationWall = System.currentTimeMillis();
                return;
            case KeyEvent.VK_D:
                engine.onShake();
                return;
            case KeyEvent.VK_H:
                PetClock.offsetMs += 60L * 60_000L;
                return;
            case KeyEvent.VK_U:
                PetClock.offsetMs += 24L * 60L * 60_000L;
                return;
            case KeyEvent.VK_0:
                PetClock.offsetMs = 0L;
                return;
            case KeyEvent.VK_R:
                engine.onPause();
                engine.onResume();
                return;
            case KeyEvent.VK_M:
                // Mostra o próximo humor (Shift+M = anterior) por alguns segundos.
                int step = (code == KeyEvent.VK_M && shiftDown) ? -1 : 1;
                moodIndex = Math.floorMod(moodIndex + step, MOOD_NAMES.length);
                previewMood(moodIndex);
                return;
            case KeyEvent.VK_W:
                watchFace = !watchFace;
                engine.setWatchFaceMode(watchFace);
                return;
            default:
                return;
        }
        engine.updateBatteryStatus(battery.level, battery.charging);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D frameG = frame.createGraphics();
        engine.renderScene(new Canvas(frameG, SCREEN, SCREEN), SCREEN, SCREEN, ambient);
        frameG.dispose();

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Treme a tela enquanto o relógio "vibra".
        int shake = 0;
        if (System.currentTimeMillis() < context.vibrator().vibrateUntil) {
            shake = (System.currentTimeMillis() / 30L) % 2L == 0L ? 2 : -2;
        }

        // Caixa do relógio + tela redonda.
        g.setColor(new Color(0x111111));
        g.fill(new Ellipse2D.Float(MARGIN - 10 + shake, MARGIN - 10, SCREEN + 20, SCREEN + 20));
        g.setClip(new Ellipse2D.Float(MARGIN + shake, MARGIN, SCREEN, SCREEN));
        g.drawImage(frame, MARGIN + shake, MARGIN, null);
        g.setClip(null);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(0x555555));
        g.draw(new Ellipse2D.Float(MARGIN - 10 + shake, MARGIN - 10, SCREEN + 20, SCREEN + 20));

        // Ajuda.
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(new Color(0xBBBBBB));
        android.os.BatteryManager battery = context.battery();
        int y = SCREEN + 2 * MARGIN + 14;
        g.drawString("Mouse = toque · Rodinha = coroa · M / Shift+M = trocar humor · S/G = passos · D = chacoalhar · N = mensagem",
                10, y);
        // Nome do humor ao lado do relógio.
        if (moodIndex >= 0) {
            boolean showing = System.currentTimeMillis() < moodShownUntil;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            g.setColor(showing ? new Color(0xFFFFFF) : new Color(0x777777));
            g.drawString("Humor " + (moodIndex + 1) + "/" + MOOD_NAMES.length + ":", SCREEN + 2 * MARGIN + 10, MARGIN + 30);
            g.drawString(MOOD_NAMES[moodIndex], SCREEN + 2 * MARGIN + 10, MARGIN + 54);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.setColor(new Color(0xBBBBBB));
            g.drawString("M = próximo, Shift+M = anterior", SCREEN + 2 * MARGIN + 10, MARGIN + 76);
        }
        java.util.Calendar cal = PetClock.calendar();
        g.drawString(String.format("H = +1h · U = +1 dia · 0 = hora real · R = apagar/acender · A = tela desligada · "
                        + "Bateria (setas): %d%%%s · %02d:%02d",
                battery.level, battery.charging ? " carregando" : "",
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)), 10, y + 18);
        g.dispose();
    }

    public static void main(String[] args) {
        File prefs = new File(args.length > 0 ? args[0] : "sim-prefs.properties");
        SwingUtilities.invokeLater(() -> {
            DesktopSim sim = new DesktopSim(prefs);
            JFrame window = new JFrame("myfriend · simulador");
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    sim.engine.onPause(); // salva o bichinho
                }
            });
            window.setContentPane(sim);
            window.setResizable(false);
            window.pack();
            window.setLocationRelativeTo(null);
            window.setVisible(true);
            sim.requestFocusInWindow();
        });
    }
}
