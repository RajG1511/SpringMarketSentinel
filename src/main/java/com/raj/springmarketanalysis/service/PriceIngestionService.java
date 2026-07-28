package com.raj.springmarketanalysis.service;

import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import com.raj.springmarketanalysis.ingestion.IngestionRun;
import com.raj.springmarketanalysis.ingestion.IngestionRunRepository;
import com.raj.springmarketanalysis.marketdata.PriceProvider;
import com.raj.springmarketanalysis.price.PriceBar;
import com.raj.springmarketanalysis.price.PriceBarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PriceIngestionService {

    private final AssetRepository assetRepo;
    private final PriceBarRepository priceRepo;
    private final IngestionRunRepository runRepo;
    private final PriceProvider priceProvider;

    public PriceIngestionService(
            AssetRepository assetRepo,
            PriceBarRepository priceRepo,
            IngestionRunRepository runRepo,
            PriceProvider priceProvider
    ) {
        this.assetRepo = assetRepo;
        this.priceRepo = priceRepo;
        this.runRepo = runRepo;
        this.priceProvider = priceProvider;
    }

    public record IngestionResult(Long assetId, String symbol, int inserted, int skipped) {}

    @Transactional
    public IngestionResult ingestDailyPrices(Long assetId) {
        Asset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        String symbol = asset.getSymbol();

        IngestionRun run = IngestionRun.start(asset);
        run = runRepo.save(run);

        try {
            List<PriceProvider.DailyBar> bars = priceProvider.fetchDailyBars(symbol);

            int inserted = 0;
            int skipped = 0;

            for (PriceProvider.DailyBar b : bars) {
                LocalDate ts = b.ts();

                if (priceRepo.existsByAssetIdAndTs(assetId, ts)) {
                    skipped++;
                    continue;
                }

                priceRepo.save(new PriceBar(
                        asset,
                        ts,
                        b.open(),
                        b.high(),
                        b.low(),
                        b.close(),
                        b.volume()
                ));
                inserted++;
            }

            run.markSuccess(inserted, skipped);
            runRepo.save(run);

            return new IngestionResult(assetId, symbol, inserted, skipped);

        } catch (RuntimeException ex) {
            run.markFailure(ex.getMessage());
            runRepo.save(run);
            throw ex;
        }
    }
}
