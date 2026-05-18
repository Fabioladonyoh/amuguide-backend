package com.amuguide.backend.dto;

import com.amuguide.backend.enums.StatutAssure;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssureProfileDTO {

    private Long idAssure;
    private String nom;
    private String prenom;
    private String numeroAMU;
    private LocalDate dateNaissance;
    private String telephone;
    private String email;
    private String adresse;
    private StatutAssure statut;
}
