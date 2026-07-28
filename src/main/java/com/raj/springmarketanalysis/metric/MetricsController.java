package com.raj.springmarketanalysis.metric;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class MetricsController {

    static final int MAX_PAGE_SIZE = 5000;

    private final MetricsService metricsService;
    private final MetricValueRepository metricRepo;

    public MetricsController(MetricsService metricsService, MetricValueRepository metricRepo) {
        this.metricsService = metricsService;
        this.metricRepo = metricRepo;
    }

    // Manual trigger (like ingestion) for testing
    @PostMapping("/{assetId}/metrics/compute")
    public MetricsService.MetricsRunResult compute(
            @PathVariable Long assetId,
            @RequestParam(defaultValue = "400") int lookbackDays
    ) {
        return metricsService.computeMetricsForAsset(assetId, lookbackDays);
    }


    // Latest value of each metric type (Redis-cached)
    @GetMapping("/{assetId}/metrics/latest")
    public MetricsService.LatestMetrics latest(@PathVariable Long assetId) {
        return metricsService.getLatestMetrics(assetId);
    }

    /**
     * Read API. Paged to bound the response — a year of history across four
     * metric types is already ~1000 rows.
     * <p>
     * The page is sliced after the per-type queries rather than pushed into the
     * database. This endpoint fans out over several metric types and concatenates
     * them, so there is no single query for the database to paginate; slicing the
     * combined list is what keeps the page boundaries meaningful. Each type's rows
     * are bounded by the lookback window, so the intermediate list stays small.
     */
    @GetMapping("/{assetId}/metrics")
    public Page<MetricResponse> metrics(
            @PathVariable Long assetId,
            @RequestParam String types, // "RETURN_1D,SMA_20,VOL_20"
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1000") int size
    ) {
        List<MetricType> metricTypes = Arrays.stream(types.split(","))
                .map(String::trim)
                .map(MetricType::valueOf)
                .toList();

        List<MetricResponse> all = metricTypes.stream()
                .flatMap(mt -> {
                    List<MetricValue> rows;
                    if (from != null && to != null) {
                        rows = metricRepo.findByAssetIdAndMetricTypeAndTsBetweenOrderByTsAsc(assetId, mt, from, to);
                    } else {
                        rows = metricRepo.findByAssetIdAndMetricTypeOrderByTsAsc(assetId, mt);
                    }
                    return rows.stream().map(r -> new MetricResponse(r.getTs(), r.getMetricType(), r.getValue()));
                })
                .toList();

        int pageSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, pageSize);
        int start = (int) pageable.getOffset();
        if (start >= all.size()) {
            return new PageImpl<>(List.of(), pageable, all.size());
        }
        return new PageImpl<>(all.subList(start, Math.min(start + pageSize, all.size())), pageable, all.size());
    }

    public record MetricResponse(LocalDate ts, MetricType metricType, BigDecimal value) {}
}
