package com.raj.springmarketanalysis.scheduler;

import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import com.raj.springmarketanalysis.service.PriceIngestionService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminIngestionController {

    private final AssetRepository assetRepo;
    private final PriceIngestionService ingestionService;

    public AdminIngestionController(AssetRepository assetRepo, PriceIngestionService ingestionService) {
        this.assetRepo = assetRepo;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest/prices")
    public List<PriceIngestionService.IngestionResult> ingestAll() {
        List<Asset> assets = assetRepo.findAll();
        List<PriceIngestionService.IngestionResult> results = new ArrayList<>();

        for (Asset a : assets) {
            results.add(ingestionService.ingestDailyPrices(a.getId()));
        }

        return results;
    }
}
