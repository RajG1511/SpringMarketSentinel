package com.raj.springmarketanalysis.notification;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Audit record of one attempt to deliver one alert_event through one channel.
 * Referenced by id (not a JPA association) because it is written from an
 * after-commit listener outside the alert's persistence context.
 */
@Entity
@Table(name = "alert_delivery")
public class AlertDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_event_id", nullable = false)
    private Long alertEventId;

    @Column(nullable = false, length = 20)
    private String channel; // webhook / email

    @Column(nullable = false, length = 20)
    private String status; // SENT / FAILED

    @Column(columnDefinition = "text")
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AlertDelivery() {}

    private AlertDelivery(Long alertEventId, String channel, String status, String detail) {
        this.alertEventId = alertEventId;
        this.channel = channel;
        this.status = status;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public static AlertDelivery sent(Long alertEventId, String channel) {
        return new AlertDelivery(alertEventId, channel, "SENT", null);
    }

    public static AlertDelivery failed(Long alertEventId, String channel, String detail) {
        return new AlertDelivery(alertEventId, channel, "FAILED", detail);
    }

    public Long getId() { return id; }
    public Long getAlertEventId() { return alertEventId; }
    public String getChannel() { return channel; }
    public String getStatus() { return status; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
