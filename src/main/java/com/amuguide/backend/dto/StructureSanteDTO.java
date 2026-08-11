package com.amuguide.backend.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class StructureSanteDTO {
    private Long idStructure;
    @NotBlank
    private String nom;
    @NotBlank
    private String type;
    @NotBlank
    private String adresse;
    @NotBlank
    private String ville;
    private String region;
    @NotBlank
    private String telephone;
    private String email;
    private Double latitude;
    private Double longitude;
    @NotNull
    private Boolean agrementAMU;
    private Boolean actif;
    private String specialites;
    private String horaires;
    private Double distanceKm;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
