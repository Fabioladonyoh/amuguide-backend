package com.amuguide.backend.chat.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PrestationSessionContextService {

    static final Duration CONTEXT_EXPIRATION = Duration.ofMinutes(30);
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);

    private final ConcurrentMap<String, PrestationContext> lastPrestationBySession = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastCleanupAt;
    private final Clock clock;
    private final Duration expiration;
    private final Duration cleanupInterval;

    public PrestationSessionContextService() {
        this(Clock.systemUTC(), CONTEXT_EXPIRATION, CLEANUP_INTERVAL);
    }

    PrestationSessionContextService(Clock clock, Duration expiration, Duration cleanupInterval) {
        this.clock = clock;
        this.expiration = expiration;
        this.cleanupInterval = cleanupInterval;
        this.lastCleanupAt = new AtomicReference<>(Instant.EPOCH);
    }

    public Optional<Long> findLastPrestationId(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId == null) {
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        PrestationContext context = lastPrestationBySession.get(normalizedSessionId);
        if (context == null) {
            cleanupExpiredIfNeeded(now);
            return Optional.empty();
        }
        if (isExpired(context, now)) {
            lastPrestationBySession.remove(normalizedSessionId, context);
            cleanupExpiredIfNeeded(now);
            return Optional.empty();
        }

        lastPrestationBySession.replace(normalizedSessionId, context, context.refreshed(now));
        cleanupExpiredIfNeeded(now);
        return Optional.of(context.prestationId());
    }

    public void rememberPrestation(String sessionId, Long prestationId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId == null || prestationId == null) {
            return;
        }
        Instant now = Instant.now(clock);
        lastPrestationBySession.put(normalizedSessionId, new PrestationContext(prestationId, now));
        cleanupExpiredIfNeeded(now);
    }

    public void clear(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId != null) {
            lastPrestationBySession.remove(normalizedSessionId);
        }
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return sessionId.trim();
    }

    private void cleanupExpiredIfNeeded(Instant now) {
        Instant previousCleanup = lastCleanupAt.get();
        if (Duration.between(previousCleanup, now).compareTo(cleanupInterval) < 0) {
            return;
        }
        if (!lastCleanupAt.compareAndSet(previousCleanup, now)) {
            return;
        }
        lastPrestationBySession.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    private boolean isExpired(PrestationContext context, Instant now) {
        return !context.lastAccessTime().plus(expiration).isAfter(now);
    }

    private record PrestationContext(Long prestationId, Instant lastAccessTime) {
        private PrestationContext refreshed(Instant now) {
            return new PrestationContext(prestationId, now);
        }
    }
}
