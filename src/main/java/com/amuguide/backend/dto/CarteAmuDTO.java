package com.amuguide.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CarteAmuDTO {
    private String numeroAmu;
    private String titulaire;
    private String statut;
    private LocalDateTime dateEmission;
    private LocalDateTime dateExpiration;
}
