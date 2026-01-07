package com.raj.springmarketanalysis.service;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assets")
public class PriceIngestionController {

    private final PriceIngestionService ingestionService;

    public PriceIngestionController(PriceIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/{assetId}/prices/ingest")
    public PriceIngestionService.IngestionResult ingest(@PathVariable Long assetId) {
        return ingestionService.ingestDailyPrices(assetId);
    }
}
