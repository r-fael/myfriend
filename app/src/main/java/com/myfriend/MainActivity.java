package com.myfriend;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

/** Tela principal: mostra o bichinho e alimenta ele com passos e status da bateria. */
public class MainActivity extends Activity implements SensorEventListener {

    /** Extra que pede para abrir o seletor de mostrador já com o My Friend escolhido. */
    public static final String EXTRA_SET_WATCH_FACE = "set_watchface";

    private static final int REQUEST_ACTIVITY_RECOGNITION = 101;
    private static final int MAX_STEPS_PER_EVENT = 20;

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private FriendView friendView;
    private float lastStepCounterValue = -1f;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (friendView == null || intent == null) {
                return;
            }
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            if (level >= 0 && scale > 0) {
                friendView.updateBatteryStatus(Math.round(level * 100f / scale), charging);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, REQUEST_ACTIVITY_RECOGNITION);
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            if (stepSensor == null) {
                stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            }
        }

        friendView = new FriendView(this);
        setContentView(friendView);
        friendView.requestFocus();

        ReminderReceiver.scheduleNextAlarm(this);
        handleWatchFaceIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWatchFaceIntent(intent);
    }

    private void handleWatchFaceIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_SET_WATCH_FACE, false)) {
            return;
        }
        intent.removeExtra(EXTRA_SET_WATCH_FACE);
        try {
            Intent picker = new Intent("android.service.wallpaper.CHANGE_LIVE_WALLPAPER");
            picker.putExtra("android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT",
                    new ComponentName(this, FriendWatchFaceService.class));
            startActivity(picker);
        } catch (Exception ignored) {
            // Alguns relógios não têm o seletor de live wallpaper.
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (friendView != null) {
            friendView.onResume();
            friendView.requestFocus();
        }
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sensorManager != null && stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception ignored) {
            // Já estava desregistrado.
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (friendView != null) {
            friendView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (friendView == null || event == null || event.values.length == 0) {
            return;
        }
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_STEP_DETECTOR) {
            friendView.onUserStep(1);
        } else if (type == Sensor.TYPE_STEP_COUNTER) {
            float total = event.values[0];
            if (lastStepCounterValue >= 0f && total > lastStepCounterValue) {
                int steps = Math.min(MAX_STEPS_PER_EVENT, Math.round(total - lastStepCounterValue));
                if (steps > 0) {
                    friendView.onUserStep(steps);
                }
            }
            lastStepCounterValue = total;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
