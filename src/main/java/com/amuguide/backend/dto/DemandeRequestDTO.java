package com.amuguide.backend.dto;


import com.amuguide.backend.enums.TypeDemande;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class DemandeRequestDTO {
    private TypeDemande typeDemande;
    private String description;
    private Long assureId;
    private Long prestationId;
}