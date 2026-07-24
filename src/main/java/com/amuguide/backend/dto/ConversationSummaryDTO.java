package com.amuguide.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationSummaryDTO {
    private Long id;
    private String sessionId;
    private String titre;
    private LocalDateTime dateCreation;
    private LocalDateTime dateDernierMessage;
    private long nombreMessages;
}
