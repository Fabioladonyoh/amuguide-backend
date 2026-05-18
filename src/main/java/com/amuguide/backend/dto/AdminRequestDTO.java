package com.amuguide.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminRequestDTO {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    private String prenom;

    @Email(message = "Format email invalide")
    @NotBlank(message = "L'email est obligatoire")
    private String email;

    @NotBlank(message = "Le login est obligatoire")
    private String login;

    /** Obligatoire à la création, optionnel à la mise à jour (null = conserver l'existant) */
    private String motDePasse;

    private Boolean actif;
}
