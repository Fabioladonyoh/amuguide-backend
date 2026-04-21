package com.amuguide.backend.dto;


import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class ChatbotResponseDTO {
    private String statut;
    private String message;
    private String codeActe;
    private String nomActe;
    private Boolean prisEnCharge;
    private Double tauxCouverture;
    private String conditionsPriseEnCharge;
    private String documentsRequis;
    private String suggestions;
}