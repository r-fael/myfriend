package com.myfriend;

import static com.myfriend.PetConfig.*;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Confere o bichinho mesmo com o app fechado (só de dia): se ele estiver com fome, sede ou a
 * bateria do relógio estiver baixa, vibra e abre o app.
 *
 * <p>Para acordar o relógio o mínimo possível, a próxima conferida é marcada para quando a fome
 * ou a sede devem chegar no limite (entre 30 min e 2 h), em vez de a cada 30 min fixos.
 *
 * <p>Respeita o modo dormindo ({@code isSleeping}) e aplica intervalo mínimo entre alertas
 * de fome/sede para não drenar bateria nem incomodar à noite.
 */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_CHECK_PET = "com.myfriend.ACTION_CHECK_PET";

    private static final long CHECK_INTERVAL_MS = 30 * 60 * 1000L;
    private static final long MAX_CHECK_INTERVAL_MS = 2 * 60 * 60 * 1000L;
    /** Com a bateria nessa faixa volta a conferir de 30 em 30 min, para não perder os avisos de 30% e 15%. */
    private static final int BATTERY_WATCH_PCT = 45;
    private static final long PET_ALERT_COOLDOWN_MS = 60 * 60 * 1000L;
    private static final int REQUEST_CODE = 1001;

    private static final String KEY_NOTIFIED_BAT_30 = "notifiedBat30";
    private static final String KEY_NOTIFIED_BAT_15 = "notifiedBat15";
    private static final String KEY_LAST_PET_ALERT = "lastPetAlertWall";

    private static final long[] PATTERN_BATTERY_15 = {0, 250, 120, 250, 120, 350};
    private static final long[] PATTERN_BATTERY_30 = {0, 180, 100, 180, 100, 180};
    private static final long[] PATTERN_THIRSTY = {0, 120, 80, 120, 80, 220};
    private static final long[] PATTERN_HUNGRY = {0, 180, 120, 180};

    public static void scheduleNextAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(ACTION_CHECK_PET);
        PendingIntent pending = PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + nextCheckDelayMs(baseDelayMs(context)), pending);
    }

    /** Fome e sede estimadas agora: {@code [0]} fome, {@code [1]} água (o app pode estar fechado há horas). */
    private static float[] estimateNeeds(SharedPreferences prefs, long nowWall) {
        float hoursAway = Math.max(0f,
                (nowWall - prefs.getLong("savedTime", nowWall)) / 3_600_000f);
        return new float[]{
                Math.max(0f, prefs.getFloat("hunger", 85f) - HUNGER_DECAY_PER_HOUR * hoursAway),
                Math.max(0f, prefs.getFloat("water", 80f) - WATER_DECAY_PER_HOUR * hoursAway),
        };
    }

    /** Quanto esperar até a próxima conferida, olhando quando ele vai precisar de cuidado. */
    private static long baseDelayMs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long nowWall = System.currentTimeMillis();
        float[] needs = estimateNeeds(prefs, nowWall);
        long untilCare = (long) (PetState.hoursUntilNeedsCare(needs[0], needs[1]) * 3_600_000f);
        if (untilCare <= 0L) {
            // Já está pedindo cuidado: o próximo aviso só sai depois do intervalo mínimo entre alertas.
            untilCare = prefs.getLong(KEY_LAST_PET_ALERT, 0L) + PET_ALERT_COOLDOWN_MS - nowWall;
        }
        long max = MAX_CHECK_INTERVAL_MS;
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null && !batteryManager.isCharging()
                && batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) <= BATTERY_WATCH_PCT) {
            max = CHECK_INTERVAL_MS;
        }
        // +1 min de folga para a conferida cair logo depois de ele passar do limite.
        return Math.max(CHECK_INTERVAL_MS, Math.min(max, untilCare + 60_000L));
    }

    private static boolean isNightHour(int hour) {
        return hour >= NIGHT_START_HOUR || hour < MORNING_HOUR;
    }

    /** De dia, espera {@code baseMs}; de noite o relógio não acorda: a próxima conferida é às 7h. */
    private static long nextCheckDelayMs(long baseMs) {
        java.util.Calendar next = java.util.Calendar.getInstance();
        next.add(java.util.Calendar.MILLISECOND, (int) baseMs);
        if (!isNightHour(next.get(java.util.Calendar.HOUR_OF_DAY))) {
            return baseMs;
        }
        if (next.get(java.util.Calendar.HOUR_OF_DAY) >= NIGHT_START_HOUR) {
            next.add(java.util.Calendar.DAY_OF_MONTH, 1);
        }
        next.set(java.util.Calendar.HOUR_OF_DAY, MORNING_HOUR);
        next.set(java.util.Calendar.MINUTE, 0);
        next.set(java.util.Calendar.SECOND, 0);
        long delay = next.getTimeInMillis() - System.currentTimeMillis();
        return Math.max(CHECK_INTERVAL_MS, delay);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long nowWall = System.currentTimeMillis();
        float[] needs = estimateNeeds(prefs, nowWall);
        float hunger = needs[0];
        float water = needs[1];
        boolean isSleeping = prefs.getBoolean("isSleeping", false)
                || isNightHour(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY));

        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        int battery = batteryManager != null
                ? batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : 100;
        boolean charging = batteryManager != null && batteryManager.isCharging();

        boolean notified30 = prefs.getBoolean(KEY_NOTIFIED_BAT_30, false);
        boolean notified15 = prefs.getBoolean(KEY_NOTIFIED_BAT_15, false);
        long lastPetAlert = prefs.getLong(KEY_LAST_PET_ALERT, 0L);

        if (battery > 32) {
            notified30 = false;
            notified15 = false;
        }

        long[] pattern = null;
        if (!charging && battery <= 15 && !notified15) {
            pattern = PATTERN_BATTERY_15;
            notified15 = true;
            notified30 = true;
        } else if (!charging && battery <= 30 && !notified30) {
            pattern = PATTERN_BATTERY_30;
            notified30 = true;
        } else if (!isSleeping && nowWall - lastPetAlert >= PET_ALERT_COOLDOWN_MS) {
            if (water < THIRSTY_THRESHOLD) {
                pattern = PATTERN_THIRSTY;
                lastPetAlert = nowWall;
            } else if (hunger < HUNGRY_THRESHOLD) {
                pattern = PATTERN_HUNGRY;
                lastPetAlert = nowWall;
            }
        }

        prefs.edit()
                .putBoolean(KEY_NOTIFIED_BAT_30, notified30)
                .putBoolean(KEY_NOTIFIED_BAT_15, notified15)
                .putLong(KEY_LAST_PET_ALERT, lastPetAlert)
                .apply();
        // Marca a próxima conferida já sabendo se acabou de avisar.
        scheduleNextAlarm(context);

        if (pattern == null) {
            return;
        }
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        }
        try {
            Intent open = new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(open);
        } catch (Exception ignored) {
            // O sistema pode bloquear abrir activity em segundo plano.
        }
    }
}
