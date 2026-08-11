package com.amuguide.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PharmacieDTO {
    private Long id;
    private String code;
    @NotBlank
    private String nom;
    private String telephone;
    private String adresse;
    private String quartier;
    private String ville;
    private String region;
    private String email;
    private Double latitude;
    private Double longitude;
    private Boolean agreee;
    private Boolean active;
    private String notes;
    private Double distanceKm;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
