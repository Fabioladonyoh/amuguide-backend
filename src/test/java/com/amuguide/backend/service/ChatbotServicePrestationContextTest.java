package com.amuguide.backend.service;

import com.amuguide.backend.chat.nlp.IntentDetector;
import com.amuguide.backend.chat.service.AiAgentService;
import com.amuguide.backend.chat.service.FaqChatbotService;
import com.amuguide.backend.chat.service.HospitalisationTarifChatbotService;
import com.amuguide.backend.chat.service.MedicamentChatbotSearchService;
import com.amuguide.backend.chat.service.MedicationSessionContextService;
import com.amuguide.backend.chat.service.PrestationSessionContextService;
import com.amuguide.backend.chat.service.ResponseGenerator;
import com.amuguide.backend.chat.service.StructureSessionContextService;
import com.amuguide.backend.dto.ChatbotRequestDTO;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.entity.Medicament;
import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.enums.CategorieActe;
import com.amuguide.backend.repository.AssureAMURepository;
import com.amuguide.backend.repository.ChatHistoryRepository;
import com.amuguide.backend.repository.MedicamentRepository;
import com.amuguide.backend.repository.PharmacieRepository;
import com.amuguide.backend.repository.PrestationRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotServicePrestationContextTest {

    @Mock
    private PrestationRepository prestationRepository;

    @Mock
    private StructureSanteRepository structureSanteRepository;

    @Mock
    private PharmacieRepository pharmacieRepository;

    @Mock
    private com.amuguide.backend.chat.service.MedicationKnowledgeService medicationKnowledgeService;

    @Mock
    private AiAgentService aiAgentService;

    @Mock
    private MedicamentRepository medicamentRepository;

    @Mock
    private FaqChatbotService faqChatbotService;

    @Mock
    private ChatHistoryRepository chatHistoryRepository;

    @Mock
    private AssureAMURepository assureAMURepository;

    @Mock
    private HospitalisationTarifChatbotService hospitalisationTarifChatbotService;

    private ChatbotService chatbotService;
    private Prestation consultationGenerale;
    private Prestation consultationSpecialite;
    private Prestation hospitalisation;
    private Prestation medicaments;
    private Medicament doliprane;

    @BeforeEach
    void setUp() {
        MedicamentChatbotSearchService medicamentChatbotSearchService = new MedicamentChatbotSearchService(medicamentRepository);
        ResponseGenerator responseGenerator = new ResponseGenerator(
                prestationRepository,
                structureSanteRepository,
                pharmacieRepository,
                medicationKnowledgeService,
                medicamentChatbotSearchService,
                hospitalisationTarifChatbotService,
                aiAgentService
        );
        chatbotService = new ChatbotService(
                new IntentDetector(),
                faqChatbotService,
                responseGenerator,
                chatHistoryRepository,
                assureAMURepository,
                new MedicationSessionContextService(),
                medicamentChatbotSearchService,
                new PrestationSessionContextService(),
                new StructureSessionContextService()
        );

        consultationGenerale = prestation(10L, "CONSULTATION_GENERALE", "Consultation générale", CategorieActe.CONSULTATION, 80.0);
        consultationSpecialite = prestation(11L, "CONSULTATION_SPECIALITE", "Consultation spécialiste", CategorieActe.CONSULTATION, 70.0);
        hospitalisation = prestation(20L, "HOSPITALISATION_SEJOUR", "Hospitalisation", CategorieActe.HOSPITALISATION, 90.0);
        medicaments = prestation(30L, "MEDICAMENTS", "Médicaments", CategorieActe.MEDICAMENT, 80.0);
        doliprane = medicament(1L, "0002601", "DOLIPRANE COMP 500MG");

        List<Prestation> prestations = List.of(consultationGenerale, consultationSpecialite, hospitalisation, medicaments);
        lenient().when(prestationRepository.findAll()).thenReturn(prestations);
        lenient().when(prestationRepository.findByPrisEnChargeTrue()).thenReturn(prestations);
        lenient().when(prestationRepository.findById(10L)).thenReturn(Optional.of(consultationGenerale));
        lenient().when(prestationRepository.findById(11L)).thenReturn(Optional.of(consultationSpecialite));
        lenient().when(prestationRepository.findById(20L)).thenReturn(Optional.of(hospitalisation));
        lenient().when(prestationRepository.findById(30L)).thenReturn(Optional.of(medicaments));
        lenient().when(prestationRepository.findByNomActeContainingIgnoreCase(anyString()))
                .thenAnswer(invocation -> {
                    String keyword = normalize(invocation.getArgument(0));
                    return prestations.stream()
                            .filter(prestation -> normalize(prestation.getNomActe()).contains(keyword))
                            .toList();
                });

        lenient().when(faqChatbotService.answer(any())).thenReturn(Optional.empty());
        lenient().when(aiAgentService.answer(any())).thenReturn(ChatbotResponseDTO.builder()
                .intent("FALLBACK")
                .found(false)
                .message("Fallback test")
                .sources(List.of())
                .build());
        lenient().when(medicamentRepository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        lenient().when(medicamentRepository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc(any())).thenReturn(List.of());
        lenient().when(medicamentRepository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc(any())).thenReturn(List.of());
        lenient().when(medicamentRepository.searchForChatbot(any(), any(Pageable.class))).thenReturn(List.of());
        lenient().when(medicamentRepository.findById(1L)).thenReturn(Optional.of(doliprane));
        lenient().when(medicamentRepository.findByNomIgnoreCaseAndActifTrue("doliprane comp 500mg")).thenReturn(Optional.of(doliprane));
    }

    @Test
    void remembersPrecisePrestationAndAnswersFollowUpRateFromPostgres() {
        ChatbotResponseDTO first = ask("session-a", "Les consultations générales sont-elles prises en charge ?");
        ChatbotResponseDTO followUp = ask("session-a", "Et à quel taux ?");

        assertThat(first.getMessage()).contains("Consultation générale").contains("80%");
        assertThat(followUp.getMessage()).contains("Consultation générale").contains("80%");
        verify(prestationRepository).findById(10L);
    }

    @Test
    void answersEtSonTauxWithPrestationContext() {
        ask("session-a", "L'hospitalisation est-elle prise en charge ?");

        ChatbotResponseDTO followUp = ask("session-a", "Et son taux ?");

        assertThat(followUp.getMessage()).contains("Hospitalisation").contains("90%");
    }

    @Test
    void answersOtherContextualPrestationFormulations() {
        ask("session-a", "L'hospitalisation est-elle prise en charge ?");

        assertThat(ask("session-a", "Elle est prise en charge à combien ?").getMessage())
                .contains("Hospitalisation").contains("90%");
        assertThat(ask("session-a", "Et cette prestation ?").getMessage())
                .contains("Hospitalisation").contains("90%");
    }

    @Test
    void asksForPrestationWhenNoContextExists() {
        ChatbotResponseDTO response = ask("session-empty", "Et à quel taux ?");

        assertThat(response.getFound()).isFalse();
        assertThat(response.getMessage()).contains("Veuillez preciser la prestation concernee");
    }

    @Test
    void generalCoverageListDoesNotCreatePrestationContext() {
        ChatbotResponseDTO list = ask("session-a", "Quelles sont les prestations couvertes ?");
        ChatbotResponseDTO followUp = ask("session-a", "Et à quel taux ?");

        assertThat(list.getMessage()).contains("Les prestations couvertes disponibles");
        assertThat(list.getSources()).hasSize(4);
        assertThat(followUp.getFound()).isFalse();
        assertThat(followUp.getMessage()).contains("Veuillez preciser la prestation concernee");
    }

    @Test
    void changesPrestationContextAfterAnotherPrecisePrestation() {
        ask("session-a", "Les consultations générales sont-elles prises en charge ?");
        ChatbotResponseDTO second = ask("session-a", "Et pour l'hospitalisation ?");
        ChatbotResponseDTO followUp = ask("session-a", "Et son taux ?");

        assertThat(second.getMessage()).contains("Hospitalisation").contains("90%");
        assertThat(followUp.getMessage()).contains("Hospitalisation").contains("90%");
    }

    @Test
    void ambiguousPrestationDoesNotReplacePreviousContext() {
        ask("session-a", "L'hospitalisation est-elle prise en charge ?");
        ChatbotResponseDTO ambiguous = ask("session-a", "Les consultations sont-elles prises en charge ?");
        ChatbotResponseDTO followUp = ask("session-a", "Et son taux ?");

        assertThat(ambiguous.getFound()).isFalse();
        assertThat(ambiguous.getMessage()).contains("plusieurs prestations possibles");
        assertThat(followUp.getMessage()).contains("Hospitalisation").contains("90%");
    }

    @Test
    void unknownPrestationDoesNotReplacePreviousContext() {
        ask("session-a", "L'hospitalisation est-elle prise en charge ?");
        ChatbotResponseDTO unknown = ask("session-a", "La balneotherapie est-elle prise en charge ?");
        ChatbotResponseDTO followUp = ask("session-a", "Et son taux ?");

        assertThat(unknown.getFound()).isFalse();
        assertThat(unknown.getMessage()).isEqualTo("Fallback test");
        assertThat(followUp.getMessage()).contains("Hospitalisation").contains("90%");
    }

    @Test
    void separatesPrestationContextBySessionId() {
        ask("session-a", "Les consultations générales sont-elles prises en charge ?");
        ask("session-b", "L'hospitalisation est-elle prise en charge ?");

        assertThat(ask("session-a", "Et son taux ?").getMessage()).contains("Consultation générale").contains("80%");
        assertThat(ask("session-b", "Et son taux ?").getMessage()).contains("Hospitalisation").contains("90%");
    }

    @Test
    void keepsMedicationAndPrestationContextsIndependentInSameSession() {
        ChatbotResponseDTO medication = ask("session-a", "Quel est le prix de DOLIPRANE COMP 500MG ?");
        ChatbotResponseDTO prestation = ask("session-a", "Les consultations générales sont-elles prises en charge ?");
        ChatbotResponseDTO prestationFollowUp = ask("session-a", "Et leur taux ?");
        ChatbotResponseDTO medicationFollowUp = ask("session-a", "Et le prix du médicament ?");

        assertThat(medication.getMessage()).contains("DOLIPRANE COMP 500MG").contains("Prix public");
        assertThat(prestation.getMessage()).contains("Consultation générale").contains("80%");
        assertThat(prestationFollowUp.getMessage()).contains("Consultation générale").contains("80%");
        assertThat(medicationFollowUp.getMessage()).contains("DOLIPRANE COMP 500MG").contains("Prix public");
    }

    @Test
    void generalQuestionAfterPreciseContextStillReturnsFullList() {
        ask("session-a", "Les consultations générales sont-elles prises en charge ?");

        ChatbotResponseDTO response = ask("session-a", "Quelles sont les prestations couvertes ?");

        assertThat(response.getMessage()).contains("Les prestations couvertes disponibles");
        assertThat(response.getMessage()).contains("Consultation générale").contains("Hospitalisation").contains("Médicaments");
        assertThat(response.getCodeActe()).isNull();
    }

    private ChatbotResponseDTO ask(String sessionId, String message) {
        return chatbotService.repondre(ChatbotRequestDTO.builder()
                .sessionId(sessionId)
                .message(message)
                .build());
    }

    private Prestation prestation(Long id, String codeActe, String nomActe, CategorieActe categorie, Double tauxCouverture) {
        return Prestation.builder()
                .idPrestation(id)
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

    private Medicament medicament(Long id, String code, String nom) {
        return Medicament.builder()
                .id(id)
                .code(code)
                .nom(nom)
                .dci("Paracetamol")
                .dosage("500 mg")
                .formePharmaceutique("Comprime")
                .statut("REMBOURSABLE")
                .typeMedicament("GENERIQUE")
                .groupeTherapeutique("Antalgique")
                .prixPublic(new BigDecimal("1000"))
                .baseRemboursement(new BigDecimal("800"))
                .tauxCouverture(80.0)
                .partInam(new BigDecimal("640"))
                .partBeneficiaire(new BigDecimal("160"))
                .prisEnCharge(true)
                .actif(true)
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
