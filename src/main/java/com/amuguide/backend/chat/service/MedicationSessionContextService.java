package com.amuguide.backend.chat.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MedicationSessionContextService {

    static final Duration CONTEXT_EXPIRATION = Duration.ofMinutes(30);
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);

    private final ConcurrentMap<String, MedicationContext> lastMedicationBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PendingMedicationSuggestionContext> pendingSuggestionsBySession = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastCleanupAt;
    private final Clock clock;
    private final Duration expiration;
    private final Duration cleanupInterval;

    public MedicationSessionContextService() {
        this(Clock.systemUTC(), CONTEXT_EXPIRATION, CLEANUP_INTERVAL);
    }

    MedicationSessionContextService(Clock clock, Duration expiration, Duration cleanupInterval) {
        this.clock = clock;
        this.expiration = expiration;
        this.cleanupInterval = cleanupInterval;
        this.lastCleanupAt = new AtomicReference<>(Instant.EPOCH);
    }

    public Optional<Long> findLastMedicationId(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId == null) {
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        MedicationContext context = lastMedicationBySession.get(normalizedSessionId);
        if (context == null) {
            cleanupExpiredIfNeeded(now);
            return Optional.empty();
        }
        if (isExpired(context, now)) {
            lastMedicationBySession.remove(normalizedSessionId, context);
            cleanupExpiredIfNeeded(now);
            return Optional.empty();
        }

        lastMedicationBySession.replace(normalizedSessionId, context, context.refreshed(now));
        cleanupExpiredIfNeeded(now);
        return Optional.of(context.medicationId());
    }

    public void rememberMedication(String sessionId, Long medicationId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId == null || medicationId == null) {
            return;
        }
        Instant now = Instant.now(clock);
        lastMedicationBySession.put(normalizedSessionId, new MedicationContext(medicationId, now));
        pendingSuggestionsBySession.remove(normalizedSessionId);
        cleanupExpiredIfNeeded(now);
    }

    public void rememberPendingSuggestions(String sessionId, List<PendingMedicationCandidate> candidates, String originalNormalizedMessage) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId == null || candidates == null || candidates.isEmpty()) {
            return;
        }
        List<PendingMedicationCandidate> validCandidates = candidates.stream()
                .filter(candidate -> candidate != null
                        && candidate.medicationId() != null
                        && candidate.name() != null
                        && !candidate.name().isBlank())
                .toList();
        if (validCandidates.isEmpty()) {
            return;
        }
        Instant now = Instant.now(clock);
        pendingSuggestionsBySession.put(
                normalizedSessionId,
                new PendingMedicationSuggestionContext(validCandidates, originalNormalizedMessage, now)
        );
        cleanupExpiredIfNeeded(now);
    }

    public Optional<PendingMedicationSuggestionContext> findPendingSuggestions(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId == null) {
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        PendingMedicationSuggestionContext context = pendingSuggestionsBySession.get(normalizedSessionId);
        if (context == null) {
            cleanupExpiredIfNeeded(now);
            return Optional.empty();
        }
        if (isExpired(context, now)) {
            pendingSuggestionsBySession.remove(normalizedSessionId, context);
            cleanupExpiredIfNeeded(now);
            return Optional.empty();
        }

        pendingSuggestionsBySession.replace(normalizedSessionId, context, context.refreshed(now));
        cleanupExpiredIfNeeded(now);
        return Optional.of(context);
    }

    public void clearPendingSuggestions(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId != null) {
            pendingSuggestionsBySession.remove(normalizedSessionId);
        }
    }

    public void clear(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId != null) {
            lastMedicationBySession.remove(normalizedSessionId);
            pendingSuggestionsBySession.remove(normalizedSessionId);
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
        lastMedicationBySession.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
        pendingSuggestionsBySession.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    private boolean isExpired(MedicationContext context, Instant now) {
        return !context.lastAccessTime().plus(expiration).isAfter(now);
    }

    private boolean isExpired(PendingMedicationSuggestionContext context, Instant now) {
        return !context.lastAccessTime().plus(expiration).isAfter(now);
    }

    private record MedicationContext(Long medicationId, Instant lastAccessTime) {
        private MedicationContext refreshed(Instant now) {
            return new MedicationContext(medicationId, now);
        }
    }

    public record PendingMedicationCandidate(Long medicationId, String name) {
    }

    public record PendingMedicationSuggestionContext(
            List<PendingMedicationCandidate> candidates,
            String originalNormalizedMessage,
            Instant lastAccessTime
    ) {
        private PendingMedicationSuggestionContext refreshed(Instant now) {
            return new PendingMedicationSuggestionContext(candidates, originalNormalizedMessage, now);
        }
    }
}
