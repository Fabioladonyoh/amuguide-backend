package com.amuguide.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicationDTO {
    private Long id;
    @NotBlank(message = "Le code du medicament est obligatoire")
    private String code;
    @NotBlank(message = "Le nom du medicament est obligatoire")
    private String nom;
    private String dci;
    private String dosage;
    private String formePharmaceutique;
    private String categorie;
    private Boolean prisEnCharge;
    @Min(value = 0, message = "Le taux de couverture doit etre au moins egal a 0")
    @Max(value = 100, message = "Le taux de couverture doit etre au plus egal a 100")
    private Double tauxCouverture;
    private String conditions;
    private Boolean actif;
    private String statut;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
