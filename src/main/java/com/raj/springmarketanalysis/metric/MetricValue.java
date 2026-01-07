package com.raj.springmarketanalysis.metric;

import com.raj.springmarketanalysis.asset.Asset;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "metric_value",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_metric_value",
                columnNames = {"asset_id", "ts", "metric_type"}
        )
)
public class MetricValue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "ts", nullable = false)
    private LocalDate ts;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 50)
    private MetricType metricType;

    @Column(nullable = false, precision = 20, scale = 10)
    private BigDecimal value;

    protected MetricValue () {}

    public MetricValue(Asset asset, LocalDate ts, MetricType metricType, BigDecimal value) {
        this.asset = asset;
        this.ts = ts;
        this.metricType = metricType;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public LocalDate getTs() {
        return ts;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public BigDecimal getValue() {
        return value;
    }
}
