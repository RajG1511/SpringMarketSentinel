package com.raj.springmarketanalysis.alert;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "alert_rule")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_key", nullable = false, unique = true, length = 50)
    private String ruleKey;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    // generic params
    @Column(precision = 20, scale = 10)
    private BigDecimal threshold;

    @Column(name = "window_days")
    private Integer windowDays;

    @Column(precision = 20, scale = 10)
    private BigDecimal multiplier;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AlertRule() {}

    public Long getId() { return id; }
    public String getRuleKey() { return ruleKey; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public BigDecimal getThreshold() { return threshold; }
    public Integer getWindowDays() { return windowDays; }
    public BigDecimal getMultiplier() { return multiplier; }
    public Instant getCreatedAt() { return createdAt; }
}
