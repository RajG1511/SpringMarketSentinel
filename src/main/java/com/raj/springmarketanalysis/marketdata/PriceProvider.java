package com.raj.springmarketanalysis.marketdata;

import java.time.LocalDate;
import java.util.List;

public interface PriceProvider {
    List<DailyBar> fetchDailyBars(String symbol);

    record DailyBar(
            LocalDate ts,
            String symbol,
            java.math.BigDecimal open,
            java.math.BigDecimal high,
            java.math.BigDecimal low,
            java.math.BigDecimal close,
            long volume
    ) {}
}
