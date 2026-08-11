package com.amuguide.backend.service;

import com.amuguide.backend.dto.PharmacieDTO;
import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.TypeStructure;
import com.amuguide.backend.repository.PharmacieRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacieServiceOfficialSourceTest {

    @Mock
    private PharmacieRepository pharmacieRepository;

    @Mock
    private StructureSanteRepository structureSanteRepository;

    @Test
    void accessiblePharmaciesComeFromOfficialStructureSanteRows() {
        PharmacieService service = new PharmacieService(pharmacieRepository, structureSanteRepository);
        StructureSante pharmacie = officialStructure(1L, "PH001", "PHARMACIE CENTRALE", "PHARMACIE", "LOME", "Avenue principale");
        StructureSante depot = officialStructure(2L, "DP001", "DEPOT PHARMACIE AGOE", "DEPOT PHARMACIE", "GRAND LOME", null);
        StructureSante legacyOnly = StructureSante.builder()
                .idStructure(3L)
                .nom("PHARMACIE HISTORIQUE")
                .type(TypeStructure.PHARMACIE)
                .typeOfficiel("PHARMACIE")
                .agrementAMU(true)
                .actif(true)
                .build();

        when(structureSanteRepository.findOfficialPharmacies(any(Sort.class))).thenReturn(List.of(pharmacie, depot, legacyOnly));

        Page<PharmacieDTO> result = service.listAccessible(null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(PharmacieDTO::getNom)
                .containsExactly("PHARMACIE CENTRALE", "DEPOT PHARMACIE AGOE");
        assertThat(result.getContent()).extracting(PharmacieDTO::getCode)
                .containsExactly("PH001", "DP001");
        assertThat(result.getTotalElements()).isEqualTo(2);
        verifyNoInteractions(pharmacieRepository);
    }

    @Test
    void accessiblePharmacyDtoDoesNotInventMissingOfficialFields() {
        PharmacieService service = new PharmacieService(pharmacieRepository, structureSanteRepository);
        StructureSante depot = officialStructure(2L, "DP001", "DEPOT PHARMACIE AGOE", "DEPOT PHARMACEUTIQUE", "GRAND LOME", null);

        when(structureSanteRepository.findById(2L)).thenReturn(Optional.of(depot));

        PharmacieDTO dto = service.getAccessible(2L);

        assertThat(dto.getTelephone()).isNull();
        assertThat(dto.getEmail()).isNull();
        assertThat(dto.getVille()).isNull();
        assertThat(dto.getQuartier()).isNull();
        assertThat(dto.getAdresse()).isNull();
        assertThat(dto.getNotes()).isEqualTo("DEPOT PHARMACEUTIQUE");
        assertThat(dto.getAgreee()).isTrue();
        assertThat(dto.getActive()).isTrue();
        verifyNoInteractions(pharmacieRepository);
    }

    private StructureSante officialStructure(Long id, String code, String nom, String typeOfficiel, String region, String adresse) {
        return StructureSante.builder()
                .idStructure(id)
                .code(code)
                .nom(nom)
                .type(TypeStructure.PHARMACIE)
                .typeOfficiel(typeOfficiel)
                .region(region)
                .adresse(adresse)
                .agrementAMU(true)
                .actif(false)
                .build();
    }
}
