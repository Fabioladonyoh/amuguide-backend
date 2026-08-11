package com.amuguide.backend.chat.service;

import com.amuguide.backend.chat.nlp.ChatIntent;
import com.amuguide.backend.chat.nlp.IntentDetectionResult;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.enums.CategorieActe;
import com.amuguide.backend.repository.PharmacieRepository;
import com.amuguide.backend.repository.PrestationRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponseGeneratorPrestationCoverageTest {

    @Mock
    private PrestationRepository prestationRepository;

    @Mock
    private StructureSanteRepository structureSanteRepository;

    @Mock
    private PharmacieRepository pharmacieRepository;

    @Mock
    private MedicationKnowledgeService medicationKnowledgeService;

    @Mock
    private MedicamentChatbotSearchService medicamentChatbotSearchService;

    @Mock
    private HospitalisationTarifChatbotService hospitalisationTarifChatbotService;

    @Mock
    private AiAgentService aiAgentService;

    private ResponseGenerator responseGenerator;
    private List<Prestation> prestations;

    @BeforeEach
    void setUp() {
        responseGenerator = new ResponseGenerator(
                prestationRepository,
                structureSanteRepository,
                pharmacieRepository,
                medicationKnowledgeService,
                medicamentChatbotSearchService,
                hospitalisationTarifChatbotService,
                aiAgentService
        );
        prestations = List.of(
                prestation("CONSULTATION_GENERALE", "Consultation générale", CategorieActe.CONSULTATION, 80.0),
                prestation("HOSPITALISATION_SEJOUR", "Hospitalisation (frais de séjour)", CategorieActe.HOSPITALISATION, 90.0),
                prestation("CESARIENNE", "Césarienne (acte)", CategorieActe.MATERNITE, 100.0),
                prestation("MEDICAMENTS", "Médicaments", CategorieActe.MEDICAMENT, 80.0)
        );

        lenient().when(prestationRepository.findAll()).thenReturn(prestations);
        lenient().when(prestationRepository.findByPrisEnChargeTrue()).thenReturn(prestations);
        lenient().when(prestationRepository.findByNomActeContainingIgnoreCase(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    String keyword = normalize(invocation.getArgument(0));
                    return prestations.stream()
                            .filter(prestation -> normalize(prestation.getNomActe()).contains(keyword))
                            .toList();
                });
    }

    @Test
    void answersConsultationHospitalisationAndCesarienneFromRepositoryRates() {
        assertCoverage("Quel est le taux de prise en charge de la consultation générale ?", "consultation", "CONSULTATION_GENERALE", 80.0);
        assertCoverage("Quel est le taux pour l'hospitalisation ?", "hospitalisation", "HOSPITALISATION_SEJOUR", 90.0);
        assertCoverage("La césarienne est-elle prise en charge ?", "cesarienne", "CESARIENNE", 100.0);
    }

    @Test
    void routesSpecificHospitalisationTarifQuestionToDedicatedReference() {
        IntentDetectionResult detection = IntentDetectionResult.builder()
                .intent(ChatIntent.COVERAGE)
                .normalizedMessage(normalize("Quel est le taux de prise en charge d'une hospitalisation en CHU ?"))
                .keyword("hospitalisation")
                .score(0.95)
                .build();
        ChatbotResponseDTO expected = ChatbotResponseDTO.builder()
                .intent(ChatIntent.COVERAGE.name())
                .found(false)
                .message("Veuillez preciser le type prestataire, la chambre et la population.")
                .build();

        when(hospitalisationTarifChatbotService.isHospitalisationTarifQuestion(detection)).thenReturn(true);
        when(hospitalisationTarifChatbotService.answer(detection)).thenReturn(expected);

        ChatbotResponseDTO response = responseGenerator.generate(detection);

        assertThat(response).isSameAs(expected);
        verify(prestationRepository, never()).findByNomActeContainingIgnoreCase("hospitalisation");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Quelles sont les prestations couvertes ?",
            "Quelles prestations sont prises en charge ?",
            "Que couvre l'AMU ?",
            "Quels soins sont couverts ?",
            "Qu'est-ce que l'AMU prend en charge ?",
            "Donne-moi la liste des prestations.",
            "Je veux connaître les prestations couvertes."
    })
    void answersGeneralCoverageQuestionsWithCoveredPrestationsList(String question) {
        ChatbotResponseDTO response = responseGenerator.generate(IntentDetectionResult.builder()
                .intent(ChatIntent.COVERAGE)
                .keyword(null)
                .normalizedMessage(normalize(question))
                .score(0.95)
                .build());

        assertThat(response.getFound()).isTrue();
        assertThat(response.getCodeActe()).isNull();
        assertThat(response.getNomActe()).isNull();
        assertThat(response.getTauxCouverture()).isNull();
        assertThat(response.getMessage()).contains("Les prestations couvertes disponibles");
        assertThat(response.getMessage()).contains("Consultation générale");
        assertThat(response.getMessage()).contains("Hospitalisation (frais de séjour)");
        assertThat(response.getMessage()).contains("Médicaments");
        assertThat(response.getMessage()).doesNotContain("Médicaments est prise en charge");
        assertThat(response.getSources()).hasSize(4);
        assertThat(response.getSources()).extracting("title")
                .contains("Consultation générale", "Hospitalisation (frais de séjour)", "Médicaments");
    }

    @Test
    void keepsSpecificMedicationPrestationQuestionSpecific() {
        ChatbotResponseDTO response = responseGenerator.generate(IntentDetectionResult.builder()
                .intent(ChatIntent.COVERAGE)
                .keyword("medicament")
                .normalizedMessage(normalize("Les médicaments sont-ils pris en charge ?"))
                .score(0.95)
                .build());

        assertThat(response.getFound()).isTrue();
        assertThat(response.getCodeActe()).isEqualTo("MEDICAMENTS");
        assertThat(response.getNomActe()).isEqualTo("Médicaments");
        assertThat(response.getMessage()).contains("Médicaments est prise en charge").contains("80%");
        assertThat(response.getSources()).hasSize(1);
    }

    @Test
    void keepsSpecificPrestationRateQuestionSpecific() {
        ChatbotResponseDTO response = responseGenerator.generate(IntentDetectionResult.builder()
                .intent(ChatIntent.COVERAGE)
                .keyword("medicament")
                .normalizedMessage(normalize("Quel est le taux de prise en charge des médicaments ?"))
                .score(0.95)
                .build());

        assertThat(response.getFound()).isTrue();
        assertThat(response.getCodeActe()).isEqualTo("MEDICAMENTS");
        assertThat(response.getTauxCouverture()).isEqualTo(80.0);
        assertThat(response.getMessage()).contains("80%");
    }

    @Test
    void answersGeneralCoverageWithNoCoveredPrestationAvailable() {
        when(prestationRepository.findByPrisEnChargeTrue()).thenReturn(List.of());

        ChatbotResponseDTO response = responseGenerator.generate(IntentDetectionResult.builder()
                .intent(ChatIntent.COVERAGE)
                .normalizedMessage(normalize("Quelles sont les prestations couvertes ?"))
                .score(0.95)
                .build());

        assertThat(response.getFound()).isFalse();
        assertThat(response.getMessage()).contains("pas trouve de prestation couverte");
        assertThat(response.getSources()).extracting("type").containsExactly("PRESTATION");
    }

    @Test
    void handlesNullRateForSpecificPrestationWithoutInventingRate() {
        Prestation nullRate = prestation("SOINS_TEST", "Soins test", CategorieActe.CONSULTATION, null);
        when(prestationRepository.findByNomActeContainingIgnoreCase("soins test")).thenReturn(List.of(nullRate));

        ChatbotResponseDTO response = responseGenerator.generate(IntentDetectionResult.builder()
                .intent(ChatIntent.COVERAGE)
                .keyword("soins test")
                .normalizedMessage(normalize("Les soins test sont-ils pris en charge ?"))
                .score(0.95)
                .build());

        assertThat(response.getFound()).isTrue();
        assertThat(response.getTauxCouverture()).isNull();
        assertThat(response.getMessage()).isEqualTo("Oui. Soins test est prise en charge.");
    }

    private void assertCoverage(String question, String keyword, String expectedCode, Double expectedRate) {
        ChatbotResponseDTO response = responseGenerator.generate(IntentDetectionResult.builder()
                .intent(ChatIntent.COVERAGE)
                .keyword(keyword)
                .normalizedMessage(normalize(question))
                .score(0.95)
                .build());

        assertThat(response.getFound()).isTrue();
        assertThat(response.getCodeActe()).isEqualTo(expectedCode);
        assertThat(response.getTauxCouverture()).isEqualTo(expectedRate);
        assertThat(response.getMessage()).contains(String.format(Locale.FRANCE, "%.0f%%", expectedRate));
    }

    private Prestation prestation(String codeActe, String nomActe, CategorieActe categorie, Double tauxCouverture) {
        return Prestation.builder()
                .idPrestation((long) codeActe.hashCode())
                .codeActe(codeActe)
                .nomActe(nomActe)
                .categorie(categorie)
                .description("")
                .conditionsPriseEnCharge("")
                .documentsRequis("")
                .tauxCouverture(tauxCouverture)
                .prisEnCharge(true)
                .build();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replaceAll("[^a-z0-9'\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
