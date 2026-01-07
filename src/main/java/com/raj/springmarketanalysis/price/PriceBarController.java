package com.raj.springmarketanalysis.price;

import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class PriceBarController {

    private final AssetRepository assetRepo;
    private final PriceBarRepository priceRepo;

    public PriceBarController(AssetRepository assetRepo, PriceBarRepository priceRepo) {
        this.assetRepo = assetRepo;
        this.priceRepo = priceRepo;
    }

    @GetMapping("/{assetId}/prices")
    public List<PriceBarResponse> prices(
            @PathVariable Long assetId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        Asset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        List<PriceBar> bars;
        if (from != null && to != null) {
            bars = priceRepo.findByAssetIdAndTsBetweenOrderByTsAsc(assetId, from, to);
        } else if (from != null) {
            bars = priceRepo.findByAssetIdAndTsGreaterThanEqualOrderByTsAsc(assetId, from);
        } else if (to != null) {
            bars = priceRepo.findByAssetIdAndTsLessThanEqualOrderByTsAsc(assetId, to);
        } else {
            bars = priceRepo.findByAssetIdOrderByTsAsc(assetId);
        }

        return bars.stream()
                .map(b -> new PriceBarResponse(
                        b.getTs(), b.getOpen(), b.getHigh(), b.getLow(), b.getClose(), b.getVolume()
                ))
                .toList();
    }

    @GetMapping("/{assetId}/prices/latest")
    public PriceBarResponse latest(@PathVariable Long assetId) {
        Asset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        PriceBar bar = priceRepo.findTopByAssetIdOrderByTsDesc(asset.getId())
                .orElseThrow(() -> new IllegalStateException("No prices found for asset: " + assetId));

        return new PriceBarResponse(
                bar.getTs(), bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(), bar.getVolume()
        );
    }

    public record PriceBarResponse(
            LocalDate ts,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            Long volume
    ) {}
}
