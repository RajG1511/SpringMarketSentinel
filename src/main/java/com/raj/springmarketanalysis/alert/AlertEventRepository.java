package com.raj.springmarketanalysis.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {

    boolean existsByAssetIdAndRuleIdAndTs(Long assetId, Long ruleId, LocalDate ts);

    List<AlertEvent> findTop50ByAssetIdOrderByTsDesc(Long assetId);

    List<AlertEvent> findByAssetIdAndTsBetweenOrderByTsDesc(Long assetId, LocalDate from, LocalDate to);
}
