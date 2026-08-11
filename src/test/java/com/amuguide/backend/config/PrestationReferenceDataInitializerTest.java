package com.amuguide.backend.config;

import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.enums.CategorieActe;
import com.amuguide.backend.repository.PrestationRepository;
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
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PrestationReferenceDataInitializerTest {

    @Mock
    private PrestationRepository prestationRepository;

    private final List<Prestation> prestations = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(10);
    private PrestationReferenceDataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new PrestationReferenceDataInitializer(prestationRepository);
        lenient().when(prestationRepository.findAll()).thenReturn(prestations);
        lenient().when(prestationRepository.findByCodeActe(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> findByCode(invocation.getArgument(0)));
        lenient().when(prestationRepository.countByPrisEnChargeTrue())
                .thenAnswer(invocation -> prestations.stream().filter(p -> Boolean.TRUE.equals(p.getPrisEnCharge())).count());
        lenient().when(prestationRepository.save(org.mockito.ArgumentMatchers.any(Prestation.class)))
                .thenAnswer(invocation -> save(invocation.getArgument(0)));
    }

    @Test
    void updatesOfficialPrestationsWithoutDuplicatesAfterTwoRuns() {
        prestations.add(prestation(1L, "CONS001", "Consultation generale", 50.0, true));
        prestations.add(prestation(2L, "ECHO_OLD", "Echographie", 40.0, true));
        prestations.add(prestation(3L, "OLD_ACTIVE", "Ancienne prestation", 30.0, true));

        initializer.run();
        initializer.run();

        assertThat(prestations).hasSize(19);
        assertThat(prestations).filteredOn(p -> Boolean.TRUE.equals(p.getPrisEnCharge())).hasSize(18);
        assertThat(prestations).extracting(Prestation::getCodeActe).doesNotHaveDuplicates();

        assertCoverage("CONSULTATION_GENERALE", 80.0);
        assertCoverage("CONSULTATION_ENFANT_MOINS_5_ANS", 100.0);
        assertCoverage("CONSULTATION_SPECIALITE", 80.0);
        assertCoverage("HOSPITALISATION_SEJOUR", 90.0);
        assertCoverage("INTERVENTION_CHIRURGICALE", 90.0);
        assertCoverage("ACCOUCHEMENT_SIMPLE", 100.0);
        assertCoverage("ACCOUCHEMENT_COMPLIQUE", 100.0);
        assertCoverage("CESARIENNE", 100.0);
        assertCoverage("ECHOGRAPHIE", 80.0);
        assertCoverage("RADIOLOGIE", 80.0);
        assertCoverage("MEDICAMENTS", 80.0);
        assertCoverage("CONSULTATION_PRENATALE", 80.0);

        Prestation old = findByCode("OLD_ACTIVE").orElseThrow();
        assertThat(old.getPrisEnCharge()).isFalse();
    }

    private void assertCoverage(String codeActe, Double tauxCouverture) {
        Prestation prestation = findByCode(codeActe).orElseThrow();
        assertThat(prestation.getTauxCouverture()).isEqualTo(tauxCouverture);
        assertThat(prestation.getPrisEnCharge()).isTrue();
    }

    private Optional<Prestation> findByCode(String codeActe) {
        return prestations.stream()
                .filter(prestation -> codeActe.equals(prestation.getCodeActe()))
                .findFirst();
    }

    private Prestation save(Prestation prestation) {
        if (prestation.getIdPrestation() == null) {
            prestation.setIdPrestation(sequence.incrementAndGet());
            prestations.add(prestation);
        }
        return prestation;
    }

    private Prestation prestation(Long id, String codeActe, String nomActe, Double tauxCouverture, Boolean prisEnCharge) {
        return Prestation.builder()
                .idPrestation(id)
                .codeActe(codeActe)
                .nomActe(nomActe)
                .categorie(CategorieActe.CONSULTATION)
                .description("ancienne description")
                .conditionsPriseEnCharge("ancienne condition")
                .documentsRequis("ancien document")
                .tauxCouverture(tauxCouverture)
                .prisEnCharge(prisEnCharge)
                .build();
    }
}
