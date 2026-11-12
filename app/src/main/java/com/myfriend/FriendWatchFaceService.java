package com.myfriend;

import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

/**
 * Mostrador do relógio (Watch Face): usa o {@link PetEngine} diretamente na Surface
 * sem instanciar uma {@link android.view.View} órfã em memória.
 */
public class FriendWatchFaceService extends WallpaperService {

    private static final long DEFAULT_FRAME_DELAY_MS = 500L;

    @Override
    public Engine onCreateEngine() {
        return new FriendEngine();
    }

    private class FriendEngine extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable drawRunner = this::drawFrame;
        private PetEngine engine;
        private boolean visible = false;
        private int surfaceWidth = 432;
        private int surfaceHeight = 432;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);
            engine = new PetEngine(FriendWatchFaceService.this);
            engine.setWatchFaceMode(true);
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
            visible = isVisible;
            handler.removeCallbacks(drawRunner);
            if (engine == null) {
                return;
            }
            if (isVisible) {
                engine.onResume();
                engine.updateGridMetrics(surfaceWidth, surfaceHeight);
                drawFrame();
            } else {
                engine.onPause();
            }
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            if (engine != null && visible) {
                engine.handleTouch(event, surfaceWidth, surfaceHeight);
                drawFrame();
            }
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(drawRunner);
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
                    nextDelay = engine.renderScene(canvas, surfaceWidth, surfaceHeight, false);
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
            if (visible) {
                handler.postDelayed(drawRunner, nextDelay);
            }
        }
    }
}
