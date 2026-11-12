package com.myfriend;

import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

/** Mostrador do relógio: desenha o mesmo bichinho da tela principal, com relógio. */
public class FriendWatchFaceService extends WallpaperService {

    private static final long DEFAULT_FRAME_DELAY_MS = 450;

    @Override
    public Engine onCreateEngine() {
        return new FriendEngine();
    }

    private class FriendEngine extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable drawRunner = this::drawFrame;
        private FriendView renderer;
        private boolean visible = false;
        private int surfaceWidth = 432;
        private int surfaceHeight = 432;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);
            renderer = new FriendView(FriendWatchFaceService.this);
            renderer.setWatchFaceMode(true);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            surfaceWidth = width;
            surfaceHeight = height;
            if (renderer != null) {
                renderer.updateGridMetrics(width, height);
            }
            drawFrame();
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            super.onVisibilityChanged(isVisible);
            visible = isVisible;
            handler.removeCallbacks(drawRunner);
            if (renderer == null) {
                return;
            }
            if (isVisible) {
                renderer.onResume();
                renderer.updateGridMetrics(surfaceWidth, surfaceHeight);
                drawFrame();
            } else {
                renderer.onPause();
            }
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            if (renderer != null && visible) {
                renderer.handleTouch(event, surfaceWidth, surfaceHeight);
                drawFrame();
            }
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(drawRunner);
            if (renderer != null) {
                renderer.onPause();
            }
            super.onDestroy();
        }

        private void drawFrame() {
            handler.removeCallbacks(drawRunner);
            if (!visible || renderer == null) {
                return;
            }
            SurfaceHolder holder = getSurfaceHolder();
            long nextDelay = DEFAULT_FRAME_DELAY_MS;
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    nextDelay = renderer.renderScene(canvas, surfaceWidth, surfaceHeight, false);
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
