package com.raj.springmarketanalysis.alert;

import com.raj.springmarketanalysis.asset.Asset;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "alert_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_alert_event",
                columnNames = {"asset_id", "rule_id", "ts"}
        )
)
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private AlertRule rule;

    @Column(nullable = false)
    private LocalDate ts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AlertEvent() {}

    public AlertEvent(Asset asset, AlertRule rule, LocalDate ts, AlertSeverity severity, String message) {
        this.asset = asset;
        this.rule = rule;
        this.ts = ts;
        this.severity = severity;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Asset getAsset() { return asset; }
    public AlertRule getRule() { return rule; }
    public LocalDate getTs() { return ts; }
    public AlertSeverity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
