package com.myfriend;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import java.util.TimeZone;

/**
 * O bichinho virtual. Tudo é desenhado numa grade de 52x52 "pixels" grandes que se ajusta à tela.
 *
 * <p>Usada tanto pela {@link MainActivity} (como View normal) quanto pelo
 * {@link FriendWatchFaceService} (chamando {@link #renderScene} direto numa Surface).
 */
public class FriendView extends View {

    // ---------------------------------------------------------------- Persistência

    public static final String PREFS_NAME = "myfriend_prefs";

    /** Quanto cada status cai por hora com o app fechado. */
    public static final float HUNGER_DECAY_PER_HOUR = 9f;
    public static final float WATER_DECAY_PER_HOUR = 22f;
    private static final float HAPPINESS_DECAY_PER_HOUR = 6f;
    private static final float ENERGY_DECAY_PER_HOUR = 7f;
    private static final float ENERGY_SLEEP_GAIN_PER_HOUR = 25f;

    public static final float THIRSTY_THRESHOLD = 35f;
    private static final float HUNGRY_THRESHOLD = 32f;
    private static final float SLEEPY_THRESHOLD = 25f;
    private static final float SAD_THRESHOLD = 30f;

    private static final int MIN_COINS_ON_LOAD = 500;

    // ---------------------------------------------------------------- Grade

    private static final int GRID_SIZE = 52;
    private static final long HUD_VISIBLE_MS = 4500;
    private static final long WALKING_WINDOW_MS = 4000;

    // ---------------------------------------------------------------- Itens da loja

    public static final int ITEM_NONE = 0;
    public static final int ITEM_SANTA_HAT = 1;
    public static final int ITEM_HALLOWEEN_WITCH = 2;
    public static final int ITEM_HALLOWEEN_PUMPKIN = 3;
    public static final int ITEM_DEVIL_HORNS = 4;
    public static final int ITEM_SUNGLASSES = 5;
    public static final int ITEM_CROWN = 6;
    public static final int ITEM_WIZARD_HAT = 7;
    public static final int ITEM_BOWTIE = 8;
    public static final int ITEM_HEADPHONES = 9;
    public static final int ITEM_HEADBAND = 10;
    public static final int ITEM_EASTER_BUNNY = 11;
    public static final int ITEM_BIRTHDAY_HAT = 12;
    public static final int ITEM_CARNIVAL = 13;
    public static final int ITEM_VALENTINE_HALO = 14;
    public static final int ITEM_FESTA_JUNINA = 15;
    public static final int ITEM_NEW_YEAR_STARS = 16;
    public static final int ITEM_CLOWN = 17;
    public static final int TOTAL_ITEMS = 18;

    private static final int[] ITEM_PRICES = {
            0,  // nenhum
            40, // gorro de Natal
            45, // bruxa
            45, // abóbora
            35, // chifrinhos
            50, // óculos escuros
            80, // coroa
            55, // mago
            30, // gravatinha
            60, // fone
            25, // faixa
            40, // orelhas de coelho
            35, // chapéu de aniversário
            50, // carnaval
            45, // auréola
            40, // chapéu de palha (festa junina)
            50, // óculos de ano novo
            60, // palhaço
    };

    // ---------------------------------------------------------------- Humores

    public static final int MOOD_HAPPY = 0;
    public static final int MOOD_LOVED = 1;
    public static final int MOOD_HUNGRY = 2;
    public static final int MOOD_SLEEPY = 3;
    public static final int MOOD_SLEEPING = 4;
    public static final int MOOD_SAD = 5;
    public static final int MOOD_GRUMPY = 6;
    public static final int MOOD_SURPRISED = 7;
    public static final int MOOD_EATING = 8;
    public static final int MOOD_WINK = 9;
    public static final int MOOD_THIRSTY = 10;
    public static final int MOOD_DRINKING = 11;
    public static final int MOOD_SHOWERING = 12;
    public static final int MOOD_LOW_BATTERY = 13;
    private static final int MOOD_NONE = -1;

    // ---------------------------------------------------------------- Partículas

    private static final int MAX_PARTICLES = 12;
    private static final long PARTICLE_LIFETIME_MS = 1350;
    private static final int PARTICLE_HEART = 1;
    private static final int PARTICLE_Z = 2;
    private static final int PARTICLE_SPARKLE = 3;
    private static final int PARTICLE_APPLE = 4;
    private static final int PARTICLE_DROP = 5;
    private static final int PARTICLE_SHOWER = 6;

    // ---------------------------------------------------------------- Sprites 5x5 / 3x5 (bits por linha)

    private static final int[] SPRITE_HEART = {10, 31, 31, 14, 4};
    private static final int[] SPRITE_BOLT = {6, 12, 31, 6, 12};
    private static final int[] SPRITE_MOON = {14, 24, 24, 24, 14};
    private static final int[] SPRITE_Z = {31, 2, 4, 8, 31};
    private static final int[] SPRITE_SPARKLE = {4, 14, 27, 14, 4};
    private static final int[] SPRITE_WATER_DROP = {4, 4, 14, 31, 14};
    private static final int[] SPRITE_SHOP_CROWN = {21, 31, 31, 31, 0};
    private static final int[][] DIGITS_3X5 = {
            {7, 5, 5, 5, 7}, {2, 6, 2, 2, 7}, {7, 1, 7, 4, 7}, {7, 1, 7, 1, 7}, {5, 5, 7, 1, 1},
            {7, 4, 7, 1, 7}, {7, 4, 7, 5, 7}, {7, 1, 1, 1, 1}, {7, 5, 7, 5, 7}, {7, 5, 7, 1, 7},
    };

    // ---------------------------------------------------------------- Cores

    private final Paint whitePaint = makePaint(0xFFFFFFFF);
    private final Paint dimPaint = makePaint(0xFF444444);
    private final Paint cyanPaint = makePaint(0xFF00E5FF);
    private final Paint deepBluePaint = makePaint(0xFF0091EA);
    private final Paint pinkPaint = makePaint(0xFFFF4081);
    private final Paint blushPaint = makePaint(0xFFFF80AB);
    private final Paint goldPaint = makePaint(0xFFFFD740);
    private final Paint redPaint = makePaint(0xFFFF5252);
    private final Paint greenPaint = makePaint(0xFF69F0AE);
    private final Paint orangePaint = makePaint(0xFFFFAB40);
    private final Paint lavenderPaint = makePaint(0xFF82B1FF);
    private final Paint purplePaint = makePaint(0xFFB388FF);
    private final Paint brownPaint = makePaint(0xFF8D6E63);
    private final Paint clownRedPaint = makePaint(0xFFFF1744);
    private final Paint clownHairPaint = makePaint(0xFFFF6D00);
    private final Paint blackPaint = makePaint(0xFF000000);

    // ---------------------------------------------------------------- Estado do bichinho (salvo)

    private float hunger = 85f;
    private float water = 80f;
    private float happiness = 90f;
    private float energy = 90f;
    private boolean isSleeping = false;
    private int mealsSincePoop = 0;
    private int poopCount = 0;
    private int coins = 200;
    private int equippedItem = ITEM_SANTA_HAT;
    /** Bit N ligado = item N comprado. Começa com "nenhum" e gorro de Natal. */
    private int unlockedMask = 0b11;

    // ---------------------------------------------------------------- Estado da tela

    private boolean shopOpen = false;
    private int shopPreviewItem = ITEM_SANTA_HAT;
    private boolean isWatchFaceMode = false;
    private boolean showBatteryInsteadOfClock = false;

    private int watchBatteryPct = 100;
    private boolean isWatchCharging = false;
    private boolean vibratedAt30 = false;
    private boolean vibratedAt15 = false;

    private long lastStepTime = 0;
    private long lastHungerVibrateTime = 0;
    private long lastWaterVibrateTime = 0;
    private long lastInteractionTime = 0;
    private long lastFrameTime = 0;

    private int forcedMood = MOOD_NONE;
    private int actionMood = MOOD_NONE;
    private long actionMoodUntil = 0;
    private long actionStartTime = 0;

    private int rapidPokeCount = 0;
    private long lastPokeTime = 0;
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private float petDistanceAccum = 0f;

    // Olhar, piscar e pulinho
    private float lookX = 0f;
    private float lookY = 0f;
    private float targetLookX = 0f;
    private float targetLookY = 0f;
    private long nextSaccadeTime = 0;
    private long nextBlinkTime = 0;
    private boolean isBlinking = false;
    private long blinkEndTime = 0;
    private float bounceY = 0f;
    private float bounceVelY = 0f;
    private float squash = 0f;
    private int chewStep = 0;
    private long lastChewTime = 0;

    // Partículas (corações, Zzz, brilhos...)
    private final float[] partX = new float[MAX_PARTICLES];
    private final float[] partY = new float[MAX_PARTICLES];
    private final float[] partVy = new float[MAX_PARTICLES];
    private final int[] partType = new int[MAX_PARTICLES];
    private final long[] partExpire = new long[MAX_PARTICLES];

