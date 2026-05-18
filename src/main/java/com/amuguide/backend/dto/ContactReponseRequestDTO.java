package com.amuguide.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContactReponseRequestDTO {

    @NotBlank(message = "La réponse ne peut pas être vide")
    private String reponse;
}
