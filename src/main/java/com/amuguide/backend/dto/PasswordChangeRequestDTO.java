package com.amuguide.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordChangeRequestDTO {
    @NotBlank
    private String ancienMotDePasse;

    @NotBlank
    @Size(min = 6, max = 100)
    private String nouveauMotDePasse;

    @NotBlank
    private String confirmationMotDePasse;
}
