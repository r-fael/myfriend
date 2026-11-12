package com.myfriend;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

/**
 * Sensores do bichinho, pensados para gastar o mínimo de bateria:
 * <ul>
 *   <li>só ficam ligados com a tela acesa (nunca na tela desligada/ambient);</li>
 *   <li>passos pelo contador de hardware, em lote (o chip junta os eventos por alguns segundos);</li>
 *   <li>acelerômetro em taxa baixa, só para detectar o pulso chacoalhando.</li>
 * </ul>
 */
public final class PetSensors implements SensorEventListener {

    private static final int STEP_BATCH_US = 4_000_000;
    private static final int ACCEL_BATCH_US = 200_000;
    private static final float SHAKE_PEAK = 22f;      // m/s² (a gravidade sozinha é ~9.8)
    private static final float SHAKE_REARM = 15f;
    private static final int SHAKE_PEAKS = 4;
    private static final long SHAKE_WINDOW_MS = 1200L;
    private static final long SHAKE_COOLDOWN_MS = 3000L;

    private final Context context;
    private final PetEngine engine;
    private final Runnable redraw;
    private final SensorManager sensorManager;
    private final Sensor stepCounter;
    private final Sensor accelerometer;

    private boolean running = false;
    private boolean peakArmed = true;
    private int peakCount = 0;
    private long firstPeakTime = 0L;
    private long lastShakeTime = 0L;

    public PetSensors(Context context, PetEngine engine, Runnable redraw) {
        this.context = context;
        this.engine = engine;
        this.redraw = redraw;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        } else {
            stepCounter = null;
            accelerometer = null;
        }
    }

    private boolean granted(String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** Liga com a tela acesa. */
    public void start() {
        if (running || sensorManager == null) {
            return;
        }
        running = true;
        peakCount = 0;
        peakArmed = true;
        if (stepCounter != null && granted(Manifest.permission.ACTIVITY_RECOGNITION)) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL, STEP_BATCH_US);
        }
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI, ACCEL_BATCH_US);
        }
    }

    /** Desliga tudo (tela apagada ou ambient). */
    public void stop() {
        if (sensorManager == null) {
            return;
        }
        running = false;
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values.length == 0) {
            return;
        }
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_STEP_COUNTER) {
            engine.onStepCounterTotal(event.values[0]);
            redraw.run();
        } else if (type == Sensor.TYPE_ACCELEROMETER && event.values.length >= 3) {
            detectShake(event.values[0], event.values[1], event.values[2]);
        }
    }

    private void detectShake(float x, float y, float z) {
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        long now = SystemClock.uptimeMillis();
        if (peakArmed && magnitude > SHAKE_PEAK) {
            peakArmed = false;
            if (peakCount == 0 || now - firstPeakTime > SHAKE_WINDOW_MS) {
                peakCount = 0;
                firstPeakTime = now;
            }
            peakCount++;
            if (peakCount >= SHAKE_PEAKS && now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                lastShakeTime = now;
                peakCount = 0;
                engine.onShake();
                redraw.run();
            }
        } else if (magnitude < SHAKE_REARM) {
            peakArmed = true;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
