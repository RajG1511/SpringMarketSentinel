package com.raj.springmarketanalysis.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Posts a Slack-compatible {@code {"text": ...}} payload to a configured webhook URL.
 * Works with Slack/Discord incoming webhooks or any endpoint accepting that shape.
 * Disabled (and skipped) when {@code market.alerts.webhook.url} is blank.
 */
@Component
public class WebhookNotificationChannel implements NotificationChannel {

    private final RestClient restClient = RestClient.create();
    private final String url;

    public WebhookNotificationChannel(@Value("${market.alerts.webhook.url:}") String url) {
        this.url = url;
    }

    @Override
    public String type() {
        return "webhook";
    }

    @Override
    public boolean isEnabled() {
        return url != null && !url.isBlank();
    }

    @Override
    public void send(AlertNotification n) {
        String text = "[" + n.severity() + "] " + n.symbol() + " " + n.ruleKey()
                + " (" + n.ts() + ")\n" + n.message();

        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", text))
                .retrieve()
                .toBodilessEntity();
    }
}
