package com.myfriend;

import static com.myfriend.PetConfig.*;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Estado persistente e regras de simulação do bichinho (status, moedas, itens e decaimento).
 */
public final class PetState {

    public float hunger = 85f;
    public float water = 80f;
    public float happiness = 90f;
    public float energy = 90f;
    public boolean isSleeping = false;
    public int mealsSincePoop = 0;
    public int poopCount = 0;
    public int coins = INITIAL_COINS;
    public int equippedItem = ITEM_SANTA_HAT;
    /** Bit N ligado = item N comprado. Começa com "nenhum" e gorro de Natal. */
    public int unlockedMask = 0b11;

    private final SharedPreferences prefs;

    public PetState(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void load() {
        hunger = prefs.getFloat("hunger", 85f);
        water = prefs.getFloat("water", 80f);
        happiness = prefs.getFloat("happiness", 90f);
        energy = prefs.getFloat("energy", 90f);
        isSleeping = prefs.getBoolean("isSleeping", false);
        poopCount = prefs.getInt("poopCount", 0);
        mealsSincePoop = prefs.getInt("mealsSincePoop", 0);
        coins = Math.max(0, prefs.getInt("coins", INITIAL_COINS));
        equippedItem = prefs.getInt("equippedItem", ITEM_SANTA_HAT);
        unlockedMask = prefs.getInt("unlockedMask", 0b11) | 0b11;
        if (equippedItem < 0 || equippedItem >= TOTAL_ITEMS) {
            equippedItem = ITEM_NONE;
        }

        long nowWall = System.currentTimeMillis();
        long savedTime = prefs.getLong("savedTime", nowWall);
        long elapsedMs = Math.max(0L, nowWall - savedTime);
        if (elapsedMs <= 0L) {
            return;
        }

        // Usa fração exata de horas em vez de truncar minutos inteiros, evitando perda
        // de decaimento quando o relógio acorda em intervalos curtos (< 60s).
        float hours = elapsedMs / 3_600_000f;
        hunger = clamp(hunger - HUNGER_DECAY_PER_HOUR * hours);
        water = clamp(water - WATER_DECAY_PER_HOUR * hours);
        happiness = clamp(happiness - HAPPINESS_DECAY_PER_HOUR * hours);
        if (isSleeping) {
            energy = clamp(energy + ENERGY_SLEEP_GAIN_PER_HOUR * hours);
            if (energy >= 100f) {
                isSleeping = false;
            }
        } else {
            energy = clamp(energy - ENERGY_DECAY_PER_HOUR * hours);
        }
    }

    public void save() {
        prefs.edit()
                .putFloat("hunger", hunger)
                .putFloat("water", water)
                .putFloat("happiness", happiness)
                .putFloat("energy", energy)
                .putBoolean("isSleeping", isSleeping)
                .putInt("poopCount", poopCount)
                .putInt("mealsSincePoop", mealsSincePoop)
                .putInt("coins", coins)
                .putInt("equippedItem", equippedItem)
                .putInt("unlockedMask", unlockedMask)
                .putLong("savedTime", System.currentTimeMillis())
                .apply();
    }

    public void updateRealtimeDecay(float dtSeconds) {
        hunger = clamp(hunger - 0.015f * dtSeconds);
        water = clamp(water - 0.025f * dtSeconds);
        if (isSleeping) {
            energy = clamp(energy + 0.08f * dtSeconds);
            if (energy >= 100f) {
                isSleeping = false;
            }
        } else {
            energy = clamp(energy - 0.012f * dtSeconds);
            happiness = clamp(happiness - 0.014f * dtSeconds);
        }
    }

    public boolean isUnlocked(int item) {
        return ((unlockedMask >> item) & 1) != 0;
    }

    public static float clamp(float value) {
        return Math.max(0f, Math.min(100f, value));
    }
}
