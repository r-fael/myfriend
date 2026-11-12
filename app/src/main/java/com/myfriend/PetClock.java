package com.myfriend;

import java.util.Calendar;

/**
 * Hora "de parede" usada pelo bichinho (dormir, bocejar, humor do dia...).
 *
 * <p>O {@link #offsetMs} existe só para o simulador de PC conseguir adiantar o relógio.
 */
public final class PetClock {

    /** Deslocamento em ms aplicado à hora real (0 no relógio). */
    public static volatile long offsetMs = 0L;

    private PetClock() {
    }

    public static long wallMillis() {
        return System.currentTimeMillis() + offsetMs;
    }

    public static Calendar calendar() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(wallMillis());
        return cal;
    }

    public static int hour() {
        return calendar().get(Calendar.HOUR_OF_DAY);
    }

    /** Dia no formato AAAAMMDD (muda à meia-noite local). */
    public static int dayKey() {
        Calendar cal = calendar();
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100
                + cal.get(Calendar.DAY_OF_MONTH);
    }
}
