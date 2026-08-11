package com.amuguide.backend.chat.service;

import com.amuguide.backend.chat.nlp.ChatIntent;
import com.amuguide.backend.chat.nlp.IntentDetectionResult;
import com.amuguide.backend.dto.ChatbotResponseDTO;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponseGeneratorMedicationPostgresTest {

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
    }

    @Test
    void answersCoverageRateFromPostgresEvidence() {
        MedicationEvidence evidence = evidence("MED001", "Doliprane");
        when(medicationKnowledgeService.isListRequest("quel est le taux de prise en charge de doliprane")).thenReturn(false);
        when(medicamentChatbotSearchService.search("quel est le taux de prise en charge de doliprane", 10))
                .thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection("quel est le taux de prise en charge de doliprane"));

        assertThat(response.getFound()).isTrue();
        assertThat(response.getMessage()).contains("80%");
        assertThat(response.getSources()).extracting("type").containsExactly("MEDICAMENT_POSTGRESQL");
    }

    @Test
    void answersInamAndBeneficiaryAmountsFromPostgresEvidence() {
        MedicationEvidence evidence = evidence("MED001", "Doliprane");
        when(medicationKnowledgeService.isListRequest("combien l inam paie pour doliprane")).thenReturn(false);
        when(medicamentChatbotSearchService.search("combien l inam paie pour doliprane", 10)).thenReturn(single(evidence));
        when(medicationKnowledgeService.isListRequest("combien dois je payer pour doliprane")).thenReturn(false);
        when(medicamentChatbotSearchService.search("combien dois je payer pour doliprane", 10)).thenReturn(single(evidence));

        ChatbotResponseDTO inam = responseGenerator.generate(detection("combien l inam paie pour doliprane"));
        ChatbotResponseDTO beneficiary = responseGenerator.generate(detection("combien dois je payer pour doliprane"));

        assertThat(inam.getMessage()).contains("Part INAM").contains("640");
        assertThat(beneficiary.getMessage()).contains("Part beneficiaire").contains("160");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "doliprane comp 500mg est il rembourse",
            "doliprane comp 500mg est il remboursable",
            "est ce que l'inam rembourse doliprane comp 500mg",
            "est ce que ce medicament est pris en charge",
            "quelle est la prise en charge de doliprane comp 500mg",
            "quel est le taux de remboursement de doliprane comp 500mg",
            "a combien l'inam rembourse doliprane comp 500mg",
            "quel pourcentage est pris en charge par l'inam"
    })
    void answersCoverageAndRateForNaturalReimbursementQuestions(String normalizedQuestion) {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        when(medicationKnowledgeService.isListRequest(normalizedQuestion)).thenReturn(false);
        if (!normalizedQuestion.contains("ce medicament")) {
            when(medicamentChatbotSearchService.search(normalizedQuestion, 10)).thenReturn(single(evidence));
        }

        ChatbotResponseDTO response = generateWithContextIfNeeded(normalizedQuestion, evidence);

        assertThat(response.getMessage()).contains("Prise en charge : oui");
        if (normalizedQuestion.contains("taux")
                || normalizedQuestion.contains("pourcentage")
                || normalizedQuestion.contains("prise en charge de")
                || normalizedQuestion.contains("a combien")) {
            assertThat(response.getMessage()).contains("Taux de couverture : 80%");
        }
        assertThat(response.getMessage()).doesNotContain("entente prealable");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "quelle est la part de l'inam pour doliprane comp 500mg",
            "combien l'inam paie pour doliprane comp 500mg"
    })
    void answersInamPartForNaturalQuestions(String normalizedQuestion) {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        when(medicationKnowledgeService.isListRequest(normalizedQuestion)).thenReturn(false);
        when(medicamentChatbotSearchService.search(normalizedQuestion, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = generateWithContextIfNeeded(normalizedQuestion, evidence);

        assertThat(response.getMessage()).contains("Part INAM").contains("640");
        assertThat(response.getMessage()).doesNotContain("Prix public");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "quelle est ma part a payer",
            "combien dois je payer",
            "quel est le reste a ma charge",
            "quelle est la part du beneficiaire"
    })
    void answersBeneficiaryPartForNaturalQuestions(String normalizedQuestion) {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        when(medicationKnowledgeService.isListRequest(normalizedQuestion)).thenReturn(false);
        when(medicamentChatbotSearchService.search(normalizedQuestion, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = generateWithContextIfNeeded(normalizedQuestion, evidence);

        assertThat(response.getMessage()).contains("Part beneficiaire").contains("160");
        assertThat(response.getMessage()).doesNotContain("Prix public");
    }

    @Test
    void answersUnavailableWhenRateAndAmountsAreNull() {
        MedicationEvidence evidence = evidenceWithFinancialValues(null, null, null, null);
        when(medicationKnowledgeService.isListRequest("quel est le taux de remboursement de doliprane")).thenReturn(false);
        when(medicamentChatbotSearchService.search("quel est le taux de remboursement de doliprane", 10)).thenReturn(single(evidence));
        when(medicationKnowledgeService.isListRequest("combien l'inam paie pour doliprane")).thenReturn(false);
        when(medicamentChatbotSearchService.search("combien l'inam paie pour doliprane", 10)).thenReturn(single(evidence));
        when(medicationKnowledgeService.isListRequest("combien dois je payer")).thenReturn(false);
        when(medicamentChatbotSearchService.search("combien dois je payer", 10)).thenReturn(single(evidence));

        ChatbotResponseDTO rate = responseGenerator.generate(detection("quel est le taux de remboursement de doliprane"));
        ChatbotResponseDTO inam = responseGenerator.generate(detection("combien l'inam paie pour doliprane"));
        ChatbotResponseDTO beneficiary = responseGenerator.generate(detection("combien dois je payer"));

        assertThat(rate.getMessage()).contains("Taux de couverture : information non disponible");
        assertThat(inam.getMessage()).contains("Part INAM : information non disponible");
        assertThat(beneficiary.getMessage()).contains("Part beneficiaire : information non disponible");
    }

    @Test
    void answersPriceAndInamPartInCombinedQuestion() {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        String question = "quel est le prix de doliprane comp 500mg et combien l'inam prend en charge";
        when(medicationKnowledgeService.isListRequest(question)).thenReturn(false);
        when(medicamentChatbotSearchService.search(question, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(question));

        assertThat(response.getMessage()).contains("Prix public").contains("1");
        assertThat(response.getMessage()).contains("Part INAM").contains("640");
        assertThat(response.getMessage()).doesNotContain("Part beneficiaire");
    }

    @Test
    void answersPriceAndBeneficiaryPartInCombinedQuestion() {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        String question = "doliprane comp 500mg coute combien et quelle est ma part a payer";
        when(medicationKnowledgeService.isListRequest(question)).thenReturn(false);
        when(medicamentChatbotSearchService.search(question, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(question));

        assertThat(response.getMessage()).contains("Prix public").contains("1");
        assertThat(response.getMessage()).contains("Part beneficiaire").contains("160");
        assertThat(response.getMessage()).doesNotContain("Part INAM");
    }

    @Test
    void answersPriceAndRateInCombinedQuestion() {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        String question = "quel est le prix et le taux de remboursement de doliprane comp 500mg";
        when(medicationKnowledgeService.isListRequest(question)).thenReturn(false);
        when(medicamentChatbotSearchService.search(question, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(question));

        assertThat(response.getMessage()).contains("Prix public").contains("1");
        assertThat(response.getMessage()).contains("Taux de couverture : 80%");
    }

    @Test
    void answersReimbursementAndRateInCombinedQuestion() {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        String question = "doliprane comp 500mg est il remboursable et a quel taux";
        when(medicationKnowledgeService.isListRequest(question)).thenReturn(false);
        when(medicamentChatbotSearchService.search(question, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(question));

        assertThat(response.getMessage()).contains("Prise en charge : oui");
        assertThat(response.getMessage()).contains("Statut officiel : REMBOURSABLE");
        assertThat(response.getMessage()).contains("Taux de couverture : 80%");
    }

    @Test
    void answersPriceAndPriorAgreementInCombinedQuestion() {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        String question = "quel est le prix de doliprane comp 500mg et est il en entente prealable";
        when(medicationKnowledgeService.isListRequest(question)).thenReturn(false);
        when(medicamentChatbotSearchService.search(question, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(question));

        assertThat(response.getMessage()).contains("Prix public").contains("1");
        assertThat(response.getMessage()).contains("Entente prealable : non");
        assertThat(response.getMessage()).contains("Statut officiel : REMBOURSABLE");
    }

    @Test
    void answersPriceInamAndBeneficiaryPartsInCombinedQuestion() {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        String question = "donne moi le prix la part inam et la part beneficiaire de doliprane comp 500mg";
        when(medicationKnowledgeService.isListRequest(question)).thenReturn(false);
        when(medicamentChatbotSearchService.search(question, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(question));

        assertThat(response.getMessage()).contains("Prix public").contains("1");
        assertThat(response.getMessage()).contains("Part INAM").contains("640");
        assertThat(response.getMessage()).contains("Part beneficiaire").contains("160");
    }

    @Test
    void answersStatusAndCodeInCombinedQuestion() {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        String question = "quel est le statut et le code de doliprane comp 500mg";
        when(medicationKnowledgeService.isListRequest(question)).thenReturn(false);
        when(medicamentChatbotSearchService.search(question, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(question));

        assertThat(response.getMessage()).contains("Code : 0002601");
        assertThat(response.getMessage()).contains("Statut officiel : REMBOURSABLE");
    }

    @Test
    void answersCoverageAndBeneficiaryPartInCombinedQuestion() {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        String question = "doliprane comp 500mg est il pris en charge et combien dois je payer";
        when(medicationKnowledgeService.isListRequest(question)).thenReturn(false);
        when(medicamentChatbotSearchService.search(question, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(question));

        assertThat(response.getMessage()).contains("Prise en charge : oui");
        assertThat(response.getMessage()).contains("Part beneficiaire").contains("160");
    }

    @Test
    void answersRateAndBeneficiaryPartInCombinedQuestion() {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        String question = "quel est le taux de prise en charge et le reste a ma charge";
        when(medicationKnowledgeService.isListRequest(question)).thenReturn(false);
        when(medicamentChatbotSearchService.search(question, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(question));

        assertThat(response.getMessage()).contains("Taux de couverture : 80%");
        assertThat(response.getMessage()).contains("Part beneficiaire").contains("160");
    }

    @Test
    void answersPriceRateAndStatusInCombinedQuestion() {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        String question = "quel est le prix le taux et le statut de doliprane comp 500mg";
        when(medicationKnowledgeService.isListRequest(question)).thenReturn(false);
        when(medicamentChatbotSearchService.search(question, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(question));

        assertThat(response.getMessage()).contains("Prix public").contains("1");
        assertThat(response.getMessage()).contains("Statut officiel : REMBOURSABLE");
        assertThat(response.getMessage()).contains("Taux de couverture : 80%");
    }

    @Test
    void answersOnlyRequestedUnavailableValueInCombinedQuestion() {
        MedicationEvidence evidence = evidenceWithFinancialValues(null, new BigDecimal("640"), new BigDecimal("160"), 80.0);
        String question = "donne moi le prix la part inam et la part beneficiaire de doliprane comp 500mg";
        when(medicationKnowledgeService.isListRequest(question)).thenReturn(false);
        when(medicamentChatbotSearchService.search(question, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(question));

        assertThat(response.getMessage()).contains("Prix public : information non disponible");
        assertThat(response.getMessage()).contains("Part INAM").contains("640");
        assertThat(response.getMessage()).contains("Part beneficiaire").contains("160");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "je veux savoir combien je paie pour doliprane comp 500mg",
            "moi je paie combien pour doliprane comp 500mg"
    })
    void answersBeneficiaryPartForConversationalQuestions(String normalizedQuestion) {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        when(medicationKnowledgeService.isListRequest(normalizedQuestion)).thenReturn(false);
        if (!normalizedQuestion.contains("ce medicament")) {
            when(medicamentChatbotSearchService.search(normalizedQuestion, 10)).thenReturn(single(evidence));
        }

        ChatbotResponseDTO response = generateWithContextIfNeeded(normalizedQuestion, evidence);

        assertThat(response.getMessage()).contains("Part beneficiaire").contains("160");
        assertThat(response.getMessage()).doesNotContain("Prix public");
    }

    @Test
    void answersInamPartForConversationalQuestion() {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        String question = "l'inam prend combien sur doliprane comp 500mg";
        when(medicationKnowledgeService.isListRequest(question)).thenReturn(false);
        when(medicamentChatbotSearchService.search(question, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(question));

        assertThat(response.getMessage()).contains("Part INAM").contains("640");
        assertThat(response.getMessage()).doesNotContain("Part beneficiaire");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "doliprane comp 500mg ca coute combien",
            "c'est combien doliprane comp 500mg"
    })
    void answersPriceForConversationalQuestions(String normalizedQuestion) {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        when(medicationKnowledgeService.isListRequest(normalizedQuestion)).thenReturn(false);
        when(medicamentChatbotSearchService.search(normalizedQuestion, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(normalizedQuestion));

        assertThat(response.getMessage()).contains("Prix public").contains("1");
        assertThat(response.getMessage()).doesNotContain("Part beneficiaire");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "doliprane comp 500mg est pris en charge",
            "l'amu prend en charge doliprane comp 500mg",
            "je peux avoir doliprane comp 500mg avec l'amu",
            "est ce que l'inam couvre doliprane comp 500mg"
    })
    void answersCoverageForConversationalQuestions(String normalizedQuestion) {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        when(medicationKnowledgeService.isListRequest(normalizedQuestion)).thenReturn(false);
        when(medicamentChatbotSearchService.search(normalizedQuestion, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(normalizedQuestion));

        assertThat(response.getMessage()).contains("Prise en charge : oui");
        assertThat(response.getMessage()).contains("Statut officiel : REMBOURSABLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "je dois faire une entente avant d'acheter doliprane comp 500mg",
            "il faut un accord avant pour doliprane comp 500mg"
    })
    void answersPriorAgreementForConversationalQuestions(String normalizedQuestion) {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        when(medicationKnowledgeService.isListRequest(normalizedQuestion)).thenReturn(false);
        when(medicamentChatbotSearchService.search(normalizedQuestion, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(normalizedQuestion));

        assertThat(response.getMessage()).startsWith("Non.");
        assertThat(response.getMessage()).contains("Son statut officiel est REMBOURSABLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "je veux les infos sur doliprane comp 500mg",
            "donne moi les informations sur doliprane comp 500mg"
    })
    void answersGeneralMedicationInformationForConversationalQuestions(String normalizedQuestion) {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        when(medicationKnowledgeService.isListRequest(normalizedQuestion)).thenReturn(false);
        when(medicamentChatbotSearchService.search(normalizedQuestion, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(normalizedQuestion));

        assertThat(response.getMessage()).contains("Code : 0002601");
        assertThat(response.getMessage()).contains("Prix public").contains("1");
        assertThat(response.getMessage()).contains("Statut officiel : REMBOURSABLE");
        assertThat(response.getMessage()).contains("Taux de couverture : 80%");
        assertThat(response.getMessage()).contains("Part INAM").contains("640");
        assertThat(response.getMessage()).contains("Part beneficiaire").contains("160");
    }

    @Test
    void answersPriorAgreementFromStatutOnly() {
        MedicationEvidence evidence = evidence("MED001", "Doliprane");
        when(medicationKnowledgeService.isListRequest("doliprane est il en entente prealable")).thenReturn(false);
        when(medicamentChatbotSearchService.search("doliprane est il en entente prealable", 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection("doliprane est il en entente prealable"));

        assertThat(response.getMessage()).startsWith("Oui.");
        assertThat(response.getMessage()).contains("est soumis a une entente prealable");
    }

    @Test
    void answersNoForPriorAgreementWhenStatusIsRemboursable() {
        MedicationEvidence evidence = evidence("MED001", "Doliprane", "REMBOURSABLE");
        when(medicationKnowledgeService.isListRequest("doliprane est il en entente prealable")).thenReturn(false);
        when(medicamentChatbotSearchService.search("doliprane est il en entente prealable", 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection("doliprane est il en entente prealable"));

        assertThat(response.getMessage()).startsWith("Non.");
        assertThat(response.getMessage()).contains("n'est pas soumis a une entente prealable");
        assertThat(response.getMessage()).contains("Son statut officiel est REMBOURSABLE");
    }

    @Test
    void answersYesForPriorAgreementWhenStatusIsEntentePrealable() {
        MedicationEvidence evidence = evidence("MED001", "Doliprane", "ENTENTE PREALABLE");
        when(medicationKnowledgeService.isListRequest("doliprane est il en entente prealable")).thenReturn(false);
        when(medicamentChatbotSearchService.search("doliprane est il en entente prealable", 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection("doliprane est il en entente prealable"));

        assertThat(response.getMessage()).startsWith("Oui.");
        assertThat(response.getMessage()).contains("est soumis a une entente prealable");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "doliprane comp 500mg est il en entente prealable",
            "est ce que doliprane comp 500mg necessite une entente prealable",
            "doliprane comp 500mg necessite t il un accord prealable",
            "faut il une entente prealable pour doliprane comp 500mg",
            "est ce que je dois avoir une entente prealable pour doliprane comp 500mg",
            "ce medicament est il soumis a une entente prealable",
            "est ce qu'il faut un accord prealable pour ce medicament",
            "une autorisation prealable est elle necessaire pour doliprane comp 500mg"
    })
    void answersPriorAgreementForNaturalFormulations(String normalizedQuestion) {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        when(medicationKnowledgeService.isListRequest(normalizedQuestion)).thenReturn(false);
        if (!normalizedQuestion.contains("ce medicament")) {
            when(medicamentChatbotSearchService.search(normalizedQuestion, 10)).thenReturn(single(evidence));
        }

        ChatbotResponseDTO response = generateWithContextIfNeeded(normalizedQuestion, evidence);

        assertThat(response.getMessage()).startsWith("Non.");
        assertThat(response.getMessage()).contains("Son statut officiel est REMBOURSABLE");
        assertThat(response.getMessage()).contains("DOLIPRANE COMP 500MG");
        assertThat(response.getMessage()).contains("0002601");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "quel est le prix de doliprane comp 500mg",
            "doliprane comp 500mg est il remboursable",
            "quel est le statut de doliprane comp 500mg",
            "quel est le code de doliprane comp 500mg",
            "je cherche doliprane comp 500mg"
    })
    void doesNotTreatOtherMedicationQuestionsAsPriorAgreement(String normalizedQuestion) {
        MedicationEvidence evidence = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        when(medicationKnowledgeService.isListRequest(normalizedQuestion)).thenReturn(false);
        when(medicamentChatbotSearchService.search(normalizedQuestion, 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection(normalizedQuestion));

        assertThat(response.getMessage()).doesNotStartWith("Non. D'apres le referentiel AMU disponible, ce medicament n'est pas soumis a une entente prealable");
        assertThat(response.getMessage()).doesNotStartWith("Oui. D'apres le referentiel AMU disponible, ce medicament est soumis a une entente prealable");
        assertThat(response.getMessage()).contains("0002601");
    }

    @Test
    void doesNotExplainTpcStatusForPriorAgreementQuestion() {
        MedicationEvidence evidence = evidence("MED001", "Doliprane", "TPC");
        when(medicationKnowledgeService.isListRequest("doliprane est il en entente prealable")).thenReturn(false);
        when(medicamentChatbotSearchService.search("doliprane est il en entente prealable", 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection("doliprane est il en entente prealable"));

        assertThat(response.getMessage()).startsWith("Non,");
        assertThat(response.getMessage()).contains("Son statut officiel est TPC");
        assertThat(response.getMessage()).doesNotContain("tiers");
        assertThat(response.getMessage()).doesNotContain("payant");
    }

    @Test
    void answersUnavailableWhenPriorAgreementStatusIsNull() {
        MedicationEvidence evidence = evidence("MED001", "Doliprane", null);
        when(medicationKnowledgeService.isListRequest("doliprane est il en entente prealable")).thenReturn(false);
        when(medicamentChatbotSearchService.search("doliprane est il en entente prealable", 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection("doliprane est il en entente prealable"));

        assertThat(response.getMessage()).contains("Le statut relatif a l'entente prealable n'est pas disponible dans le referentiel AMU.");
    }

    @Test
    void answersPublicPriceFromPostgresEvidence() {
        MedicationEvidence evidence = evidence("MED001", "Doliprane");
        when(medicationKnowledgeService.isListRequest("quel est le prix de doliprane")).thenReturn(false);
        when(medicamentChatbotSearchService.search("quel est le prix de doliprane", 10)).thenReturn(single(evidence));

        ChatbotResponseDTO response = responseGenerator.generate(detection("quel est le prix de doliprane"));

        assertThat(response.getMessage()).contains("Prix public").contains("1");
        assertThat(response.getSources()).extracting("type").containsExactly("MEDICAMENT_POSTGRESQL");
    }

    @Test
    void answersNotFoundWithoutAiCompletion() {
        when(medicationKnowledgeService.isListRequest("medicament inconnu xyz")).thenReturn(false);
        when(medicamentChatbotSearchService.search("medicament inconnu xyz", 10))
                .thenReturn(new MedicamentChatbotSearchService.MedicationSearchResult("inconnu xyz", List.of(), List.of(), false, false));

        ChatbotResponseDTO response = responseGenerator.generate(detection("medicament inconnu xyz"));

        assertThat(response.getFound()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Je n'ai pas trouve ce medicament dans le referentiel AMU disponible.");
    }

    @Test
    void doesNotReturnPriceWhenMedicationSearchIsAmbiguous() {
        when(medicationKnowledgeService.isListRequest("quel est le prix de doliprane")).thenReturn(false);
        when(medicamentChatbotSearchService.search("quel est le prix de doliprane", 10))
                .thenReturn(new MedicamentChatbotSearchService.MedicationSearchResult(
                        "doliprane",
                        List.of(
                                evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE"),
                                evidence("0002602", "DOLIPRANE SUSP 2,4%", "REMBOURSABLE")
                        ),
                        List.of(),
                        true,
                        true));

        ChatbotResponseDTO response = responseGenerator.generate(detection("quel est le prix de doliprane"));

        assertThat(response.getMessage()).contains("plusieurs medicaments possibles").contains("Precisez");
        assertThat(response.getMessage()).contains("DOLIPRANE COMP 500MG").contains("DOLIPRANE SUSP 2,4%");
        assertThat(response.getMessage()).doesNotContain("Prix public");
        assertThat(response.getMessage()).doesNotContain("1 000 FCFA");
    }

    @Test
    void doesNotReturnReimbursementWhenMedicationSearchIsAmbiguous() {
        when(medicationKnowledgeService.isListRequest("doliprane est il remboursable")).thenReturn(false);
        when(medicamentChatbotSearchService.search("doliprane est il remboursable", 10))
                .thenReturn(new MedicamentChatbotSearchService.MedicationSearchResult(
                        "doliprane",
                        List.of(
                                evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE"),
                                evidence("0002602", "DOLIPRANE SUSP 2,4%", "REMBOURSABLE")
                        ),
                        List.of(),
                        true,
                        true));

        ChatbotResponseDTO response = responseGenerator.generate(detection("doliprane est il remboursable"));

        assertThat(response.getMessage()).contains("plusieurs medicaments possibles").contains("Precisez");
        assertThat(response.getMessage()).doesNotContain("Prise en charge : oui");
        assertThat(response.getMessage()).doesNotContain("Statut officiel : REMBOURSABLE");
    }

    @Test
    void doesNotReturnRateWhenMedicationSearchIsAmbiguous() {
        when(medicationKnowledgeService.isListRequest("quel est le taux de doliprane")).thenReturn(false);
        when(medicamentChatbotSearchService.search("quel est le taux de doliprane", 10))
                .thenReturn(new MedicamentChatbotSearchService.MedicationSearchResult(
                        "doliprane",
                        List.of(
                                evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE"),
                                evidence("0002602", "DOLIPRANE SUSP 2,4%", "REMBOURSABLE")
                        ),
                        List.of(),
                        true,
                        true));

        ChatbotResponseDTO response = responseGenerator.generate(detection("quel est le taux de doliprane"));

        assertThat(response.getMessage()).contains("plusieurs medicaments possibles").contains("Precisez");
        assertThat(response.getMessage()).doesNotContain("Taux de couverture");
        assertThat(response.getMessage()).doesNotContain("80%");
    }

    @Test
    void doesNotReturnBusinessDataWhenMedicationIsNotFound() {
        when(medicationKnowledgeService.isListRequest("quel est le prix de medicamentinexistant 500mg")).thenReturn(false);
        when(medicamentChatbotSearchService.search("quel est le prix de medicamentinexistant 500mg", 10))
                .thenReturn(new MedicamentChatbotSearchService.MedicationSearchResult(
                        "medicamentinexistant 500mg",
                        List.of(),
                        List.of(),
                        false,
                        false));

        ChatbotResponseDTO response = responseGenerator.generate(detection("quel est le prix de medicamentinexistant 500mg"));

        assertThat(response.getFound()).isFalse();
        assertThat(response.getMessage()).contains("Je n'ai pas trouve ce medicament");
        assertThat(response.getMessage()).doesNotContain("FCFA");
        assertThat(response.getMessage()).doesNotContain("Taux de couverture");
        assertThat(response.getMessage()).doesNotContain("Statut officiel");
    }

    @Test
    void keepsSuggestionClearlySeparatedFromFoundResult() {
        MedicationEvidence suggestion = evidence("0002601", "DOLIPRANE COMP 500MG", "REMBOURSABLE");
        when(medicationKnowledgeService.isListRequest("quel est le prix de dolipran comp 500mg")).thenReturn(false);
        when(medicamentChatbotSearchService.search("quel est le prix de dolipran comp 500mg", 10))
                .thenReturn(new MedicamentChatbotSearchService.MedicationSearchResult(
                        "dolipran comp 500mg",
                        List.of(),
                        List.of(suggestion),
                        false,
                        false));

        ChatbotResponseDTO response = responseGenerator.generate(detection("quel est le prix de dolipran comp 500mg"));

        assertThat(response.getFound()).isFalse();
        assertThat(response.getMessage()).contains("Voulez-vous dire : DOLIPRANE COMP 500MG ?");
        assertThat(response.getMessage()).doesNotContain("Prix public");
        assertThat(response.getMessage()).doesNotContain("1 000 FCFA");
    }

    @Test
    void asksForPrecisionWhenSeveralMatchesExist() {
        when(medicationKnowledgeService.isListRequest("paracetamol")).thenReturn(false);
        when(medicamentChatbotSearchService.search("paracetamol", 10))
                .thenReturn(new MedicamentChatbotSearchService.MedicationSearchResult(
                        "paracetamol",
                        List.of(evidence("MED001", "Paracetamol 500"), evidence("MED002", "Paracetamol sirop")),
                        List.of(),
                        true,
                        true));

        ChatbotResponseDTO response = responseGenerator.generate(detection("paracetamol"));

        assertThat(response.getMessage()).contains("plusieurs medicaments possibles").contains("Precisez");
    }

    private IntentDetectionResult detection(String normalizedMessage) {
        return IntentDetectionResult.builder()
                .intent(ChatIntent.MEDICATION_SEARCH)
                .normalizedMessage(normalizedMessage)
                .score(0.9)
                .build();
    }

    private ChatbotResponseDTO generateWithContextIfNeeded(String normalizedQuestion, MedicationEvidence evidence) {
        if (normalizedQuestion.contains("ce medicament")) {
            when(medicamentChatbotSearchService.findActiveById(evidence.id())).thenReturn(Optional.of(evidence));
            return responseGenerator.generate(detection(normalizedQuestion), evidence.id());
        }
        return responseGenerator.generate(detection(normalizedQuestion));
    }

    private MedicamentChatbotSearchService.MedicationSearchResult single(MedicationEvidence evidence) {
        return new MedicamentChatbotSearchService.MedicationSearchResult(
                evidence.nom(), List.of(evidence), List.of(), true, false);
    }

    private MedicationEvidence evidence(String code, String nom) {
        return evidence(code, nom, "ENTENTE PREALABLE");
    }

    private MedicationEvidence evidence(String code, String nom, String statut) {
        return new MedicationEvidence(
                1L,
                code,
                nom,
                "Paracetamol",
                "500 mg",
                "Comprime",
                statut,
                "GENERIQUE",
                "Antalgique",
                new BigDecimal("1000"),
                new BigDecimal("800"),
                80.0,
                new BigDecimal("640"),
                new BigDecimal("160"),
                true
        );
    }

    private MedicationEvidence evidenceWithFinancialValues(
            BigDecimal prixPublic,
            BigDecimal partInam,
            BigDecimal partBeneficiaire,
            Double tauxCouverture
    ) {
        return new MedicationEvidence(
                1L,
                "MED001",
                "Doliprane",
                "Paracetamol",
                "500 mg",
                "Comprime",
                "REMBOURSABLE",
                "GENERIQUE",
                "Antalgique",
                prixPublic,
                null,
                tauxCouverture,
                partInam,
                partBeneficiaire,
                true
        );
    }
}
