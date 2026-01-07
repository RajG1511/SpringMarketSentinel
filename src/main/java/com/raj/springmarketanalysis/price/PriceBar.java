package com.raj.springmarketanalysis.price;

import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "price_bar",
        uniqueConstraints = @UniqueConstraint(name = "uq_price_bar_asset_ts", columnNames = {"asset_id", "ts"})
)
public class PriceBar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "ts", nullable = false)
    private LocalDate ts;

    @Column(precision = 18, scale = 6, nullable = false)
    private BigDecimal open;
    @Column(precision = 18, scale = 6, nullable = false)
    private BigDecimal high;
    @Column(precision = 18, scale = 6, nullable = false)
    private BigDecimal low;

    @Column(nullable = false)
    private BigDecimal close;

    @Column(nullable = false)
    private Long volume;

    protected PriceBar() {}

    public PriceBar(Asset asset, LocalDate ts, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, Long volume) {
        this.asset = asset;
        this.ts = ts;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
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

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public Long getVolume() {
        return volume;
    }
}
