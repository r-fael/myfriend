package com.myfriend;

/**
 * Sinais que chegam de fora do bichinho (sem custo de bateria: só leitura de campos).
 *
 * <p>O {@code PetNotificationListener} grava aqui quando chega uma notificação; o
 * {@link PetEngine} lê no próximo quadro em que a tela estiver ligada.
 */
public final class PetSignals {

    /** Hora real (System.currentTimeMillis) da última notificação de mensagem. */
    public static volatile long lastNotificationWall = 0L;

    private PetSignals() {
    }
}
