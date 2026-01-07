package com.raj.springmarketanalysis.ingestion;

import com.raj.springmarketanalysis.asset.Asset;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "ingestion_run")
public class IngestionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false, length = 20)
    private String status; // SUCCESS / FAILED

    @Column(nullable = false)
    private int inserted;

    @Column(nullable = false)
    private int skipped;

    @Column(columnDefinition = "text")
    private String message;

    protected IngestionRun() {}

    private IngestionRun(Asset asset) {
        this.asset = asset;
        this.startedAt = Instant.now();
        this.status = "FAILED"; // default until we mark success
        this.inserted = 0;
        this.skipped = 0;
    }

    public static IngestionRun start(Asset asset) {
        return new IngestionRun(asset);
    }

    public void markSuccess(int inserted, int skipped) {
        this.inserted = inserted;
        this.skipped = skipped;
        this.status = "SUCCESS";
        this.finishedAt = Instant.now();
    }

    public void markFailure(String message) {
        this.message = message;
        this.status = "FAILED";
        this.finishedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Asset getAsset() { return asset; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getStatus() { return status; }
    public int getInserted() { return inserted; }
    public int getSkipped() { return skipped; }
    public String getMessage() { return message; }
}
