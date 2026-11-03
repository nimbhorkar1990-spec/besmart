package com.smartsearch.crawler;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DomainRateLimiter {

    private final Map<String, Long> lastAccessTimePerDomain = new ConcurrentHashMap<>();
    private static final long DEFAULT_DELAY_MS = 500L;

    public void awaitPoliteness(String domain, int crawlDelaySeconds) {
        if (domain == null) return;
        long delayNeeded = crawlDelaySeconds > 0 ? (crawlDelaySeconds * 1000L) : DEFAULT_DELAY_MS;

        synchronized (domain.intern()) {
            Long last = lastAccessTimePerDomain.get(domain);
            long now = System.currentTimeMillis();
            if (last != null) {
                long elapsed = now - last;
                if (elapsed < delayNeeded) {
                    try {
                        Thread.sleep(delayNeeded - elapsed);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            lastAccessTimePerDomain.put(domain, System.currentTimeMillis());
        }
    }
}

