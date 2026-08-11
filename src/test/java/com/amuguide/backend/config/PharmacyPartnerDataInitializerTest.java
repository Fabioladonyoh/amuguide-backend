package com.amuguide.backend.config;

import com.amuguide.backend.entity.Pharmacie;
import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.TypeStructure;
import com.amuguide.backend.repository.PharmacieRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PharmacyPartnerDataInitializerTest {

    @Mock
    private PharmacieRepository pharmacieRepository;

    @Mock
    private StructureSanteRepository structureRepository;

    private final List<Pharmacie> pharmacies = new ArrayList<>();
    private final List<StructureSante> structures = new ArrayList<>();
    private final AtomicLong pharmacySequence = new AtomicLong(100);
    private final AtomicLong structureSequence = new AtomicLong(1);
    private PharmacyPartnerDataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new PharmacyPartnerDataInitializer(pharmacieRepository, structureRepository);
        lenient().when(pharmacieRepository.findAll()).thenReturn(pharmacies);
        lenient().when(pharmacieRepository.findByCode(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> findByCode(invocation.getArgument(0)));
        lenient().when(pharmacieRepository.save(any(Pharmacie.class)))
                .thenAnswer(invocation -> savePharmacie(invocation.getArgument(0)));
        lenient().when(structureRepository.findByType(TypeStructure.PHARMACIE)).thenReturn(structures);
        lenient().when(structureRepository.save(any(StructureSante.class)))
                .thenAnswer(invocation -> saveStructure(invocation.getArgument(0)));
    }

    @Test
    void importsPartnerPharmaciesInDedicatedTableAndMigratesOldStructuresWithoutDuplicates() {
        structures.add(StructureSante.builder()
                .idStructure(1L)
                .nom("Pharmacie St Pierre")
                .type(TypeStructure.PHARMACIE)
                .adresse("Ancienne adresse")
                .ville("Lome")
                .telephone("22 26 19 73")
                .latitude(6.1)
                .longitude(1.2)
                .agrementAMU(false)
                .actif(true)
                .build());

        initializer.run();
        initializer.run();

        assertThat(pharmacies).hasSize(51);
        assertThat(pharmacies).extracting(Pharmacie::getCode).doesNotHaveDuplicates();
        assertThat(structures).hasSize(1);
        assertThat(structures.get(0).getActif()).isFalse();

        Pharmacie bonPasteur = find("Pharmacie Bon Pasteur").orElseThrow();
        assertThat(bonPasteur.getTelephone()).isEqualTo("22 21 13 67");
        assertThat(bonPasteur.getLatitude()).isNull();
        assertThat(bonPasteur.getLongitude()).isNull();
        assertThat(bonPasteur.getAgreee()).isTrue();
        assertThat(bonPasteur.getActive()).isTrue();

        assertThat(find("Pharmacie El-Nissi")).isPresent();
        assertThat(find("Pharmacie Sika")).isPresent();
        assertThat(find("Pharmacie Saint-Pierre").orElseThrow().getAdresse()).contains("Sagboville");
        assertThat(find("Pharmacie La Prosperite").orElseThrow().getNotes()).isEqualTo("Donnee a verifier");
        assertThat(find("Pharmacie Baguida").orElseThrow().getTelephone()).isEqualTo("70 42 47 7");
    }

    private Optional<Pharmacie> find(String name) {
        return pharmacies.stream()
                .filter(pharmacie -> name.equals(pharmacie.getNom()))
                .findFirst();
    }

    private Optional<Pharmacie> findByCode(String code) {
        return pharmacies.stream()
                .filter(pharmacie -> code.equals(pharmacie.getCode()))
                .findFirst();
    }

    private Pharmacie savePharmacie(Pharmacie pharmacie) {
        if (pharmacie.getId() == null) {
            pharmacie.setId(pharmacySequence.incrementAndGet());
            pharmacies.add(pharmacie);
        }
        return pharmacie;
    }

    private StructureSante saveStructure(StructureSante structure) {
        if (structure.getIdStructure() == null) {
            structure.setIdStructure(structureSequence.incrementAndGet());
            structures.add(structure);
        }
        return structure;
    }
}
