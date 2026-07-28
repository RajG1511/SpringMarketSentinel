package com.raj.springmarketanalysis.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Fans a fired alert out to every enabled {@link NotificationChannel} and records the
 * outcome per channel in {@code alert_delivery}. Runs only after the alert transaction
 * commits (so we never notify about an alert that later rolls back) and in its own new
 * transaction (the original one is already gone by AFTER_COMMIT). A single channel
 * failing never blocks the others.
 */
@Component
public class NotificationDispatcher {

    private final List<NotificationChannel> channels;
    private final AlertDeliveryRepository deliveryRepo;

    public NotificationDispatcher(List<NotificationChannel> channels, AlertDeliveryRepository deliveryRepo) {
        this.channels = channels;
        this.deliveryRepo = deliveryRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlertFired(AlertNotification n) {
        for (NotificationChannel channel : channels) {
            if (!channel.isEnabled()) {
                continue;
            }
            try {
                channel.send(n);
                deliveryRepo.save(AlertDelivery.sent(n.alertEventId(), channel.type()));
                System.out.println("[NotificationDispatcher] SENT " + channel.type()
                        + " for alert_event=" + n.alertEventId());
            } catch (Exception ex) {
                deliveryRepo.save(AlertDelivery.failed(n.alertEventId(), channel.type(), ex.getMessage()));
                System.out.println("[NotificationDispatcher] FAILED " + channel.type()
                        + " for alert_event=" + n.alertEventId() + " : " + ex.getMessage());
            }
        }
    }
}
