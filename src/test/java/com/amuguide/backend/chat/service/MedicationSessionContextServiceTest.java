package com.amuguide.backend.chat.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MedicationSessionContextServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-07T12:00:00Z"));
    private final MedicationSessionContextService service = new MedicationSessionContextService(
            clock,
            Duration.ofMinutes(30),
            Duration.ZERO
    );

    @Test
    void ignoresInvalidSessionIds() {
        service.rememberMedication(null, 1L);
        service.rememberMedication("", 1L);
        service.rememberMedication("   ", 1L);

        assertThat(service.findLastMedicationId(null)).isEmpty();
        assertThat(service.findLastMedicationId("")).isEmpty();
        assertThat(service.findLastMedicationId("   ")).isEmpty();
    }

    @Test
    void ignoresNullMedicationId() {
        service.rememberMedication("session-a", null);

        assertThat(service.findLastMedicationId("session-a")).isEmpty();
    }

    @Test
    void keepsDifferentSessionsIsolated() {
        service.rememberMedication("session-a", 1L);
        service.rememberMedication("session-b", 2L);

        assertThat(service.findLastMedicationId("session-a")).contains(1L);
        assertThat(service.findLastMedicationId("session-b")).contains(2L);
    }

    @Test
    void replacesOnlyTheUpdatedSession() {
        service.rememberMedication("session-a", 1L);
        service.rememberMedication("session-b", 2L);
        service.rememberMedication("session-a", 3L);

        assertThat(service.findLastMedicationId("session-a")).contains(3L);
        assertThat(service.findLastMedicationId("session-b")).contains(2L);
    }

    @Test
    void clearRemovesOnlyRequestedSession() {
        service.rememberMedication("session-a", 1L);
        service.rememberMedication("session-b", 2L);

        service.clear("session-a");

        assertThat(service.findLastMedicationId("session-a")).isEmpty();
        assertThat(service.findLastMedicationId("session-b")).contains(2L);
    }

    @Test
    void clearIgnoresInvalidSessionIds() {
        service.rememberMedication("session-a", 1L);

        service.clear(null);
        service.clear("");
        service.clear("   ");

        assertThat(service.findLastMedicationId("session-a")).contains(1L);
    }

    @Test
    void expiresInactiveSession() {
        service.rememberMedication("session-a", 1L);

        clock.advance(Duration.ofMinutes(30));

        assertThat(service.findLastMedicationId("session-a")).isEmpty();
    }

    @Test
    void refreshesLastAccessTimeWhenContextIsRead() {
        service.rememberMedication("session-a", 1L);

        clock.advance(Duration.ofMinutes(20));
        assertThat(service.findLastMedicationId("session-a")).contains(1L);

        clock.advance(Duration.ofMinutes(20));
        assertThat(service.findLastMedicationId("session-a")).contains(1L);
    }

    @Test
    void activeSessionSurvivesWhileExpiredSessionIsRemoved() {
        service.rememberMedication("session-a", 1L);
        service.rememberMedication("session-b", 2L);

        clock.advance(Duration.ofMinutes(20));
        assertThat(service.findLastMedicationId("session-b")).contains(2L);

        clock.advance(Duration.ofMinutes(15));

        assertThat(service.findLastMedicationId("session-a")).isEmpty();
        assertThat(service.findLastMedicationId("session-b")).contains(2L);
    }

    @Test
    void keepsPendingSuggestionsSeparateFromConfirmedMedication() {
        service.rememberMedication("session-a", 1L);
        service.rememberPendingSuggestions("session-a", List.of(
                new MedicationSessionContextService.PendingMedicationCandidate(2L, "DOLIPRANE COMP EFF 500MG")
        ), "quel est le prix");

        assertThat(service.findLastMedicationId("session-a")).contains(1L);
        assertThat(service.findPendingSuggestions("session-a"))
                .get()
                .extracting(MedicationSessionContextService.PendingMedicationSuggestionContext::originalNormalizedMessage)
                .isEqualTo("quel est le prix");
    }

    @Test
    void confirmedMedicationClearsPendingSuggestions() {
        service.rememberPendingSuggestions("session-a", List.of(
                new MedicationSessionContextService.PendingMedicationCandidate(2L, "DOLIPRANE COMP EFF 500MG")
        ), "quel est le prix");

        service.rememberMedication("session-a", 2L);

        assertThat(service.findLastMedicationId("session-a")).contains(2L);
        assertThat(service.findPendingSuggestions("session-a")).isEmpty();
    }

    @Test
    void expiresPendingSuggestions() {
        service.rememberPendingSuggestions("session-a", List.of(
                new MedicationSessionContextService.PendingMedicationCandidate(2L, "DOLIPRANE COMP EFF 500MG")
        ), "quel est le prix");

        clock.advance(Duration.ofMinutes(30));

        assertThat(service.findPendingSuggestions("session-a")).isEmpty();
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
