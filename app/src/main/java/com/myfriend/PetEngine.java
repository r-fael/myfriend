package com.myfriend;

import static com.myfriend.PetConfig.*;
import static com.myfriend.PixelGrid.*;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;

/**
 * Motor principal de simulação, animação, entrada e renderização do bichinho.
 *
 * <p>Desacoplado de {@link android.view.View} para poder rodar direto tanto na
 * {@link MainActivity} quanto no {@link FriendWatchFaceService} sem alocar Views órfãs.
 */
public final class PetEngine {

    private final Context context;
    private final Vibrator vibrator;
    public final PetState state;
    public final PixelGrid grid;
    private final PetAccessoryRenderer accessories;

    // ---------------------------------------------------------------- Estado da tela

    private boolean shopOpen = false;
    private int shopCategory = CATEGORY_HEAD;
    /** Item mostrado na loja em cada categoria (só a categoria aberta pode diferir do equipado). */
    private final int[] shopPreview = new int[CATEGORY_COUNT];
    private boolean isWatchFaceMode = false;
    private boolean showDateInsteadOfClock = false;
    /** Zoom do rosto: maior quando está sozinho (sem HUD), normal quando mexem nele. */
    private float faceZoom = 1f;

    private int watchBatteryPct = 100;
    private boolean isWatchCharging = false;
    private boolean vibratedAt30 = false;
    private boolean vibratedAt15 = false;

    private long lastStepTime = 0L;
    private long lastHungerVibrateTime = 0L;
    private long lastWaterVibrateTime = 0L;
    private long lastInteractionTime = 0L;
    private long lastFrameTime = 0L;
    private long lastBatteryPollTime = 0L;

    private int forcedMood = MOOD_NONE;
    private int actionMood = MOOD_NONE;
    private long actionMoodUntil = 0L;
    private long actionStartTime = 0L;

    private int rapidPokeCount = 0;
    private long lastPokeTime = 0L;
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private float petDistanceAccum = 0f;

    // Olhar, piscar e pulinho
    private float lookX = 0f;
    private float lookY = 0f;
    private float targetLookX = 0f;
    private float targetLookY = 0f;
    private long nextSaccadeTime = 0L;
    private long nextBlinkTime = 0L;
    private boolean isBlinking = false;
    private long blinkEndTime = 0L;
    private float bounceY = 0f;
    private float bounceVelY = 0f;
    private float squash = 0f;
    /** Quantos "eventos" da animação de comer/beber já aconteceram (mordidas, goles, final). */
    private int mealFxStep = 0;
    private static final long EAT_MS = 2700L;
    private static final long DRINK_MS = 2700L;

    // Rotina, humor do dia e sinais de fora
    /** Cor atual do rostinho (olhos e boca). */
    private Paint facePaint;
    private boolean sulking = false;
    private int personality = PERSONALITY_NORMAL;
    private int cachedHour = 12;
    private long lastMinuteCheck = 0L;
    private long nextYawnTime = 0L;
    private long nextHopTime = 0L;
    private long lastLoveTime = 0L;
    private long lastMailShownWall = 0L;
    private long walkWindowStart = 0L;
    private int walkWindowSteps = 0;
    private long lastExcitedTime = -60_000L;
    private int celebrateFxStep = 0;

    // Toque duplo: passos e batimentos

    /** Quadro "parado" (sem animação): 1 por segundo, alinhado à respiração. */
    private static final long IDLE_FRAME_MS = 1000L;

    // Partículas (corações, Zzz, brilhos...)
    private final float[] partX = new float[MAX_PARTICLES];
    private final float[] partY = new float[MAX_PARTICLES];
    private final float[] partVy = new float[MAX_PARTICLES];
    private final int[] partType = new int[MAX_PARTICLES];
    private final long[] partExpire = new long[MAX_PARTICLES];

    public PetEngine(Context context) {
        this.context = context.getApplicationContext();
        this.vibrator = (Vibrator) this.context.getSystemService(Context.VIBRATOR_SERVICE);
        this.state = new PetState(this.context);
        this.grid = new PixelGrid();
        this.accessories = new PetAccessoryRenderer(this.grid);

        refreshBatteryDirect();
        state.load();

        long now = SystemClock.uptimeMillis();
        lastInteractionTime = now;
        lastFrameTime = now;
        lastBatteryPollTime = now;
        nextBlinkTime = now + 2000L;
        nextSaccadeTime = now + 1500L;
        lastLoveTime = now;
        facePaint = grid.whitePaint;
        refreshTimeOfDay(now);
        nextYawnTime = now + yawnIntervalMs();
        nextHopTime = now + 6000L;
        lastMailShownWall = PetSignals.lastNotificationWall;
    }

    public void setWatchFaceMode(boolean watchFaceMode) {
        this.isWatchFaceMode = watchFaceMode;
    }

    public void updateGridMetrics(int width, int height) {
        grid.updateMetrics(width, height);
    }

    // ================================================================= Ciclo de vida

    public void onResume() {
        refreshBatteryDirect();
        state.load();
        long now = SystemClock.uptimeMillis();
        lastFrameTime = now;
        lastInteractionTime = now;
        lastBatteryPollTime = now;
        showDateInsteadOfClock = false;
        refreshTimeOfDay(now);

        long wall = PetClock.wallMillis();
        int day = PetClock.dayKey();
        boolean night = isNight(cachedHour);
        boolean morning = cachedHour >= MORNING_HOUR && cachedHour < 12;
        if ((state.nightSleep && !night) || (morning && state.stretchDay != day)) {
            // Bom dia: acorda se espreguiçando.
            state.stretchDay = day;
            state.nightSleep = false;
            state.isSleeping = false;
            sulking = false;
            startAction(MOOD_STRETCH, now, 2200L);
            triggerBounce(-1.2f, -0.35f);
            spawnParticle(PARTICLE_SPARKLE, 12, 12, now);
            spawnParticle(PARTICLE_SPARKLE, 36, 10, now);
        } else if (!state.isSleeping && !night && cachedHour >= 9 && state.lastSeenWall > 0L
                && wall - state.lastSeenWall >= SULK_AFTER_MS) {
            // Ficou horas sem ver ele: virou de costas emburrado.
            sulking = true;
        }
        state.lastSeenWall = wall;
        nextYawnTime = now + yawnIntervalMs();
    }

    public void onPause() {
        state.lastSeenWall = PetClock.wallMillis();
        if (isNight(PetClock.hour()) && !state.isSleeping) {
            // Apagou a tela de noite: ele vai dormir sozinho.
            state.isSleeping = true;
            state.nightSleep = true;
        }
        state.save();
    }

    // ================================================================= Sinais de fora (sensores)

    /** Contador de passos do sistema (acumulado desde que o relógio ligou). */
    public void onStepCounterTotal(float total) {
        int day = PetClock.dayKey();
        if (state.stepDay != day) {
            state.stepDay = day;
            state.stepBase = total;
            state.stepsToday = 0;
        } else if (state.lastStepCounter >= 0f && total < state.lastStepCounter) {
            // Relógio reiniciou: o contador voltou a zero.
            state.stepBase = total - state.stepsToday;
        }
        float delta = state.lastStepCounter >= 0f && total >= state.lastStepCounter
                ? total - state.lastStepCounter : 0f;
        state.lastStepCounter = total;
        state.stepsToday = Math.max(0, Math.round(total - state.stepBase));

        long now = SystemClock.uptimeMillis();
        if (delta > 0f && delta < 200f) {
            // Passos "ao vivo" (tela ligada): moedas e animação de caminhada.
            onUserStep(Math.min(20, Math.round(delta)));
            if (now - walkWindowStart > EXCITED_WINDOW_MS) {
                walkWindowStart = now;
                walkWindowSteps = 0;
            }
            walkWindowSteps += Math.round(delta);
            if (walkWindowSteps >= EXCITED_STEPS && now - lastExcitedTime > 60_000L
                    && !state.isSleeping && !isActionRunning(now)) {
                lastExcitedTime = now;
                startAction(MOOD_EXCITED, now, 2600L);
                triggerBounce(-1.0f, -0.25f);
            }
        }
        if (state.stepsToday >= STEP_GOAL && state.goalDay != day) {
            state.goalDay = day;
            state.coins += STEP_GOAL_REWARD;
            state.isSleeping = false;
            celebrateFxStep = 0;
            startAction(MOOD_CELEBRATE, now, 3200L);
            triggerBounce(-1.3f, -0.3f);
            hapticPattern(new long[]{0, 60, 80, 60, 80, 160});
            state.save();
        }
    }

    /** Chacoalhou o pulso: fica tonto. */
    public void onShake() {
        long now = SystemClock.uptimeMillis();
        if (shopOpen || sulking) {
            return;
        }
        state.isSleeping = false;
        lastInteractionTime = now;
        startAction(MOOD_DIZZY, now, 2400L);
        triggerBounce(-0.9f, 0.3f);
        hapticPattern(new long[]{0, 40, 50, 40, 50, 40});
    }

