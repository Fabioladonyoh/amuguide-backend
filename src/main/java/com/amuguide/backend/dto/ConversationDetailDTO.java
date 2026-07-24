package com.amuguide.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ConversationDetailDTO {
    private Long id;
    private String sessionId;
    private String titre;
    private LocalDateTime dateCreation;
    private LocalDateTime dateDernierMessage;
    private long nombreMessages;
    private List<ChatMessageDTO> messages;
}
