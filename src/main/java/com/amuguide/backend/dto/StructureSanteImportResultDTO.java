package com.amuguide.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructureSanteImportResultDTO {

    private int lignesLues;
    private int ajoutees;
    private int misesAJour;
    private int ignorees;
    private int erreurs;

    @Builder.Default
    private List<String> detailsErreurs = new ArrayList<>();
}
