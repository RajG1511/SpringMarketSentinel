package com.raj.springmarketanalysis.price;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceBarRepository extends JpaRepository<PriceBar, Long> {

    Page<PriceBar> findByAssetId(Long assetId, Pageable pageable);

    Page<PriceBar> findByAssetIdAndTsBetween(Long assetId, LocalDate from, LocalDate to, Pageable pageable);

    Page<PriceBar> findByAssetIdAndTsGreaterThanEqual(Long assetId, LocalDate from, Pageable pageable);

    Page<PriceBar> findByAssetIdAndTsLessThanEqual(Long assetId, LocalDate to, Pageable pageable);

    List<PriceBar> findByAssetIdOrderByTsAsc(Long assetId);

    List<PriceBar> findByAssetIdAndTsBetweenOrderByTsAsc(Long assetId, LocalDate from, LocalDate to);

    List<PriceBar> findByAssetIdAndTsGreaterThanEqualOrderByTsAsc(Long assetId, LocalDate from);

    List<PriceBar> findByAssetIdAndTsLessThanEqualOrderByTsAsc(Long assetId, LocalDate to);

    Optional<PriceBar> findTopByAssetIdOrderByTsDesc(Long assetId);

    Optional<PriceBar> findByAssetIdAndTs(Long assetId, LocalDate ts);

    boolean existsByAssetIdAndTs(Long assetId, LocalDate ts);
}
