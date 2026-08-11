package com.amuguide.backend.chat.service;

import com.amuguide.backend.chat.service.StructureSessionContextService.StructureReference;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class StructureSessionContextServiceTest {

    @Test
    void remembersStructureReferenceBySessionAndIgnoresNullValues() {
        StructureSessionContextService service = new StructureSessionContextService();

        service.rememberStructure("session-a", new StructureReference("STRUCTURE", 1L));
        service.rememberStructure("session-b", null);
        service.rememberStructure("session-c", new StructureReference("PHARMACIE", null));

        assertThat(service.findLastStructureReference("session-a")).contains(new StructureReference("STRUCTURE", 1L));
        assertThat(service.findLastStructureReference("session-b")).isEmpty();
        assertThat(service.findLastStructureReference("session-c")).isEmpty();
    }

    @Test
    void clearsStructureContext() {
        StructureSessionContextService service = new StructureSessionContextService();
        service.rememberStructure("session-a", new StructureReference("PHARMACIE", 10L));

        service.clear("session-a");

        assertThat(service.findLastStructureReference("session-a")).isEmpty();
    }

    @Test
    void expiresStructureContextAfterConfiguredDuration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T10:00:00Z"));
        StructureSessionContextService service = new StructureSessionContextService(
                clock,
                Duration.ofMinutes(30),
                Duration.ZERO
        );
        service.rememberStructure("session-a", new StructureReference("STRUCTURE", 1L));

        clock.setInstant(Instant.parse("2026-08-09T10:31:00Z"));

        assertThat(service.findLastStructureReference("session-a")).isEmpty();
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
