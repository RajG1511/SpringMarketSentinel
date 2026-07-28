package com.raj.springmarketanalysis.marketdata;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class StooqPriceProvider implements PriceProvider {

    private final RestClient restClient = RestClient.create();

    @Override
    public List<DailyBar> fetchDailyBars(String symbol) {
        // Stooq uses lowercase symbols, often with ".us" for US tickers.
        // For demo reliability, we default to symbol.us.
        // Ex: SPY -> spy.us, AAPL -> aapl.us
        String stooqSymbol = symbol.toLowerCase() + ".us";

        String url = "https://stooq.com/q/d/l/?s=" + stooqSymbol + "&i=d";

        String csv = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);

        if (csv == null || csv.isBlank()) {
            throw new IllegalStateException("Empty response from Stooq for symbol=" + symbol);
        }

        // Sometimes Stooq returns "No data" etc.
        if (!csv.startsWith("Date,Open,High,Low,Close,Volume")) {
            throw new IllegalStateException("Unexpected Stooq CSV response for symbol=" + symbol + ". Body: " +
                    (csv.length() > 200 ? csv.substring(0, 200) + "..." : csv));
        }

        return parseCsv(symbol, csv);
    }

    private List<DailyBar> parseCsv(String symbol, String csv) {
        // CSV header: Date,Open,High,Low,Close,Volume
        String[] lines = csv.split("\\R"); // any newline
        List<DailyBar> out = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split(",");
            if (parts.length < 6) continue;

            LocalDate ts = LocalDate.parse(parts[0]);

            // Stooq can sometimes include empty values on some rows; skip those.
            if (parts[1].isBlank() || parts[2].isBlank() || parts[3].isBlank() || parts[4].isBlank() || parts[5].isBlank()) {
                continue;
            }

            BigDecimal open = new BigDecimal(parts[1]);
            BigDecimal high = new BigDecimal(parts[2]);
            BigDecimal low  = new BigDecimal(parts[3]);
            BigDecimal close= new BigDecimal(parts[4]);
            long volume = Long.parseLong(parts[5]);

            out.add(new DailyBar(ts, symbol, open, high, low, close, volume));
        }

        if (out.isEmpty()) {
            throw new IllegalStateException("Parsed 0 bars from Stooq CSV");
        }

        return out;
    }
}
