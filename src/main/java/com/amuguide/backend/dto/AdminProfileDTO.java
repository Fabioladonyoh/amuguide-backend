package com.amuguide.backend.dto;

import lombok.*;

import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
