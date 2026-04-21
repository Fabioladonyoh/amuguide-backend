package com.amuguide.backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class VerificationResponseDTO {
    private String statut;
    private String message;
    private String codeActe;
    private String nomActe;
    private String categorie;
    private Boolean prisEnCharge;
    private Double tauxCouverture;
    private String conditionsPriseEnCharge;
    private String documentsRequis;
}