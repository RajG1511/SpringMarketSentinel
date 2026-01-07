package com.raj.springmarketanalysis.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {
    List<IngestionRun> findTop10ByAssetIdOrderByStartedAtDesc(Long assetId);
}
