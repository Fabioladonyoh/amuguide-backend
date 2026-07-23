package com.amuguide.backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDTO {

    private String token;
    private String type;
    private String role;
    private Long utilisateurId;
    private String identifiant;
    private String nom;
    private String prenom;
    private String email;
    private String statut;
}
