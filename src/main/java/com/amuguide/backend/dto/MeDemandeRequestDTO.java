package com.amuguide.backend.dto;

import com.amuguide.backend.enums.TypeDemande;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MeDemandeRequestDTO {

    @NotNull(message = "Le type de demande est obligatoire")
    private TypeDemande typeDemande;

    @NotBlank(message = "La description est obligatoire")
    private String description;

    private Long prestationId;
}
