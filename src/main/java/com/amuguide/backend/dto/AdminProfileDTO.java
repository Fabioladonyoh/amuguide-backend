package com.amuguide.backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminProfileDTO {

    private Long idAdmin;
    private String nom;
    private String prenom;
    private String email;
    private String login;
    private Boolean actif;
}
