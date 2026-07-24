package com.amuguide.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssureProfileUpdateDTO {
    @NotBlank
    private String nom;

    @NotBlank
    private String prenom;

    @NotBlank
    @Size(max = 20)
    private String telephone;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String adresse;
}
