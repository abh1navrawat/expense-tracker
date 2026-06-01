package com.tracker.expense.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.HashMap;

@Service
public class ExchangeRateService {

    private final RestClient restClient;

    public ExchangeRateService() {
        this.restClient = RestClient.create();
    }

    // Constructor injection for testing/mocking RestClient if needed
    public ExchangeRateService(RestClient restClient) {
        this.restClient = restClient;
    }

    @Cacheable(value = "exchangeRates", key = "#baseCurrency")
    public Map<String, BigDecimal> getExchangeRates(String baseCurrency) {
        try {
            String url = "https://open.er-api.com/v6/latest/" + baseCurrency.toUpperCase();
            ExchangeRateResponse response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(ExchangeRateResponse.class);

            if (response != null && "success".equalsIgnoreCase(response.getResult())) {
                return response.getRates();
            }
        } catch (Exception e) {
            // Graceful fallback to avoid application blocks when offline
        }
        
        // Return default exchange rates relative to the requested baseCurrency as a safe fallback
        return getFallbackRates(baseCurrency);
    }

    public BigDecimal convertCurrency(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null) return BigDecimal.ZERO;
        if (fromCurrency.equalsIgnoreCase(toCurrency)) return amount;

        // Fetch exchange rates relative to the target currency (which becomes the base rate)
        Map<String, BigDecimal> rates = getExchangeRates(toCurrency);
        
        // In the er-api format: 1 targetCurrency = X fromCurrency.
        // Therefore: targetAmount = fromAmount / rate.
        BigDecimal rate = rates.get(fromCurrency.toUpperCase());
        if (rate == null || rate.compareTo(BigDecimal.ZERO) == 0) {
            return amount; // return unconverted if rate cannot be resolved
        }

        return amount.divide(rate, 4, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> getFallbackRates(String baseCurrency) {
        Map<String, BigDecimal> fallback = new HashMap<>();
        
        // Quick lookup tables for typical currencies
        if ("USD".equalsIgnoreCase(baseCurrency)) {
            fallback.put("USD", BigDecimal.ONE);
            fallback.put("EUR", new BigDecimal("0.92"));
            fallback.put("INR", new BigDecimal("83.20"));
            fallback.put("GBP", new BigDecimal("0.79"));
        } else if ("INR".equalsIgnoreCase(baseCurrency)) {
            fallback.put("INR", BigDecimal.ONE);
            fallback.put("USD", new BigDecimal("0.012"));
            fallback.put("EUR", new BigDecimal("0.011"));
            fallback.put("GBP", new BigDecimal("0.009"));
        } else if ("EUR".equalsIgnoreCase(baseCurrency)) {
            fallback.put("EUR", BigDecimal.ONE);
            fallback.put("USD", new BigDecimal("1.08"));
            fallback.put("INR", new BigDecimal("90.40"));
            fallback.put("GBP", new BigDecimal("0.85"));
        } else {
            // Safe general baseline
            fallback.put(baseCurrency.toUpperCase(), BigDecimal.ONE);
            fallback.put("USD", BigDecimal.ONE);
            fallback.put("EUR", BigDecimal.ONE);
            fallback.put("INR", BigDecimal.ONE);
        }
        return fallback;
    }

    public static class ExchangeRateResponse {
        private String result;
        private String base_code;
        private Map<String, BigDecimal> rates;

        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }

        public String getBase_code() { return base_code; }
        public void setBase_code(String base_code) { this.base_code = base_code; }

        public Map<String, BigDecimal> getRates() { return rates; }
        public void setRates(Map<String, BigDecimal> rates) { this.rates = rates; }
    }
}
