package com.raj.springmarketanalysis.metric;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MetricValueRepository extends JpaRepository<MetricValue, Long> {

    boolean existsByAssetIdAndTsAndMetricType(Long assetId, LocalDate ts, MetricType metricType);

    Optional<MetricValue> findTopByAssetIdAndMetricTypeOrderByTsDesc(Long assetId, MetricType metricType);

    List<MetricValue> findByAssetIdAndMetricTypeOrderByTsAsc(Long assetId, MetricType metricType);

    List<MetricValue> findByAssetIdAndMetricTypeAndTsBetweenOrderByTsAsc(
            Long assetId, MetricType metricType, LocalDate from, LocalDate to
    );

    List<MetricValue> findByAssetIdAndMetricTypeAndTsLessThanEqualOrderByTsDesc(
            Long assetId, MetricType metricType, LocalDate ts
    );

    List<MetricValue> findTop2ByAssetIdAndMetricTypeAndTsLessThanEqualOrderByTsDesc(
            Long assetId, MetricType metricType, LocalDate ts
    );



}
