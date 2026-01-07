package com.raj.springmarketanalysis.scheduler;

import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import com.raj.springmarketanalysis.service.PriceIngestionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "market.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MarketIngestionJob {

    private final AssetRepository assetRepo;
    private final PriceIngestionService ingestionService;

    public MarketIngestionJob(AssetRepository assetRepo, PriceIngestionService ingestionService) {
        this.assetRepo = assetRepo;
        this.ingestionService = ingestionService;
    }

    // Pull cron from application.yml so you can change schedule without code changes
    @Scheduled(cron = "${market.scheduler.cron}")
    public void ingestAllAssetsDaily() {
        List<Asset> assets = assetRepo.findAll();

        // If no assets exist, just do nothing
        if (assets.isEmpty()) {
            System.out.println("[MarketIngestionJob] No assets found; skipping.");
            return;
        }

        System.out.println("[MarketIngestionJob] Starting scheduled ingestion for " + assets.size() + " assets.");

        for (Asset a : assets) {
            try {
                PriceIngestionService.IngestionResult result = ingestionService.ingestDailyPrices(a.getId());
                System.out.println("[MarketIngestionJob] " + a.getSymbol() + " inserted=" + result.inserted() + " skipped=" + result.skipped());
            } catch (Exception ex) {
                // IMPORTANT: don't crash the entire job if one asset fails
                System.out.println("[MarketIngestionJob] FAILED for " + a.getSymbol() + " : " + ex.getMessage());
            }
        }

        System.out.println("[MarketIngestionJob] Finished scheduled ingestion.");
    }
}
