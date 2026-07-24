package com.amuguide.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusUpdateRequestDTO {
    @NotNull
    private Boolean actif;
}
