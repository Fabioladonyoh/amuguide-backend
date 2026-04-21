package com.amuguide.backend.dto;

import com.amuguide.backend.enums.StatutDemande;
import com.amuguide.backend.enums.TypeDemande;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter

public class DemandeResponseDTO {
    private Long idDemande;
    private TypeDemande typeDemande;
    private LocalDateTime dateDemande;
    private StatutDemande statut;
    private String description;
    private String resultat;
}
