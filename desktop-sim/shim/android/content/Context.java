package android.content;

import android.os.BatteryManager;
import android.os.Vibrator;
import java.io.File;

/** Imitação mínima do Context: preferências num arquivo, bateria e vibração simuladas. */
public class Context {
    public static final String VIBRATOR_SERVICE = "vibrator";
    public static final String BATTERY_SERVICE = "batterymanager";
    public static final int MODE_PRIVATE = 0;

    private final SharedPreferences prefs;
    private final BatteryManager battery = new BatteryManager();
    private final Vibrator vibrator = new Vibrator();

    public Context(File prefsFile) {
        this.prefs = new SharedPreferences(prefsFile);
    }

    public Context getApplicationContext() {
        return this;
    }

    public SharedPreferences getSharedPreferences(String name, int mode) {
        return prefs;
    }

    public Object getSystemService(String name) {
        if (BATTERY_SERVICE.equals(name)) {
            return battery;
        }
        if (VIBRATOR_SERVICE.equals(name)) {
            return vibrator;
        }
        return null;
    }

    public BatteryManager battery() {
        return battery;
    }

    public Vibrator vibrator() {
        return vibrator;
    }
}