    // Métricas da grade
    private float cellSize = 8f;
    private float originX = 0f;
    private float originY = 0f;
    private float cellDrawSize = 7f;

    private final TimeZone localTz = TimeZone.getDefault();
    private final Vibrator vibrator;
    private final float scaledScrollFactor;

    public FriendView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        scaledScrollFactor = ViewConfiguration.get(context).getScaledVerticalScrollFactor();

        refreshBatteryDirect();
        loadState();

        long now = SystemClock.uptimeMillis();
        lastInteractionTime = now;
        lastFrameTime = now;
        nextBlinkTime = now + 2000;
        nextSaccadeTime = now + 1500;
    }

    private static Paint makePaint(int color) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(false);
        return paint;
    }

    public void setWatchFaceMode(boolean watchFaceMode) {
        isWatchFaceMode = watchFaceMode;
    }

    // ================================================================= Ciclo de vida

    public void onResume() {
        refreshBatteryDirect();
        loadState();
        long now = SystemClock.uptimeMillis();
        lastFrameTime = now;
        lastInteractionTime = now;
        showBatteryInsteadOfClock = false;
        invalidate();
    }

    public void onPause() {
        saveState();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        updateGridMetrics(w, h);
    }

    public void updateGridMetrics(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        cellSize = Math.min(width, height) / (float) GRID_SIZE;
        originX = (width - cellSize * GRID_SIZE) * 0.5f;
        originY = (height - cellSize * GRID_SIZE) * 0.5f;
        // Deixa um vãozinho entre os pixels para dar cara de tela de LCD.
        cellDrawSize = cellSize - (cellSize > 5f ? 1.2f : 0.6f);
    }

    // ================================================================= Salvar / carregar

    public void loadState() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        hunger = prefs.getFloat("hunger", 85f);
        water = prefs.getFloat("water", 80f);
        happiness = prefs.getFloat("happiness", 90f);
        energy = prefs.getFloat("energy", 90f);
        isSleeping = prefs.getBoolean("isSleeping", false);
        poopCount = prefs.getInt("poopCount", 0);
        mealsSincePoop = prefs.getInt("mealsSincePoop", 0);
        coins = Math.max(MIN_COINS_ON_LOAD, prefs.getInt("coins", MIN_COINS_ON_LOAD));
        equippedItem = prefs.getInt("equippedItem", ITEM_SANTA_HAT);
        unlockedMask = prefs.getInt("unlockedMask", 0b11);
        if (equippedItem < 0 || equippedItem >= TOTAL_ITEMS) {
            equippedItem = ITEM_NONE;
        }

        // Aplica o tempo que o app ficou fechado.
        long minutesAway = Math.max(0L,
                (System.currentTimeMillis() - prefs.getLong("savedTime", System.currentTimeMillis())) / 60_000);
        if (minutesAway <= 0) {
            return;
        }
        float hours = minutesAway / 60f;
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

    public void saveState() {
        getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
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

    private static float clamp(float value) {
        return Math.max(0f, Math.min(100f, value));
    }

    // ================================================================= Bateria e passos

    public void refreshBatteryDirect() {
        BatteryManager batteryManager = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager == null) {
            return;
        }
        int capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (capacity > 0) {
            watchBatteryPct = capacity;
        }
        isWatchCharging = batteryManager.isCharging();
    }

    public void updateBatteryStatus(int percent, boolean charging) {
        int pct = Math.max(0, Math.min(100, percent));
        boolean wasCharging = isWatchCharging;
        watchBatteryPct = pct;
        isWatchCharging = charging;

        if (pct > 32) {
            vibratedAt30 = false;
            vibratedAt15 = false;
        }
        if (!charging) {
            if (pct <= 15 && !vibratedAt15) {
                vibratedAt15 = true;
                vibratedAt30 = true;
                hapticPattern(new long[]{0, 260, 120, 260, 120, 380});
                triggerBounce(-0.9f, 0.3f);
            } else if (pct <= 30 && !vibratedAt30) {
                vibratedAt30 = true;
                hapticPattern(new long[]{0, 180, 100, 180, 100, 180});
                triggerBounce(-0.7f, 0.2f);
            }
        } else if (!wasCharging) {
            // Acabou de ligar no carregador: comemora.
            long now = SystemClock.uptimeMillis();
            spawnParticle(PARTICLE_SPARKLE, 14, 14, now);
            spawnParticle(PARTICLE_SPARKLE, 35, 14, now);
            triggerBounce(-1.0f, -0.25f);
            hapticTick(25);
        }
        invalidate();
    }

    /** Cada passo dá 2 moedas e um pouquinho de felicidade. */
    public void onUserStep(int steps) {
        long now = SystemClock.uptimeMillis();
        lastStepTime = now;
        coins += steps * 2;
        happiness = clamp(happiness + steps * 0.5f);
        triggerBounce(-0.45f, -0.12f);
        if (steps > 0 && now % 3 == 0) {
            spawnParticle(PARTICLE_SPARKLE, (now % 28) + 11, 14, now);
        }
        invalidate();
    }

    // ================================================================= Vibração

    private void hapticTick(int millis) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {
            // Sem permissão ou vibrador ocupado.
        }
    }

    private void hapticPattern(long[] pattern) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        try {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } catch (Exception ignored) {
            // Idem.
        }
    }

    // ================================================================= Lógica do bichinho

    private int getCurrentMood(long now) {
        if (actionMood != MOOD_NONE && now < actionMoodUntil) {
            return actionMood;
        }
        if (forcedMood != MOOD_NONE) {
            return forcedMood;
        }
        if (isSleeping) {
            return MOOD_SLEEPING;
        }
        if (!isWatchCharging && watchBatteryPct <= 15) {
            return MOOD_LOW_BATTERY;
        }
        if (water < THIRSTY_THRESHOLD) {
            return MOOD_THIRSTY;
        }
        if (hunger < HUNGRY_THRESHOLD) {
            return MOOD_HUNGRY;
        }
        if (energy < SLEEPY_THRESHOLD) {
            return MOOD_SLEEPY;
        }
        if (happiness < SAD_THRESHOLD || poopCount >= 2) {
            return MOOD_SAD;
        }
        return MOOD_HAPPY;
    }

    private void spawnParticle(int type, float x, float y, long now) {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (now >= partExpire[i]) {
                partType[i] = type;
                partX[i] = x;
                partY[i] = y;
                partVy[i] = type == PARTICLE_SHOWER ? 0.45f : -0.35f; // chuveiro cai, o resto sobe
                partExpire[i] = now + PARTICLE_LIFETIME_MS;
                return;
            }
        }
    }

    private void triggerBounce(float velocity, float squashAmount) {
        bounceVelY = velocity;
        squash = squashAmount;
    }

    private void startAction(int mood, long now, long durationMs) {
        actionMood = mood;
        actionStartTime = now;
        actionMoodUntil = now + durationMs;
    }

    private void feedPet(long now) {
        isSleeping = false;
        forcedMood = MOOD_NONE;
        hunger = clamp(hunger + 28f);
        happiness = clamp(happiness + 8f);
        coins += 5;
        mealsSincePoop++;
        if (mealsSincePoop >= 2 && poopCount < 3) {
            mealsSincePoop = 0;
            poopCount++;
        }
        startAction(MOOD_EATING, now, 2000);
        chewStep = 0;
        lastChewTime = now;
        triggerBounce(-0.9f, 0.25f);
        spawnParticle(PARTICLE_APPLE, 37, 16, now);
        spawnParticle(PARTICLE_HEART, 11, 15, now);
        hapticTick(25);
        saveState();
    }

    private void giveWater(long now) {
        isSleeping = false;
        forcedMood = MOOD_NONE;
        water = 100f;
        happiness = clamp(happiness + 10f);
        coins += 8;
        startAction(MOOD_DRINKING, now, 2400);
        chewStep = 0;
        lastChewTime = now;
        triggerBounce(-0.85f, -0.2f);
        spawnParticle(PARTICLE_DROP, 11, 15, now);
        spawnParticle(PARTICLE_DROP, 37, 13, now);
        spawnParticle(PARTICLE_SPARKLE, 14, 11, now);
        hapticPattern(new long[]{0, 35, 70, 35});
        saveState();
    }

    private void cleanPoopShower(long now) {
        isSleeping = false;
        poopCount = 0;
        mealsSincePoop = 0;
        happiness = clamp(happiness + 20f);
        coins += 15;
        startAction(MOOD_SHOWERING, now, 2000);
        triggerBounce(-1.0f, -0.25f);
        for (int i = 0; i < 6; i++) {
            spawnParticle(PARTICLE_SHOWER, i * 5 + 12, (i % 3) * 2 + 6, now);
        }
        spawnParticle(PARTICLE_SPARKLE, 36, 14, now);
        hapticPattern(new long[]{0, 30, 50, 30, 50, 45});
        saveState();
    }

    private void lovePet(long now) {
        isSleeping = false;
        forcedMood = MOOD_NONE;
        happiness = clamp(happiness + 22f);
        coins += 3;
        startAction(now % 2 == 0 ? MOOD_LOVED : MOOD_WINK, now, 1800);
        triggerBounce(-1.1f, -0.25f);
        spawnParticle(PARTICLE_HEART, (now % 7) + 11, 15, now);
        spawnParticle(PARTICLE_HEART, (now % 6) + 35, 13, now);
        hapticTick(20);
        saveState();
    }

    private void toggleSleep(long now) {
        forcedMood = MOOD_NONE;
        isSleeping = !isSleeping;
        if (isSleeping) {
            actionMood = MOOD_NONE;
            targetLookX = 0f;
            targetLookY = 0f;
            spawnParticle(PARTICLE_Z, 35, 16, now);
        } else {
            startAction(MOOD_SURPRISED, now, 900);
            triggerBounce(-0.8f, -0.2f);
        }
        hapticTick(20);
        saveState();
    }

    private void toggleShop() {
        shopOpen = !shopOpen;
        if (shopOpen) {
            isSleeping = false;
            shopPreviewItem = equippedItem;
            triggerBounce(-0.6f, 0.15f);
        }
        hapticTick(20);
    }

    private boolean isUnlocked(int item) {
        return ((unlockedMask >> item) & 1) != 0;
    }

    private void shopStep(int delta) {
        shopPreviewItem = ((shopPreviewItem + delta) % TOTAL_ITEMS + TOTAL_ITEMS) % TOTAL_ITEMS;
        triggerBounce(-0.4f, 0.12f);
    }

    private void buyOrEquipPreviewItem(long now) {
        int item = shopPreviewItem;
        if (isUnlocked(item)) {
            equippedItem = item;
            shopOpen = false;
            startAction(MOOD_LOVED, now, 1400);
            triggerBounce(-0.9f, -0.2f);
            spawnParticle(PARTICLE_SPARKLE, 14, 14, now);
            spawnParticle(PARTICLE_SPARKLE, 35, 14, now);
            hapticTick(25);
            saveState();
            return;
        }
        int price = ITEM_PRICES[item];
        if (coins >= price) {
            coins -= price;
            unlockedMask |= 1 << item;
            equippedItem = item;
            shopOpen = false;
            startAction(MOOD_LOVED, now, 1600);
            triggerBounce(-1.1f, -0.25f);
            spawnParticle(PARTICLE_SPARKLE, 12, 14, now);
            spawnParticle(PARTICLE_HEART, 36, 14, now);
            hapticPattern(new long[]{0, 40, 60, 60});
            saveState();
            return;
        }
        // Sem moedas: balança "não".
        triggerBounce(-0.4f, 0.25f);
        hapticPattern(new long[]{0, 80, 60, 80});
    }

    // ================================================================= Entrada (coroa giratória e toque)

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_SCROLL
                || !event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            return super.onGenericMotionEvent(event);
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastInteractionTime >= HUD_VISIBLE_MS) {
            showBatteryInsteadOfClock = false;
        }
        lastInteractionTime = now;

        float delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL) * scaledScrollFactor;
        if (shopOpen) {
            if (delta > 0) {
                shopStep(1);
            } else if (delta < 0) {
                shopStep(-1);
            }
            hapticTick(12);
            invalidate();
            return true;
        }

        // Fora da loja, girar a coroa faz carinho e o bichinho olha para o lado.
        isSleeping = false;
        happiness = clamp(happiness + 4f);
        coins++;
        actionMood = MOOD_LOVED;
        actionMoodUntil = now + 1400;
        targetLookX = delta > 0 ? 2.5f : -2.5f;
        triggerBounce(-0.5f, 0.18f);
        spawnParticle(PARTICLE_HEART, delta > 0 ? 35 : 12, 15, now);
        hapticTick(12);
        invalidate();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = handleTouch(event, getWidth(), getHeight());
        invalidate();
        return handled;
    }

    /**
     * Mapa de toque (em fração da tela):
     * <ul>
     *   <li>Topo (y &lt; 22%), com HUD visível: alterna relógio/bateria.</li>
     *   <li>Cocô (canto esquerdo, 48–74% da altura): banho.</li>
     *   <li>Meio (y ≤ 73%): cutucar. 5 cutucadas rápidas = bravo.</li>
     *   <li>Barra de baixo: comer | água | carinho | dormir | loja.</li>
     *   <li>Arrastar o dedo: fazer carinho.</li>
     * </ul>
     */
    public boolean handleTouch(MotionEvent event, int width, int height) {
        if (width <= 0 || height <= 0) {
            return true;
        }
        long now = SystemClock.uptimeMillis();
        boolean wasIdle = now - lastInteractionTime >= HUD_VISIBLE_MS;
        lastInteractionTime = now;
        if (wasIdle) {
            showBatteryInsteadOfClock = false;
        }

        float x = event.getX();
        float y = event.getY();
        float fx = x / width;
        float fy = y / height;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = x;
                lastTouchY = y;
                petDistanceAccum = 0f;
                if (shopOpen) {
                    handleShopTap(fx, fy, now);
                } else {
                    handlePetTap(fx, fy, wasIdle, now);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (!shopOpen) {
                    handlePetDrag(x, y, width, height, now);
                }
                break;

            default:
                break;
        }
        return true;
    }

    private void handleShopTap(float fx, float fy, long now) {
        boolean bottomRow = fy > 0.68f;
        if (bottomRow && fx >= 0.28f && fx <= 0.72f) {
            buyOrEquipPreviewItem(now);
            return;
        }
        if (fx < 0.3f) {
            shopStep(-1);
        } else if (fx > 0.7f) {
            shopStep(1);
        } else {
            shopOpen = false;
        }
        hapticTick(15);
    }

    private void handlePetTap(float fx, float fy, boolean wasIdle, long now) {
        if (!wasIdle && fy < 0.22f) {
            showBatteryInsteadOfClock = !showBatteryInsteadOfClock;
            hapticTick(12);
            return;
        }
        if (poopCount > 0 && fx < 0.3f && fy > 0.48f && fy <= 0.74f) {
            cleanPoopShower(now);
        }
        if (fy > 0.73f) {
            // Barra de ações.
            if (fx < 0.27f) {
                feedPet(now);
            } else if (fx < 0.42f) {
                giveWater(now);
            } else if (fx < 0.58f) {
                lovePet(now);
            } else if (fx < 0.73f) {
                toggleSleep(now);
            } else {
                toggleShop();
            }
            return;
        }

        // Cutucada.
        targetLookX = (fx - 0.5f) * 6f;
        targetLookY = (fy - 0.5f) * 5f;
        rapidPokeCount = now - lastPokeTime < 550 ? rapidPokeCount + 1 : 1;
        lastPokeTime = now;

        if (isSleeping) {
            isSleeping = false;
            startAction(MOOD_SURPRISED, now, 1100);
            triggerBounce(-0.9f, -0.25f);
        } else if (rapidPokeCount >= 5) {
            startAction(MOOD_GRUMPY, now, 1800);
            triggerBounce(-0.8f, 0.3f);
            hapticTick(35);
        } else {
            startAction(rapidPokeCount % 2 == 0 ? MOOD_WINK : MOOD_SURPRISED, now, 900);
            triggerBounce(-0.85f, -0.22f);
            spawnParticle(PARTICLE_SPARKLE, (now % 24) + 12, 13, now);
            hapticTick(15);
        }
    }

    private void handlePetDrag(float x, float y, int width, int height, long now) {
        petDistanceAccum += (float) Math.hypot(x - lastTouchX, y - lastTouchY);
        lastTouchX = x;
        lastTouchY = y;
        targetLookX = Math.max(-3.5f, Math.min(3.5f, (x / width - 0.5f) * 7f));
        targetLookY = Math.max(-2.5f, Math.min(2.5f, (y / height - 0.5f) * 6f));
        if (petDistanceAccum > width * 0.16f) {
            petDistanceAccum = 0f;
            rapidPokeCount = 0;
            isSleeping = false;
            happiness = clamp(happiness + 5f);
            coins++;
            actionMood = MOOD_LOVED;
            actionMoodUntil = now + 1500;
            squash = 0.22f;
            spawnParticle(PARTICLE_HEART, (now % 26) + 11, 14, now);
            hapticTick(12);
        }
    }

    // ================================================================= Desenho: primitivas da grade

    private void px(Canvas canvas, int gx, int gy, Paint paint) {
        float left = originX + gx * cellSize;
        float top = originY + gy * cellSize;
        canvas.drawRect(left, top, left + cellDrawSize, top + cellDrawSize, paint);
    }

    /** Pixel cheio (sem o vãozinho), usado para "apagar" com preto. */
    private void pxSolid(Canvas canvas, int gx, int gy, Paint paint) {
        float left = originX + gx * cellSize;
        float top = originY + gy * cellSize;
        canvas.drawRect(left, top, left + cellSize, top + cellSize, paint);
    }

    private void hline(Canvas canvas, int x0, int x1, int y, Paint paint) {
        for (int x = x0; x <= x1; x++) {
            px(canvas, x, y, paint);
        }
    }

    private void rectPx(Canvas canvas, int x0, int y0, int x1, int y1, Paint paint) {
        for (int y = y0; y <= y1; y++) {
            hline(canvas, x0, x1, y, paint);
        }
    }

    private void drawSprite5x5(Canvas canvas, int x, int y, int[] rows, Paint paint) {
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                if (((rows[row] >> (4 - col)) & 1) != 0) {
                    px(canvas, x + col, y + row, paint);
                }
            }
        }
    }

    private void drawDigit3x5(Canvas canvas, int x, int y, int digit, Paint paint) {
        if (digit < 0 || digit > 9) {
            return;
        }
        int[] rows = DIGITS_3X5[digit];
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 3; col++) {
                if (((rows[row] >> (2 - col)) & 1) != 0) {
                    px(canvas, x + col, y + row, paint);
                }
            }
        }
    }

    private void drawNumber3x5(Canvas canvas, int x, int y, int number, Paint paint) {
        int n = Math.max(0, Math.min(999, number));
        if (n >= 100) {
            drawDigit3x5(canvas, x, y, n / 100, paint);
            drawDigit3x5(canvas, x + 4, y, (n / 10) % 10, paint);
            drawDigit3x5(canvas, x + 8, y, n % 10, paint);
        } else if (n >= 10) {
            drawDigit3x5(canvas, x, y, n / 10, paint);
            drawDigit3x5(canvas, x + 4, y, n % 10, paint);
        } else {
            drawDigit3x5(canvas, x, y, n, paint);
        }
    }

    // ================================================================= Desenho: objetos

    private void drawClockHHMM(Canvas canvas, int y, long now, Paint paint) {
        long wall = System.currentTimeMillis();
        int minuteOfDay = (int) (((wall + localTz.getOffset(wall)) / 60_000) % 1440);
        if (minuteOfDay < 0) {
            minuteOfDay += 1440;
        }
        int hours = minuteOfDay / 60;
        int minutes = minuteOfDay % 60;
        drawDigit3x5(canvas, 13, y, hours / 10, paint);
        drawDigit3x5(canvas, 17, y, hours % 10, paint);
        if ((now / 500) % 2 == 0) {
            px(canvas, 25, y + 1, cyanPaint);
            px(canvas, 25, y + 3, cyanPaint);
        }
        drawDigit3x5(canvas, 31, y, minutes / 10, paint);
        drawDigit3x5(canvas, 35, y, minutes % 10, paint);
    }

    private void drawWaterCup(Canvas canvas, int x, int y, int level) {
        for (int row = 0; row < 5; row++) {
            px(canvas, x, y + row, whitePaint);
            px(canvas, x + 4, y + row, whitePaint);
        }
        hline(canvas, x + 1, x + 3, y + 5, whitePaint);
        int fill = Math.max(0, Math.min(4, level));
        for (int i = 0; i < fill; i++) {
            hline(canvas, x + 1, x + 3, y + 4 - i, i == fill - 1 ? cyanPaint : deepBluePaint);
        }
    }

    private void drawApple(Canvas canvas, int x, int y) {
        px(canvas, x + 2, y, greenPaint);
        px(canvas, x + 3, y, greenPaint);
        hline(canvas, x + 1, x + 3, y + 1, redPaint);
        hline(canvas, x, x + 4, y + 2, redPaint);
        hline(canvas, x, x + 4, y + 3, redPaint);
        hline(canvas, x + 1, x + 3, y + 4, redPaint);
        px(canvas, x + 1, y + 2, whitePaint);
    }

    private void drawPoop(Canvas canvas, int x, int y, long now) {
        px(canvas, x + 2, y, brownPaint);
        hline(canvas, x + 1, x + 3, y + 1, brownPaint);
        hline(canvas, x, x + 4, y + 2, brownPaint);
        hline(canvas, x, x + 4, y + 3, brownPaint);
        px(canvas, x + 1, y + 2, whitePaint);
        px(canvas, x + 3, y + 2, whitePaint);
        // Cheirinho verde subindo.
        int wiggle = (now / 400) % 2 == 0 ? 0 : 1;
        px(canvas, x + 1 + wiggle, y - 2, greenPaint);
        px(canvas, x + 3 - wiggle, y - 3, greenPaint);
    }

    private void drawBatteryBadge(Canvas canvas, int x, int y, long now) {
        Paint fillPaint;
        if (isWatchCharging) {
            fillPaint = greenPaint;
        } else if (watchBatteryPct <= 15) {
            fillPaint = (now / 300) % 2 == 1 ? whitePaint : redPaint; // pisca
        } else if (watchBatteryPct <= 30) {
            fillPaint = orangePaint;
        } else {
            fillPaint = greenPaint;
        }

        // Contorno da pilha.
        hline(canvas, x, x + 5, y, whitePaint);
        hline(canvas, x, x + 5, y + 3, whitePaint);
        px(canvas, x, y + 1, whitePaint);
        px(canvas, x, y + 2, whitePaint);
        px(canvas, x + 5, y + 1, whitePaint);
        px(canvas, x + 5, y + 2, whitePaint);
        px(canvas, x + 6, y + 1, whitePaint);
        px(canvas, x + 6, y + 2, whitePaint);

        int bars = Math.max(1, Math.round(watchBatteryPct / 100f * 4f));
        for (int i = 0; i < bars; i++) {
            px(canvas, x + 1 + i, y + 1, fillPaint);
            px(canvas, x + 1 + i, y + 2, fillPaint);
        }

        if (!isWatchCharging && watchBatteryPct <= 30) {
            // "!" de alerta.
            Paint alert = watchBatteryPct <= 15 ? redPaint : goldPaint;
            px(canvas, x - 2, y, alert);
            px(canvas, x - 2, y + 1, alert);
            px(canvas, x - 2, y + 3, alert);
        } else if (isWatchCharging) {
            // Raiozinho.
            px(canvas, x - 2, y, goldPaint);
            px(canvas, x - 3, y + 1, goldPaint);
            px(canvas, x - 2, y + 1, goldPaint);
            px(canvas, x - 3, y + 2, goldPaint);
        }
    }

    private void drawMiniStatBar(Canvas canvas, int x, int y, float value, Paint paint) {
        int filled = Math.round(value / 100f * 5f);
        for (int i = 0; i < 5; i++) {
            px(canvas, x + i, y, i < filled ? paint : dimPaint);
        }
    }

    // ================================================================= Desenho: acessórios

    /**
     * @param cx    centro horizontal do rosto
     * @param eyeY  linha central dos olhos
     * @param mouthY linha da boca
     */
    private void drawAccessory(Canvas canvas, int cx, int eyeY, int mouthY, int item) {
        int top = eyeY - 9; // logo acima dos olhos
        int lx = cx - 8;    // centro do olho esquerdo
        int rx = cx + 9;    // centro do olho direito

        switch (item) {
            case ITEM_SANTA_HAT:
                hline(canvas, cx - 11, cx + 11, top + 1, whitePaint);
                hline(canvas, cx - 12, cx + 12, top + 2, whitePaint);
                hline(canvas, cx - 9, cx + 9, top, redPaint);
                hline(canvas, cx - 8, cx + 9, top - 1, redPaint);
                hline(canvas, cx - 6, cx + 10, top - 2, redPaint);
                hline(canvas, cx - 3, cx + 11, top - 3, redPaint);
                hline(canvas, cx + 1, cx + 12, top - 4, redPaint);
                rectPx(canvas, cx + 12, top - 2, cx + 14, top, whitePaint); // pompom
                break;

            case ITEM_HALLOWEEN_WITCH:
                hline(canvas, cx - 14, cx + 14, top + 2, purplePaint);
                hline(canvas, cx - 11, cx + 11, top + 1, purplePaint);
                hline(canvas, cx - 8, cx + 8, top, orangePaint);
                px(canvas, cx - 1, top, goldPaint);
                px(canvas, cx, top, goldPaint);
                px(canvas, cx + 1, top, goldPaint);
                hline(canvas, cx - 7, cx + 7, top - 1, purplePaint);
                hline(canvas, cx - 5, cx + 5, top - 2, purplePaint);
                hline(canvas, cx - 3, cx + 4, top - 3, purplePaint);
                hline(canvas, cx - 1, cx + 5, top - 4, purplePaint);
                hline(canvas, cx + 2, cx + 6, top - 5, purplePaint);
                break;

            case ITEM_HALLOWEEN_PUMPKIN:
                hline(canvas, cx - 1, cx + 1, top - 5, greenPaint);
                px(canvas, cx, top - 6, greenPaint);
                hline(canvas, cx - 6, cx + 6, top - 4, orangePaint);
                rectPx(canvas, cx - 8, top - 3, cx + 8, top + 1, orangePaint);
                hline(canvas, cx - 6, cx + 6, top + 2, orangePaint);
                px(canvas, cx - 4, top - 2, goldPaint);
                px(canvas, cx - 3, top - 2, goldPaint);
                px(canvas, cx + 3, top - 2, goldPaint);
                px(canvas, cx + 4, top - 2, goldPaint);
                hline(canvas, cx - 3, cx + 3, top, goldPaint);
                break;

            case ITEM_DEVIL_HORNS:
                hline(canvas, cx - 10, cx - 7, top + 1, redPaint);
                hline(canvas, cx - 11, cx - 8, top, redPaint);
                hline(canvas, cx - 12, cx - 9, top - 1, redPaint);
                hline(canvas, cx - 12, cx - 10, top - 2, redPaint);
                px(canvas, cx - 11, top - 3, redPaint);
                hline(canvas, cx + 7, cx + 10, top + 1, redPaint);
                hline(canvas, cx + 8, cx + 11, top, redPaint);
                hline(canvas, cx + 9, cx + 12, top - 1, redPaint);
                hline(canvas, cx + 10, cx + 12, top - 2, redPaint);
                px(canvas, cx + 11, top - 3, redPaint);
                break;

            case ITEM_SUNGLASSES:
                hline(canvas, lx - 6, rx + 6, eyeY - 3, whitePaint);
                drawSunglassLens(canvas, lx, eyeY);
                drawSunglassLens(canvas, rx, eyeY);
                break;

            case ITEM_CROWN:
                hline(canvas, cx - 8, cx + 8, top + 1, goldPaint);
                hline(canvas, cx - 8, cx + 8, top, goldPaint);
                hline(canvas, cx - 8, cx + 8, top - 1, goldPaint);
                rectPx(canvas, cx - 8, top - 4, cx - 6, top - 2, goldPaint);
                rectPx(canvas, cx - 1, top - 5, cx + 1, top - 2, goldPaint);
                rectPx(canvas, cx + 6, top - 4, cx + 8, top - 2, goldPaint);
                px(canvas, cx, top, redPaint);
                px(canvas, cx - 5, top, cyanPaint);
                px(canvas, cx + 5, top, cyanPaint);
                break;

            case ITEM_WIZARD_HAT:
                hline(canvas, cx - 13, cx + 13, top + 2, deepBluePaint);
                hline(canvas, cx - 9, cx + 9, top + 1, deepBluePaint);
                hline(canvas, cx - 7, cx + 7, top, deepBluePaint);
                hline(canvas, cx - 5, cx + 5, top - 1, deepBluePaint);
                hline(canvas, cx - 4, cx + 4, top - 2, deepBluePaint);
                hline(canvas, cx - 2, cx + 3, top - 3, deepBluePaint);
                hline(canvas, cx, cx + 3, top - 4, deepBluePaint);
                px(canvas, cx - 3, top, goldPaint);
                px(canvas, cx + 2, top - 2, goldPaint);
                px(canvas, cx + 4, top + 1, goldPaint);
                break;

            case ITEM_BOWTIE: {
                int by = mouthY + 6;
                rectPx(canvas, cx - 5, by - 1, cx - 2, by + 1, redPaint);
                rectPx(canvas, cx + 2, by - 1, cx + 5, by + 1, redPaint);
                px(canvas, cx - 5, by - 2, redPaint);
                px(canvas, cx - 5, by + 2, redPaint);
                px(canvas, cx + 5, by - 2, redPaint);
                px(canvas, cx + 5, by + 2, redPaint);
                rectPx(canvas, cx - 1, by - 1, cx + 1, by + 1, whitePaint);
                break;
            }

            case ITEM_HEADPHONES:
                hline(canvas, cx - 14, cx + 14, top, cyanPaint);
                hline(canvas, cx - 15, cx - 13, top + 1, cyanPaint);
                hline(canvas, cx + 13, cx + 15, top + 1, cyanPaint);
                rectPx(canvas, cx - 17, eyeY - 4, cx - 14, eyeY + 3, cyanPaint);
                rectPx(canvas, cx + 14, eyeY - 4, cx + 17, eyeY + 3, cyanPaint);
                rectPx(canvas, cx - 16, eyeY - 2, cx - 15, eyeY + 1, whitePaint);
                rectPx(canvas, cx + 15, eyeY - 2, cx + 16, eyeY + 1, whitePaint);
                break;

            case ITEM_HEADBAND:
                hline(canvas, cx - 12, cx + 12, top + 1, redPaint);
                hline(canvas, cx - 12, cx + 12, top + 2, redPaint);
                px(canvas, cx, top + 1, whitePaint);
                px(canvas, cx + 13, top + 2, redPaint);
                px(canvas, cx + 14, top + 3, redPaint);
                px(canvas, cx + 13, top + 4, redPaint);
                break;

            case ITEM_EASTER_BUNNY:
                rectPx(canvas, cx - 8, top - 7, cx - 4, top + 1, whitePaint);
                hline(canvas, cx - 7, cx - 5, top - 8, whitePaint);
                rectPx(canvas, cx - 7, top - 6, cx - 5, top, blushPaint);
                rectPx(canvas, cx + 4, top - 6, cx + 8, top + 1, whitePaint);
                rectPx(canvas, cx + 5, top - 5, cx + 7, top, blushPaint);
                hline(canvas, cx + 6, cx + 10, top - 7, whitePaint); // orelha dobrada
                hline(canvas, cx + 8, cx + 11, top - 6, whitePaint);
                rectPx(canvas, cx + 9, top - 5, cx + 11, top - 4, whitePaint);
                break;

            case ITEM_BIRTHDAY_HAT:
                hline(canvas, cx - 7, cx + 7, top + 1, cyanPaint);
                hline(canvas, cx - 6, cx + 6, top, pinkPaint);
                hline(canvas, cx - 5, cx + 5, top - 1, goldPaint);
                hline(canvas, cx - 4, cx + 4, top - 2, cyanPaint);
                hline(canvas, cx - 3, cx + 3, top - 3, pinkPaint);
                hline(canvas, cx - 2, cx + 2, top - 4, goldPaint);
                hline(canvas, cx - 1, cx + 1, top - 5, cyanPaint);
                rectPx(canvas, cx - 1, top - 7, cx + 1, top - 6, whitePaint);
                px(canvas, cx, top - 8, goldPaint);
                break;

            case ITEM_CARNIVAL:
                rectPx(canvas, cx - 1, top - 7, cx + 1, top, pinkPaint);
                px(canvas, cx, top - 8, goldPaint);
                hline(canvas, cx - 6, cx - 3, top - 4, cyanPaint);
                hline(canvas, cx - 8, cx - 4, top - 3, cyanPaint);
                hline(canvas, cx - 7, cx - 3, top - 2, cyanPaint);
                hline(canvas, cx + 3, cx + 6, top - 4, greenPaint);
                hline(canvas, cx + 4, cx + 8, top - 3, greenPaint);
                hline(canvas, cx + 3, cx + 7, top - 2, greenPaint);
                hline(canvas, cx - 9, cx + 9, top + 1, goldPaint);
                px(canvas, cx, top + 1, purplePaint);
                px(canvas, cx - 4, top + 1, cyanPaint);
                px(canvas, cx + 4, top + 1, pinkPaint);
                break;

            case ITEM_VALENTINE_HALO:
                hline(canvas, cx - 8, cx + 8, top - 3, goldPaint);
                hline(canvas, cx - 10, cx - 8, top - 2, goldPaint);
                hline(canvas, cx + 8, cx + 10, top - 2, goldPaint);
                hline(canvas, cx - 8, cx + 8, top - 1, goldPaint);
                drawSprite5x5(canvas, cx - 2, top - 8, SPRITE_HEART, pinkPaint);
                break;

            case ITEM_FESTA_JUNINA:
                hline(canvas, cx - 14, cx + 14, top + 2, goldPaint);
                px(canvas, cx - 15, top + 1, orangePaint);
                px(canvas, cx + 15, top + 1, orangePaint);
                hline(canvas, cx - 12, cx + 12, top + 1, orangePaint);
                for (int x = cx - 8; x <= cx + 8; x++) {
                    px(canvas, x, top, (x & 1) == 0 ? redPaint : cyanPaint); // fita xadrez
                }
                hline(canvas, cx - 8, cx + 8, top - 1, goldPaint);
                hline(canvas, cx - 8, cx + 8, top - 2, orangePaint);
                hline(canvas, cx - 7, cx + 7, top - 3, goldPaint);
                hline(canvas, cx - 5, cx + 5, top - 4, goldPaint);
                break;

            case ITEM_NEW_YEAR_STARS:
                hline(canvas, lx + 4, rx - 4, eyeY - 1, goldPaint);
                drawStarFrame(canvas, lx, eyeY, -6, 5, -5, 4, -4, 3);
                drawStarFrame(canvas, rx, eyeY, -5, 6, -4, 5, -3, 4);
                break;

            case ITEM_CLOWN:
                drawClown(canvas, cx, eyeY);
                break;

            default:
                break;
        }
    }

    private void drawSunglassLens(Canvas canvas, int ex, int eyeY) {
        rectPx(canvas, ex - 5, eyeY - 2, ex + 5, eyeY + 2, blackPaint);
        hline(canvas, ex - 5, ex + 5, eyeY - 3, whitePaint);
        hline(canvas, ex - 4, ex + 4, eyeY + 3, whitePaint);
        px(canvas, ex - 5, eyeY - 2, whitePaint);
        px(canvas, ex + 5, eyeY - 2, whitePaint);
        px(canvas, ex - 3, eyeY - 1, cyanPaint); // reflexo
        px(canvas, ex - 2, eyeY, cyanPaint);
    }

    private void drawStarFrame(Canvas canvas, int ex, int eyeY,
                               int barL, int barR, int armL, int armR, int footL, int footR) {
        px(canvas, ex, eyeY - 6, goldPaint);
        hline(canvas, ex + barL, ex + barR, eyeY - 3, goldPaint);
        px(canvas, ex + armL, eyeY - 2, goldPaint);
        px(canvas, ex + armR, eyeY - 2, goldPaint);
        px(canvas, ex + footL, eyeY + 3, goldPaint);
        px(canvas, ex + footR, eyeY + 3, goldPaint);
    }

    /**
     * Palhaço: nariz vermelho redondo entre os olhos e a boca, e o cabelão estilo Bozo —
     * careca em cima, com dois tufos laranja/vermelhos enormes saindo pelos lados.
     */
    private void drawClown(Canvas canvas, int cx, int eyeY) {
        // Tufos laterais. Coordenadas do tufo esquerdo em relação ao centro do rosto;
        // o direito é espelhado em torno do meio dos olhos (cx + 0.5).
        int[][] tuft = {
                // {dy, xMin, xMax} com dx negativo = para fora à esquerda
                {-12, -18, -15},
                {-11, -20, -14},
                {-10, -21, -13},
                {-9, -22, -13},
                {-8, -22, -13},
                {-7, -21, -13},
                {-6, -20, -14},
                {-5, -18, -15},
        };
        for (int[] row : tuft) {
            int y = eyeY + row[0];
            for (int dx = row[1]; dx <= row[2]; dx++) {
                // Mistura laranja e vermelho em xadrez para dar volume ao cabelo.
                Paint hair = ((dx + row[0]) & 3) == 0 ? clownRedPaint : clownHairPaint;
                px(canvas, cx + dx, y, hair);
                px(canvas, cx + 1 - dx, y, hair);
            }
        }
        // Pontinhas arrepiadas.
        px(canvas, cx - 23, eyeY - 10, clownHairPaint);
        px(canvas, cx + 24, eyeY - 10, clownHairPaint);
        px(canvas, cx - 23, eyeY - 7, clownRedPaint);
        px(canvas, cx + 24, eyeY - 7, clownRedPaint);
        px(canvas, cx - 17, eyeY - 13, clownRedPaint);
        px(canvas, cx + 18, eyeY - 13, clownRedPaint);

        // Nariz vermelho (bolinha 4x4) com brilho.
        int ny = eyeY + 4;
        hline(canvas, cx, cx + 1, ny, clownRedPaint);
        hline(canvas, cx - 1, cx + 2, ny + 1, clownRedPaint);
        hline(canvas, cx - 1, cx + 2, ny + 2, clownRedPaint);
        hline(canvas, cx, cx + 1, ny + 3, clownRedPaint);
        px(canvas, cx, ny + 1, whitePaint);
    }

    // ================================================================= Desenho: olhos e boca

    private void drawEyes(Canvas canvas, int leftX, int rightX, int y, int mood, long now) {
        if (isBlinking || mood == MOOD_SLEEPING) {
            drawClosedEye(canvas, leftX, y);
            drawClosedEye(canvas, rightX, y);
            return;
        }
        switch (mood) {
            case MOOD_LOVED:
            case MOOD_DRINKING:
            case MOOD_SHOWERING:
                drawHappyArcEye(canvas, leftX, y);
                drawHappyArcEye(canvas, rightX, y);
                drawBlush(canvas, leftX - 6, y + 6);
                drawBlush(canvas, rightX + 4, y + 6);
                break;

            case MOOD_HUNGRY:
            case MOOD_SAD:
            case MOOD_THIRSTY:
                drawOvalEye(canvas, leftX, y, true);
                drawOvalEye(canvas, rightX, y, true);
                // Sobrancelhas tristes.
                hline(canvas, leftX - 1, leftX + 2, y - 7, whitePaint);
                hline(canvas, leftX - 4, leftX - 2, y - 6, whitePaint);
                hline(canvas, rightX - 2, rightX + 1, y - 7, whitePaint);
                hline(canvas, rightX + 2, rightX + 4, y - 6, whitePaint);
                if (mood != MOOD_HUNGRY) {
                    // Lágrima escorrendo.
                    int tearY = y + 6 + (int) ((now / 250) % 3);
                    px(canvas, leftX - 5, tearY, cyanPaint);
                    px(canvas, leftX - 5, tearY + 1, cyanPaint);
                }
                break;

            case MOOD_SLEEPY:
            case MOOD_LOW_BATTERY:
                drawHalfClosedEye(canvas, leftX, y);
                drawHalfClosedEye(canvas, rightX, y);
                break;

            case MOOD_GRUMPY:
                drawGrumpyEye(canvas, leftX, y, true);
                drawGrumpyEye(canvas, rightX, y, false);
                break;

            case MOOD_SURPRISED:
                drawSurprisedEye(canvas, leftX, y);
                drawSurprisedEye(canvas, rightX, y);
                break;

            case MOOD_WINK:
                drawHappyArcEye(canvas, leftX, y);
                drawOvalEye(canvas, rightX, y, false);
                drawBlush(canvas, leftX - 6, y + 6);
                drawBlush(canvas, rightX + 4, y + 6);
                break;

            default:
                drawOvalEye(canvas, leftX, y, false);
                drawOvalEye(canvas, rightX, y, false);
                break;
        }
    }

    private void drawClosedEye(Canvas canvas, int x, int y) {
        hline(canvas, x - 3, x + 3, y + 1, whitePaint);
        px(canvas, x - 4, y, whitePaint);
        px(canvas, x + 4, y, whitePaint);
    }

    /** Olho oval branco com pupila preta que segue o olhar. */
    private void drawOvalEye(Canvas canvas, int x, int y, boolean sadHighlight) {
        hline(canvas, x - 2, x + 2, y - 5, whitePaint);
        hline(canvas, x - 3, x + 3, y - 4, whitePaint);
        rectPx(canvas, x - 4, y - 3, x + 4, y + 3, whitePaint);
        hline(canvas, x - 3, x + 3, y + 4, whitePaint);
        hline(canvas, x - 2, x + 2, y + 5, whitePaint);

        int ox = Math.round(lookX * 0.45f);
        int oy = Math.round(lookY * 0.45f);
        for (int py = y - 2 + oy; py <= y + 2 + oy; py++) {
            for (int pxX = x - 2 + ox; pxX <= x + 2 + ox; pxX++) {
                boolean shine = pxX <= x - 1 + ox && py <= y - 1 + oy;           // brilho no canto
                boolean sadShine = sadHighlight && pxX == x + 1 + ox && py == y + 2 + oy;
                if (!shine && !sadShine) {
                    pxSolid(canvas, pxX, py, blackPaint);
                }
            }
        }
    }

    private void drawHappyArcEye(Canvas canvas, int x, int y) {
        hline(canvas, x - 2, x + 2, y - 3, whitePaint);
        hline(canvas, x - 3, x + 3, y - 2, whitePaint);
        px(canvas, x - 4, y - 1, whitePaint);
        px(canvas, x - 3, y - 1, whitePaint);
        px(canvas, x + 3, y - 1, whitePaint);
        px(canvas, x + 4, y - 1, whitePaint);
        px(canvas, x - 4, y, whitePaint);
        px(canvas, x + 4, y, whitePaint);
    }

    private void drawHalfClosedEye(Canvas canvas, int x, int y) {
        hline(canvas, x - 4, x + 4, y, whitePaint);
        hline(canvas, x - 3, x + 3, y + 1, whitePaint);
        hline(canvas, x - 3, x + 3, y + 2, whitePaint);
        hline(canvas, x - 2, x + 2, y + 3, whitePaint);
    }

    private void drawGrumpyEye(Canvas canvas, int x, int y, boolean leftSide) {
        rectPx(canvas, x - 3, y - 1, x + 3, y + 3, whitePaint);
        hline(canvas, x - 2, x + 2, y + 4, whitePaint);
        // Sobrancelha franzida apontando para o meio.
        if (leftSide) {
            hline(canvas, x - 4, x - 2, y - 4, whitePaint);
            hline(canvas, x - 1, x + 1, y - 3, whitePaint);
            hline(canvas, x + 2, x + 4, y - 2, whitePaint);
        } else {
            hline(canvas, x + 2, x + 4, y - 4, whitePaint);
            hline(canvas, x - 1, x + 1, y - 3, whitePaint);
            hline(canvas, x - 4, x - 2, y - 2, whitePaint);
        }
    }

    private void drawSurprisedEye(Canvas canvas, int x, int y) {
        hline(canvas, x - 2, x + 2, y - 4, whitePaint);
        hline(canvas, x - 3, x + 3, y - 3, whitePaint);
        rectPx(canvas, x - 4, y - 2, x + 4, y + 2, whitePaint);
        hline(canvas, x - 3, x + 3, y + 3, whitePaint);
        hline(canvas, x - 2, x + 2, y + 4, whitePaint);
        // Pupila grande com um pontinho de brilho no centro.
        for (int py = y - 2; py <= y + 2; py++) {
            for (int pxX = x - 2; pxX <= x + 2; pxX++) {
                if (pxX != x || py != y) {
                    pxSolid(canvas, pxX, py, blackPaint);
                }
            }
        }
    }

    private void drawBlush(Canvas canvas, int x, int y) {
        px(canvas, x, y, blushPaint);
        px(canvas, x + 2, y, blushPaint);
        px(canvas, x + 1, y + 1, blushPaint);
    }

    private void drawMouth(Canvas canvas, int x, int y, int mood, long now) {
        switch (mood) {
            case MOOD_LOVED:
            case MOOD_SHOWERING:
                // Sorrisão aberto com linguinha.
                hline(canvas, x - 5, x + 5, y, whitePaint);
                hline(canvas, x - 4, x + 4, y + 1, whitePaint);
                hline(canvas, x - 4, x + 4, y + 2, whitePaint);
                hline(canvas, x - 3, x + 3, y + 3, whitePaint);
                hline(canvas, x - 2, x + 2, y + 4, blushPaint);
                break;

            case MOOD_HUNGRY:
            case MOOD_THIRSTY:
            case MOOD_LOW_BATTERY:
                hline(canvas, x - 2, x + 2, y, whitePaint);
                px(canvas, x - 3, y + 1, whitePaint);
                px(canvas, x + 3, y + 1, whitePaint);
                if (mood == MOOD_HUNGRY) {
                    // Babinha.
                    px(canvas, x + 3, y + 2, cyanPaint);
                    if ((now / 400) % 2 == 0) {
                        px(canvas, x + 3, y + 3, cyanPaint);
                    }
                }
                break;

            case MOOD_SLEEPY:
            case MOOD_SURPRISED:
                drawOMouth(canvas, x, y);
                break;

            case MOOD_SLEEPING: {
                int half = (now / 900) % 2 == 0 ? 1 : 2; // respiração
                hline(canvas, x - half, x + half, y + 1, whitePaint);
                break;
            }

            case MOOD_SAD:
                hline(canvas, x - 3, x + 3, y, whitePaint);
                px(canvas, x - 4, y + 1, whitePaint);
                px(canvas, x + 4, y + 1, whitePaint);
                px(canvas, x - 5, y + 2, whitePaint);
                px(canvas, x + 5, y + 2, whitePaint);
                break;

            case MOOD_GRUMPY:
                // Boca em zigue-zague.
                for (int dx = -3; dx <= 3; dx++) {
                    px(canvas, x + dx, (dx & 1) != 0 ? y + 1 : y, whitePaint);
                }
                break;

            case MOOD_EATING:
                if (now - lastChewTime > 200) {
                    chewStep = (chewStep + 1) % 2;
                    lastChewTime = now;
                }
                if (chewStep != 0) {
                    hline(canvas, x - 3, x + 3, y + 1, whitePaint);
                    px(canvas, x - 4, y, whitePaint);
                    px(canvas, x + 4, y, whitePaint);
                } else {
                    hline(canvas, x - 3, x + 3, y - 1, whitePaint);
                    rectPx(canvas, x - 4, y, x + 4, y + 3, whitePaint);
                    hline(canvas, x - 3, x + 3, y + 4, whitePaint);
                }
                break;

            case MOOD_WINK:
                // Boquinha de gato ":3".
                px(canvas, x - 4, y, whitePaint);
                hline(canvas, x - 3, x - 1, y + 1, whitePaint);
                px(canvas, x, y, whitePaint);
                hline(canvas, x + 1, x + 3, y + 1, whitePaint);
                px(canvas, x + 4, y, whitePaint);
                break;

            case MOOD_DRINKING:
                drawOMouth(canvas, x, y);
                if ((now / 180) % 2 == 0) {
                    px(canvas, x + 3, y + 1, cyanPaint);
                    px(canvas, x + 4, y + 1, cyanPaint);
                }
                break;

            default:
                // Sorriso simples.
                px(canvas, x - 5, y, whitePaint);
                px(canvas, x + 5, y, whitePaint);
                px(canvas, x - 4, y + 1, whitePaint);
                px(canvas, x + 4, y + 1, whitePaint);
                hline(canvas, x - 3, x + 3, y + 2, whitePaint);
                hline(canvas, x - 2, x + 2, y + 3, whitePaint);
                break;
        }
    }

    private void drawOMouth(Canvas canvas, int x, int y) {
        hline(canvas, x - 1, x + 1, y, whitePaint);
        px(canvas, x - 2, y + 1, whitePaint);
        px(canvas, x + 2, y + 1, whitePaint);
        px(canvas, x - 2, y + 2, whitePaint);
        px(canvas, x + 2, y + 2, whitePaint);
        hline(canvas, x - 1, x + 1, y + 3, whitePaint);
    }

    // ================================================================= Desenho: telas

    @Override
    protected void onDraw(Canvas canvas) {
        long nextDelay = renderScene(canvas, getWidth(), getHeight(), false);
        if (nextDelay <= 16) {
            postInvalidateOnAnimation();
        } else {
            postInvalidateDelayed(nextDelay);
        }
    }

    /**
     * Atualiza a simulação e desenha um quadro.
     *
     * @param ambient modo econômico (só relógio e bichinho dormindo)
     * @return quantos ms esperar até o próximo quadro
     */
    public long renderScene(Canvas canvas, int width, int height, boolean ambient) {
        canvas.drawColor(0xFF000000);
        if (width <= 0 || height <= 0) {
            return 500;
        }
        long now = SystemClock.uptimeMillis();

        if (ambient) {
            drawClockHHMM(canvas, 6, 0, whitePaint);
            drawEyes(canvas, 17, 34, 26, MOOD_SLEEPING, now);
            drawMouth(canvas, 25, 35, MOOD_SLEEPING, now);
            return 60_000;
        }

        float dt = Math.min(0.05f, Math.max(0.001f, (now - lastFrameTime) / 1000f));
        lastFrameTime = now;

        updateStats(dt, now);
        int mood = getCurrentMood(now);
        updateEyesAndBounce(dt, now);

        // Zzz quando está dormindo.
        if (mood == MOOD_SLEEPING && now / 900 != (now - 30) / 900) {
            spawnParticle(PARTICLE_Z, (int) (now % 4) + 34, 17, now);
        }

        // ---- Posição do rosto
        int breathe = (mood == MOOD_SLEEPING || (now / 900) % 2 != 0) ? 1 : 0;
        int lookOffsetX = shopOpen ? 0 : Math.round(lookX);
        int offsetY = Math.round(lookY + bounceY) + breathe;
        int cx = lookOffsetX + 25;
        int squashX = Math.round(squash * 3f);
        int squashY = Math.round(squash * 2f);
        int eyeY = offsetY + 26 + squashY;
        int mouthY = offsetY + 35 + squashY;

        drawEyes(canvas, lookOffsetX + 17 - squashX, lookOffsetX + 34 + squashX, eyeY, mood, now);
        drawMouth(canvas, cx, mouthY, mood, now);

        // ---- Acessório (na loja mostra o item em pré-visualização)
        boolean walking = now - lastStepTime < WALKING_WINDOW_MS;
        int item;
        if (shopOpen) {
            item = shopPreviewItem;
        } else if (walking && equippedItem == ITEM_NONE) {
            item = ITEM_HEADBAND; // faixa de academia enquanto caminha
        } else {
            item = equippedItem;
        }
        if (item != ITEM_NONE) {
            drawAccessory(canvas, cx, eyeY, mouthY, item);
        }

        if (poopCount > 0 && !shopOpen) {
            drawPoop(canvas, 5, 34, now);
        }

        // ---- Topo: alerta de bateria ou relógio (modo mostrador)
        boolean hudVisible = now - lastInteractionTime < HUD_VISIBLE_MS;
        if (!shopOpen && !hudVisible) {
            if (watchBatteryPct <= 30 || isWatchCharging) {
                drawBatteryBadge(canvas, 19, 2, now);
                Paint pctPaint = watchBatteryPct <= 15 ? redPaint
                        : watchBatteryPct <= 30 ? orangePaint : greenPaint;
                drawNumber3x5(canvas, 27, 2, watchBatteryPct, pctPaint);
            } else if (isWatchFaceMode) {
                drawClockHHMM(canvas, 2, now, whitePaint);
            }
        }

        drawMoodProps(canvas, mood, lookOffsetX, offsetY, now);
        boolean anyParticle = updateAndDrawParticles(canvas, dt, now);

        if (shopOpen) {
            drawShopOverlay(canvas);
        } else if (hudVisible) {
            drawMinimalHud(canvas, now);
        }

        boolean animating = isBlinking
                || anyParticle
                || Math.abs(bounceY) > 0.05f
                || Math.abs(squash) > 0.02f
                || Math.abs(targetLookX - lookX) > 0.15f
                || (actionMood != MOOD_NONE && now < actionMoodUntil);
        if (animating) {
            return 16;
        }
        return (shopOpen || hudVisible) ? 150 : 450;
    }

    private void updateStats(float dt, long now) {
        hunger = clamp(hunger - 0.015f * dt);
        water = clamp(water - 0.025f * dt);
        if (isSleeping) {
            energy = clamp(energy + 0.08f * dt);
            if (energy >= 100f) {
                isSleeping = false;
            }
        } else {
            energy = clamp(energy - 0.012f * dt);
            happiness = clamp(happiness - 0.014f * dt);
        }

        // Lembretes vibrando (sede a cada 15 min, fome a cada 20 min).
        if (!isSleeping && water < THIRSTY_THRESHOLD
                && (lastWaterVibrateTime == 0 || now - lastWaterVibrateTime > 15 * 60_000L)) {
            lastWaterVibrateTime = now;
            hapticPattern(new long[]{0, 110, 80, 110, 80, 220});
            triggerBounce(-0.7f, -0.2f);
        }
        if (!isSleeping && hunger < HUNGRY_THRESHOLD
                && (lastHungerVibrateTime == 0 || now - lastHungerVibrateTime > 20 * 60_000L)) {
            lastHungerVibrateTime = now;
            hapticPattern(new long[]{0, 180, 120, 180});
            triggerBounce(-0.7f, 0.2f);
        }
    }

    private void updateEyesAndBounce(float dt, long now) {
        // Piscar.
        if (!isBlinking && now >= nextBlinkTime) {
            isBlinking = true;
            blinkEndTime = now + 140;
            nextBlinkTime = now + 2600 + (now % 2400);
        } else if (isBlinking && now >= blinkEndTime) {
            isBlinking = false;
        }

        // Olhar para os lados sozinho quando ninguém mexe.
        if (now >= nextSaccadeTime && now - lastInteractionTime > 1200) {
            switch ((int) ((now / 173) % 7)) {
                case 0: targetLookX = -2.2f; targetLookY = 0f; break;
                case 1: targetLookX = 2.2f; targetLookY = 0f; break;
                case 2: targetLookX = 0f; targetLookY = -1.5f; break;
                case 3: targetLookX = -1.5f; targetLookY = 1.2f; break;
                case 4: targetLookX = 1.5f; targetLookY = 1.2f; break;
                default: targetLookX = 0f; targetLookY = 0f; break;
            }
            nextSaccadeTime = now + 2200 + (now % 2000);
        }

        float follow = Math.min(1f, 12f * dt);
        lookX += (targetLookX - lookX) * follow;
        lookY += (targetLookY - lookY) * follow;

        // Pulinho com gravidade.
        bounceY += bounceVelY;
        bounceVelY += 0.14f;
        if (bounceY > 0f) {
            bounceY = 0f;
            bounceVelY = 0f;
        }
        squash *= 0.84f;
    }

    /** Copo, maçã etc. que aparecem do lado do rosto conforme o humor. */
    private void drawMoodProps(Canvas canvas, int mood, int lookOffsetX, int offsetY, long now) {
        int fastBob = (now / 180) % 2 == 0 ? 0 : -1;
        int slowBob = (now / 350) % 2 == 0 ? 0 : -1;
        switch (mood) {
            case MOOD_DRINKING: {
                int level = 4 - Math.min(3, (int) (Math.max(0L, now - actionStartTime) / 550));
                drawWaterCup(canvas, lookOffsetX + 33, offsetY + 29 + fastBob, level);
                break;
            }
            case MOOD_THIRSTY:
                if (!shopOpen) {
                    drawWaterCup(canvas, 39, 13 + slowBob, 3);
                    drawSprite5x5(canvas, 33, 14 + slowBob, SPRITE_WATER_DROP, cyanPaint);
                }
                break;
            case MOOD_EATING:
                drawApple(canvas, lookOffsetX + 33, offsetY + 30 + fastBob);
                break;
            case MOOD_HUNGRY:
                if (!shopOpen) {
                    drawApple(canvas, 39, 13 + slowBob);
                }
                break;
            case MOOD_GRUMPY:
                drawSprite5x5(canvas, lookOffsetX + 37, offsetY + 11, SPRITE_SPARKLE, redPaint);
                break;
            default:
                break;
        }
    }

    private boolean updateAndDrawParticles(Canvas canvas, float dt, long now) {
        boolean any = false;
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (now >= partExpire[i]) {
                continue;
            }
            partY[i] += partVy[i] * dt * 18f;
            int x = Math.round(partX[i]);
            int y = Math.round(partY[i]);
            switch (partType[i]) {
                case PARTICLE_HEART:
                    drawSprite5x5(canvas, x, y, SPRITE_HEART, pinkPaint);
                    break;
                case PARTICLE_Z:
                    drawSprite5x5(canvas, x, y, SPRITE_Z, lavenderPaint);
                    break;
                case PARTICLE_SPARKLE:
                    drawSprite5x5(canvas, x, y, SPRITE_SPARKLE, goldPaint);
                    break;
                case PARTICLE_APPLE:
                    drawApple(canvas, x, y);
                    break;
                default: // gota / chuveiro
                    drawSprite5x5(canvas, x, y, SPRITE_WATER_DROP, cyanPaint);
                    break;
            }
            any = true;
        }
        return any;
    }

    private void drawShopOverlay(Canvas canvas) {
        // Moedas no topo.
        rectPx(canvas, 17, 4, 19, 8, goldPaint);
        px(canvas, 18, 5, whitePaint);
        drawNumber3x5(canvas, 22, 4, coins, goldPaint);

        // Setinhas < >.
        px(canvas, 6, 25, whitePaint);
        px(canvas, 7, 24, whitePaint);
        px(canvas, 7, 26, whitePaint);
        px(canvas, 8, 23, whitePaint);
        px(canvas, 8, 27, whitePaint);
        px(canvas, 45, 25, whitePaint);
        px(canvas, 44, 24, whitePaint);
        px(canvas, 44, 26, whitePaint);
        px(canvas, 43, 23, whitePaint);
        px(canvas, 43, 27, whitePaint);

        int item = shopPreviewItem;
        if (isUnlocked(item)) {
            // Check: verde se já está equipado, ciano se só comprado.
            Paint check = equippedItem == item ? greenPaint : cyanPaint;
            px(canvas, 23, 43, check);
            px(canvas, 24, 44, check);
            px(canvas, 25, 43, check);
            px(canvas, 26, 42, check);
            px(canvas, 27, 41, check);
            return;
        }
        int price = ITEM_PRICES[item];
        rectPx(canvas, 19, 41, 20, 44, goldPaint);
        drawNumber3x5(canvas, 23, 41, price, coins >= price ? goldPaint : redPaint);
    }

    private void drawMinimalHud(Canvas canvas, long now) {
        if (showBatteryInsteadOfClock) {
            drawBatteryBadge(canvas, 19, 2, now);
            drawNumber3x5(canvas, 27, 2, watchBatteryPct, whitePaint);
        } else {
            drawClockHHMM(canvas, 2, now, whitePaint);
            if (watchBatteryPct <= 30 || isWatchCharging) {
                drawBatteryBadge(canvas, 9, 2, now);
            }
        }

        drawMiniStatBar(canvas, 13, 8, hunger, orangePaint);
        drawMiniStatBar(canvas, 20, 8, water, cyanPaint);
        drawMiniStatBar(canvas, 27, 8, happiness, pinkPaint);
        drawMiniStatBar(canvas, 34, 8, energy, goldPaint);

        // Barra de ações: comer | água | carinho | dormir/acordar | loja.
        drawApple(canvas, 10, 42);
        drawWaterCup(canvas, 17, 41, 3);
        drawSprite5x5(canvas, 24, 42, SPRITE_HEART, pinkPaint);
        drawSprite5x5(canvas, 31, 42, isSleeping ? SPRITE_BOLT : SPRITE_MOON,
                isSleeping ? goldPaint : lavenderPaint);
        drawSprite5x5(canvas, 37, 42, SPRITE_SHOP_CROWN, goldPaint);
    }
}
