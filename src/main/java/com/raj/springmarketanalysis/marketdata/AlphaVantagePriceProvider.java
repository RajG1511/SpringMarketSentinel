package com.raj.springmarketanalysis.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class AlphaVantagePriceProvider implements PriceProvider {

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public AlphaVantagePriceProvider(
            ObjectMapper objectMapper,
            @Value("${market.alphavantage.apiKey:}") String apiKey
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public List<DailyBar> fetchDailyBars(String symbol) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Alpha Vantage API key missing. Set ALPHAVANTAGE_API_KEY.");
        }

        String url = "https://www.alphavantage.co/query" +
                "?function=TIME_SERIES_DAILY" +
                "&symbol=" + symbol +
                "&outputsize=compact" +
                "&apikey=" + apiKey;

        String body = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);

        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Empty response from Alpha Vantage for symbol=" + symbol);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Alpha Vantage JSON", e);
        }

        if (root.has("Error Message")) {
            throw new IllegalStateException("Alpha Vantage error: " + root.get("Error Message").asText());
        }
        if (root.has("Note")) {
            throw new IllegalStateException("Alpha Vantage throttled: " + root.get("Note").asText());
        }
        if (root.has("Information")) {
            throw new IllegalStateException("Alpha Vantage info: " + root.get("Information").asText());
        }

        JsonNode series = root.get("Time Series (Daily)");
        if (series == null || series.isNull()) {
            throw new IllegalStateException("Missing Time Series (Daily). Body: " + body);
        }

        List<DailyBar> out = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = series.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            LocalDate ts = LocalDate.parse(entry.getKey());
            JsonNode bar = entry.getValue();

            BigDecimal open = new BigDecimal(bar.get("1. open").asText());
            BigDecimal high = new BigDecimal(bar.get("2. high").asText());
            BigDecimal low  = new BigDecimal(bar.get("3. low").asText());
            BigDecimal close= new BigDecimal(bar.get("4. close").asText());
            long volume = Long.parseLong(bar.get("5. volume").asText());

            out.add(new DailyBar(ts, symbol, open, high, low, close, volume));
        }

        if (out.isEmpty()) {
            throw new IllegalStateException("Alpha Vantage returned 0 bars");
        }

        return out;
    }
}
