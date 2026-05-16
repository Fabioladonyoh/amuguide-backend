package com.amuguide.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminLoginRequestDTO {

    @NotBlank(message = "Le login est obligatoire")
    private String login;

    @NotBlank(message = "Le mot de passe est obligatoire")
    private String motDePasse;
}
