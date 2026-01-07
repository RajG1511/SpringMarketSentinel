package com.raj.springmarketanalysis.metric;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class MetricsController {

    private final MetricsService metricsService;
    private final MetricValueRepository metricRepo;

    public MetricsController(MetricsService metricsService, MetricValueRepository metricRepo) {
        this.metricsService = metricsService;
        this.metricRepo = metricRepo;
    }

    // Manual trigger (like ingestion) for testing
    @PostMapping("/{assetId}/metrics/compute")
    public MetricsService.MetricsRunResult compute(@PathVariable Long assetId) {
        return metricsService.computeMetricsForAsset(assetId);
    }

    // Read API
    @GetMapping("/{assetId}/metrics")
    public List<MetricResponse> metrics(
            @PathVariable Long assetId,
            @RequestParam String types, // "RETURN_1D,SMA_20,VOL_20"
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        List<MetricType> metricTypes = Arrays.stream(types.split(","))
                .map(String::trim)
                .map(MetricType::valueOf)
                .toList();

        return metricTypes.stream()
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
    }

    public record MetricResponse(LocalDate ts, MetricType metricType, BigDecimal value) {}
}