    // ================================================================= Rotina e humor do dia

    private static boolean isNight(int hour) {
        return hour >= NIGHT_START_HOUR || hour < MORNING_HOUR;
    }

    /** Lê a hora (aloca um Calendar) no máximo uma vez por minuto. */
    private void refreshTimeOfDay(long now) {
        lastMinuteCheck = now;
        cachedHour = PetClock.hour();
        int day = PetClock.dayKey();
        int mix = (int) ((day * 2654435761L) >>> 11);
        personality = Math.floorMod(mix, 4);
    }

    private long yawnIntervalMs() {
        long base = personality == PERSONALITY_LAZY ? 150_000L : 240_000L;
        return base + (SystemClock.uptimeMillis() % 90_000L);
    }

    private boolean isActionRunning(long now) {
        return actionMood != MOOD_NONE && now < actionMoodUntil;
    }

    /** Comportamentos espontâneos (só rodam com a tela ligada, junto dos quadros normais). */
    private void updateRoutine(long now, boolean hudVisible) {
        if (now - lastMinuteCheck >= 60_000L) {
            refreshTimeOfDay(now);
            // De noite, parado há um minuto: dorme sozinho.
            if (isNight(cachedHour) && !state.isSleeping && !shopOpen
                    && now - lastInteractionTime > 60_000L && !isActionRunning(now)) {
                state.isSleeping = true;
                state.nightSleep = true;
                actionMood = MOOD_NONE;
                state.save();
            }
        }

        // Mensagem nova: segura o envelopinho.
        long notif = PetSignals.lastNotificationWall;
        if (notif > lastMailShownWall) {
            lastMailShownWall = notif;
            if (System.currentTimeMillis() - notif < 90_000L && !state.isSleeping && !shopOpen && !sulking) {
                startAction(MOOD_MAIL, now, 3200L);
                targetLookY = 2f;
                triggerBounce(-0.9f, -0.2f);
            }
        }

        if (state.isSleeping || shopOpen || sulking || isActionRunning(now)) {
            return;
        }
        // Bocejo: à noitinha, quando está cansado ou no dia preguiçoso.
        if (now >= nextYawnTime) {
            nextYawnTime = now + yawnIntervalMs();
            boolean evening = cachedHour >= EVENING_YAWN_HOUR || isNight(cachedHour);
            if (evening || state.energy < 40f || personality == PERSONALITY_LAZY) {
                startAction(MOOD_YAWN, now, 1800L);
                return;
            }
        }
        // Dia elétrico: pulinhos sozinho de vez em quando.
        if (personality == PERSONALITY_HYPER && !hudVisible && now >= nextHopTime) {
            nextHopTime = now + 7000L + (now % 4000L);
            triggerBounce(-0.7f, -0.15f);
        }
    }

    // ================================================================= Bateria e passos

