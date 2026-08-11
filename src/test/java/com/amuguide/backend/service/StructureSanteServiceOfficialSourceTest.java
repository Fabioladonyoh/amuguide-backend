package com.amuguide.backend.service;

import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.TypeStructure;
import com.amuguide.backend.repository.StructureSanteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StructureSanteServiceOfficialSourceTest {

    @Mock
    private StructureSanteRepository structureSanteRepository;

    @Test
    void publicStructuresExcludeRowsWithoutOfficialCode() {
        StructureSanteService service = new StructureSanteService(structureSanteRepository);
        StructureSante officialHospital = structure(1L, "ST001", "CHU OFFICIEL", TypeStructure.HOPITAL);
        StructureSante legacyHospital = structure(2L, null, "ANCIEN HOPITAL", TypeStructure.HOPITAL);

        when(structureSanteRepository.findByAgrementAMUTrue()).thenReturn(List.of(officialHospital, legacyHospital));

        List<StructureSante> result = service.getStructuresAgrees();

        assertThat(result).extracting(StructureSante::getNom).containsExactly("CHU OFFICIEL");
    }

    @Test
    void publicStructuresDoNotExposePharmacyTypeThroughHealthStructureService() {
        StructureSanteService service = new StructureSanteService(structureSanteRepository);
        StructureSante officialHospital = structure(1L, "ST001", "CHU OFFICIEL", TypeStructure.HOPITAL);
        StructureSante officialPharmacy = structure(2L, "PH001", "PHARMACIE OFFICIELLE", TypeStructure.PHARMACIE);

        when(structureSanteRepository.findByAgrementAMUTrue()).thenReturn(List.of(officialHospital, officialPharmacy));

        List<StructureSante> result = service.getStructuresAgrees();

        assertThat(result).extracting(StructureSante::getNom).containsExactly("CHU OFFICIEL");
    }

    private StructureSante structure(Long id, String code, String nom, TypeStructure type) {
        return StructureSante.builder()
                .idStructure(id)
                .code(code)
                .nom(nom)
                .type(type)
                .agrementAMU(true)
                .actif(true)
                .build();
    }
}
