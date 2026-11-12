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
    /** Linha do relógio no HUD (abaixo do anel de bateria). */
    public static final int HUD_CLOCK_Y = 5;
    public static final long WALKING_WINDOW_MS = 4000L;

    // ---------------------------------------------------------------- Loja: categorias

    public static final int CATEGORY_HEAD = 0;
    public static final int CATEGORY_NOSE = 1;
    public static final int CATEGORY_EYES = 2;
    public static final int CATEGORY_COUNT = 3;

    /** Item 0 de toda categoria é "nada equipado". */
    public static final int ITEM_NONE = 0;

    // Cabeça
    public static final int HEAD_SANTA_HAT = 1;
    public static final int HEAD_WITCH_HAT = 2;
    public static final int HEAD_PUMPKIN = 3;
    public static final int HEAD_DEVIL_HORNS = 4;
    public static final int HEAD_CROWN = 5;
    public static final int HEAD_WIZARD_HAT = 6;
    public static final int HEAD_HEADPHONES = 7;
    public static final int HEAD_HEADBAND = 8;
    public static final int HEAD_BUNNY_EARS = 9;
    public static final int HEAD_BIRTHDAY_HAT = 10;
    public static final int HEAD_HALO = 11;
    public static final int HEAD_STRAW_HAT = 12;
    public static final int HEAD_CLOWN_HAIR = 13;
    public static final int HEAD_PLUMBER_RED = 14;
    public static final int HEAD_PLUMBER_GREEN = 15;
    /** Id do cocar de carnaval (removido na v1.9), usado só para migrar o que estava salvo. */
    public static final int REMOVED_HEAD_CARNIVAL = 11;
    public static final int REMOVED_CARNIVAL_PRICE = 50;
    /** Id da roda divina (removida na v1.10), usado só para migrar o que estava salvo. */
    public static final int REMOVED_HEAD_DIVINE_WHEEL = 14;
    public static final int REMOVED_DIVINE_WHEEL_PRICE = 90;

    // Nariz
    public static final int NOSE_CLOWN = 1;
    public static final int NOSE_PIG = 2;
    public static final int NOSE_CAT = 3;
    public static final int NOSE_MUSTACHE = 4;
    public static final int NOSE_DISGUISE = 5;

    // Olhos
    public static final int EYES_SUNGLASSES = 1;
    public static final int EYES_PARTY_GLASSES = 2;
    public static final int EYES_PATCH = 3;
    public static final int EYES_MONOCLE = 4;
    public static final int EYES_DISGUISE = 5;

    /** Preço de cada item, por categoria (índice = id do item). */
    public static final int[][] ITEM_PRICES = {
            { // cabeça
                    0,  // nada
                    40, // gorro de Natal
                    45, // chapéu de bruxa
                    45, // abóbora
                    35, // chifrinhos
                    80, // coroa
                    55, // chapéu de mago
                    60, // fone
                    25, // faixa
                    40, // orelhas de coelho
                    35, // chapéu de aniversário
                    45, // auréola
                    40, // chapéu de palha (festa junina)
                    40, // cabelo de palhaço
                    60, // boné vermelho de encanador
                    60, // boné verde de encanador
            },
            { // nariz
                    0,  // nada
                    20, // nariz de palhaço
                    30, // focinho de porquinho
                    30, // nariz e bigodes de gato
                    35, // bigodão
                    40, // narigão com bigode (disfarce)
            },
            { // olhos
                    0,  // nada
                    50, // óculos escuros
                    50, // óculos de festa (ano novo)
                    45, // tapa-olho de pirata
                    55, // monóculo
                    40, // óculos de disfarce com sobrancelhas
            },
    };

    public static int itemCount(int category) {
        return ITEM_PRICES[category].length;
    }

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