    public void refreshBatteryDirect() {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) {
            return;
        }
        int capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (capacity > 0) {
            watchBatteryPct = Math.min(100, capacity);
        }
        isWatchCharging = bm.isCharging();
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
    }

    /** Cada passo dá 2 moedas e um pouquinho de felicidade. */
    public void onUserStep(int steps) {
        if (steps <= 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        lastStepTime = now;
        state.coins += steps * 2;
        state.happiness = PetState.clamp(state.happiness + steps * 0.5f);
        triggerBounce(-0.45f, -0.12f);
        if (now % 3L == 0L) {
            spawnParticle(PARTICLE_SPARKLE, (now % 28L) + 11, 14, now);
        }
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

    // ================================================================= Lógica e Ações

    private int getCurrentMood(long now) {
        if (actionMood != MOOD_NONE && now < actionMoodUntil) {
            return actionMood;
        }
        if (sulking && !shopOpen) {
            return MOOD_SULK;
        }
        if (forcedMood != MOOD_NONE) {
            return forcedMood;
        }
        if (state.isSleeping) {
            return MOOD_SLEEPING;
        }
        if (!isWatchCharging && watchBatteryPct <= 15) {
            return MOOD_LOW_BATTERY;
        }
        if (state.water < THIRSTY_THRESHOLD) {
            return MOOD_THIRSTY;
        }
        if (state.hunger < HUNGRY_THRESHOLD) {
            return MOOD_HUNGRY;
        }
        if (personality == PERSONALITY_NEEDY && now - lastLoveTime > NEEDY_AFTER_MS) {
            return MOOD_NEEDY; // dia carente: pede carinho
        }
        if (state.energy < SLEEPY_THRESHOLD) {
            return MOOD_SLEEPY;
        }
        if (state.happiness < SAD_THRESHOLD || state.poopCount >= 2) {
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
                partVy[i] = type == PARTICLE_SHOWER ? 0.45f
                        : type == PARTICLE_CRUMB ? 0.8f : -0.35f; // chuveiro e farelo caem, o resto sobe
                partExpire[i] = now + (type == PARTICLE_CRUMB ? 450L : PARTICLE_LIFETIME_MS);
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
        state.isSleeping = false;
        forcedMood = MOOD_NONE;
        state.hunger = PetState.clamp(state.hunger + 28f);
        state.happiness = PetState.clamp(state.happiness + 8f);
        state.coins += 5;
        state.mealsSincePoop++;
        if (state.mealsSincePoop >= 2 && state.poopCount < 3) {
            state.mealsSincePoop = 0;
            state.poopCount++;
        }
        startAction(MOOD_EATING, now, EAT_MS);
        mealFxStep = 0;
        targetLookX = 2.5f; // olha a maçã chegando
        targetLookY = 0.5f;
        triggerBounce(-0.6f, 0.15f);
        hapticTick(20);
        state.save();
    }

    private void giveWater(long now) {
        state.isSleeping = false;
        forcedMood = MOOD_NONE;
        state.water = 100f;
        state.happiness = PetState.clamp(state.happiness + 10f);
        state.coins += 8;
        startAction(MOOD_DRINKING, now, DRINK_MS);
        mealFxStep = 0;
        targetLookX = 2.5f; // olha o copo chegando
        targetLookY = 0.5f;
        triggerBounce(-0.6f, -0.15f);
        hapticTick(20);
        state.save();
    }

    private void cleanPoopShower(long now) {
        state.isSleeping = false;
        state.poopCount = 0;
        state.mealsSincePoop = 0;
        state.happiness = PetState.clamp(state.happiness + 20f);
        state.coins += 15;
        startAction(MOOD_SHOWERING, now, 2000L);
        triggerBounce(-1.0f, -0.25f);
        for (int i = 0; i < 6; i++) {
            spawnParticle(PARTICLE_SHOWER, i * 5 + 12, (i % 3) * 2 + 6, now);
        }
        spawnParticle(PARTICLE_SPARKLE, 36, 14, now);
        hapticPattern(new long[]{0, 30, 50, 30, 50, 45});
        state.save();
    }

    private void lovePet(long now) {
        state.isSleeping = false;
        forcedMood = MOOD_NONE;
        boolean wasNeedy = personality == PERSONALITY_NEEDY && now - lastLoveTime > NEEDY_AFTER_MS;
        lastLoveTime = now;
        state.happiness = PetState.clamp(state.happiness + (personality == PERSONALITY_NEEDY ? 33f : 22f));
        state.coins += wasNeedy ? 8 : 3;
        startAction(now % 2L == 0L ? MOOD_LOVED : MOOD_WINK, now, 1800L);
        triggerBounce(-1.1f, -0.25f);
        spawnParticle(PARTICLE_HEART, (now % 7L) + 11, 15, now);
        spawnParticle(PARTICLE_HEART, (now % 6L) + 35, 13, now);
        hapticTick(20);
        state.save();
    }

    private void toggleSleep(long now) {
        forcedMood = MOOD_NONE;
        state.isSleeping = !state.isSleeping;
        if (state.isSleeping) {
            actionMood = MOOD_NONE;
            targetLookX = 0f;
            targetLookY = 0f;
            spawnParticle(PARTICLE_Z, 35, 16, now);
        } else {
            startAction(MOOD_SURPRISED, now, 900L);
            triggerBounce(-0.8f, -0.2f);
        }
        hapticTick(20);
        state.save();
    }

    private void toggleShop() {
        shopOpen = !shopOpen;
        if (shopOpen) {
            state.isSleeping = false;
            shopCategory = CATEGORY_HEAD;
            System.arraycopy(state.equipped, 0, shopPreview, 0, CATEGORY_COUNT);
            triggerBounce(-0.6f, 0.15f);
        }
        hapticTick(20);
    }

    private void selectShopCategory(int category) {
        // Ao trocar de aba, a aba anterior volta a mostrar o que está equipado.
        shopPreview[shopCategory] = state.equipped[shopCategory];
        shopCategory = category;
        shopPreview[category] = state.equipped[category];
        triggerBounce(-0.4f, 0.12f);
        hapticTick(15);
    }

    private void shopStep(int delta) {
        int count = itemCount(shopCategory);
        shopPreview[shopCategory] = ((shopPreview[shopCategory] + delta) % count + count) % count;
        triggerBounce(-0.4f, 0.12f);
    }

    /** Item que aparece no bichinho: o da pré-visualização com a loja aberta, senão o equipado. */
    private int shownItem(int category) {
        return shopOpen ? shopPreview[category] : state.equipped[category];
    }

    private void buyOrEquipPreviewItem(long now) {
        int category = shopCategory;
        int item = shopPreview[category];
        if (state.isUnlocked(category, item)) {
            state.equipped[category] = item;
            startAction(MOOD_LOVED, now, 1400L);
            triggerBounce(-0.9f, -0.2f);
            spawnParticle(PARTICLE_SPARKLE, 14, 14, now);
            spawnParticle(PARTICLE_SPARKLE, 35, 14, now);
            hapticTick(25);
            state.save();
            return;
        }
        int price = ITEM_PRICES[category][item];
        if (state.coins >= price) {
            state.coins -= price;
            state.unlock(category, item);
            state.equipped[category] = item;
            startAction(MOOD_LOVED, now, 1600L);
            triggerBounce(-1.1f, -0.25f);
            spawnParticle(PARTICLE_SPARKLE, 12, 14, now);
            spawnParticle(PARTICLE_HEART, 36, 14, now);
            hapticPattern(new long[]{0, 40, 60, 60});
            state.save();
            return;
        }
        // Sem moedas: balança "não".
        triggerBounce(-0.4f, 0.25f);
        hapticPattern(new long[]{0, 80, 60, 80});
    }

    // ================================================================= Entrada (Coroa e Toque)

    public void handleRotaryScroll(float delta) {
        long now = SystemClock.uptimeMillis();
        if (now - lastInteractionTime >= HUD_VISIBLE_MS) {
            showDateInsteadOfClock = false;
        }
        lastInteractionTime = now;

        if (shopOpen) {
            if (delta > 0f) {
                shopStep(1);
            } else if (delta < 0f) {
                shopStep(-1);
            }
            hapticTick(12);
            return;
        }

        // Fora da loja, girar a coroa faz carinho e o bichinho olha para o lado.
        state.isSleeping = false;
        state.happiness = PetState.clamp(state.happiness + 4f);
        state.coins++;
        actionMood = MOOD_LOVED;
        actionMoodUntil = now + 1400L;
        targetLookX = delta > 0f ? 2.5f : -2.5f;
        triggerBounce(-0.5f, 0.18f);
        spawnParticle(PARTICLE_HEART, delta > 0f ? 35 : 12, 15, now);
        hapticTick(12);
    }

    public boolean handleTouch(MotionEvent event, int width, int height) {
        if (width <= 0 || height <= 0) {
            return true;
        }
        long now = SystemClock.uptimeMillis();
        boolean wasIdle = now - lastInteractionTime >= HUD_VISIBLE_MS;
        lastInteractionTime = now;
        if (wasIdle) {
            showDateInsteadOfClock = false;
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

    /**
     * Loja: barra de baixo = [fechar | cabeça | nariz | olhos]; laterais = item anterior/próximo;
     * tocar no bichinho = comprar ou equipar o item mostrado (a loja continua aberta).
     */
    private void handleShopTap(float fx, float fy, long now) {
        if (fy < 0.28f) {
            // Topo: X de sair fica no canto de cima à direita; o resto do topo também fecha.
            shopOpen = false;
            hapticTick(15);
            return;
        }
        // Parte de baixo, perto da borda: abas em arco ou o botão de comprar no meio.
        double dx = (fx - 0.5f) * GRID_SIZE;
        double dy = (fy - 0.5f) * GRID_SIZE;
        if (dy >= 4.0 && Math.hypot(dx, dy) >= SHOP_TAB_RADIUS - 6.0) {
            double deg = Math.toDegrees(Math.atan2(dx, dy)); // 0 = embaixo, + = direita
            if (Math.abs(deg) < SHOP_BUY_HALF_DEG) {
                buyOrEquipPreviewItem(now);
            } else {
                int nearest = 0;
                for (int c = 1; c < CATEGORY_COUNT; c++) {
                    if (Math.abs(deg - SHOP_TAB_DEG[c]) < Math.abs(deg - SHOP_TAB_DEG[nearest])) {
                        nearest = c;
                    }
                }
                selectShopCategory(nearest);
            }
            return;
        }
        if (fx < 0.25f) {
            shopStep(-1);
        } else if (fx > 0.75f) {
            shopStep(1);
        } else {
            buyOrEquipPreviewItem(now);
            return;
        }
        hapticTick(15);
    }

    private void handlePetTap(float fx, float fy, boolean wasIdle, long now) {
        if (sulking) {
            // Emburrado: o toque faz ele se virar todo feliz.
            sulking = false;
            state.isSleeping = false;
            startAction(MOOD_LOVED, now, 1800L);
            triggerBounce(-1.2f, -0.3f);
            spawnParticle(PARTICLE_HEART, 12, 14, now);
            spawnParticle(PARTICLE_HEART, 35, 12, now);
            hapticPattern(new long[]{0, 30, 60, 30});
            return;
        }
        int actionIcon = actionIconAt(fx, fy);
        if (wasIdle && (fy < 0.28f || actionIcon >= 0)) {
            // Primeiro toque nas bordas só mostra relógio/barras/botões (que estavam escondidos).
            triggerBounce(-0.4f, 0.1f);
            hapticTick(10);
            return;
        }
        if (!wasIdle && fy < 0.28f) {
            showDateInsteadOfClock = !showDateInsteadOfClock;
            hapticTick(12);
            return;
        }
        if (state.poopCount > 0 && fx < 0.25f && fy > 0.36f && fy <= 0.62f) {
            cleanPoopShower(now);
        }
        if (actionIcon >= 0) {
            // Ícones em arco na borda de baixo.
            switch (actionIcon) {
                case 0: feedPet(now); break;
                case 1: giveWater(now); break;
                case 2: lovePet(now); break;
                case 3: toggleSleep(now); break;
                default: toggleShop(); break;
            }
            return;
        }

        // Cutucada.
        targetLookX = (fx - 0.5f) * 6f;
        targetLookY = (fy - 0.5f) * 5f;
        rapidPokeCount = now - lastPokeTime < 550L ? rapidPokeCount + 1 : 1;
        lastPokeTime = now;

        if (state.isSleeping) {
            state.isSleeping = false;
            startAction(MOOD_SURPRISED, now, 1100L);
            triggerBounce(-0.9f, -0.25f);
        } else if (actionMood == MOOD_GRUMPY && now < actionMoodUntil) {
            // Já está bravo: cutucar mais só aumenta a birra (carinho acalma).
            actionMoodUntil = Math.min(now + GRUMPY_MAX_MS, actionMoodUntil + GRUMPY_EXTRA_PER_POKE_MS);
            triggerBounce(-0.6f, 0.3f);
            hapticTick(35);
        } else if (rapidPokeCount >= GRUMPY_POKES) {
            startAction(MOOD_GRUMPY, now, GRUMPY_MS);
            triggerBounce(-0.8f, 0.3f);
            hapticTick(35);
        } else {
            startAction(rapidPokeCount % 2 == 0 ? MOOD_WINK : MOOD_SURPRISED, now, 900L);
            triggerBounce(-0.85f, -0.22f);
            spawnParticle(PARTICLE_SPARKLE, (now % 24L) + 12, 13, now);
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
            state.isSleeping = false;
            state.happiness = PetState.clamp(state.happiness + 5f);
            state.coins++;
            actionMood = MOOD_LOVED;
            actionMoodUntil = now + 1500L;
            lastLoveTime = now;
            squash = 0.22f;
            spawnParticle(PARTICLE_HEART, (now % 26L) + 11, 14, now);
            hapticTick(12);
        }
    }

    // ================================================================= Renderização Principal

    /**
     * Atualiza a simulação e desenha um quadro.
     *
     * @param ambient modo econômico (só relógio e bichinho dormindo)
     * @return quantos ms esperar até o próximo quadro
     */
    public long renderScene(Canvas canvas, int width, int height, boolean ambient) {
        canvas.drawColor(0xFF000000);
        if (width <= 0 || height <= 0) {
            return 500L;
        }
        long now = SystemClock.uptimeMillis();

        if (ambient) {
            drawAmbient(canvas, width, height);
            return 60_000L;
        }

        // No modo WatchFace atualiza o nível de bateria periodicamente sem gastar IPC a cada quadro.
        if (isWatchFaceMode && now - lastBatteryPollTime > 60_000L) {
            lastBatteryPollTime = now;
            refreshBatteryDirect();
        }

        float realDt = Math.min(1.0f, Math.max(0.001f, (now - lastFrameTime) / 1000f));
        float physDt = Math.min(0.05f, realDt);
        lastFrameTime = now;

        boolean hudVisible = now - lastInteractionTime < HUD_VISIBLE_MS;
        updateStatsAndAlerts(realDt, now);
        updateRoutine(now, hudVisible);
        int mood = getCurrentMood(now);
        updateEyesAndBounce(physDt, now);
        facePaint = grid.whitePaint;

        // Zzz quando está dormindo (um a cada 1,8 s).
        if (mood == MOOD_SLEEPING && now / 1800L != lastZzzSlot) {
            lastZzzSlot = now / 1800L;
            spawnParticle(PARTICLE_Z, (int) (now % 4L) + 34, 17, now);
        }
        // Animado com a caminhada: pulinhos seguidos.
        if (mood == MOOD_EXCITED && bounceY == 0f && bounceVelY == 0f) {
            triggerBounce(-0.8f, -0.15f);
        }
        // Meta de passos: confete.
        if (mood == MOOD_CELEBRATE) {
            int slot = (int) ((now - actionStartTime) / 450L);
            if (slot >= celebrateFxStep && slot < 6) {
                celebrateFxStep = slot + 1;
                spawnParticle(slot % 2 == 0 ? PARTICLE_SPARKLE : PARTICLE_HEART, 8 + (slot * 7) % 34, 12 + slot % 3, now);
            }
        }

        // Fundo comprado na loja (bem apagadinho).
        accessories.drawBackground(canvas, shownItem(CATEGORY_BG), now);

        // ---- Posição do rosto (só rosto, sem corpo)
        int breathe = (mood == MOOD_SLEEPING || (now / IDLE_FRAME_MS) % 2L != 0L) ? 1 : 0;
        int sway = shopOpen ? 0 : clampInt(Math.round(lookX * 0.4f), -1, 1);
        if (mood == MOOD_DIZZY) {
            sway = (now / 140L) % 2L == 0L ? -1 : 1; // cambaleando
        }
        int cx = 25 + sway;
        int squashX = Math.round(squash * 3f);
        int squashY = Math.round(squash * 2f);
        int head = FACE_TOP + Math.round(bounceY) + breathe + squashY - (shopOpen ? SHOP_FACE_LIFT : 0);
        int eyeY = head + 7;
        int lx = cx - 5 - squashX;
        int rx = cx + 6 + squashX;
        int mouthY = eyeY + MOUTH_OFFSET;

        // Sozinho por um tempo: o rostinho cresce para ocupar a tela.
        float zoomTarget = (!shopOpen && !hudVisible && mood != MOOD_MAIL) ? IDLE_FACE_ZOOM : 1f;
        faceZoom += (zoomTarget - faceZoom) * Math.min(1f, physDt * 7f);
        if (Math.abs(zoomTarget - faceZoom) < 0.004f) {
            faceZoom = zoomTarget;
        }
        canvas.save();
        canvas.scale(faceZoom, faceZoom, width * 0.5f, height * (FACE_ZOOM_PIVOT_ROW / GRID_SIZE));

        // ---- Acessórios (na loja mostra a pré-visualização)
        int headItem = shownItem(CATEGORY_HEAD);
        boolean walking = now - lastStepTime < WALKING_WINDOW_MS;
        if (!shopOpen && walking && headItem == ITEM_NONE) {
            headItem = HEAD_HEADBAND; // faixa de academia enquanto caminha
        }
        if (mood == MOOD_SULK) {
            accessories.drawSulkBack(canvas, cx, eyeY, now);
            accessories.drawHeadItem(canvas, cx, eyeY, headItem, now);
        } else {
            drawCheeks(canvas, cx, eyeY);
            drawEyes(canvas, lx, rx, eyeY, mood, now);
            drawMouth(canvas, cx, mouthY, mood, now);
            int neck = shownItem(CATEGORY_NECK);
            boolean mouthBusy = mood == MOOD_EATING || mood == MOOD_DRINKING || mood == MOOD_YAWN;
            if (!(neck == NECK_PACIFIER && mouthBusy)) {
                accessories.drawNeckItem(canvas, cx, mouthY, neck);
            }
            accessories.drawNoseItem(canvas, cx, eyeY, shownItem(CATEGORY_NOSE));
            accessories.drawEyesItem(canvas, lx, rx, eyeY, cx, shownItem(CATEGORY_EYES));
            accessories.drawHeadItem(canvas, cx, eyeY, headItem, now);
            // Coisa na mãozinha (guarda enquanto come, bebe ou toma banho).
            boolean handsBusy = mood == MOOD_EATING || mood == MOOD_DRINKING || mood == MOOD_SHOWERING;
            if (!handsBusy) {
                accessories.drawHoldItem(canvas, cx + 12, mouthY, shownItem(CATEGORY_HOLD), facePaint, now);
            }
            drawMealProps(canvas, mood, cx, mouthY, now);
            drawMoodExtras(canvas, mood, cx, eyeY, mouthY, now);
        }
        canvas.restore();

        if (state.poopCount > 0 && !shopOpen) {
            accessories.drawPoop(canvas, 4, 25, now);
        }


        accessories.drawMoodProps(canvas, mood, cx, head, shopOpen, actionStartTime, now);
        int particles = updateAndDrawParticles(canvas, physDt, now);

        if (shopOpen) {
            drawShopOverlay(canvas);
        } else if (hudVisible) {
            drawMinimalHud(canvas, now);
        }

        return nextFrameDelay(now, hudVisible, zoomTarget, particles, mood == MOOD_SLEEPING);
    }

    /**
     * Quanto esperar até o próximo quadro. Para gastar pouca bateria, só anima a 60 fps
     * durante movimento de verdade; parado, acorda só nos eventos (piscar, olhar) ou a cada 1 s.
     */
    private long nextFrameDelay(long now, boolean hudVisible, float zoomTarget, int particles, boolean sleeping) {
        boolean moving = Math.abs(bounceY) > 0.05f
                || Math.abs(squash) > 0.02f
                || Math.abs(zoomTarget - faceZoom) > 0f
                || Math.abs(targetLookX - lookX) > 0.15f
                || Math.abs(targetLookY - lookY) > 0.15f
                || isActionRunning(now);
        if (moving) {
            return 16L;
        }
        if (particles == PARTICLES_MOVING) {
            return 90L;
        }
        long delay;
        if (shopOpen || hudVisible) {
            delay = 500L - now % 500L; // dois pontinhos do relógio piscando
        } else {
            delay = IDLE_FRAME_MS - now % IDLE_FRAME_MS; // respiração
        }
        if (particles == PARTICLES_SLOW) {
            delay = Math.min(delay, 500L); // só Zzz subindo devagar
        }
        if (!sleeping) {
            // Dormindo de olho fechado não precisa acordar a tela para piscar nem olhar.
            delay = Math.min(delay, (isBlinking ? blinkEndTime : nextBlinkTime) - now);
            if (!hudVisible && !shopOpen) {
                delay = Math.min(delay, nextSaccadeTime - now);
            }
        }
        if (hudVisible || shopOpen) {
            // Redesenha exatamente quando o HUD some.
            delay = Math.min(delay, lastInteractionTime + HUD_VISIBLE_MS - now);
        }
        return Math.max(16L, Math.min(IDLE_FRAME_MS, delay));
    }

    private void updateStatsAndAlerts(float realDt, long now) {
        state.updateRealtimeDecay(realDt);

        // Lembretes vibrando (sede a cada 15 min, fome a cada 20 min).
        if (!state.isSleeping && state.water < THIRSTY_THRESHOLD
                && (lastWaterVibrateTime == 0L || now - lastWaterVibrateTime > 15 * 60_000L)) {
            lastWaterVibrateTime = now;
            hapticPattern(new long[]{0, 110, 80, 110, 80, 220});
            triggerBounce(-0.7f, -0.2f);
        }
        if (!state.isSleeping && state.hunger < HUNGRY_THRESHOLD
                && (lastHungerVibrateTime == 0L || now - lastHungerVibrateTime > 20 * 60_000L)) {
            lastHungerVibrateTime = now;
            hapticPattern(new long[]{0, 180, 120, 180});
            triggerBounce(-0.7f, 0.2f);
        }
    }

    private void updateEyesAndBounce(float dt, long now) {
        // Piscar.
        if (!isBlinking && now >= nextBlinkTime) {
            isBlinking = true;
            blinkEndTime = now + 140L;
            nextBlinkTime = now + 2600L + (now % 2400L);
        } else if (isBlinking && now >= blinkEndTime) {
            isBlinking = false;
        }

        // Olhar para os lados sozinho quando ninguém mexe.
        boolean eatingOrDrinking = (actionMood == MOOD_EATING || actionMood == MOOD_DRINKING) && now < actionMoodUntil;
        if (!eatingOrDrinking && now >= nextSaccadeTime && now - lastInteractionTime > 1200L) {
            switch ((int) ((now / 173L) % 7L)) {
                case 0: targetLookX = -2.2f; targetLookY = 0f; break;
                case 1: targetLookX = 2.2f; targetLookY = 0f; break;
                case 2: targetLookX = 0f; targetLookY = -1.5f; break;
                case 3: targetLookX = -1.5f; targetLookY = 1.2f; break;
                case 4: targetLookX = 1.5f; targetLookY = 1.2f; break;
                default: targetLookX = 0f; targetLookY = 0f; break;
            }
            long gap = personality == PERSONALITY_HYPER ? 1300L : personality == PERSONALITY_LAZY ? 4200L : 2200L;
            nextSaccadeTime = now + gap + (now % 2000L);
            // Olhar sozinho é um "pulo" de pixel: sem quadros intermediários (economiza bateria).
            lookX = targetLookX;
            lookY = targetLookY;
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

    private static final int PARTICLES_NONE = 0;
    private static final int PARTICLES_SLOW = 1;
    private static final int PARTICLES_MOVING = 2;
    private long lastZzzSlot = -1L;

    /** @return {@link #PARTICLES_NONE}, {@link #PARTICLES_SLOW} (só Zzz) ou {@link #PARTICLES_MOVING} */
    private int updateAndDrawParticles(Canvas canvas, float dt, long now) {
        int result = PARTICLES_NONE;
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (now >= partExpire[i]) {
                continue;
            }
            partY[i] += partVy[i] * dt * 18f;
            int x = Math.round(partX[i]);
            int y = Math.round(partY[i]);
            switch (partType[i]) {
                case PARTICLE_HEART:
                    grid.drawSprite5x5(canvas, x, y, SPRITE_HEART, grid.pinkPaint);
                    break;
                case PARTICLE_Z:
                    grid.drawSprite5x5(canvas, x, y, SPRITE_Z, grid.lavenderPaint);
                    break;
                case PARTICLE_SPARKLE:
                    grid.drawSprite5x5(canvas, x, y, SPRITE_SPARKLE, grid.goldPaint);
                    break;
                case PARTICLE_APPLE:
                    accessories.drawApple(canvas, x, y);
                    break;
                case PARTICLE_CRUMB:
                    grid.px(canvas, x, y, (i & 1) == 0 ? grid.redPaint : grid.goldPaint);
                    break;
                default: // gota / chuveiro
                    grid.drawSprite5x5(canvas, x, y, SPRITE_WATER_DROP, grid.cyanPaint);
                    break;
            }
            if (partType[i] == PARTICLE_Z) {
                result = Math.max(result, PARTICLES_SLOW);
            } else {
                result = PARTICLES_MOVING;
            }
        }
        return result;
    }

    // ================================================================= Desenho: Rosto (olhos, bochechas e boca)

    /** Linha de referência da "cabeça": os olhos ficam em FACE_TOP + 7 (abaixo do relógio). */
    private static final int FACE_TOP = 22;
    /** Distância vertical dos olhos até a boca. */
    private static final int MOUTH_OFFSET = 6;
    /** Quanto o rosto cresce quando está sozinho, e a linha da grade que fica parada no zoom. */
    private static final float IDLE_FACE_ZOOM = 1.55f;
    private static final float FACE_ZOOM_PIVOT_ROW = 28f;
    /** Na loja o rosto sobe um pouco para caber o item de pescoço acima das abas. */
    private static final int SHOP_FACE_LIFT = 3;

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void mirrorPx(Canvas canvas, int cx, int dx, int y, Paint paint) {
        grid.px(canvas, cx + dx, y, paint);
        grid.px(canvas, cx + 1 - dx, y, paint);
    }

    /** Bochechas rosadas embaixo dos olhos. */
    private void drawCheeks(Canvas canvas, int cx, int eyeY) {
        mirrorPx(canvas, cx, -8, eyeY + 3, grid.blushPaint);
        mirrorPx(canvas, cx, -7, eyeY + 3, grid.blushPaint);
    }

    private void drawEyes(Canvas canvas, int leftX, int rightX, int y, int mood, long now) {
        if (mood == MOOD_SLEEPING) {
            drawSleepingEye(canvas, leftX, y);
            drawSleepingEye(canvas, rightX, y);
            return;
        }
        if (isBlinking) {
            grid.hline(canvas, leftX - 2, leftX + 2, y, facePaint);
            grid.hline(canvas, rightX - 2, rightX + 2, y, facePaint);
            return;
        }
        switch (mood) {
            case MOOD_LOVED:
            case MOOD_SHOWERING:
                drawHappyArcEye(canvas, leftX, y);
                drawHappyArcEye(canvas, rightX, y);
                break;

            case MOOD_EATING:
            case MOOD_DRINKING: {
                // Olhos abertos vendo a comida chegar; depois fecha de gostoso "^^".
                long arrive = mood == MOOD_EATING ? EAT_ARRIVE_MS : DRINK_ARRIVE_MS;
                if (now - actionStartTime < arrive) {
                    drawOvalEye(canvas, leftX, y, false);
                    drawOvalEye(canvas, rightX, y, false);
                } else {
                    drawHappyArcEye(canvas, leftX, y);
                    drawHappyArcEye(canvas, rightX, y);
                }
                break;
            }

            case MOOD_HUNGRY:
            case MOOD_SAD:
            case MOOD_THIRSTY:
                drawOvalEye(canvas, leftX, y, true);
                drawOvalEye(canvas, rightX, y, true);
                // Sobrancelhas tristes (ponta de dentro levantada).
                grid.hline(canvas, leftX - 2, leftX - 1, y - 4, facePaint);
                grid.hline(canvas, leftX, leftX + 1, y - 5, facePaint);
                grid.hline(canvas, rightX + 1, rightX + 2, y - 4, facePaint);
                grid.hline(canvas, rightX - 1, rightX, y - 5, facePaint);
                if (mood != MOOD_HUNGRY) {
                    // Lágrima escorrendo.
                    int tearY = y + 1 + (int) ((now / 250L) % 3L);
                    grid.px(canvas, leftX - 3, tearY, grid.cyanPaint);
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
                break;

            case MOOD_STRETCH:
            case MOOD_CELEBRATE:
                drawHappyArcEye(canvas, leftX, y);
                drawHappyArcEye(canvas, rightX, y);
                break;

            case MOOD_EXCITED:
                // Olhos de estrela.
                grid.drawSprite5x5(canvas, leftX - 2, y - 2, SPRITE_SPARKLE, grid.goldPaint);
                grid.drawSprite5x5(canvas, rightX - 2, y - 2, SPRITE_SPARKLE, grid.goldPaint);
                break;

            case MOOD_DIZZY: {
                int[] spiral = (now / 150L) % 2L == 0L ? SPRITE_SPIRAL_A : SPRITE_SPIRAL_B;
                grid.drawSprite5x5(canvas, leftX - 2, y - 2, spiral, facePaint);
                grid.drawSprite5x5(canvas, rightX - 2, y - 2, spiral, facePaint);
                break;
            }

            case MOOD_YAWN: {
                long t = now - actionStartTime;
                if (t < 300L || t >= 1400L) {
                    drawHalfClosedEye(canvas, leftX, y);
                    drawHalfClosedEye(canvas, rightX, y);
                    if (t >= 1400L) {
                        grid.px(canvas, leftX - 3, y + 1, grid.cyanPaint); // lagriminha do bocejo
                    }
                } else {
                    drawSleepingEye(canvas, leftX, y);
                    drawSleepingEye(canvas, rightX, y);
                }
                break;
            }

            case MOOD_MAIL:
                drawSurprisedEye(canvas, leftX, y);
                drawSurprisedEye(canvas, rightX, y);
                break;

            case MOOD_NEEDY:
                // Olhinhos de cachorro pidão.
                drawOvalEye(canvas, leftX, y, true);
                drawOvalEye(canvas, rightX, y, true);
                break;

            default:
                if (personality == PERSONALITY_LAZY && (now / 5000L) % 3L == 0L) {
                    drawHalfClosedEye(canvas, leftX, y); // dia preguiçoso: olho caído
                    drawHalfClosedEye(canvas, rightX, y);
                } else {
                    drawOvalEye(canvas, leftX, y, false);
                    drawOvalEye(canvas, rightX, y, false);
                }
                break;
        }
    }

    /** Contorno oval 5x6 preenchido de branco (base dos olhos). */
    private void fillOval(Canvas canvas, int x, int y) {
        grid.hline(canvas, x - 1, x + 1, y - 3, facePaint);
        grid.rectPx(canvas, x - 2, y - 2, x + 2, y + 1, facePaint);
        grid.hline(canvas, x - 1, x + 1, y + 2, facePaint);
    }

    /** Olho oval branco com pupila 3x3 que segue o olhar e um brilho (dois quando triste). */
    private void drawOvalEye(Canvas canvas, int x, int y, boolean teary) {
        fillOval(canvas, x, y);
        int dx = clampInt(Math.round(lookX * 0.4f), -1, 1);
        int dy = clampInt(Math.round(lookY * 0.4f), -1, 1);
        grid.rectSolid(canvas, x - 1 + dx, y - 1 + dy, x + 1 + dx, y + 1 + dy, grid.blackPaint);
        grid.px(canvas, x - 1 + dx, y - 1 + dy, grid.whitePaint);
        if (teary) {
            grid.px(canvas, x + 1 + dx, y + 1 + dy, grid.whitePaint);
        }
    }

    private void drawSleepingEye(Canvas canvas, int x, int y) {
        grid.px(canvas, x - 2, y, facePaint);
        grid.hline(canvas, x - 1, x + 1, y + 1, facePaint);
        grid.px(canvas, x + 2, y, facePaint);
    }

    /** Olho feliz "∩". */
    private void drawHappyArcEye(Canvas canvas, int x, int y) {
        grid.hline(canvas, x - 1, x + 1, y - 1, facePaint);
        grid.px(canvas, x - 2, y, facePaint);
        grid.px(canvas, x + 2, y, facePaint);
        grid.px(canvas, x - 2, y + 1, facePaint);
        grid.px(canvas, x + 2, y + 1, facePaint);
    }

    private void drawHalfClosedEye(Canvas canvas, int x, int y) {
        grid.hline(canvas, x - 2, x + 2, y, facePaint);
        grid.hline(canvas, x - 2, x + 2, y + 1, facePaint);
        grid.hline(canvas, x - 1, x + 1, y + 2, facePaint);
        grid.px(canvas, x, y + 1, grid.blackPaint);
    }

    private void drawGrumpyEye(Canvas canvas, int x, int y, boolean leftSide) {
        grid.rectPx(canvas, x - 2, y - 1, x + 2, y + 1, facePaint);
        grid.hline(canvas, x - 1, x + 1, y + 2, facePaint);
        grid.px(canvas, x, y, grid.blackPaint);
        // Sobrancelha franzida descendo para o meio do rosto.
        int dir = leftSide ? 1 : -1;
        grid.px(canvas, x - 2 * dir, y - 4, facePaint);
        grid.px(canvas, x - dir, y - 4, facePaint);
        grid.px(canvas, x, y - 3, facePaint);
        grid.px(canvas, x + dir, y - 3, facePaint);
        grid.px(canvas, x + 2 * dir, y - 2, facePaint);
    }

    /** Olho arregalado: oval branco com pupilinha de 1 pixel. */
    private void drawSurprisedEye(Canvas canvas, int x, int y) {
        fillOval(canvas, x, y);
        grid.px(canvas, x, y - 1, grid.blackPaint);
        grid.px(canvas, x, y, grid.blackPaint);
    }

    /** Boquinha centrada entre cx e cx + 1. */
    private void drawMouth(Canvas canvas, int cx, int y, int mood, long now) {
        Paint ink = facePaint;
        switch (mood) {
            case MOOD_LOVED:
            case MOOD_SHOWERING:
                // Boquinha aberta com linguinha.
                grid.hline(canvas, cx - 1, cx + 2, y, ink);
                grid.px(canvas, cx - 1, y + 1, ink);
                grid.hline(canvas, cx, cx + 1, y + 1, grid.pinkPaint);
                grid.px(canvas, cx + 2, y + 1, ink);
                grid.hline(canvas, cx, cx + 1, y + 2, ink);
                break;

            case MOOD_HUNGRY:
                grid.hline(canvas, cx - 1, cx + 2, y + 1, ink);
                // Babinha.
                grid.px(canvas, cx + 2, y + 2, grid.cyanPaint);
                if ((now / 400L) % 2L == 0L) {
                    grid.px(canvas, cx + 2, y + 3, grid.cyanPaint);
                }
                break;

            case MOOD_SAD:
            case MOOD_THIRSTY:
            case MOOD_LOW_BATTERY:
                grid.hline(canvas, cx, cx + 1, y, ink);
                grid.px(canvas, cx - 1, y + 1, ink);
                grid.px(canvas, cx + 2, y + 1, ink);
                break;

            case MOOD_SLEEPY:
            case MOOD_SURPRISED:
                drawOMouth(canvas, cx, y);
                break;

            case MOOD_SLEEPING: {
                int half = (now / 900L) % 2L == 0L ? 1 : 2; // respiração
                grid.hline(canvas, cx + 1 - half, cx + half, y + 1, ink);
                break;
            }

            case MOOD_GRUMPY:
                grid.px(canvas, cx - 1, y + 1, ink);
                grid.px(canvas, cx, y, ink);
                grid.px(canvas, cx + 1, y + 1, ink);
                grid.px(canvas, cx + 2, y, ink);
                break;

            case MOOD_EATING: {
                long t = now - actionStartTime;
                if (t < EAT_ARRIVE_MS) {
                    drawBigOpenMouth(canvas, cx, y);          // "aaah" esperando a maçã
                } else if (t < EAT_CHEW_END_MS) {
                    long sub = (t - EAT_ARRIVE_MS) % EAT_BITE_MS;
                    if (sub < 140L) {
                        drawBigOpenMouth(canvas, cx, y);      // nhac!
                    } else {
                        // Mastigando: boquinha ondulada subindo e descendo.
                        int up = ((sub / 110L) % 2L == 0L) ? 0 : 1;
                        grid.px(canvas, cx - 1, y + 1 - up, ink);
                        grid.hline(canvas, cx, cx + 1, y + up, ink);
                        grid.px(canvas, cx + 2, y + 1 - up, ink);
                    }
                } else {
                    // Lambendo os beiços.
                    grid.px(canvas, cx - 1, y, ink);
                    grid.hline(canvas, cx, cx + 1, y + 1, ink);
                    grid.px(canvas, cx + 2, y, ink);
                    grid.px(canvas, cx + 2, y + 1, grid.pinkPaint);
                    if ((t / 150L) % 2L == 0L) {
                        grid.px(canvas, cx + 3, y + 1, grid.pinkPaint);
                    }
                }
                break;
            }

            case MOOD_WINK:
                // Boquinha de gato "w".
                grid.px(canvas, cx - 2, y, ink);
                grid.px(canvas, cx - 1, y + 1, ink);
                grid.hline(canvas, cx, cx + 1, y, ink);
                grid.px(canvas, cx + 2, y + 1, ink);
                grid.px(canvas, cx + 3, y, ink);
                break;

            case MOOD_DRINKING: {
                long t = now - actionStartTime;
                if (t < DRINK_SIP_END_MS) {
                    drawOMouth(canvas, cx, y);                // biquinho no canudo
                } else {
                    // "Aaah!" satisfeito, com uma gotinha escorrendo no canto da boca.
                    drawBigOpenMouth(canvas, cx, y);
                    int drip = (int) Math.min(2L, (t - DRINK_SIP_END_MS) / 150L);
                    grid.px(canvas, cx - 2, y + 2 + drip, grid.cyanPaint);
                }
                break;
            }

            case MOOD_STRETCH:
            case MOOD_CELEBRATE:
            case MOOD_EXCITED:
                drawBigOpenMouth(canvas, cx, y);
                break;

            case MOOD_DIZZY:
                // Boquinha ondulada.
                grid.px(canvas, cx - 2, y + 1, ink);
                grid.px(canvas, cx - 1, y, ink);
                grid.px(canvas, cx, y + 1, ink);
                grid.px(canvas, cx + 1, y, ink);
                grid.px(canvas, cx + 2, y + 1, ink);
                grid.px(canvas, cx + 3, y, ink);
                break;

            case MOOD_YAWN: {
                long t = now - actionStartTime;
                if (t < 300L || t >= 1500L) {
                    drawOMouth(canvas, cx, y);
                } else {
                    // Bocejão: boca alta com linguinha.
                    grid.hline(canvas, cx, cx + 1, y - 1, ink);
                    for (int row = y; row <= y + 3; row++) {
                        grid.px(canvas, cx - 1, row, ink);
                        grid.px(canvas, cx + 2, row, ink);
                    }
                    grid.hline(canvas, cx, cx + 1, y + 3, grid.pinkPaint);
                    grid.hline(canvas, cx, cx + 1, y + 4, ink);
                }
                break;
            }

            case MOOD_MAIL:
                drawOMouth(canvas, cx, y);
                break;

            case MOOD_NEEDY:
                // Biquinho pidão.
                grid.hline(canvas, cx, cx + 1, y + 1, ink);
                grid.px(canvas, cx - 1, y, ink);
                grid.px(canvas, cx + 2, y, ink);
                break;

            default:
                // Sorrisinho.
                grid.px(canvas, cx - 1, y, ink);
                grid.hline(canvas, cx, cx + 1, y + 1, ink);
                grid.px(canvas, cx + 2, y, ink);
                break;
        }
    }

    // ---- Animação de comer e beber (fases pelo tempo desde o começo da ação)
    private static final long EAT_ARRIVE_MS = 450L;
    private static final long EAT_BITE_MS = 400L;
    private static final long EAT_CHEW_END_MS = EAT_ARRIVE_MS + 4 * EAT_BITE_MS;
    private static final long DRINK_ARRIVE_MS = 400L;
    private static final long DRINK_SIP_END_MS = 2100L;

    /** Desacelera no fim (chega devagarinho). */
    private static float easeOut(float p) {
        float q = 1f - Math.max(0f, Math.min(1f, p));
        return 1f - q * q * q;
    }

    /** Maçã chegando e sendo mordida / copo com canudinho, colados na boca. */
    private void drawMealProps(Canvas canvas, int mood, int cx, int mouthY, long now) {
        long t = now - actionStartTime;
        if (mood == MOOD_EATING) {
            int ax = cx + 3;
            int ay = mouthY - 3;
            if (t < EAT_ARRIVE_MS) {
                float rest = 1f - easeOut(t / (float) EAT_ARRIVE_MS);
                accessories.drawApple(canvas, Math.round(ax + 14 * rest), Math.round(ay - 7 * rest));
            } else if (t < EAT_CHEW_END_MS) {
                long into = t - EAT_ARRIVE_MS;
                int bites = (int) Math.min(3L, (into + EAT_BITE_MS - 140L) / EAT_BITE_MS);
                boolean pushing = into % EAT_BITE_MS < 140L;
                accessories.drawBittenApple(canvas, pushing ? ax - 1 : ax, ay, bites);
                if (bites > mealFxStep) {
                    mealFxStep = bites;
                    // Farelinhos caindo e um "nhac" na vibração.
                    spawnParticle(PARTICLE_CRUMB, cx + 3, mouthY + 1, now);
                    spawnParticle(PARTICLE_CRUMB, cx + 1, mouthY + 2, now);
                    triggerBounce(-0.35f, 0.12f);
                    hapticTick(12);
                }
            } else if (mealFxStep < 4) {
                mealFxStep = 4;
                targetLookX = 0f;
                targetLookY = 0f;
                spawnParticle(PARTICLE_HEART, cx + 9, mouthY - 14, now);
                triggerBounce(-0.8f, -0.2f);
                hapticPattern(new long[]{0, 25, 60, 25});
            }
        } else if (mood == MOOD_DRINKING) {
            int baseX = cx + 5;
            int cupX = baseX;
            int cupY = mouthY - 2;
            int level;
            boolean straw = true;
            if (t < DRINK_ARRIVE_MS) {
                cupX += Math.round(14 * (1f - easeOut(t / (float) DRINK_ARRIVE_MS)));
                level = 4;
            } else if (t < DRINK_SIP_END_MS) {
                level = 4 - (int) ((t - DRINK_ARRIVE_MS) * 4L / (DRINK_SIP_END_MS - DRINK_ARRIVE_MS));
                int gulp = (int) ((t - DRINK_ARRIVE_MS) / 425L);
                if (gulp >= mealFxStep && gulp < 4) {
                    mealFxStep = gulp + 1;
                    triggerBounce(-0.25f, -0.08f);
                    hapticTick(8);
                }
            } else {
                // Copo vazio indo embora.
                cupX += Math.round(16 * easeOut((t - DRINK_SIP_END_MS) / 450f));
                level = 0;
                straw = false;
                if (mealFxStep < 5) {
                    mealFxStep = 5;
                    targetLookX = 0f;
                    targetLookY = 0f;
                    spawnParticle(PARTICLE_SPARKLE, cx - 12, mouthY - 16, now);
                    triggerBounce(-0.8f, -0.2f);
                    hapticPattern(new long[]{0, 25, 60, 25});
                }
            }
            level = Math.max(0, level);
            accessories.drawWaterCup(canvas, cupX, cupY, level);
            // Bolhinhas subindo na água.
            if (level > 0 && t >= DRINK_ARRIVE_MS) {
                int rise = (int) ((t / 140L) % level);
                grid.px(canvas, cupX + 2 + (int) ((t / 280L) % 2L), cupY + 4 - rise, grid.whitePaint);
            }
            if (straw) {
                // Canudinho listrado saindo da boca, passando pela borda e entrando na água.
                int off = cupX - baseX;
                int[][] path = {
                        {cx + 3, mouthY + 1}, {cx + 4, mouthY}, {cx + 4, mouthY - 1}, {cx + 5, mouthY - 2},
                        {cx + 6, mouthY - 1}, {cx + 6, mouthY}, {cx + 6, mouthY + 1}, {cx + 6, mouthY + 2},
                };
                for (int i = 0; i < path.length; i++) {
                    grid.px(canvas, path[i][0] + off, path[i][1],
                            (i & 1) == 0 ? grid.pinkPaint : grid.whitePaint);
                }
                // Golinho subindo pelo canudo até a boca.
                if (level > 0 && t >= DRINK_ARRIVE_MS) {
                    int idx = path.length - 1 - (int) ((t / 60L) % path.length);
                    grid.px(canvas, path[idx][0], path[idx][1], grid.cyanPaint);
                }
            }
        }
    }

    /** Envelope, coração pedindo carinho e estrelinhas de tontura (desenhados junto do rosto). */
    private void drawMoodExtras(Canvas canvas, int mood, int cx, int eyeY, int mouthY, long now) {
        switch (mood) {
            case MOOD_MAIL: {
                int bob = (now / 300L) % 2L == 0L ? 0 : 1;
                accessories.drawEnvelope(canvas, cx - 3, mouthY + 4 + bob);
                break;
            }
            case MOOD_NEEDY:
                // Balãozinho com coração pulsando.
                grid.px(canvas, cx + 8, eyeY - 5, grid.dimPaint);
                grid.px(canvas, cx + 9, eyeY - 7, grid.dimPaint);
                if ((now / IDLE_FRAME_MS) % 2L == 0L) {
                    grid.drawSprite5x5(canvas, cx + 9, eyeY - 13, SPRITE_HEART, grid.pinkPaint);
                }
                break;
            case MOOD_DIZZY: {
                // Duas estrelinhas girando em volta da cabeça.
                double a = (now % 900L) / 900.0 * Math.PI * 2;
                for (int k = 0; k < 2; k++) {
                    double ang = a + k * Math.PI;
                    int sx = (int) Math.round(cx + 0.5 + 9 * Math.cos(ang));
                    int sy = (int) Math.round(eyeY - 8 + 2 * Math.sin(ang));
                    grid.px(canvas, sx, sy, grid.goldPaint);
                    grid.px(canvas, sx, sy - 1, grid.goldPaint);
                    grid.px(canvas, sx - 1, sy, grid.goldPaint);
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * Modo tela desligada: relógio e rostinho dormindo em cinza (poucos pixels acesos),
     * respirando a cada minuto e mudando 1 pixel de lugar para não marcar a tela.
     */
    private void drawAmbient(Canvas canvas, int width, int height) {
        long minute = PetClock.wallMillis() / 60_000L;
        int shiftX = (int) (minute % 3L) - 1;
        int shiftY = (int) ((minute / 3L) % 3L) - 1;
        canvas.save();
        canvas.translate(shiftX * width / (float) GRID_SIZE, shiftY * height / (float) GRID_SIZE);
        accessories.drawClockHHMM(canvas, HUD_CLOCK_Y, 0L, grid.ambientPaint);
        int eyeY = FACE_TOP + 7;
        Paint saved = facePaint;
        facePaint = grid.ambientPaint;
        drawSleepingEye(canvas, 20, eyeY);
        drawSleepingEye(canvas, 31, eyeY);
        // Respira devagar: a boquinha abre e fecha a cada minuto.
        int half = minute % 2L == 0L ? 1 : 2;
        grid.hline(canvas, 26 - half, 25 + half, eyeY + MOUTH_OFFSET + 1, grid.ambientPaint);
        if (minute % 2L == 0L) {
            grid.drawSprite5x5(canvas, 36, eyeY - 10, SPRITE_Z, grid.ambientDimPaint);
        }
        facePaint = saved;
        canvas.restore();
    }

    /** Boca bem aberta 4x4 com linguinha. */
    private void drawBigOpenMouth(Canvas canvas, int cx, int y) {
        grid.hline(canvas, cx, cx + 1, y, facePaint);
        grid.px(canvas, cx - 1, y + 1, facePaint);
        grid.px(canvas, cx + 2, y + 1, facePaint);
        grid.px(canvas, cx - 1, y + 2, facePaint);
        grid.px(canvas, cx + 2, y + 2, facePaint);
        grid.hline(canvas, cx, cx + 1, y + 2, grid.pinkPaint);
        grid.hline(canvas, cx, cx + 1, y + 3, facePaint);
    }

    private void drawOMouth(Canvas canvas, int cx, int y) {
        grid.hline(canvas, cx, cx + 1, y, facePaint);
        grid.px(canvas, cx - 1, y + 1, facePaint);
        grid.px(canvas, cx + 2, y + 1, facePaint);
        grid.hline(canvas, cx, cx + 1, y + 2, facePaint);
    }

    // ================================================================= Desenho: Loja e HUD

    private void drawShopOverlay(Canvas canvas) {
        // Topo: moedas à esquerda e X de sair à direita.
        grid.rectPx(canvas, 12, 5, 14, 9, grid.goldPaint);
        grid.px(canvas, 13, 6, grid.whitePaint);
        grid.drawNumber3x5(canvas, 17, 5, state.coins, grid.goldPaint);
        grid.drawSprite5x5(canvas, 34, 5, SPRITE_TAB_CLOSE, grid.redPaint);

        // Botão de baixo: preço (comprar) ou check (verde = já está usando, ciano = é seu, toque p/ usar).
        int item = shopPreview[shopCategory];
        if (state.isUnlocked(shopCategory, item)) {
            Paint check = state.equipped[shopCategory] == item ? grid.greenPaint : grid.cyanPaint;
            grid.px(canvas, 22, 48, check);
            grid.px(canvas, 23, 49, check);
            grid.px(canvas, 24, 50, check);
            grid.px(canvas, 25, 49, check);
            grid.px(canvas, 26, 48, check);
            grid.px(canvas, 27, 47, check);
            grid.px(canvas, 28, 46, check);
        } else {
            int price = ITEM_PRICES[shopCategory][item];
            // Moedinha + preço centralizados entre as abas de baixo.
            int digits = price >= 100 ? 3 : price >= 10 ? 2 : 1;
            int x0 = 26 - (4 + 4 * digits) / 2;
            grid.rectPx(canvas, x0, 46, x0 + 2, 50, grid.goldPaint);
            grid.px(canvas, x0 + 1, 47, grid.whitePaint);
            grid.drawNumber3x5(canvas, x0 + 5, 46, price, state.coins >= price ? grid.goldPaint : grid.redPaint);
        }

        // Setinhas < > nas laterais (um pouco acima das abas).
        grid.px(canvas, 3, 25, grid.whitePaint);
        grid.px(canvas, 4, 24, grid.whitePaint);
        grid.px(canvas, 4, 26, grid.whitePaint);
        grid.px(canvas, 5, 23, grid.whitePaint);
        grid.px(canvas, 5, 27, grid.whitePaint);
        grid.px(canvas, 48, 25, grid.whitePaint);
        grid.px(canvas, 47, 24, grid.whitePaint);
        grid.px(canvas, 47, 26, grid.whitePaint);
        grid.px(canvas, 46, 23, grid.whitePaint);
        grid.px(canvas, 46, 27, grid.whitePaint);

        // Abas em arco acompanhando a borda: cabeça | nariz | olhos (esquerda), pescoço | cor | fundo (direita).
        drawShopTab(canvas, SPRITE_TAB_HAT, CATEGORY_HEAD, grid.goldPaint);
        drawShopTab(canvas, SPRITE_TAB_NOSE, CATEGORY_NOSE, grid.clownRedPaint);
        drawShopTab(canvas, SPRITE_TAB_EYE, CATEGORY_EYES, grid.cyanPaint);
        drawShopTab(canvas, SPRITE_TAB_NECK, CATEGORY_NECK, grid.redPaint);
        drawShopTab(canvas, SPRITE_TAB_HOLD, CATEGORY_HOLD, grid.goldPaint);
        drawShopTab(canvas, SPRITE_TAB_BG, CATEGORY_BG, grid.lavenderPaint);
    }

    /** Ângulo de cada aba no arco de baixo (0 = embaixo; negativo = esquerda). */
    private static final double[] SHOP_TAB_DEG = {-70, -50, -30, 30, 50, 70};
    private static final double SHOP_TAB_RADIUS = 22.0;
    /** Faixa do meio de baixo que é o botão de comprar/usar. */
    private static final double SHOP_BUY_HALF_DEG = 18.0;

    private void drawShopTab(Canvas canvas, int[] sprite, int category, Paint activePaint) {
        boolean active = shopCategory == category;
        double angle = Math.toRadians(SHOP_TAB_DEG[category]);
        int x = (int) Math.round(GRID_SIZE / 2.0 + SHOP_TAB_RADIUS * Math.sin(angle) - 2.5);
        int y = (int) Math.round(GRID_SIZE / 2.0 + SHOP_TAB_RADIUS * Math.cos(angle) - 2.5);
        grid.drawSprite5x5(canvas, x, y, sprite, active ? activePaint : grid.dimPaint);
        if (active) {
            // Tracinho do lado de dentro do arco (virado para o bichinho).
            grid.hline(canvas, x + 1, x + 3, y - 2, grid.whitePaint);
        }
    }

    private void drawMinimalHud(Canvas canvas, long now) {
        // Bateria: anel fininho na borda.
        accessories.drawBatteryRing(canvas, watchBatteryPct, isWatchCharging, now);

        // Relógio (tocar troca para a data).
        if (showDateInsteadOfClock) {
            accessories.drawDateDDMM(canvas, HUD_CLOCK_Y, grid.whitePaint);
        } else {
            accessories.drawClockHHMM(canvas, HUD_CLOCK_Y, now, grid.whitePaint);
        }

        int barsY = HUD_CLOCK_Y + 7;
        accessories.drawMiniStatBar(canvas, 13, barsY, state.hunger, grid.orangePaint);
        accessories.drawMiniStatBar(canvas, 20, barsY, state.water, grid.cyanPaint);
        accessories.drawMiniStatBar(canvas, 27, barsY, state.happiness, grid.pinkPaint);
        accessories.drawMiniStatBar(canvas, 34, barsY, state.energy, grid.goldPaint);

        // Ações em arco acompanhando a borda: comer | água | carinho | dormir/acordar | loja.
        for (int i = 0; i < ACTION_ICON_COUNT; i++) {
            double angle = Math.toRadians((i - 2) * ACTION_ICON_STEP_DEG);
            int x = (int) Math.round(GRID_SIZE / 2.0 + ACTION_ICON_RADIUS * Math.sin(angle) - 2.5);
            int y = (int) Math.round(GRID_SIZE / 2.0 + ACTION_ICON_RADIUS * Math.cos(angle) - 2.5);
            switch (i) {
                case 0:
                    accessories.drawApple(canvas, x, y);
                    break;
                case 1:
                    accessories.drawWaterCup(canvas, x, y - 1, 3);
                    break;
                case 2:
                    grid.drawSprite5x5(canvas, x, y, SPRITE_HEART, grid.pinkPaint);
                    break;
                case 3:
                    grid.drawSprite5x5(canvas, x, y, state.isSleeping ? SPRITE_BOLT : SPRITE_MOON,
                            state.isSleeping ? grid.goldPaint : grid.lavenderPaint);
                    break;
                default:
                    grid.drawSprite5x5(canvas, x, y, SPRITE_SHOP_CROWN, grid.goldPaint);
                    break;
            }
        }
    }

    // Ícones de ação num arco perto da borda (dentro do anel da bateria).
    private static final int ACTION_ICON_COUNT = 5;
    private static final double ACTION_ICON_STEP_DEG = 28.0;
    private static final double ACTION_ICON_RADIUS = 19.5;

    /**
     * Qual ícone de ação foi tocado (0..4, da esquerda para a direita) ou -1.
     * Vale a faixa perto da borda de baixo; escolhe o ícone de ângulo mais próximo.
     */
    private static int actionIconAt(float fx, float fy) {
        double dx = (fx - 0.5f) * GRID_SIZE;
        double dy = (fy - 0.5f) * GRID_SIZE;
        double r = Math.hypot(dx, dy);
        if (dy <= 0 || r < ACTION_ICON_RADIUS - 5.5) {
            return -1;
        }
        double deg = Math.toDegrees(Math.atan2(dx, dy)); // 0 = embaixo, + = direita
        double maxDeg = 2 * ACTION_ICON_STEP_DEG + ACTION_ICON_STEP_DEG * 0.6;
        if (Math.abs(deg) > maxDeg) {
            return -1;
        }
        int index = (int) Math.round(deg / ACTION_ICON_STEP_DEG) + 2;
        return Math.max(0, Math.min(ACTION_ICON_COUNT - 1, index));
    }
}
