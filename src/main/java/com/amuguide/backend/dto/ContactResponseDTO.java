package com.amuguide.backend.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactResponseDTO {

    private Long id;
    private String nom;
    private String prenom;
    private String telephone;
    private String email;
    private String sujet;
    private String message;
    private LocalDateTime dateEnvoi;
    private Boolean traite;
    private String reponse;
    private LocalDateTime dateReponse;
}
