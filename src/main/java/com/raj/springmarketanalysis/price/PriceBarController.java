package com.raj.springmarketanalysis.price;

import com.raj.springmarketanalysis.api.ApiExceptions;
import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/assets")
public class PriceBarController {

    static final int MAX_PAGE_SIZE = 2000;

    private final AssetRepository assetRepo;
    private final PriceBarRepository priceRepo;

    public PriceBarController(AssetRepository assetRepo, PriceBarRepository priceRepo) {
        this.assetRepo = assetRepo;
        this.priceRepo = priceRepo;
    }

    /**
     * Paged so the response can't grow without bound as history accumulates.
     * Sorting is fixed to ascending {@code ts} — chart consumers depend on the
     * order, and letting callers re-sort a page of a time series only produces
     * confusing results.
     */
    @GetMapping("/{assetId}/prices")
    public Page<PriceBarResponse> prices(
            @PathVariable Long assetId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "500") int size
    ) {
        assetRepo.findById(assetId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Asset not found: " + assetId));

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by(Sort.Direction.ASC, "ts"));

        Page<PriceBar> bars;
        if (from != null && to != null) {
            bars = priceRepo.findByAssetIdAndTsBetween(assetId, from, to, pageable);
        } else if (from != null) {
            bars = priceRepo.findByAssetIdAndTsGreaterThanEqual(assetId, from, pageable);
        } else if (to != null) {
            bars = priceRepo.findByAssetIdAndTsLessThanEqual(assetId, to, pageable);
        } else {
            bars = priceRepo.findByAssetId(assetId, pageable);
        }

        return bars.map(b -> new PriceBarResponse(
                b.getTs(), b.getOpen(), b.getHigh(), b.getLow(), b.getClose(), b.getVolume()));
    }

    @GetMapping("/{assetId}/prices/latest")
    public PriceBarResponse latest(@PathVariable Long assetId) {
        Asset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Asset not found: " + assetId));

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
