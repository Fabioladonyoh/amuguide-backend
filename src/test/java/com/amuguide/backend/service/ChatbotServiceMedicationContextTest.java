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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceMedicationContextTest {

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
    private Medicament doliprane;
    private Medicament paracetamolXyz;
    private Medicament dolipraneSuspension;
    private Medicament dolipraneEff;

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

        doliprane = medicament(1L, "0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE", 80.0, "1000", "640", "160");
        paracetamolXyz = medicament(2L, "0007777", "PARACETAMOL XYZ", "ENTENTE PREALABLE", 60.0, "2000", "1200", "800");
        dolipraneSuspension = medicament(3L, "0002602", "DOLIPRANE SUSP 2,4%", "REMBOURSABLE", 70.0, "1500", "1050", "450");
        dolipraneEff = medicament(4L, "0002603", "DOLIPRANE COMP EFF 500MG", "REMBOURSABLE", 80.0, "1200", "768", "192");

        lenient().when(faqChatbotService.answer(any())).thenReturn(Optional.empty());
        lenient().when(aiAgentService.answer(any())).thenReturn(ChatbotResponseDTO.builder()
                .intent("FALLBACK")
                .found(false)
                .message("Fallback test")
                .build());
        lenient().when(medicamentRepository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        lenient().when(medicamentRepository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc(any())).thenReturn(List.of());
        lenient().when(medicamentRepository.searchForChatbot(any(), any(Pageable.class))).thenReturn(List.of());
        lenient().when(medicamentRepository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc(any())).thenReturn(List.of());
        lenient().when(medicamentRepository.findById(1L)).thenReturn(Optional.of(doliprane));
        lenient().when(medicamentRepository.findById(2L)).thenReturn(Optional.of(paracetamolXyz));
        lenient().when(medicamentRepository.findById(3L)).thenReturn(Optional.of(dolipraneSuspension));
        lenient().when(medicamentRepository.findById(4L)).thenReturn(Optional.of(dolipraneEff));
        stubExactMedication(doliprane);
        stubExactMedication(paracetamolXyz);
    }

    @Test
    void remembersMedicationAfterExactMatchAndAnswersFollowUpRateFromPostgres() {
        ChatbotResponseDTO first = ask("session-a", "Quel est le prix de DOLIPRANE COMP 500MG ?");
        ChatbotResponseDTO followUp = ask("session-a", "Et son taux ?");

        assertThat(first.getMessage()).contains("Prix public").contains("1");
        assertThat(followUp.getMessage()).contains("DOLIPRANE COMP 500MG");
        assertThat(followUp.getMessage()).contains("Taux de couverture : 80%");
        verify(medicamentRepository, atLeastOnce()).findById(1L);
    }

    @Test
    void medicationFollowUpUsesSessionContextEvenWhenFaqWouldMatch() {
        ask("session-a", "Quel est le prix de DOLIPRANE COMP 500MG ?");
        lenient().when(faqChatbotService.answer(argThat(detection ->
                detection != null && "et son taux".equals(detection.getNormalizedMessage())
        ))).thenReturn(Optional.of(ChatbotResponseDTO.builder()
                .intent("AMU_INFO")
                .found(true)
                .message("Reponse FAQ generale a ignorer pour une relance medicament.")
                .build()));

        ChatbotResponseDTO followUp = ask("session-a", "Et son taux ?");

        assertThat(followUp.getMessage()).contains("DOLIPRANE COMP 500MG");
        assertThat(followUp.getMessage()).contains("Taux de couverture : 80%");
        verify(faqChatbotService, never()).answer(argThat(detection ->
                detection != null && "et son taux".equals(detection.getNormalizedMessage())
        ));
    }

    @Test
    void answersFollowUpPriceBeneficiaryPartAndPriorAgreementWithSameMedication() {
        ask("session-a", "Je veux les infos sur DOLIPRANE COMP 500MG.");

        ChatbotResponseDTO price = ask("session-a", "Et son prix ?");
        ChatbotResponseDTO beneficiary = ask("session-a", "Et ma part ?");
        ChatbotResponseDTO inam = ask("session-a", "Et la part INAM ?");
        ChatbotResponseDTO priorAgreement = ask("session-a", "Et l'entente prealable ?");

        assertThat(price.getMessage()).contains("Prix public").contains("1");
        assertThat(beneficiary.getMessage()).contains("Part beneficiaire").contains("160");
        assertThat(inam.getMessage()).contains("Part INAM").contains("640");
        assertThat(priorAgreement.getMessage()).startsWith("Non.");
        assertThat(priorAgreement.getMessage()).contains("Son statut officiel est REMBOURSABLE");
    }

    @Test
    void answersFollowUpBeneficiaryPartAfterReimbursementQuestion() {
        ask("session-a", "DOLIPRANE COMP 500MG est-il remboursable ?");

        ChatbotResponseDTO followUp = ask("session-a", "Et combien je paie ?");

        assertThat(followUp.getMessage()).contains("Part beneficiaire").contains("160");
    }

    @Test
    void doesNotCreateContextAfterAmbiguousResult() {
        stubAmbiguousDolipraneSearch();

        ChatbotResponseDTO ambiguous = ask("session-a", "Quel est le prix de DOLIPRANE ?");
        ChatbotResponseDTO followUp = ask("session-a", "Et son taux ?");

        assertThat(ambiguous.getMessage()).contains("plusieurs medicaments possibles");
        assertThat(followUp.getFound()).isFalse();
        assertThat(followUp.getMessage()).contains("Veuillez preciser le medicament");
    }

    @Test
    void keepsPreviousContextAfterLaterAmbiguousExplicitQuestion() {
        ask("session-a", "DOLIPRANE COMP 500MG");
        stubAmbiguousDolipraneSearch();

        ChatbotResponseDTO ambiguous = ask("session-a", "Quel est le prix de DOLIPRANE ?");
        ChatbotResponseDTO followUp = ask("session-a", "Et son taux ?");

        assertThat(ambiguous.getMessage()).contains("plusieurs medicaments possibles");
        assertThat(followUp.getMessage()).contains("DOLIPRANE COMP 500MG");
        assertThat(followUp.getMessage()).contains("Taux de couverture : 80%");
    }

    @Test
    void doesNotCreateContextAfterAbsentMedicationOrSuggestionOnly() {
        when(medicamentRepository.findByNomIgnoreCaseAndActifTrue("medicamentinexistant")).thenReturn(Optional.empty());
        when(medicamentRepository.searchForChatbot(eq("medi"), any(Pageable.class))).thenReturn(List.of(doliprane));

        ChatbotResponseDTO absent = ask("session-a", "Quel est le prix de MEDICAMENTINEXISTANT ?");
        ChatbotResponseDTO followUp = ask("session-a", "Et son taux ?");

        assertThat(absent.getFound()).isFalse();
        assertThat(followUp.getFound()).isFalse();
        assertThat(followUp.getMessage()).contains("Veuillez preciser le medicament");
    }

    @Test
    void switchesContextWhenAnotherMedicationIsConfirmed() {
        ask("session-a", "Quel est le prix de DOLIPRANE COMP 500MG ?");
        ask("session-a", "Quel est le prix de PARACETAMOL XYZ ?");

        ChatbotResponseDTO followUp = ask("session-a", "Et son taux ?");

        assertThat(followUp.getMessage()).contains("PARACETAMOL XYZ");
        assertThat(followUp.getMessage()).contains("Taux de couverture : 60%");
    }

    @Test
    void separatesMedicationContextBySessionId() {
        ask("session-a", "DOLIPRANE COMP 500MG");
        ask("session-b", "PARACETAMOL XYZ");

        ChatbotResponseDTO sessionA = ask("session-a", "Et son taux ?");
        ChatbotResponseDTO sessionB = ask("session-b", "Et son taux ?");

        assertThat(sessionA.getMessage()).contains("DOLIPRANE COMP 500MG").contains("80%");
        assertThat(sessionB.getMessage()).contains("PARACETAMOL XYZ").contains("60%");
    }

    @Test
    void asksForMedicationWhenSessionHasNoContext() {
        ChatbotResponseDTO response = ask("session-empty", "Et son taux ?");

        assertThat(response.getFound()).isFalse();
        assertThat(response.getMessage()).contains("Veuillez preciser le medicament");
    }

    @Test
    void usesBackendGeneratedSessionIdForMedicationContext() {
        ChatbotResponseDTO first = chatbotService.repondre(ChatbotRequestDTO.builder()
                .message("Quel est le prix de DOLIPRANE COMP 500MG ?")
                .build());
        ChatbotResponseDTO followUp = chatbotService.repondre(ChatbotRequestDTO.builder()
                .message("Et son taux ?")
                .sessionId(first.getSessionId())
                .build());

        assertThat(first.getSessionId()).isNotBlank();
        assertThat(followUp.getSessionId()).isEqualTo(first.getSessionId());
        assertThat(followUp.getMessage()).contains("DOLIPRANE COMP 500MG").contains("80%");
    }

    @Test
    void keepsExplicitMedicationQuestionsWorkingWithoutContext() {
        ChatbotResponseDTO response = ask("session-a", "Quel est le code de DOLIPRANE COMP 500MG ?");

        assertThat(response.getFound()).isTrue();
        assertThat(response.getMessage()).contains("Code : 0002601");
    }

    @Test
    void confirmsSinglePendingSuggestionWithOuiAndResumesInitialPriceIntentFromPostgres() {
        stubSingleDolipraneSuggestion();

        ChatbotResponseDTO suggestion = ask("session-a", "Quel est le prix de DOLIPRAN COMP 500MG ?");
        ChatbotResponseDTO confirmation = ask("session-a", "Oui");

        assertThat(suggestion.getFound()).isFalse();
        assertThat(suggestion.getMessage()).contains("Voulez-vous dire : DOLIPRANE COMP 500MG");
        assertThat(confirmation.getFound()).isTrue();
        assertThat(confirmation.getMessage()).contains("DOLIPRANE COMP 500MG");
        assertThat(confirmation.getMessage()).contains("Prix public").contains("1");
        verify(medicamentRepository, atLeastOnce()).findById(1L);
    }

    @Test
    void confirmsSinglePendingSuggestionWithNaturalConfirmation() {
        stubSingleDolipraneSuggestion();

        ask("session-a", "Quel est le prix de DOLIPRAN COMP 500MG ?");
        ChatbotResponseDTO confirmation = ask("session-a", "Oui c'est ça");

        assertThat(confirmation.getFound()).isTrue();
        assertThat(confirmation.getMessage()).contains("DOLIPRANE COMP 500MG");
        assertThat(confirmation.getMessage()).contains("Prix public");
    }

    @Test
    void refusalClearsPendingSuggestionWithoutReplacingPreviousConfirmedMedication() {
        ask("session-a", "DOLIPRANE COMP 500MG");
        stubSingleParacetamolSuggestion();

        ask("session-a", "Quel est le prix de PARACETAMOL XYY ?");
        ChatbotResponseDTO refusal = ask("session-a", "non ce n'est pas ça");
        ChatbotResponseDTO followUp = ask("session-a", "Et son taux ?");

        assertThat(refusal.getFound()).isFalse();
        assertThat(refusal.getMessage()).contains("preciser le nom complet");
        assertThat(followUp.getMessage()).contains("DOLIPRANE COMP 500MG");
        assertThat(followUp.getMessage()).contains("Taux de couverture : 80%");
    }

    @Test
    void multiplePendingSuggestionsPlusOuiDoesNotSelectFirstCandidate() {
        stubAmbiguousDolipraneCompSearch();

        ask("session-a", "Quel est le prix de DOLIPRANE COMP ?");
        ChatbotResponseDTO confirmation = ask("session-a", "oui");
        ChatbotResponseDTO followUp = ask("session-a", "Et son taux ?");

        assertThat(confirmation.getFound()).isFalse();
        assertThat(confirmation.getMessage()).contains("plusieurs medicaments possibles");
        assertThat(confirmation.getMessage()).contains("1. DOLIPRANE COMP 500MG");
        assertThat(confirmation.getMessage()).contains("2. DOLIPRANE COMP EFF 500MG");
        assertThat(followUp.getFound()).isFalse();
        assertThat(followUp.getMessage()).contains("Veuillez preciser le medicament");
    }

    @Test
    void multiplePendingSuggestionsCanBeConfirmedByExactCandidateName() {
        stubAmbiguousDolipraneCompSearch();

        ask("session-a", "Quel est le prix de DOLIPRANE COMP ?");
        ChatbotResponseDTO selected = ask("session-a", "DOLIPRANE COMP 500MG");
        ChatbotResponseDTO followUp = ask("session-a", "Et son taux ?");

        assertThat(selected.getFound()).isTrue();
        assertThat(selected.getMessage()).contains("Prix public");
        assertThat(followUp.getMessage()).contains("DOLIPRANE COMP 500MG");
        assertThat(followUp.getMessage()).contains("Taux de couverture : 80%");
    }

    @Test
    void pendingSuggestionConfirmationIsIsolatedBySessionId() {
        stubSingleDolipraneSuggestion();

        ask("session-a", "Quel est le prix de DOLIPRAN COMP 500MG ?");
        ChatbotResponseDTO sessionB = ask("session-b", "oui");
        ChatbotResponseDTO sessionA = ask("session-a", "oui");

        assertThat(sessionB.getMessage()).doesNotContain("DOLIPRANE COMP 500MG");
        assertThat(sessionA.getMessage()).contains("DOLIPRANE COMP 500MG");
        assertThat(sessionA.getMessage()).contains("Prix public");
    }

    @Test
    void ouiWithoutPendingSuggestionDoesNotSelectMedication() {
        ChatbotResponseDTO response = ask("session-empty", "oui");
        ChatbotResponseDTO followUp = ask("session-empty", "Et son taux ?");

        assertThat(response.getMessage()).doesNotContain("DOLIPRANE COMP 500MG");
        assertThat(followUp.getFound()).isFalse();
        assertThat(followUp.getMessage()).contains("Veuillez preciser le medicament");
    }

    @Test
    void confirmedSuggestionBecomesMedicationContextForNextFollowUp() {
        stubSingleDolipraneSuggestion();

        ask("session-a", "Quel est le prix de DOLIPRAN COMP 500MG ?");
        ask("session-a", "exactement");
        ChatbotResponseDTO followUp = ask("session-a", "Et son taux ?");

        assertThat(followUp.getMessage()).contains("DOLIPRANE COMP 500MG");
        assertThat(followUp.getMessage()).contains("Taux de couverture : 80%");
    }

    private ChatbotResponseDTO ask(String sessionId, String message) {
        return chatbotService.repondre(ChatbotRequestDTO.builder()
                .sessionId(sessionId)
                .message(message)
                .build());
    }

    private void stubExactMedication(Medicament medicament) {
        String normalizedName = medicament.getNom().toLowerCase()
                .replace("  ", " ")
                .trim();
        lenient().when(medicamentRepository.findByNomIgnoreCaseAndActifTrue(normalizedName)).thenReturn(Optional.of(medicament));
        lenient().when(medicamentRepository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc(normalizedName)).thenReturn(List.of(medicament));
        lenient().when(medicamentRepository.searchForChatbot(eq(normalizedName), any(Pageable.class))).thenReturn(List.of(medicament));
    }

    private void stubAmbiguousDolipraneSearch() {
        when(medicamentRepository.findByNomIgnoreCaseAndActifTrue("doliprane")).thenReturn(Optional.empty());
        when(medicamentRepository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane"))
                .thenReturn(List.of(doliprane, dolipraneSuspension));
        when(medicamentRepository.searchForChatbot(eq("doliprane"), any(Pageable.class)))
                .thenReturn(List.of(doliprane, dolipraneSuspension));
    }

    private void stubSingleDolipraneSuggestion() {
        when(medicamentRepository.findByNomIgnoreCaseAndActifTrue("dolipran comp 500mg")).thenReturn(Optional.empty());
        when(medicamentRepository.searchForChatbot(eq("doli"), any(Pageable.class))).thenReturn(List.of(doliprane));
    }

    private void stubSingleParacetamolSuggestion() {
        when(medicamentRepository.findByNomIgnoreCaseAndActifTrue("paracetamol xyy")).thenReturn(Optional.empty());
        when(medicamentRepository.searchForChatbot(eq("para"), any(Pageable.class))).thenReturn(List.of(paracetamolXyz));
    }

    private void stubAmbiguousDolipraneCompSearch() {
        when(medicamentRepository.findByNomIgnoreCaseAndActifTrue("doliprane comp")).thenReturn(Optional.empty());
        when(medicamentRepository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane comp"))
                .thenReturn(List.of(doliprane, dolipraneEff));
        when(medicamentRepository.searchForChatbot(eq("doliprane comp"), any(Pageable.class)))
                .thenReturn(List.of(doliprane, dolipraneEff));
    }

    private Medicament medicament(
            Long id,
            String code,
            String nom,
            String statut,
            Double tauxCouverture,
            String prixPublic,
            String partInam,
            String partBeneficiaire
    ) {
        return Medicament.builder()
                .id(id)
                .code(code)
                .nom(nom)
                .dci("Paracetamol")
                .dosage("500 mg")
                .formePharmaceutique("Comprime")
                .statut(statut)
                .typeMedicament("GENERIQUE")
                .groupeTherapeutique("Antalgique")
                .prixPublic(new BigDecimal(prixPublic))
                .baseRemboursement(new BigDecimal(partInam).add(new BigDecimal(partBeneficiaire)))
                .tauxCouverture(tauxCouverture)
                .partInam(new BigDecimal(partInam))
                .partBeneficiaire(new BigDecimal(partBeneficiaire))
                .prisEnCharge(true)
                .actif(true)
                .build();
    }
}
