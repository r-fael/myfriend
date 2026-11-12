package com.myfriend;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Avisa o bichinho quando chega uma mensagem nova: ele aparece segurando um envelopinho.
 *
 * <p>Não desenha nada nem acorda a tela: só anota a hora da notificação, e o motor mostra o
 * envelope na próxima vez que a tela acender (até 90 s depois). Custo de bateria ~zero.
 */
public class PetNotificationListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.isOngoing() || getPackageName().equals(sbn.getPackageName())) {
            return; // música, timers, downloads e o próprio app não contam
        }
        Notification notification = sbn.getNotification();
        if (notification == null || (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return; // resumo de grupo repete uma mensagem que já chegou
        }
        PetSignals.lastNotificationWall = System.currentTimeMillis();
    }
}
