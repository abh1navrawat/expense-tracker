package com.tracker.expense.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("exchangeRates");
    }

    // Automatically evicts exchange rates cache every 24 hours to refresh real-time currency ratios
    @Scheduled(fixedRate = 86400000)
    public void evictExchangeRatesCache() {
        logger.info("Triggered scheduled eviction of exchange rates cache.");
        CacheManager cm = cacheManager();
        if (cm.getCache("exchangeRates") != null) {
            cm.getCache("exchangeRates").clear();
            logger.info("In-memory exchange rates cache cleared successfully.");
        }
    }
}
