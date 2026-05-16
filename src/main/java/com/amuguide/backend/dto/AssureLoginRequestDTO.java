package com.amuguide.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssureLoginRequestDTO {

    @NotBlank(message = "Le numéro AMU est obligatoire")
    private String numeroAMU;

    @NotBlank(message = "Le mot de passe est obligatoire")
    private String motDePasse;
}
