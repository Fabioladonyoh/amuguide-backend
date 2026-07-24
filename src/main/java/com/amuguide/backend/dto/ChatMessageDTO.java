package com.amuguide.backend.dto;

import com.amuguide.backend.chat.nlp.ChatIntent;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageDTO {
    private Long id;
    private String message;
    private String answer;
    private String sessionId;
    private ChatIntent intention;
    private LocalDateTime date;
}
