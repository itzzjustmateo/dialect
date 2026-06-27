package com.vomlabs.dialect.service.ai;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RateLimiter {

    private final int maxRequestsPerMinute;
    private final int maxTokensPerMinute;
    private final AtomicInteger requestCount;
    private final AtomicInteger tokenCount;
    private final AtomicLong windowStart;
    private final Object lock;

    public RateLimiter(int maxRequestsPerMinute, int maxTokensPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.maxTokensPerMinute = maxTokensPerMinute;
        this.requestCount = new AtomicInteger(0);
        this.tokenCount = new AtomicInteger(0);
        this.windowStart = new AtomicLong(System.currentTimeMillis());
        this.lock = new Object();
    }

    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    public boolean tryAcquire(int estimatedTokens) {
        resetIfWindowExpired();
        synchronized (lock) {
            if (requestCount.get() >= maxRequestsPerMinute) {
                return false;
            }
            if (tokenCount.get() + estimatedTokens > maxTokensPerMinute) {
                return false;
            }
            requestCount.incrementAndGet();
            tokenCount.addAndGet(estimatedTokens);
            return true;
        }
    }

    public long getResetTimeMillis() {
        long start = windowStart.get();
        return (start + 60_000) - System.currentTimeMillis();
    }

    public boolean isLimited() {
        resetIfWindowExpired();
        return requestCount.get() >= maxRequestsPerMinute;
    }

    public int getRemainingRequests() {
        resetIfWindowExpired();
        return Math.max(0, maxRequestsPerMinute - requestCount.get());
    }

    public int getRemainingTokens() {
        resetIfWindowExpired();
        return Math.max(0, maxTokensPerMinute - tokenCount.get());
    }

    private void resetIfWindowExpired() {
        long now = System.currentTimeMillis();
        long start = windowStart.get();
        if (now - start >= 60_000) {
            if (windowStart.compareAndSet(start, now)) {
                requestCount.set(0);
                tokenCount.set(0);
            }
        }
    }
}
