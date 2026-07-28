package com.raj.springmarketanalysis.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    Optional<AlertRule> findByRuleKey(String ruleKey);
    List<AlertRule> findByEnabledTrue();
}
