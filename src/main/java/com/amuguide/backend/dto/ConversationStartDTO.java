package com.amuguide.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationStartDTO {
    private Long id;
    private String sessionId;
    private String message;
}
