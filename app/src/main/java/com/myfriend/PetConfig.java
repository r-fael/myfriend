package com.myfriend;

/**
 * Constantes globais de balanceamento, itens da loja, humores e partículas do bichinho.
 */
public final class PetConfig {

    private PetConfig() {
    }

    // ---------------------------------------------------------------- Persistência

    public static final String PREFS_NAME = "myfriend_prefs";

    /** Quanto cada status cai por hora com o app fechado. */
    public static final float HUNGER_DECAY_PER_HOUR = 9f;
    public static final float WATER_DECAY_PER_HOUR = 22f;
    public static final float HAPPINESS_DECAY_PER_HOUR = 6f;
    public static final float ENERGY_DECAY_PER_HOUR = 7f;
    public static final float ENERGY_SLEEP_GAIN_PER_HOUR = 25f;

    public static final float THIRSTY_THRESHOLD = 35f;
    public static final float HUNGRY_THRESHOLD = 32f;
    public static final float SLEEPY_THRESHOLD = 25f;
    public static final float SAD_THRESHOLD = 30f;

    public static final int INITIAL_COINS = 500;

    // ---------------------------------------------------------------- Grade e Tempos

    public static final int GRID_SIZE = 52;
    public static final long HUD_VISIBLE_MS = 4500L;
    public static final long WALKING_WINDOW_MS = 4000L;

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

    public static final int[] ITEM_PRICES = {
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

    public static final int MOOD_NONE = -1;
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

    // ---------------------------------------------------------------- Partículas

    public static final int MAX_PARTICLES = 12;
    public static final long PARTICLE_LIFETIME_MS = 1350L;
    public static final int PARTICLE_HEART = 1;
    public static final int PARTICLE_Z = 2;
    public static final int PARTICLE_SPARKLE = 3;
    public static final int PARTICLE_APPLE = 4;
    public static final int PARTICLE_DROP = 5;
    public static final int PARTICLE_SHOWER = 6;
}
