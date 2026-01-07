package com.raj.springmarketanalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import com.raj.springmarketanalysis.ingestion.IngestionRun;
import com.raj.springmarketanalysis.ingestion.IngestionRunRepository;
import com.raj.springmarketanalysis.price.PriceBar;
import com.raj.springmarketanalysis.price.PriceBarRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Map;

@Service
public class PriceIngestionService {

    private final AssetRepository assetRepo;
    private final PriceBarRepository priceRepo;
    private final IngestionRunRepository runRepo;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public PriceIngestionService(
            AssetRepository assetRepo,
            PriceBarRepository priceRepo,
            IngestionRunRepository runRepo,
            ObjectMapper objectMapper,
            @Value("${market.alphavantage.apiKey:}") String apiKey
    ) {
        this.assetRepo = assetRepo;
        this.priceRepo = priceRepo;
        this.runRepo = runRepo;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.restClient = RestClient.create();
    }

    public record IngestionResult(Long assetId, String symbol, int inserted, int skipped) {}

    @Transactional
    public IngestionResult ingestDailyPrices(Long assetId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Alpha Vantage API key is missing. Set ALPHAVANTAGE_API_KEY.");
        }

        Asset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        String symbol = asset.getSymbol();

        // 1) Create run row at start
        IngestionRun run = IngestionRun.start(asset);
        run = runRepo.save(run); // persist early so we always have a record

        try {
            String url = "https://www.alphavantage.co/query" +
                    "?function=TIME_SERIES_DAILY" +
                    "&symbol=" + symbol +
                    "&outputsize=compact" +
                    "&apikey=" + apiKey;

            String body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            JsonNode root;
            try {
                root = objectMapper.readTree(body);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse Alpha Vantage response", e);
            }

            // Upstream “error-style” responses
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
                throw new IllegalStateException("Missing Time Series (Daily) in response. Body: " + body);
            }

            Iterator<Map.Entry<String, JsonNode>> fields = series.fields();
            int inserted = 0;
            int skipped = 0;

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                LocalDate ts = LocalDate.parse(entry.getKey());

                if (priceRepo.existsByAssetIdAndTs(assetId, ts)) {
                    skipped++;
                    continue;
                }

                JsonNode bar = entry.getValue();

                BigDecimal open = new BigDecimal(bar.get("1. open").asText());
                BigDecimal high = new BigDecimal(bar.get("2. high").asText());
                BigDecimal low  = new BigDecimal(bar.get("3. low").asText());
                BigDecimal close = new BigDecimal(bar.get("4. close").asText());
                long volume = Long.parseLong(bar.get("5. volume").asText());

                priceRepo.save(new PriceBar(asset, ts, open, high, low, close, volume));
                inserted++;
            }

            // 2) Mark success
            run.markSuccess(inserted, skipped);
            runRepo.save(run);

            System.out.println("Ingestion complete for " + symbol + ": inserted=" + inserted + ", skipped=" + skipped);
            return new IngestionResult(assetId, symbol, inserted, skipped);

        } catch (RuntimeException ex) {
            // 3) Mark failure
            run.markFailure(ex.getMessage());
            runRepo.save(run);
            throw ex;
        }
    }
}
