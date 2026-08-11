package com.amuguide.backend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatbotRequestDTOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsBlankMessage() {
        ChatbotRequestDTO request = ChatbotRequestDTO.builder()
                .message(" ")
                .build();

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("message"));
    }

    @Test
    void rejectsOversizedMessageAndSessionId() {
        ChatbotRequestDTO request = ChatbotRequestDTO.builder()
                .message("a".repeat(1001))
                .sessionId("s".repeat(121))
                .build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("message", "sessionId");
    }
}
