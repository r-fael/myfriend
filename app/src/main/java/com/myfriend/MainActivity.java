package com.myfriend;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.Window;

/**
 * Tela principal: mostra o bichinho. Não segura a tela acesa (deixa o relógio apagar sozinho
 * para economizar bateria); os sensores ficam no {@link PetSensors} e só rodam com a tela acesa.
 */
public class MainActivity extends Activity {

    /** Extra que pede para abrir o seletor de mostrador já com o My Friend escolhido. */
    public static final String EXTRA_SET_WATCH_FACE = "set_watchface";

    private static final int REQUEST_PERMISSIONS = 101;

    private FriendView friendView;

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
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        for (String permission : new String[]{Manifest.permission.ACTIVITY_RECOGNITION}) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
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
    }

    @Override
    protected void onPause() {
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception ignored) {
            // Já estava desregistrado.
        }
        if (friendView != null) {
            friendView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (friendView != null) {
            // Religa os sensores agora que a permissão pode ter sido dada.
            friendView.restartSensors();
        }
    }
}
