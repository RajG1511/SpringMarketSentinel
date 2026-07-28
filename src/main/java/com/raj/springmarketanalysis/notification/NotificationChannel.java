package com.raj.springmarketanalysis.notification;

/**
 * A delivery target for fired alerts. Implementations are discovered as beans and
 * dispatched to in turn; a channel that is not configured reports {@link #isEnabled()}
 * false and is skipped, so the app runs fine with no channels wired up.
 */
public interface NotificationChannel {

    /** Short identifier persisted on the delivery record, e.g. "webhook" or "email". */
    String type();

    /** True only when this channel has the config it needs to actually send. */
    boolean isEnabled();

    void send(AlertNotification notification);
}
