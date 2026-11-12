package com.myfriend;

import android.content.Context;
import android.graphics.Canvas;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * View principal usada pela {@link MainActivity}.
 *
 * <p>Delega toda a lógica de simulação e desenho na grade 52x52 para o {@link PetEngine},
 * evitando camadas de textura offscreen desnecessárias na GPU do Galaxy Watch 6.
 */
public class FriendView extends View {

    private final PetEngine engine;
    private final float scaledScrollFactor;

    public FriendView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        scaledScrollFactor = ViewConfiguration.get(context).getScaledVerticalScrollFactor();
        engine = new PetEngine(context);
    }

    public void onResume() {
        engine.onResume();
        invalidate();
    }

    public void onPause() {
        engine.onPause();
    }

    public void updateBatteryStatus(int percent, boolean charging) {
        engine.updateBatteryStatus(percent, charging);
        invalidate();
    }

    public void onUserStep(int steps) {
        engine.onUserStep(steps);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        engine.updateGridMetrics(w, h);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_SCROLL
                || !event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            return super.onGenericMotionEvent(event);
        }
        float delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL) * scaledScrollFactor;
        engine.handleRotaryScroll(delta);
        invalidate();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = engine.handleTouch(event, getWidth(), getHeight());
        invalidate();
        return handled;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long nextDelay = engine.renderScene(canvas, getWidth(), getHeight(), false);
        if (nextDelay <= 16L) {
            postInvalidateOnAnimation();
        } else {
            postInvalidateDelayed(nextDelay);
        }
    }
}
