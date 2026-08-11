package com.amuguide.backend.chat.service;

import com.amuguide.backend.chat.nlp.ChatIntent;
import com.amuguide.backend.chat.nlp.IntentDetectionResult;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.entity.HospitalisationTarif;
import com.amuguide.backend.service.HospitalisationTarifService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HospitalisationTarifChatbotServiceTest {

    @Mock
    private HospitalisationTarifService hospitalisationTarifService;

    @ParameterizedTest
    @ValueSource(strings = {"CHU", "CHR", "HOPITAL DE DISTRICT", "NIVEAU DE SOINS 4"})
    void filtersByOfficialProviderType(String typePrestataire) {
        HospitalisationTarifChatbotService service = new HospitalisationTarifChatbotService(hospitalisationTarifService);
        HospitalisationTarif tarif = tarif(typePrestataire, 90.0, "3000.0", "2000.0", "1000.0");
        String question = "quel est le taux de prise en charge d'une hospitalisation en " + typePrestataire.toLowerCase();

        when(hospitalisationTarifService.searchForChatbot(
                eq("CABINE D'HOSPITALISATION"), eq(null), eq(null), eq(typePrestataire)))
                .thenReturn(List.of(tarif));

        ChatbotResponseDTO response = service.answer(detection(question));

        assertThat(response.getFound()).isTrue();
        assertThat(response.getMessage()).contains("Taux remboursement : 90%");
        assertThat(response.getSources()).extracting("type").containsExactly("HOSPITALISATION_TARIF_POSTGRESQL");
    }

    @Test
    void answersHundredPercentRateFromPostgres() {
        HospitalisationTarifChatbotService service = new HospitalisationTarifChatbotService(hospitalisationTarifService);
        HospitalisationTarif tarif = tarif("CHU", 100.0, "2500.0", "1250.0", "875.0");

        when(hospitalisationTarifService.searchForChatbot(eq("CABINE D'HOSPITALISATION"), eq(null), eq("SCHOOL AMU"), eq("CHU")))
                .thenReturn(List.of(tarif));

        ChatbotResponseDTO response = service.answer(detection("quel est le taux de prise en charge d'une hospitalisation en chu pour school amu"));

        assertThat(response.getMessage()).contains("Taux remboursement : 100%");
    }

    @Test
    void reportsExclusWithoutConvertingToZero() {
        HospitalisationTarifChatbotService service = new HospitalisationTarifChatbotService(hospitalisationTarifService);
        HospitalisationTarif tarif = tarif("CHR", 90.0, "3750.0", "Exclus", "Exclus");

        when(hospitalisationTarifService.searchForChatbot(eq("CABINE D'HOSPITALISATION"), eq(null), eq(null), eq("CHR")))
                .thenReturn(List.of(tarif));

        ChatbotResponseDTO response = service.answer(detection("hospitalisation en chr deuxieme semaine"));

        assertThat(response.getMessage()).contains("Cette prise en charge est indiquee comme exclue");
        assertThat(response.getMessage()).doesNotContain("0.0");
        assertThat(response.getMessage()).doesNotContain("0 FCFA");
    }

    @Test
    void asksForPrecisionWhenSeveralRowsMatch() {
        HospitalisationTarifChatbotService service = new HospitalisationTarifChatbotService(hospitalisationTarifService);
        when(hospitalisationTarifService.searchForChatbot(eq("CABINE D'HOSPITALISATION"), eq(null), eq(null), eq("CHU")))
                .thenReturn(List.of(
                        tarif("CHU", 90.0, "3000.0", "2000.0", "1000.0"),
                        tarif("CHU", 100.0, "2500.0", "1250.0", "875.0")
                ));

        ChatbotResponseDTO response = service.answer(detection("quel est le tarif d'hospitalisation en chu"));

        assertThat(response.getFound()).isFalse();
        assertThat(response.getMessage()).contains("plusieurs tarifs d'hospitalisation possibles");
        assertThat(response.getMessage()).contains("Veuillez preciser");
    }

    @Test
    void returnsNotFoundWhenNoOfficialRowMatches() {
        HospitalisationTarifChatbotService service = new HospitalisationTarifChatbotService(hospitalisationTarifService);
        when(hospitalisationTarifService.searchForChatbot(eq("CABINE D'HOSPITALISATION"), eq(null), eq(null), eq("CHU")))
                .thenReturn(List.of());

        ChatbotResponseDTO response = service.answer(detection("quel est le tarif d'hospitalisation en chu"));

        assertThat(response.getFound()).isFalse();
        assertThat(response.getMessage()).contains("Je n'ai pas trouve de tarif d'hospitalisation");
    }

    @Test
    void detectsOnlySpecificHospitalisationTarifQuestions() {
        HospitalisationTarifChatbotService service = new HospitalisationTarifChatbotService(hospitalisationTarifService);

        assertThat(service.isHospitalisationTarifQuestion(detection("quel est le taux de prise en charge d'une hospitalisation en chu"))).isTrue();
        assertThat(service.isHospitalisationTarifQuestion(detection("quelles sont les prestations couvertes"))).isFalse();
        assertThat(service.isHospitalisationTarifQuestion(detection("quel est le taux pour l'hospitalisation"))).isFalse();
    }

    private IntentDetectionResult detection(String normalizedMessage) {
        return IntentDetectionResult.builder()
                .intent(ChatIntent.COVERAGE)
                .normalizedMessage(normalizedMessage)
                .keyword("hospitalisation")
                .score(0.95)
                .build();
    }

    private HospitalisationTarif tarif(String typePrestataire, Double taux, String premiere, String deuxieme, String troisieme) {
        return HospitalisationTarif.builder()
                .id((long) typePrestataire.hashCode())
                .categorie("CABINE D'HOSPITALISATION")
                .chambre("CABINE CLIMATISEE AVEC 1 LIT")
                .population(taux == 100.0 ? "SCHOOL AMU" : "Toute autre population")
                .typePrestataire(typePrestataire)
                .dateDebut(LocalDate.of(2024, 12, 9))
                .tauxRemboursement(taux)
                .premiereSemaine(premiere)
                .deuxiemeSemaine(deuxieme)
                .aPartirTroisiemeSemaine(troisieme)
                .build();
    }
}
