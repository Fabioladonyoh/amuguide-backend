package com.amuguide.backend.dto;

import com.amuguide.backend.enums.FaqChatbotCategorie;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaqChatbotDTO {
    private Long id;

    @NotBlank(message = "Le code de la FAQ est obligatoire")
    private String code;

    @NotBlank(message = "La question est obligatoire")
    private String question;

    @NotBlank(message = "La reponse est obligatoire")
    private String reponse;

    @NotNull(message = "La categorie est obligatoire")
    private FaqChatbotCategorie categorie;

    private String motsCles;

    @PositiveOrZero(message = "La priorite doit etre positive ou egale a zero")
    private Integer priorite;

    private Boolean actif;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
