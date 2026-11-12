package com.myfriend;

import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

/**
 * Mostrador do relógio (Watch Face): usa o {@link PetEngine} diretamente na Surface
 * sem instanciar uma {@link android.view.View} órfã em memória.
 *
 * <p>Economia de bateria: com a tela acesa o motor decide o ritmo (≈1 quadro/s parado);
 * na tela desligada (ambient) desenha só um quadro cinza por minuto, sem sensores nem loop.
 */
public class FriendWatchFaceService extends WallpaperService {

    private static final long DEFAULT_FRAME_DELAY_MS = 500L;
    private static final String ACTION_BACKGROUND = "com.google.android.wearable.action.BACKGROUND_ACTION";
    private static final String ACTION_AMBIENT_UPDATE = "com.google.android.wearable.action.AMBIENT_UPDATE";
    private static final String EXTRA_AMBIENT = "ambient_mode";

    @Override
    public Engine onCreateEngine() {
        return new FriendEngine();
    }

    private class FriendEngine extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable drawRunner = this::drawFrame;
        private PetEngine engine;
        private PetSensors sensors;
        private boolean visible = false;
        private boolean ambient = false;
        private int surfaceWidth = 432;
        private int surfaceHeight = 432;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);
            engine = new PetEngine(FriendWatchFaceService.this);
            engine.setWatchFaceMode(true);
            sensors = new PetSensors(FriendWatchFaceService.this, engine, this::drawFrame);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            surfaceWidth = width;
            surfaceHeight = height;
            if (engine != null) {
                engine.updateGridMetrics(width, height);
            }
            drawFrame();
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            super.onVisibilityChanged(isVisible);
            if (visible == isVisible) {
                return;
            }
            visible = isVisible;
            handler.removeCallbacks(drawRunner);
            if (engine == null) {
                return;
            }
            if (isVisible) {
                if (!ambient) {
                    engine.onResume();
                    sensors.start();
                }
                engine.updateGridMetrics(surfaceWidth, surfaceHeight);
                drawFrame();
            } else {
                sensors.stop();
                if (!ambient) {
                    engine.onPause();
                }
            }
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            if (ACTION_BACKGROUND.equals(action) && extras != null && extras.containsKey(EXTRA_AMBIENT)) {
                setAmbient(extras.getBoolean(EXTRA_AMBIENT, false));
            } else if (ACTION_AMBIENT_UPDATE.equals(action)) {
                drawFrame(); // tique de 1 minuto do sistema na tela desligada
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        private void setAmbient(boolean on) {
            if (ambient == on || engine == null) {
                return;
            }
            ambient = on;
            handler.removeCallbacks(drawRunner);
            if (on) {
                sensors.stop();
                engine.onPause(); // salva; de noite ele já vai dormir
            } else if (visible) {
                engine.onResume();
                sensors.start();
            }
            drawFrame();
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            if (engine != null && visible && !ambient) {
                engine.handleTouch(event, surfaceWidth, surfaceHeight);
                drawFrame();
            }
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(drawRunner);
            if (sensors != null) {
                sensors.stop();
            }
            if (engine != null) {
                engine.onPause();
            }
            super.onDestroy();
        }

        private void drawFrame() {
            handler.removeCallbacks(drawRunner);
            if (!visible || engine == null) {
                return;
            }
            SurfaceHolder holder = getSurfaceHolder();
            long nextDelay = DEFAULT_FRAME_DELAY_MS;
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    nextDelay = engine.renderScene(canvas, surfaceWidth, surfaceHeight, ambient);
                }
            } catch (Exception ignored) {
                // Superfície pode sumir no meio do desenho.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (Exception ignored) {
                        // Idem.
                    }
                }
            }
            if (!visible) {
                return;
            }
            if (ambient) {
                // Reserva: se o tique do sistema não vier, atualiza o relógio na virada do minuto.
                long wall = System.currentTimeMillis();
                handler.postDelayed(drawRunner, 60_000L - (wall % 60_000L) + 50L);
            } else {
                handler.postDelayed(drawRunner, nextDelay);
            }
        }
    }
}
