package com.amuguide.backend.dto;

import com.amuguide.backend.enums.StatutAssure;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AssureAdminRequestDTO {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    private String prenom;

    @NotBlank(message = "Le numéro AMU est obligatoire")
    private String numeroAMU;

    @NotNull(message = "La date de naissance est obligatoire")
    private LocalDate dateNaissance;

    @NotBlank(message = "Le téléphone est obligatoire")
    private String telephone;

    @Email(message = "Format email invalide")
    @NotBlank(message = "L'email est obligatoire")
    private String email;

    @NotBlank(message = "L'adresse est obligatoire")
    private String adresse;

    private StatutAssure statut;

    /** Optionnel : si absent, le mot de passe par défaut sera dateNaissance au format ddMMyyyy */
    private String motDePasse;
}
