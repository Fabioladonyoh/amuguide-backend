package com.amuguide.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContactRequestDTO {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    private String prenom;

    private String telephone;

    @Email(message = "Format email invalide")
    @NotBlank(message = "L'email est obligatoire")
    private String email;

    @NotBlank(message = "Le sujet est obligatoire")
    private String sujet;

    @NotBlank(message = "Le message est obligatoire")
    private String message;
}
