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
public class StructureSessionContextService {

    static final Duration CONTEXT_EXPIRATION = Duration.ofMinutes(30);
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);

    private final ConcurrentMap<String, StructureContext> lastStructureBySession = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastCleanupAt;
    private final Clock clock;
    private final Duration expiration;
    private final Duration cleanupInterval;

    public StructureSessionContextService() {
        this(Clock.systemUTC(), CONTEXT_EXPIRATION, CLEANUP_INTERVAL);
    }

    StructureSessionContextService(Clock clock, Duration expiration, Duration cleanupInterval) {
        this.clock = clock;
        this.expiration = expiration;
        this.cleanupInterval = cleanupInterval;
        this.lastCleanupAt = new AtomicReference<>(Instant.EPOCH);
    }

    public Optional<StructureReference> findLastStructureReference(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId == null) {
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        StructureContext context = lastStructureBySession.get(normalizedSessionId);
        if (context == null) {
            cleanupExpiredIfNeeded(now);
            return Optional.empty();
        }
        if (isExpired(context, now)) {
            lastStructureBySession.remove(normalizedSessionId, context);
            cleanupExpiredIfNeeded(now);
            return Optional.empty();
        }

        lastStructureBySession.replace(normalizedSessionId, context, context.refreshed(now));
        cleanupExpiredIfNeeded(now);
        return Optional.of(context.reference());
    }

    public void rememberStructure(String sessionId, StructureReference reference) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId == null || reference == null || reference.id() == null || reference.sourceType() == null
                || reference.sourceType().isBlank()) {
            return;
        }
        Instant now = Instant.now(clock);
        lastStructureBySession.put(normalizedSessionId, new StructureContext(reference, now));
        cleanupExpiredIfNeeded(now);
    }

    public void clear(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId != null) {
            lastStructureBySession.remove(normalizedSessionId);
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
        lastStructureBySession.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    private boolean isExpired(StructureContext context, Instant now) {
        return !context.lastAccessTime().plus(expiration).isAfter(now);
    }

    private record StructureContext(StructureReference reference, Instant lastAccessTime) {
        private StructureContext refreshed(Instant now) {
            return new StructureContext(reference, now);
        }
    }

    public record StructureReference(String sourceType, Long id) {
    }
}
