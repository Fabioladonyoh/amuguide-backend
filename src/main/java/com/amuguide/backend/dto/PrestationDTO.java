package com.amuguide.backend.dto;


import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class PrestationDTO {
    private Long idPrestation;
    @NotBlank
    private String codeActe;
    @NotBlank
    private String nomActe;
    @NotBlank
    private String categorie;
    private String description;
    @NotNull
    private Boolean prisEnCharge;
    @NotNull
    @Min(0)
    @Max(100)
    private Double tauxCouverture;
    private String conditionsPriseEnCharge;
    private String documentsRequis;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
