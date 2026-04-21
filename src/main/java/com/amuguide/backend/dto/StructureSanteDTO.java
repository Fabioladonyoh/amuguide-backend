package com.amuguide.backend.dto;


import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class StructureSanteDTO {
    private Long idStructure;
    private String nom;
    private String type;
    private String adresse;
    private String ville;
    private String telephone;
    private Double latitude;
    private Double longitude;
    private Boolean agrementAMU;
    private String specialites;
    private String horaires;
}