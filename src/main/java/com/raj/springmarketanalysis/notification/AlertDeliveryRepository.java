package com.raj.springmarketanalysis.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertDeliveryRepository extends JpaRepository<AlertDelivery, Long> {

    List<AlertDelivery> findByAlertEventIdOrderByCreatedAtAsc(Long alertEventId);
}
