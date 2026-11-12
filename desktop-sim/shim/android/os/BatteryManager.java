package android.os;

/** Bateria simulada (o simulador muda pelas teclas). */
public class BatteryManager {
    public static final int BATTERY_PROPERTY_CAPACITY = 4;

    public int level = 80;
    public boolean charging = false;

    public int getIntProperty(int id) {
        return level;
    }

    public boolean isCharging() {
        return charging;
    }
}
