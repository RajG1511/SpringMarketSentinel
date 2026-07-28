package com.raj.springmarketanalysis.notification;

import com.raj.springmarketanalysis.alert.AlertSeverity;

import java.time.LocalDate;

/**
 * Immutable snapshot of a fired alert, published as an application event after the
 * alert transaction commits and consumed by {@link NotificationDispatcher}. Carries
 * plain values (no JPA entities) so it stays valid outside the original transaction.
 */
public record AlertNotification(
        Long alertEventId,
        Long assetId,
        String symbol,
        String ruleKey,
        AlertSeverity severity,
        LocalDate ts,
        String message
) {}
