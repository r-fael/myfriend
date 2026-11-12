package com.myfriend;

/**
 * Constantes globais de balanceamento, itens da loja, humores e partículas do bichinho.
 */
public final class PetConfig {

    private PetConfig() {
    }

    // ---------------------------------------------------------------- Persistência

    public static final String PREFS_NAME = "myfriend_prefs";

    /**
     * Quanto cada status cai por hora (tela acesa ou apagada, é a mesma conta).
     * Tudo zera em no máximo 3 horas, para precisar cuidar dele sempre:
     * água ~2h15, fome ~2h45, felicidade e energia ~2h55.
     */
    public static final float HUNGER_DECAY_PER_HOUR = 36f;
    public static final float WATER_DECAY_PER_HOUR = 45f;
    public static final float HAPPINESS_DECAY_PER_HOUR = 34f;
    public static final float ENERGY_DECAY_PER_HOUR = 34f;
    /**
     * Dormindo, tudo cai bem mais devagar: numa noite de 8 h a fome fica em ~50%,
     * a água em ~45% e a felicidade não cai.
     */
    public static final float HUNGER_SLEEP_DECAY_PER_HOUR = 6f;
    public static final float WATER_SLEEP_DECAY_PER_HOUR = 7f;
    public static final float HAPPINESS_SLEEP_DECAY_PER_HOUR = 0f;
    /** Dormindo, a energia enche em 30 min (cada pixel da barrinha de 5 = ~6 min de soneca). */
    public static final float ENERGY_SLEEP_GAIN_PER_HOUR = 200f;

    /** Apertar muito: fica bravo por um tempo e cada cutucada a mais aumenta a birra. */
    public static final int GRUMPY_POKES = 5;
    public static final long GRUMPY_MS = 6000L;
    public static final long GRUMPY_EXTRA_PER_POKE_MS = 1500L;
    public static final long GRUMPY_MAX_MS = 12_000L;

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
    public static final int CATEGORY_NECK = 3;
    public static final int CATEGORY_COLOR = 4;
    public static final int CATEGORY_BG = 5;
    public static final int CATEGORY_COUNT = 6;

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

    // Boca/pescoço
    public static final int NECK_BOWTIE = 1;
    public static final int NECK_SCARF = 2;
    public static final int NECK_PACIFIER = 3;
    public static final int NECK_NECKLACE = 4;

    // Cor do rostinho (0 = branco)
    public static final int COLOR_PINK = 1;
    public static final int COLOR_CYAN = 2;
    public static final int COLOR_GOLD = 3;

    // Fundo (0 = preto)
    public static final int BG_STARS = 1;
    public static final int BG_RAIN = 2;
    public static final int BG_SNOW = 3;

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
            { // boca/pescoço
                    0,  // nada
                    30, // gravatinha borboleta
                    35, // cachecol
                    25, // chupeta
                    45, // colar com pingente
            },
            { // cor do rostinho
                    0,  // branco
                    40, // rosa
                    40, // ciano
                    60, // dourado
            },
            { // fundo
                    0,  // preto
                    30, // estrelinhas
                    30, // chuva
                    30, // neve
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
    public static final int MOOD_YAWN = 14;
    public static final int MOOD_STRETCH = 15;
    public static final int MOOD_EXCITED = 16;
    public static final int MOOD_DIZZY = 17;
    public static final int MOOD_MAIL = 18;
    public static final int MOOD_SULK = 19;
    public static final int MOOD_CELEBRATE = 20;
    public static final int MOOD_NEEDY = 21;

    // ---------------------------------------------------------------- Rotina e humor do dia

    /** Das 23h às 7h é noite: ao apagar a tela ele dorme sozinho. */
    public static final int NIGHT_START_HOUR = 23;
    public static final int MORNING_HOUR = 7;
    /** Das 21h até a hora de dormir ele boceja de vez em quando. */
    public static final int EVENING_YAWN_HOUR = 21;
    /** Sem abrir o relógio por esse tempo (de dia), ele fica emburrado de costas. */
    public static final long SULK_AFTER_MS = 3 * 60 * 60 * 1000L;
    /** Personalidade sorteada a cada dia. */
    public static final int PERSONALITY_NORMAL = 0;
    public static final int PERSONALITY_LAZY = 1;
    public static final int PERSONALITY_HYPER = 2;
    public static final int PERSONALITY_NEEDY = 3;
    /** Carente: pede carinho se ficar esse tempo sem receber. */
    public static final long NEEDY_AFTER_MS = 15 * 60 * 1000L;

    // ---------------------------------------------------------------- Passos

    public static final int STEP_GOAL = 6000;
    public static final int STEP_GOAL_REWARD = 100;
    /** Passos dentro de {@link #EXCITED_WINDOW_MS} para ele ficar animado com a caminhada. */
    public static final int EXCITED_STEPS = 25;
    public static final long EXCITED_WINDOW_MS = 20_000L;

    // ---------------------------------------------------------------- Partículas

    public static final int MAX_PARTICLES = 12;
    public static final long PARTICLE_LIFETIME_MS = 1350L;
    public static final int PARTICLE_HEART = 1;
    public static final int PARTICLE_Z = 2;
    public static final int PARTICLE_SPARKLE = 3;
    public static final int PARTICLE_APPLE = 4;
    public static final int PARTICLE_DROP = 5;
    public static final int PARTICLE_SHOWER = 6;
    /** Farelinho de maçã caindo (1 pixel). */
    public static final int PARTICLE_CRUMB = 7;
}
