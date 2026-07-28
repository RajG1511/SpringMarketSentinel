package com.raj.springmarketanalysis.alert;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class AlertsController {

    private final AlertsService alertsService;
    private final AlertEventRepository eventRepo;

    public AlertsController(AlertsService alertsService, AlertEventRepository eventRepo) {
        this.alertsService = alertsService;
        this.eventRepo = eventRepo;
    }

    @PostMapping("/{assetId}/alerts/evaluate")
    public AlertsService.AlertRunResult evaluate(@PathVariable Long assetId) {
        return alertsService.evaluateAlertsForAsset(assetId);
    }

    @GetMapping("/{assetId}/alerts")
    public List<AlertResponse> alerts(
            @PathVariable Long assetId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        List<AlertEvent> events;
        if (from != null && to != null) {
            events = eventRepo.findByAssetIdAndTsBetweenOrderByTsDesc(assetId, from, to);
        } else {
            events = eventRepo.findTop50ByAssetIdOrderByTsDesc(assetId);
        }

        return events.stream()
                .map(e -> new AlertResponse(
                        e.getTs(),
                        e.getRule().getRuleKey(),
                        e.getSeverity(),
                        e.getMessage()
                ))
                .toList();
    }

    public record AlertResponse(LocalDate ts, String ruleKey, AlertSeverity severity, String message) {}
}
