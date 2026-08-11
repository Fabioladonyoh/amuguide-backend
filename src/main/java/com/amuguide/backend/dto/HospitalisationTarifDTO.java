package com.amuguide.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalisationTarifDTO {
    private Long id;
    private String categorie;
    private String chambre;
    private String population;
    private String typePrestataire;
    private LocalDate dateDebut;
    private Double tauxRemboursement;
    private String premiereSemaine;
    private String deuxiemeSemaine;
    private String aPartirTroisiemeSemaine;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
