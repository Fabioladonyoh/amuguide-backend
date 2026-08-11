package com.amuguide.backend.chat.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class PrestationSessionContextServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-09T12:00:00Z"));
    private final PrestationSessionContextService service = new PrestationSessionContextService(
            clock,
            Duration.ofMinutes(30),
            Duration.ZERO
    );

    @Test
    void ignoresInvalidSessionIdsAndNullPrestationId() {
        service.rememberPrestation(null, 1L);
        service.rememberPrestation("", 1L);
        service.rememberPrestation("session-a", null);

        assertThat(service.findLastPrestationId(null)).isEmpty();
        assertThat(service.findLastPrestationId("")).isEmpty();
        assertThat(service.findLastPrestationId("session-a")).isEmpty();
    }

    @Test
    void keepsDifferentSessionsIsolated() {
        service.rememberPrestation("session-a", 10L);
        service.rememberPrestation("session-b", 20L);

        assertThat(service.findLastPrestationId("session-a")).contains(10L);
        assertThat(service.findLastPrestationId("session-b")).contains(20L);
    }

    @Test
    void clearRemovesOnlyRequestedSession() {
        service.rememberPrestation("session-a", 10L);
        service.rememberPrestation("session-b", 20L);

        service.clear("session-a");

        assertThat(service.findLastPrestationId("session-a")).isEmpty();
        assertThat(service.findLastPrestationId("session-b")).contains(20L);
    }

    @Test
    void expiresInactiveSession() {
        service.rememberPrestation("session-a", 10L);

        clock.advance(Duration.ofMinutes(30));

        assertThat(service.findLastPrestationId("session-a")).isEmpty();
    }

    @Test
    void refreshesLastAccessTimeWhenContextIsRead() {
        service.rememberPrestation("session-a", 10L);

        clock.advance(Duration.ofMinutes(20));
        assertThat(service.findLastPrestationId("session-a")).contains(10L);

        clock.advance(Duration.ofMinutes(20));
        assertThat(service.findLastPrestationId("session-a")).contains(10L);
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
