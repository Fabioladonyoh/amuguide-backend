package com.amuguide.backend.chat.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatHistorySchemaUpdater {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void updateChatHistoryIntentConstraint() {
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE chat_history
                    DROP CONSTRAINT IF EXISTS chat_history_intention_check
                    """);

            jdbcTemplate.execute("""
                    ALTER TABLE chat_history
                    ADD CONSTRAINT chat_history_intention_check
                    CHECK (intention IN (
                        'GREETING',
                        'AMU_INFO',
                        'COVERAGE',
                        'MEDICATION_SEARCH',
                        'HOSPITAL_SEARCH',
                        'PROCEDURE',
                        'AGENT_ASSISTED',
                        'FALLBACK'
                    ))
                    """);
        } catch (RuntimeException ex) {
            log.warn("Impossible de mettre a jour la contrainte chat_history_intention_check", ex);
        }
    }
}
