package com.raj.springmarketanalysis.marketdata;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PriceProviderConfig {

    @Bean
    public PriceProvider priceProvider(
            StooqPriceProvider stooq,
            AlphaVantagePriceProvider alpha,
            @Value("${market.prices.provider:stooq}") String provider
    ) {
        String p = provider == null ? "stooq" : provider.trim().toLowerCase();
        return switch (p) {
            case "stooq" -> stooq;
            case "alphavantage", "alpha_vantage", "alpha" -> alpha;
            default -> throw new IllegalStateException("Unknown market.prices.provider=" + provider + " (use stooq or alphavantage)");
        };
    }
}
