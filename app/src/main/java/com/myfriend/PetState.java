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
    /** Item equipado em cada categoria (cabeça, nariz, olhos, pescoço, cor, fundo). */
    public final int[] equipped = new int[CATEGORY_COUNT];
    /** Por categoria: bit N ligado = item N comprado. "Nada" vem sempre liberado. */
    public final int[] unlocked = new int[CATEGORY_COUNT];

    // Passos do dia (contador do sistema é acumulado desde que o relógio ligou).
    public int stepDay = 0;
    public float stepBase = 0f;
    public float lastStepCounter = -1f;
    public int stepsToday = 0;
    public int goalDay = 0;

    // Rotina.
    public int stretchDay = 0;
    public long lastSeenWall = 0L;
    /** Dormiu sozinho à noite (acorda se espreguiçando de manhã). */
    public boolean nightSleep = false;

    private static final String[] KEY_EQUIPPED = {"equippedHead", "equippedNose", "equippedEyes",
            "equippedNeck", "equippedHold", "equippedBg"};
    private static final String[] KEY_UNLOCKED = {"unlockedHead", "unlockedNose", "unlockedEyes",
            "unlockedNeck", "unlockedHold", "unlockedBg"};

    {
        equipped[CATEGORY_HEAD] = HEAD_SANTA_HAT;
        for (int c = 0; c < CATEGORY_COUNT; c++) {
            unlocked[c] = c == CATEGORY_HEAD ? 0b11 : 0b1;
        }
    }

    /**
     * Como os itens da loja antiga (uma lista só, v1.6) viram itens por categoria.
     * {categoria, item}; o palhaço antigo vira cabelo + nariz. -1 = item que deixou de existir.
     */
    private static final int[][][] LEGACY_ITEMS = {
            {},                                                   // 0 nada
            {{CATEGORY_HEAD, HEAD_SANTA_HAT}},                    // 1
            {{CATEGORY_HEAD, HEAD_WITCH_HAT}},                    // 2
            {{CATEGORY_HEAD, HEAD_PUMPKIN}},                      // 3
            {{CATEGORY_HEAD, HEAD_DEVIL_HORNS}},                  // 4
            {{CATEGORY_EYES, EYES_SUNGLASSES}},                   // 5
            {{CATEGORY_HEAD, HEAD_CROWN}},                        // 6
            {{CATEGORY_HEAD, HEAD_WIZARD_HAT}},                   // 7
            {},                                                   // 8 gravatinha (removida)
            {{CATEGORY_HEAD, HEAD_HEADPHONES}},                   // 9
            {{CATEGORY_HEAD, HEAD_HEADBAND}},                     // 10
            {{CATEGORY_HEAD, HEAD_BUNNY_EARS}},                   // 11
            {{CATEGORY_HEAD, HEAD_BIRTHDAY_HAT}},                 // 12
            {},                                                   // 13 carnaval (removido)
            {{CATEGORY_HEAD, HEAD_HALO}},                         // 14
            {{CATEGORY_HEAD, HEAD_STRAW_HAT}},                    // 15
            {{CATEGORY_EYES, EYES_PARTY_GLASSES}},                // 16
            {{CATEGORY_HEAD, HEAD_CLOWN_HAIR}, {CATEGORY_NOSE, NOSE_CLOWN}}, // 17
    };

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
        stepDay = prefs.getInt("stepDay", 0);
        stepBase = prefs.getFloat("stepBase", 0f);
        lastStepCounter = prefs.getFloat("lastStepCounter", -1f);
        stepsToday = prefs.getInt("stepsToday", 0);
        goalDay = prefs.getInt("goalDay", 0);
        stretchDay = prefs.getInt("stretchDay", 0);
        lastSeenWall = prefs.getLong("lastSeenWall", 0L);
        nightSleep = prefs.getBoolean("nightSleep", false);
        if (!prefs.contains(KEY_EQUIPPED[CATEGORY_HEAD]) && prefs.contains("unlockedMask")) {
            migrateLegacyItems();
        } else {
            for (int c = 0; c < CATEGORY_COUNT; c++) {
                equipped[c] = prefs.getInt(KEY_EQUIPPED[c], c == CATEGORY_HEAD ? HEAD_SANTA_HAT : ITEM_NONE);
                unlocked[c] = prefs.getInt(KEY_UNLOCKED[c], c == CATEGORY_HEAD ? 0b11 : 0b1);
            }
            int itemsVersion = prefs.getInt("itemsVersion", 1);
            if (itemsVersion < 2) {
                removeHeadItem(REMOVED_HEAD_CARNIVAL, REMOVED_CARNIVAL_PRICE);
            }
            if (itemsVersion < 3) {
                removeHeadItem(REMOVED_HEAD_DIVINE_WHEEL, REMOVED_DIVINE_WHEEL_PRICE);
            }
            if (itemsVersion < 4) {
                // A loja de cores saiu: devolve as moedas de cada cor comprada.
                int colors = prefs.getInt("unlockedColor", 0b1);
                for (int i = 1; i < REMOVED_COLOR_PRICES.length; i++) {
                    if (((colors >> i) & 1) != 0) {
                        coins += REMOVED_COLOR_PRICES[i];
                    }
                }
            }
        }
        for (int c = 0; c < CATEGORY_COUNT; c++) {
            unlocked[c] |= 1; // "nada" sempre disponível
            if (equipped[c] < 0 || equipped[c] >= itemCount(c) || !isUnlocked(c, equipped[c])) {
                equipped[c] = ITEM_NONE;
            }
        }

        long nowWall = System.currentTimeMillis();
        long savedTime = prefs.getLong("savedTime", nowWall);
        long elapsedMs = Math.max(0L, nowWall - savedTime);
        if (elapsedMs <= 0L) {
            return;
        }

        // Usa fração exata de horas em vez de truncar minutos inteiros, evitando perda
        // de decaimento quando o relógio acorda em intervalos curtos (< 60s).
        passTime(elapsedMs / 3_600_000f);
    }

    public void save() {
        SharedPreferences.Editor editor = prefs.edit();
        for (int c = 0; c < CATEGORY_COUNT; c++) {
            editor.putInt(KEY_EQUIPPED[c], equipped[c]);
            editor.putInt(KEY_UNLOCKED[c], unlocked[c]);
        }
        editor.putFloat("hunger", hunger)
                .putFloat("water", water)
                .putFloat("happiness", happiness)
                .putFloat("energy", energy)
                .putBoolean("isSleeping", isSleeping)
                .putInt("poopCount", poopCount)
                .putInt("mealsSincePoop", mealsSincePoop)
                .putInt("coins", coins)
                .putInt("stepDay", stepDay)
                .putFloat("stepBase", stepBase)
                .putFloat("lastStepCounter", lastStepCounter)
                .putInt("stepsToday", stepsToday)
                .putInt("goalDay", goalDay)
                .putInt("stretchDay", stretchDay)
                .putLong("lastSeenWall", lastSeenWall)
                .putBoolean("nightSleep", nightSleep)
                .putInt("itemsVersion", 4)
                .remove("equippedItem")
                .remove("unlockedMask")
                .remove("equippedColor")
                .remove("unlockedColor")
                .putLong("savedTime", System.currentTimeMillis())
                .apply();
    }

    public void updateRealtimeDecay(float dtSeconds) {
        passTime(dtSeconds / 3600f);
    }

    /**
     * Passa o tempo (tela acesa ou apagada, mesma conta). Dormindo, fome/água/felicidade
     * caem bem devagar e a energia enche; se acordar no meio (energia cheia de dia),
     * o resto do tempo conta como acordado.
     */
    private void passTime(float hours) {
        if (hours <= 0f) {
            return;
        }
        if (isSleeping) {
            float sleepHours = hours;
            if (!nightSleep) {
                // Soneca de dia: acorda quando a energia enche.
                sleepHours = Math.min(hours, (100f - energy) / ENERGY_SLEEP_GAIN_PER_HOUR);
            }
            hunger = clamp(hunger - HUNGER_SLEEP_DECAY_PER_HOUR * sleepHours);
            water = clamp(water - WATER_SLEEP_DECAY_PER_HOUR * sleepHours);
            happiness = clamp(happiness - HAPPINESS_SLEEP_DECAY_PER_HOUR * sleepHours);
            energy = clamp(energy + ENERGY_SLEEP_GAIN_PER_HOUR * sleepHours);
            hours -= sleepHours;
            if (hours <= 0f) {
                return;
            }
            isSleeping = false; // acordou com a energia cheia
        }
        hunger = clamp(hunger - HUNGER_DECAY_PER_HOUR * hours);
        water = clamp(water - WATER_DECAY_PER_HOUR * hours);
        happiness = clamp(happiness - HAPPINESS_DECAY_PER_HOUR * hours);
        energy = clamp(energy - ENERGY_DECAY_PER_HOUR * hours);
    }

    public boolean isUnlocked(int category, int item) {
        return ((unlocked[category] >> item) & 1) != 0;
    }

    public void unlock(int category, int item) {
        unlocked[category] |= 1 << item;
    }

    /**
     * Tira um item de cabeça que saiu da loja (cocar na v1.9, roda divina na v1.10): quem tinha
     * recebe as moedas de volta e os itens depois dele descem uma posição.
     */
    private void removeHeadItem(int id, int refund) {
        int mask = unlocked[CATEGORY_HEAD];
        if (((mask >> id) & 1) != 0) {
            coins += refund;
        }
        int low = mask & ((1 << id) - 1);
        int high = (mask >>> (id + 1)) << id;
        unlocked[CATEGORY_HEAD] = low | high;
        int eq = equipped[CATEGORY_HEAD];
        equipped[CATEGORY_HEAD] = eq == id ? ITEM_NONE : (eq > id ? eq - 1 : eq);
    }

    private void migrateLegacyItems() {
        int oldMask = prefs.getInt("unlockedMask", 0b11);
        int oldEquipped = prefs.getInt("equippedItem", 1);
        for (int c = 0; c < CATEGORY_COUNT; c++) {
            equipped[c] = ITEM_NONE;
            unlocked[c] = 1;
        }
        for (int old = 1; old < LEGACY_ITEMS.length; old++) {
            boolean owned = ((oldMask >> old) & 1) != 0;
            for (int[] mapped : LEGACY_ITEMS[old]) {
                if (owned) {
                    unlocked[mapped[0]] |= 1 << mapped[1];
                }
                if (old == oldEquipped) {
                    equipped[mapped[0]] = mapped[1];
                }
            }
        }
    }

    public static float clamp(float value) {
        return Math.max(0f, Math.min(100f, value));
    }
}
