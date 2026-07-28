package com.raj.springmarketanalysis.scheduler;

import com.raj.springmarketanalysis.alert.AlertsService;
import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import com.raj.springmarketanalysis.metric.MetricsService;
import com.raj.springmarketanalysis.service.PriceIngestionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;


import java.util.List;

@Component
@ConditionalOnProperty(prefix = "market.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MarketIngestionJob {

    private final AssetRepository assetRepo;
    private final PriceIngestionService ingestionService;
    private final MetricsService metricsService;
    private final AlertsService alertsService;
    private final int metricsLookbackDays;


    public MarketIngestionJob(
            AssetRepository assetRepo,
            PriceIngestionService ingestionService,
            MetricsService metricsService,
            AlertsService alertsService,
            @Value("${market.metrics.lookbackDays:400}") int metricsLookbackDays
    ) {
        this.assetRepo = assetRepo;
        this.ingestionService = ingestionService;
        this.metricsService = metricsService;
        this.alertsService = alertsService;
        this.metricsLookbackDays = metricsLookbackDays;
    }

    // Pull cron from application.yml so you can change schedule without code changes
    @Scheduled(cron = "${market.scheduler.cron}")
    public void ingestAllAssetsDaily() {
        List<Asset> assets = assetRepo.findAll();

        if (assets.isEmpty()) {
            System.out.println("[MarketIngestionJob] No assets found; skipping.");
            return;
        }

        System.out.println("[MarketIngestionJob] Starting scheduled pipeline for " + assets.size() + " assets.");

        for (Asset a : assets) {
            String symbol = a.getSymbol();
            Long assetId = a.getId();

            try {
                // 1) Ingest prices
                PriceIngestionService.IngestionResult ingest = ingestionService.ingestDailyPrices(assetId);
                System.out.println("[MarketIngestionJob] INGEST " + symbol +
                        " inserted=" + ingest.inserted() + " skipped=" + ingest.skipped());

                // Optional: if ingestion inserted nothing, you can skip metrics/alerts.
                // But I usually still compute/evaluate because sometimes metrics/alerts lag behind.
                // If you want the skip optimization, uncomment:
                // if (ingest.inserted() == 0) continue;

                // 2) Compute metrics
                MetricsService.MetricsRunResult metrics = metricsService.computeMetricsForAsset(assetId, metricsLookbackDays);
                System.out.println("[MarketIngestionJob] METRICS " + symbol +
                        " inserted=" + metrics.inserted() + " status=" + metrics.status());

                // 3) Evaluate alerts
                AlertsService.AlertRunResult alerts = alertsService.evaluateAlertsForAsset(assetId);
                System.out.println("[MarketIngestionJob] ALERTS " + symbol +
                        " fired=" + alerts.fired() + " status=" + alerts.status());

            } catch (Exception ex) {
                // IMPORTANT: one asset failing should not stop others
                System.out.println("[MarketIngestionJob] FAILED pipeline for " + symbol + " : " + ex.getMessage());
            }
        }

        System.out.println("[MarketIngestionJob] Finished scheduled pipeline.");
    }
}
