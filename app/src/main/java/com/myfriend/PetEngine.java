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
    private int shopPreviewItem = ITEM_SANTA_HAT;
    private boolean isWatchFaceMode = false;
    private boolean showBatteryInsteadOfClock = false;

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
    private int chewStep = 0;
    private long lastChewTime = 0L;

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
        showBatteryInsteadOfClock = false;
    }

    public void onPause() {
        state.save();
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
        startAction(MOOD_EATING, now, 2000L);
        chewStep = 0;
        lastChewTime = now;
        triggerBounce(-0.9f, 0.25f);
        spawnParticle(PARTICLE_APPLE, 37, 16, now);
        spawnParticle(PARTICLE_HEART, 11, 15, now);
        hapticTick(25);
        state.save();
    }

    private void giveWater(long now) {
        state.isSleeping = false;
        forcedMood = MOOD_NONE;
        state.water = 100f;
        state.happiness = PetState.clamp(state.happiness + 10f);
        state.coins += 8;
        startAction(MOOD_DRINKING, now, 2400L);
        chewStep = 0;
        lastChewTime = now;
        triggerBounce(-0.85f, -0.2f);
        spawnParticle(PARTICLE_DROP, 11, 15, now);
        spawnParticle(PARTICLE_DROP, 37, 13, now);
        spawnParticle(PARTICLE_SPARKLE, 14, 11, now);
        hapticPattern(new long[]{0, 35, 70, 35});
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
        state.happiness = PetState.clamp(state.happiness + 22f);
        state.coins += 3;
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
            shopPreviewItem = state.equippedItem;
            triggerBounce(-0.6f, 0.15f);
        }
        hapticTick(20);
    }

    private void shopStep(int delta) {
        shopPreviewItem = ((shopPreviewItem + delta) % TOTAL_ITEMS + TOTAL_ITEMS) % TOTAL_ITEMS;
        triggerBounce(-0.4f, 0.12f);
    }

    private void buyOrEquipPreviewItem(long now) {
        int item = shopPreviewItem;
        if (state.isUnlocked(item)) {
            state.equippedItem = item;
            shopOpen = false;
            startAction(MOOD_LOVED, now, 1400L);
            triggerBounce(-0.9f, -0.2f);
            spawnParticle(PARTICLE_SPARKLE, 14, 14, now);
            spawnParticle(PARTICLE_SPARKLE, 35, 14, now);
            hapticTick(25);
            state.save();
            return;
        }
        int price = ITEM_PRICES[item];
        if (state.coins >= price) {
            state.coins -= price;
            state.unlockedMask |= 1 << item;
            state.equippedItem = item;
            shopOpen = false;
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
            showBatteryInsteadOfClock = false;
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
        if (state.poopCount > 0 && fx < 0.3f && fy > 0.48f && fy <= 0.74f) {
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
        rapidPokeCount = now - lastPokeTime < 550L ? rapidPokeCount + 1 : 1;
        lastPokeTime = now;

        if (state.isSleeping) {
            state.isSleeping = false;
            startAction(MOOD_SURPRISED, now, 1100L);
            triggerBounce(-0.9f, -0.25f);
        } else if (rapidPokeCount >= 5) {
            startAction(MOOD_GRUMPY, now, 1800L);
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
            accessories.drawClockHHMM(canvas, 6, 0L, grid.whitePaint);
            drawEyes(canvas, 17, 34, 26, MOOD_SLEEPING, now);
            drawMouth(canvas, 25, 35, MOOD_SLEEPING, now);
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

        updateStatsAndAlerts(realDt, now);
        int mood = getCurrentMood(now);
        updateEyesAndBounce(physDt, now);

        // Zzz quando está dormindo.
        if (mood == MOOD_SLEEPING && now / 900L != (now - 30L) / 900L) {
            spawnParticle(PARTICLE_Z, (int) (now % 4L) + 34, 17, now);
        }

        // ---- Posição do rosto
        int breathe = (mood == MOOD_SLEEPING || (now / 900L) % 2L != 0L) ? 1 : 0;
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
        } else if (walking && state.equippedItem == ITEM_NONE) {
            item = ITEM_HEADBAND; // faixa de academia enquanto caminha
        } else {
            item = state.equippedItem;
        }
        if (item != ITEM_NONE) {
            accessories.drawAccessory(canvas, cx, eyeY, mouthY, item);
        }

        if (state.poopCount > 0 && !shopOpen) {
            accessories.drawPoop(canvas, 5, 34, now);
        }

        // ---- Topo: alerta de bateria ou relógio (modo mostrador)
        boolean hudVisible = now - lastInteractionTime < HUD_VISIBLE_MS;
        if (!shopOpen && !hudVisible) {
            if (watchBatteryPct <= 30 || isWatchCharging) {
                accessories.drawBatteryBadge(canvas, 19, 2, watchBatteryPct, isWatchCharging, now);
                Paint pctPaint = watchBatteryPct <= 15 ? grid.redPaint
                        : watchBatteryPct <= 30 ? grid.orangePaint : grid.greenPaint;
                grid.drawNumber3x5(canvas, 27, 2, watchBatteryPct, pctPaint);
            } else if (isWatchFaceMode) {
                accessories.drawClockHHMM(canvas, 2, now, grid.whitePaint);
            }
        }

        accessories.drawMoodProps(canvas, mood, lookOffsetX, offsetY, shopOpen, actionStartTime, now);
        boolean anyParticle = updateAndDrawParticles(canvas, physDt, now);

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
                || Math.abs(targetLookY - lookY) > 0.15f
                || (actionMood != MOOD_NONE && now < actionMoodUntil);
        if (animating) {
            return 16L;
        }
        return (shopOpen || hudVisible) ? 200L : 500L;
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
        if (now >= nextSaccadeTime && now - lastInteractionTime > 1200L) {
            switch ((int) ((now / 173L) % 7L)) {
                case 0: targetLookX = -2.2f; targetLookY = 0f; break;
                case 1: targetLookX = 2.2f; targetLookY = 0f; break;
                case 2: targetLookX = 0f; targetLookY = -1.5f; break;
                case 3: targetLookX = -1.5f; targetLookY = 1.2f; break;
                case 4: targetLookX = 1.5f; targetLookY = 1.2f; break;
                default: targetLookX = 0f; targetLookY = 0f; break;
            }
            nextSaccadeTime = now + 2200L + (now % 2000L);
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
                default: // gota / chuveiro
                    grid.drawSprite5x5(canvas, x, y, SPRITE_WATER_DROP, grid.cyanPaint);
                    break;
            }
            any = true;
        }
        return any;
    }

    // ================================================================= Desenho: Olhos e Boca

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
                grid.hline(canvas, leftX - 1, leftX + 2, y - 7, grid.whitePaint);
                grid.hline(canvas, leftX - 4, leftX - 2, y - 6, grid.whitePaint);
                grid.hline(canvas, rightX - 2, rightX + 1, y - 7, grid.whitePaint);
                grid.hline(canvas, rightX + 2, rightX + 4, y - 6, grid.whitePaint);
                if (mood != MOOD_HUNGRY) {
                    // Lágrima escorrendo.
                    int tearY = y + 6 + (int) ((now / 250L) % 3L);
                    grid.px(canvas, leftX - 5, tearY, grid.cyanPaint);
                    grid.px(canvas, leftX - 5, tearY + 1, grid.cyanPaint);
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
        grid.hline(canvas, x - 3, x + 3, y + 1, grid.whitePaint);
        grid.px(canvas, x - 4, y, grid.whitePaint);
        grid.px(canvas, x + 4, y, grid.whitePaint);
    }

    /** Olho oval branco com pupila preta otimizada (1 drawRect para a pupila + reflexo). */
    private void drawOvalEye(Canvas canvas, int x, int y, boolean sadHighlight) {
        grid.hline(canvas, x - 2, x + 2, y - 5, grid.whitePaint);
        grid.hline(canvas, x - 3, x + 3, y - 4, grid.whitePaint);
        grid.rectPx(canvas, x - 4, y - 3, x + 4, y + 3, grid.whitePaint);
        grid.hline(canvas, x - 3, x + 3, y + 4, grid.whitePaint);
        grid.hline(canvas, x - 2, x + 2, y + 5, grid.whitePaint);

        int ox = Math.round(lookX * 0.45f);
        int oy = Math.round(lookY * 0.45f);
        // Apaga a área 5x5 da pupila de uma só vez e redesenha os pixels de brilho.
        grid.rectSolid(canvas, x - 2 + ox, y - 2 + oy, x + 2 + ox, y + 2 + oy, grid.blackPaint);
        grid.rectPx(canvas, x - 2 + ox, y - 2 + oy, x - 1 + ox, y - 1 + oy, grid.whitePaint);
        if (sadHighlight) {
            grid.px(canvas, x + 1 + ox, y + 2 + oy, grid.whitePaint);
        }
    }

    private void drawHappyArcEye(Canvas canvas, int x, int y) {
        grid.hline(canvas, x - 2, x + 2, y - 3, grid.whitePaint);
        grid.hline(canvas, x - 3, x + 3, y - 2, grid.whitePaint);
        grid.px(canvas, x - 4, y - 1, grid.whitePaint);
        grid.px(canvas, x - 3, y - 1, grid.whitePaint);
        grid.px(canvas, x + 3, y - 1, grid.whitePaint);
        grid.px(canvas, x + 4, y - 1, grid.whitePaint);
        grid.px(canvas, x - 4, y, grid.whitePaint);
        grid.px(canvas, x + 4, y, grid.whitePaint);
    }

    private void drawHalfClosedEye(Canvas canvas, int x, int y) {
        grid.hline(canvas, x - 4, x + 4, y, grid.whitePaint);
        grid.hline(canvas, x - 3, x + 3, y + 1, grid.whitePaint);
        grid.hline(canvas, x - 3, x + 3, y + 2, grid.whitePaint);
        grid.hline(canvas, x - 2, x + 2, y + 3, grid.whitePaint);
    }

    private void drawGrumpyEye(Canvas canvas, int x, int y, boolean leftSide) {
        grid.rectPx(canvas, x - 3, y - 1, x + 3, y + 3, grid.whitePaint);
        grid.hline(canvas, x - 2, x + 2, y + 4, grid.whitePaint);
        // Sobrancelha franzida apontando para o meio.
        if (leftSide) {
            grid.hline(canvas, x - 4, x - 2, y - 4, grid.whitePaint);
            grid.hline(canvas, x - 1, x + 1, y - 3, grid.whitePaint);
            grid.hline(canvas, x + 2, x + 4, y - 2, grid.whitePaint);
        } else {
            grid.hline(canvas, x + 2, x + 4, y - 4, grid.whitePaint);
            grid.hline(canvas, x - 1, x + 1, y - 3, grid.whitePaint);
            grid.hline(canvas, x - 4, x - 2, y - 2, grid.whitePaint);
        }
    }

    private void drawSurprisedEye(Canvas canvas, int x, int y) {
        grid.hline(canvas, x - 2, x + 2, y - 4, grid.whitePaint);
        grid.hline(canvas, x - 3, x + 3, y - 3, grid.whitePaint);
        grid.rectPx(canvas, x - 4, y - 2, x + 4, y + 2, grid.whitePaint);
        grid.hline(canvas, x - 3, x + 3, y + 3, grid.whitePaint);
        grid.hline(canvas, x - 2, x + 2, y + 4, grid.whitePaint);
        // Pupila grande com um pontinho de brilho no centro (otimizado para 2 draw calls).
        grid.rectSolid(canvas, x - 2, y - 2, x + 2, y + 2, grid.blackPaint);
        grid.px(canvas, x, y, grid.whitePaint);
    }

    private void drawBlush(Canvas canvas, int x, int y) {
        grid.px(canvas, x, y, grid.blushPaint);
        grid.px(canvas, x + 2, y, grid.blushPaint);
        grid.px(canvas, x + 1, y + 1, grid.blushPaint);
    }

    private void drawMouth(Canvas canvas, int x, int y, int mood, long now) {
        switch (mood) {
            case MOOD_LOVED:
            case MOOD_SHOWERING:
                // Sorrisão aberto com linguinha.
                grid.hline(canvas, x - 5, x + 5, y, grid.whitePaint);
                grid.hline(canvas, x - 4, x + 4, y + 1, grid.whitePaint);
                grid.hline(canvas, x - 4, x + 4, y + 2, grid.whitePaint);
                grid.hline(canvas, x - 3, x + 3, y + 3, grid.whitePaint);
                grid.hline(canvas, x - 2, x + 2, y + 4, grid.blushPaint);
                break;

            case MOOD_HUNGRY:
            case MOOD_THIRSTY:
            case MOOD_LOW_BATTERY:
                grid.hline(canvas, x - 2, x + 2, y, grid.whitePaint);
                grid.px(canvas, x - 3, y + 1, grid.whitePaint);
                grid.px(canvas, x + 3, y + 1, grid.whitePaint);
                if (mood == MOOD_HUNGRY) {
                    // Babinha.
                    grid.px(canvas, x + 3, y + 2, grid.cyanPaint);
                    if ((now / 400L) % 2L == 0L) {
                        grid.px(canvas, x + 3, y + 3, grid.cyanPaint);
                    }
                }
                break;

            case MOOD_SLEEPY:
            case MOOD_SURPRISED:
                drawOMouth(canvas, x, y);
                break;

            case MOOD_SLEEPING: {
                int half = (now / 900L) % 2L == 0L ? 1 : 2; // respiração
                grid.hline(canvas, x - half, x + half, y + 1, grid.whitePaint);
                break;
            }

            case MOOD_SAD:
                grid.hline(canvas, x - 3, x + 3, y, grid.whitePaint);
                grid.px(canvas, x - 4, y + 1, grid.whitePaint);
                grid.px(canvas, x + 4, y + 1, grid.whitePaint);
                grid.px(canvas, x - 5, y + 2, grid.whitePaint);
                grid.px(canvas, x + 5, y + 2, grid.whitePaint);
                break;

            case MOOD_GRUMPY:
                // Boca em zigue-zague.
                for (int dx = -3; dx <= 3; dx++) {
                    grid.px(canvas, x + dx, (dx & 1) != 0 ? y + 1 : y, grid.whitePaint);
                }
                break;

            case MOOD_EATING:
                if (now - lastChewTime > 200L) {
                    chewStep = (chewStep + 1) % 2;
                    lastChewTime = now;
                }
                if (chewStep != 0) {
                    grid.hline(canvas, x - 3, x + 3, y + 1, grid.whitePaint);
                    grid.px(canvas, x - 4, y, grid.whitePaint);
                    grid.px(canvas, x + 4, y, grid.whitePaint);
                } else {
                    grid.hline(canvas, x - 3, x + 3, y - 1, grid.whitePaint);
                    grid.rectPx(canvas, x - 4, y, x + 4, y + 3, grid.whitePaint);
                    grid.hline(canvas, x - 3, x + 3, y + 4, grid.whitePaint);
                }
                break;

            case MOOD_WINK:
                // Boquinha de gato ":3".
                grid.px(canvas, x - 4, y, grid.whitePaint);
                grid.hline(canvas, x - 3, x - 1, y + 1, grid.whitePaint);
                grid.px(canvas, x, y, grid.whitePaint);
                grid.hline(canvas, x + 1, x + 3, y + 1, grid.whitePaint);
                grid.px(canvas, x + 4, y, grid.whitePaint);
                break;

            case MOOD_DRINKING:
                drawOMouth(canvas, x, y);
                if ((now / 180L) % 2L == 0L) {
                    grid.px(canvas, x + 3, y + 1, grid.cyanPaint);
                    grid.px(canvas, x + 4, y + 1, grid.cyanPaint);
                }
                break;

            default:
                // Sorriso simples.
                grid.px(canvas, x - 5, y, grid.whitePaint);
                grid.px(canvas, x + 5, y, grid.whitePaint);
                grid.px(canvas, x - 4, y + 1, grid.whitePaint);
                grid.px(canvas, x + 4, y + 1, grid.whitePaint);
                grid.hline(canvas, x - 3, x + 3, y + 2, grid.whitePaint);
                grid.hline(canvas, x - 2, x + 2, y + 3, grid.whitePaint);
                break;
        }
    }

    private void drawOMouth(Canvas canvas, int x, int y) {
        grid.hline(canvas, x - 1, x + 1, y, grid.whitePaint);
        grid.px(canvas, x - 2, y + 1, grid.whitePaint);
        grid.px(canvas, x + 2, y + 1, grid.whitePaint);
        grid.px(canvas, x - 2, y + 2, grid.whitePaint);
        grid.px(canvas, x + 2, y + 2, grid.whitePaint);
        grid.hline(canvas, x - 1, x + 1, y + 3, grid.whitePaint);
    }

    // ================================================================= Desenho: Loja e HUD

    private void drawShopOverlay(Canvas canvas) {
        // Moedas no topo.
        grid.rectPx(canvas, 17, 4, 19, 8, grid.goldPaint);
        grid.px(canvas, 18, 5, grid.whitePaint);
        grid.drawNumber3x5(canvas, 22, 4, state.coins, grid.goldPaint);

        // Setinhas < >.
        grid.px(canvas, 6, 25, grid.whitePaint);
        grid.px(canvas, 7, 24, grid.whitePaint);
        grid.px(canvas, 7, 26, grid.whitePaint);
        grid.px(canvas, 8, 23, grid.whitePaint);
        grid.px(canvas, 8, 27, grid.whitePaint);
        grid.px(canvas, 45, 25, grid.whitePaint);
        grid.px(canvas, 44, 24, grid.whitePaint);
        grid.px(canvas, 44, 26, grid.whitePaint);
        grid.px(canvas, 43, 23, grid.whitePaint);
        grid.px(canvas, 43, 27, grid.whitePaint);

        int item = shopPreviewItem;
        if (state.isUnlocked(item)) {
            // Check: verde se já está equipado, ciano se só comprado.
            Paint check = state.equippedItem == item ? grid.greenPaint : grid.cyanPaint;
            grid.px(canvas, 23, 43, check);
            grid.px(canvas, 24, 44, check);
            grid.px(canvas, 25, 43, check);
            grid.px(canvas, 26, 42, check);
            grid.px(canvas, 27, 41, check);
            return;
        }
        int price = ITEM_PRICES[item];
        grid.rectPx(canvas, 19, 41, 20, 44, grid.goldPaint);
        grid.drawNumber3x5(canvas, 23, 41, price, state.coins >= price ? grid.goldPaint : grid.redPaint);
    }

    private void drawMinimalHud(Canvas canvas, long now) {
        if (showBatteryInsteadOfClock) {
            accessories.drawBatteryBadge(canvas, 19, 2, watchBatteryPct, isWatchCharging, now);
            grid.drawNumber3x5(canvas, 27, 2, watchBatteryPct, grid.whitePaint);
        } else {
            accessories.drawClockHHMM(canvas, 2, now, grid.whitePaint);
            if (watchBatteryPct <= 30 || isWatchCharging) {
                accessories.drawBatteryBadge(canvas, 9, 2, watchBatteryPct, isWatchCharging, now);
            }
        }

        accessories.drawMiniStatBar(canvas, 13, 8, state.hunger, grid.orangePaint);
        accessories.drawMiniStatBar(canvas, 20, 8, state.water, grid.cyanPaint);
        accessories.drawMiniStatBar(canvas, 27, 8, state.happiness, grid.pinkPaint);
        accessories.drawMiniStatBar(canvas, 34, 8, state.energy, grid.goldPaint);

        // Barra de ações: comer | água | carinho | dormir/acordar | loja.
        accessories.drawApple(canvas, 10, 42);
        accessories.drawWaterCup(canvas, 17, 41, 3);
        grid.drawSprite5x5(canvas, 24, 42, SPRITE_HEART, grid.pinkPaint);
        grid.drawSprite5x5(canvas, 31, 42, state.isSleeping ? SPRITE_BOLT : SPRITE_MOON,
                state.isSleeping ? grid.goldPaint : grid.lavenderPaint);
        grid.drawSprite5x5(canvas, 37, 42, SPRITE_SHOP_CROWN, grid.goldPaint);
    }
}
