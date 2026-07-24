package com.amuguide.backend.dto;


import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Getter
@Setter
public class ChatbotResponseDTO {
    private String answer;
    private String category;
    private Boolean found;
    private String source;
    private String sessionId;
    private Double confidence;
    private List<ChatbotSourceDTO> sources;
    private LocalDateTime timestamp;

    private String statut;
    private String intent;
    private String message;
    private String codeActe;
    private String nomActe;
    private Boolean prisEnCharge;
    private Double tauxCouverture;
    private String conditionsPriseEnCharge;
    private String documentsRequis;
    private String suggestions;
    private List<String> suggestionList;
}
