package com.amuguide.backend.chat.service;

import com.amuguide.backend.entity.Medicament;
import com.amuguide.backend.repository.MedicamentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicamentChatbotSearchServiceTest {

    @Mock
    private MedicamentRepository repository;

    @ParameterizedTest
    @ValueSource(strings = {
            "Est-ce que DOLIPRANE COMP 500MG est pris en charge ?",
            "Quel est le taux de prise en charge de DOLIPRANE COMP 500MG ?",
            "Combien l'INAM paie pour DOLIPRANE COMP 500MG ?",
            "Combien dois-je payer pour DOLIPRANE COMP 500MG ?",
            "Quel est le prix de DOLIPRANE COMP 500MG ?",
            "DOLIPRANE COMP 500MG est-il en entente prealable ?",
            "DOLIPRANE COMP 500MG est-il en entente préalable ?",
            "Est-ce que DOLIPRANE COMP 500MG nécessite une entente préalable ?",
            "DOLIPRANE COMP 500MG nécessite-t-il un accord préalable ?",
            "Faut-il une entente préalable pour DOLIPRANE COMP 500MG ?"
    })
    void findsSameMedicationAcrossCommonQuestionFormulations(String question) {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane comp 500mg")).thenReturn(Optional.of(doliprane));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search(question, 10);

        assertThat(service.extractSearchTerm(question)).isEqualTo("doliprane comp 500mg");
        assertThat(result.found()).isTrue();
        assertThat(result.ambiguous()).isFalse();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("0002601");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Est-ce que je dois avoir une entente pr\u00e9alable pour DOLIPRANE COMP 500MG ?",
            "Une autorisation pr\u00e9alable est-elle n\u00e9cessaire pour DOLIPRANE COMP 500MG ?",
            "quel est le code de DOLIPRANE COMP 500MG",
            "Je cherche DOLIPRANE COMP 500MG"
    })
    void extractsMedicationNameFromAdditionalNaturalFormulations(String question) {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane comp 500mg")).thenReturn(Optional.of(doliprane));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search(question, 10);

        assertThat(service.extractSearchTerm(question)).isEqualTo("doliprane comp 500mg");
        assertThat(result.found()).isTrue();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("0002601");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DOLIPRANE COMP 500MG est-il rembours\u00e9 ?",
            "DOLIPRANE COMP 500MG est-il remboursable ?",
            "Est-ce que l'INAM rembourse DOLIPRANE COMP 500MG ?",
            "Quelle est la prise en charge de DOLIPRANE COMP 500MG ?",
            "Quel est le taux de remboursement de DOLIPRANE COMP 500MG ?",
            "\u00c0 combien l'INAM rembourse DOLIPRANE COMP 500MG ?",
            "Quelle est la part de l'INAM pour DOLIPRANE COMP 500MG ?",
            "Combien l'INAM paie pour DOLIPRANE COMP 500MG ?",
            "Quelle est la part du b\u00e9n\u00e9ficiaire pour DOLIPRANE COMP 500MG ?",
            "Quel est le reste \u00e0 ma charge pour DOLIPRANE COMP 500MG ?"
    })
    void extractsMedicationNameFromReimbursementAndAmountQuestions(String question) {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane comp 500mg")).thenReturn(Optional.of(doliprane));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search(question, 10);

        assertThat(service.extractSearchTerm(question)).isEqualTo("doliprane comp 500mg");
        assertThat(result.found()).isTrue();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("0002601");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Quel est le prix de DOLIPRANE COMP 500MG et combien l'INAM prend en charge ?",
            "DOLIPRANE COMP 500MG co\u00fbte combien et quelle est ma part \u00e0 payer ?",
            "Quel est le prix et le taux de remboursement de DOLIPRANE COMP 500MG ?",
            "DOLIPRANE COMP 500MG est-il remboursable et \u00e0 quel taux ?",
            "Quel est le prix de DOLIPRANE COMP 500MG et est-il en entente pr\u00e9alable ?",
            "Donne-moi le prix, la part INAM et la part b\u00e9n\u00e9ficiaire de DOLIPRANE COMP 500MG.",
            "Quel est le statut et le code de DOLIPRANE COMP 500MG ?",
            "DOLIPRANE COMP 500MG est-il pris en charge et combien dois-je payer ?",
            "Quel est le prix, le taux et le statut de DOLIPRANE COMP 500MG ?"
    })
    void extractsMedicationNameFromCombinedQuestions(String question) {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane comp 500mg")).thenReturn(Optional.of(doliprane));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search(question, 10);

        assertThat(service.extractSearchTerm(question)).isEqualTo("doliprane comp 500mg");
        assertThat(result.found()).isTrue();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("0002601");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Je veux savoir combien je paie pour DOLIPRANE COMP 500MG.",
            "Moi je paie combien pour DOLIPRANE COMP 500MG ?",
            "L'INAM prend combien sur DOLIPRANE COMP 500MG ?",
            "DOLIPRANE COMP 500MG, \u00e7a co\u00fbte combien ?",
            "C'est combien DOLIPRANE COMP 500MG ?",
            "DOLIPRANE COMP 500MG est pris en charge ?",
            "L'AMU prend en charge DOLIPRANE COMP 500MG ?",
            "Je peux avoir DOLIPRANE COMP 500MG avec l'AMU ?",
            "Est-ce que l'INAM couvre DOLIPRANE COMP 500MG ?",
            "Je dois faire une entente avant d'acheter DOLIPRANE COMP 500MG ?",
            "Il faut un accord avant pour DOLIPRANE COMP 500MG ?",
            "Je veux les infos sur DOLIPRANE COMP 500MG.",
            "Donne-moi les informations sur DOLIPRANE COMP 500MG."
    })
    void extractsMedicationNameFromConversationalQuestions(String question) {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane comp 500mg")).thenReturn(Optional.of(doliprane));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search(question, 10);

        assertThat(service.extractSearchTerm(question)).isEqualTo("doliprane comp 500mg");
        assertThat(result.found()).isTrue();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("0002601");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Quel est le prix de DOLIPRANE COMP 500 MG ?",
            "Quel est le prix de DOLIPRANE COMP. 500MG ?",
            "Quel est le prix de Doliprane Comp 500mg ?",
            "Quel est le prix de doliprane comp 500 mg ?",
            "Quel est le prix de DOLIPRANE COMP500MG ?",
            "Quel est le prix de DOLIPRANE COMP 500 MG.",
            "Quel est le prix de DOLIPRANE-COMP-500MG ?",
            "DOLIPRANE COMP 500MG est-il remboursable ?",
            "doliprane comp 500mg est il remboursable",
            "Quel est le prix de DOLIPRANE   COMP   500MG ?"
    })
    void normalizesSafeWritingVariantsForExactMedicationName(String question) {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane comp 500mg")).thenReturn(Optional.of(doliprane));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search(question, 10);

        assertThat(service.extractSearchTerm(question)).isEqualTo("doliprane comp 500mg");
        assertThat(result.found()).isTrue();
        assertThat(result.ambiguous()).isFalse();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("0002601");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DOLIPRANE comprimé 500 mg",
            "DOLIPRANE comprime 500 mg",
            "DOLIPRANE comprimés 500 mg",
            "DOLIPRANE comprimes 500 mg",
            "DOLIPRANE comprimé 500 milligrammes",
            "DOLIPRANE comp 500 milligrammes"
    })
    void normalizesSafeVoiceMedicationNameVariantsForTabletAndDosage(String question) {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane comp 500mg")).thenReturn(Optional.of(doliprane));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search(question, 10);

        assertThat(service.extractSearchTerm(question)).isEqualTo("doliprane comp 500mg");
        assertThat(result.found()).isTrue();
        assertThat(result.ambiguous()).isFalse();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("0002601");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DOLIPRANE comprimé effervescent 500 mg",
            "DOLIPRANE comprime effervescente 500 milligrammes",
            "DOLIPRANE comp eff 500 mg"
    })
    void normalizesSafeVoiceEffervescentVariantsWhenReferenceUsesEffAbbreviation(String question) {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament dolipraneEff = medicament("0002603", "DOLIPRANE COMP EFF 500MG", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane comp eff 500mg")).thenReturn(Optional.of(dolipraneEff));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search(question, 10);

        assertThat(service.extractSearchTerm(question)).isEqualTo("doliprane comp eff 500mg");
        assertThat(result.found()).isTrue();
        assertThat(result.ambiguous()).isFalse();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("0002603");
    }

    @Test
    void normalizesMilligrammesWithoutInventingTabletForm() {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane 500mg")).thenReturn(Optional.empty());
        when(repository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane 500mg")).thenReturn(List.of());
        when(repository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane 500mg")).thenReturn(List.of());
        when(repository.searchForChatbot(eq("doliprane 500mg"), any(Pageable.class))).thenReturn(List.of());
        when(repository.searchForChatbot(eq("doliprane"), any(Pageable.class))).thenReturn(List.of(doliprane));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search("DOLIPRANE 500 milligrammes", 10);

        assertThat(service.extractSearchTerm("DOLIPRANE 500 milligrammes")).isEqualTo("doliprane 500mg");
        assertThat(result.found()).isTrue();
        assertThat(result.ambiguous()).isFalse();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("0002601");
    }

    @Test
    void keepsAmbiguityAfterSafeVoiceNormalization() {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament comp = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");
        Medicament compEff = medicament("0002603", "DOLIPRANE COMP EFF 500MG", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane comp 500mg")).thenReturn(Optional.empty());
        when(repository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane comp 500mg")).thenReturn(List.of());
        when(repository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane comp 500mg")).thenReturn(List.of());
        when(repository.searchForChatbot(eq("doliprane comp 500mg"), any(Pageable.class))).thenReturn(List.of());
        when(repository.searchForChatbot(eq("doliprane"), any(Pageable.class))).thenReturn(List.of(comp, compEff));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search("DOLIPRANE comprimé 500 milligrammes", 10);

        assertThat(service.extractSearchTerm("DOLIPRANE comprimé 500 milligrammes")).isEqualTo("doliprane comp 500mg");
        assertThat(result.found()).isTrue();
        assertThat(result.ambiguous()).isTrue();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("0002601", "0002603");
    }

    @Test
    void doesNotNormalizeSimilarUnrelatedWordsAsTabletForm() {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");

        lenient().when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        lenient().when(repository.findByNomIgnoreCaseAndActifTrue("doliprane compost 500mg")).thenReturn(Optional.empty());
        lenient().when(repository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane compost 500mg")).thenReturn(List.of());
        lenient().when(repository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane compost 500mg")).thenReturn(List.of());
        lenient().when(repository.searchForChatbot(eq("doliprane compost 500mg"), any(Pageable.class))).thenReturn(List.of());
        lenient().when(repository.searchForChatbot(eq("doliprane"), any(Pageable.class))).thenReturn(List.of(doliprane));
        when(repository.searchForChatbot(eq("doli"), any(Pageable.class))).thenReturn(List.of());

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search("DOLIPRANE compost 500 milligrammes", 10);

        assertThat(service.extractSearchTerm("DOLIPRANE compost 500 milligrammes")).isEqualTo("doliprane compost 500mg");
        assertThat(result.found()).isFalse();
    }

    @Test
    void findsPartialMedicationNameOnlyWhenTokenMatchIsUnique() {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane 500mg")).thenReturn(Optional.empty());
        when(repository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane 500mg")).thenReturn(List.of());
        when(repository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane 500mg")).thenReturn(List.of());
        when(repository.searchForChatbot(eq("doliprane 500mg"), any(Pageable.class))).thenReturn(List.of());
        when(repository.searchForChatbot(eq("doliprane"), any(Pageable.class))).thenReturn(List.of(doliprane));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search("Quel est le prix de DOLIPRANE 500MG ?", 10);

        assertThat(service.extractSearchTerm("Quel est le prix de DOLIPRANE 500MG ?")).isEqualTo("doliprane 500mg");
        assertThat(result.found()).isTrue();
        assertThat(result.ambiguous()).isFalse();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("0002601");
    }

    @Test
    void keepsPartialMedicationNameAmbiguousWhenSeveralTokenMatchesExist() {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament comp = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");
        Medicament sachet = medicament("0002602", "DOLIPRANE SACHET 500MG", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane 500mg")).thenReturn(Optional.empty());
        when(repository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane 500mg")).thenReturn(List.of());
        when(repository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane 500mg")).thenReturn(List.of());
        when(repository.searchForChatbot(eq("doliprane 500mg"), any(Pageable.class))).thenReturn(List.of());
        when(repository.searchForChatbot(eq("doliprane"), any(Pageable.class))).thenReturn(List.of(comp, sachet));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search("Quel est le prix de DOLIPRANE 500MG ?", 10);

        assertThat(result.found()).isTrue();
        assertThat(result.ambiguous()).isTrue();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("0002601", "0002602");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Quel est le prix de DOLIPRAN COMP 500MG ?",
            "Quel est le prix de DOLIPRANE COM 500MG ?",
            "Quel est le prix de DOLIPRANE COMP 500M ?"
    })
    void doesNotForceLightTyposAsMedicationMatches(String question) {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");
        String searchTerm = service.extractSearchTerm(question);

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue(searchTerm)).thenReturn(Optional.empty());
        when(repository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc(searchTerm)).thenReturn(List.of());
        when(repository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc(searchTerm)).thenReturn(List.of());
        when(repository.searchForChatbot(eq(searchTerm), any(Pageable.class))).thenReturn(List.of());
        lenient().when(repository.searchForChatbot(eq(searchTerm.split("\\s+")[0]), any(Pageable.class))).thenReturn(List.of(doliprane));
        when(repository.searchForChatbot(eq(searchTerm.substring(0, Math.min(4, searchTerm.length()))), any(Pageable.class))).thenReturn(List.of(doliprane));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search(question, 10);

        assertThat(result.found()).isFalse();
    }

    @Test
    void returnsAmbiguousForGenericDosageSearchWhenSeveralCandidatesExist() {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");
        Medicament paracetamol = medicament("0003601", "PARACETAMOL COMP 500MG", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("500mg")).thenReturn(Optional.empty());
        when(repository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc("500mg")).thenReturn(List.of(doliprane, paracetamol));
        when(repository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc("500mg")).thenReturn(List.of());
        when(repository.searchForChatbot(eq("500mg"), any(Pageable.class))).thenReturn(List.of(doliprane, paracetamol));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search("Quel est le prix de 500MG ?", 10);

        assertThat(result.found()).isTrue();
        assertThat(result.ambiguous()).isTrue();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("0002601", "0003601");
    }

    @Test
    void returnsAmbiguousForGenericFormAndDosageSearchWhenSeveralCandidatesExist() {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("0002601", "DOLIPRANE COMP 500MG", "Paracetamol");
        Medicament ibuprofen = medicament("0004701", "IBUPROFENE COMP 500MG", "Ibuprofene");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("comp 500mg")).thenReturn(Optional.empty());
        when(repository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc("comp 500mg")).thenReturn(List.of(doliprane, ibuprofen));
        when(repository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc("comp 500mg")).thenReturn(List.of());
        when(repository.searchForChatbot(eq("comp 500mg"), any(Pageable.class))).thenReturn(List.of(doliprane, ibuprofen));
        when(repository.searchForChatbot(eq("comp"), any(Pageable.class))).thenReturn(List.of(doliprane, ibuprofen));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search("Quel est le prix de COMP 500MG ?", 10);

        assertThat(result.found()).isTrue();
        assertThat(result.ambiguous()).isTrue();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("0002601", "0004701");
    }

    @Test
    void returnsNotFoundForAbsentMedicationName() {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("medicamentinexistant 500mg")).thenReturn(Optional.empty());
        when(repository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc("medicamentinexistant 500mg")).thenReturn(List.of());
        when(repository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc("medicamentinexistant 500mg")).thenReturn(List.of());
        when(repository.searchForChatbot(eq("medicamentinexistant 500mg"), any(Pageable.class))).thenReturn(List.of());
        when(repository.searchForChatbot(eq("medicamentinexistant"), any(Pageable.class))).thenReturn(List.of());
        when(repository.searchForChatbot(eq("medi"), any(Pageable.class))).thenReturn(List.of());

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search("Quel est le prix de MEDICAMENTINEXISTANT 500MG ?", 10);

        assertThat(result.found()).isFalse();
        assertThat(result.ambiguous()).isFalse();
        assertThat(result.matches()).isEmpty();
        assertThat(result.suggestions()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "L'INAM paie combien sur ce medicament ?",
            "Quel montant reste pour moi ?",
            "Et moi je paie quoi ?",
            "L'INAM paie quoi ?"
    })
    void doesNotInventMedicationForContextOnlyQuestions(String question) {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search(question, 10);

        assertThat(service.extractSearchTerm(question)).isBlank();
        assertThat(result.found()).isFalse();
    }

    @Test
    void findsMedicationByExactName() {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("MED001", "Doliprane", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane")).thenReturn(Optional.of(doliprane));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search("Doliprane", 10);

        assertThat(result.found()).isTrue();
        assertThat(result.ambiguous()).isFalse();
        assertThat(result.matches()).extracting(MedicationEvidence::code).containsExactly("MED001");
    }

    @Test
    void excludesHistoricalMedicationWithoutOfficialFieldsEvenWhenExactNameMatches() {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament legacyDoliprane = Medicament.builder()
                .id(99L)
                .code("LEGACY001")
                .nom("Doliprane")
                .dci("Paracetamol")
                .actif(true)
                .prisEnCharge(true)
                .build();

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane")).thenReturn(Optional.of(legacyDoliprane));
        when(repository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane")).thenReturn(List.of(legacyDoliprane));
        when(repository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane")).thenReturn(List.of());
        when(repository.searchForChatbot(eq("doliprane"), any(Pageable.class))).thenReturn(List.of());
        when(repository.searchForChatbot(eq("doli"), any(Pageable.class))).thenReturn(List.of());

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search("Doliprane", 10);

        assertThat(result.found()).isFalse();
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void findsMedicationByDci() {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament paracetamol = medicament("MED002", "Paracetamol 500", "Paracetamol");

        when(repository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc("paracetamol"))
                .thenReturn(List.of(paracetamol));

        List<MedicationEvidence> result = service.searchByDci("Quels medicaments contiennent du paracetamol ?", 10);

        assertThat(result).extracting(MedicationEvidence::nom).containsExactly("Paracetamol 500");
    }

    @Test
    void returnsMultipleResultsForSharedDci() {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament first = medicament("MED002", "Paracetamol 500", "Paracetamol");
        Medicament second = medicament("MED003", "Paracetamol sirop", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("paracetamol")).thenReturn(Optional.empty());
        when(repository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc("paracetamol")).thenReturn(List.of(first, second));
        when(repository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc("paracetamol")).thenReturn(List.of(first, second));
        when(repository.searchForChatbot(eq("paracetamol"), any(Pageable.class))).thenReturn(List.of(first, second));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search("paracetamol", 10);

        assertThat(result.found()).isTrue();
        assertThat(result.ambiguous()).isTrue();
        assertThat(result.matches()).hasSize(2);
    }

    @Test
    void findsMedicationByReasonablePartialName() {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament doliprane = medicament("MED001", "Doliprane 500", "Paracetamol");

        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByNomIgnoreCaseAndActifTrue("doliprane")).thenReturn(Optional.empty());
        when(repository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane")).thenReturn(List.of(doliprane));
        when(repository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc("doliprane")).thenReturn(List.of());
        when(repository.searchForChatbot(eq("doliprane"), any(Pageable.class))).thenReturn(List.of(doliprane));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search("doliprane", 10);

        assertThat(result.found()).isTrue();
        assertThat(result.matches()).extracting(MedicationEvidence::nom).containsExactly("Doliprane 500");
    }

    @Test
    void doesNotAssertTypoAsFound() {
        MedicamentChatbotSearchService service = new MedicamentChatbotSearchService(repository);
        Medicament suggestion = medicament("MED001", "Doliprane", "Paracetamol");

        lenient().when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        lenient().when(repository.findByNomIgnoreCaseAndActifTrue("dolipran")).thenReturn(Optional.empty());
        lenient().when(repository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc("dolipran")).thenReturn(List.of());
        lenient().when(repository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc("dolipran")).thenReturn(List.of());
        lenient().when(repository.searchForChatbot(eq("dolipran"), any(Pageable.class))).thenReturn(List.of());
        when(repository.searchForChatbot(eq("doli"), any(Pageable.class))).thenReturn(List.of(suggestion));

        MedicamentChatbotSearchService.MedicationSearchResult result = service.search("dolipran", 10);

        assertThat(result.found()).isFalse();
        assertThat(result.suggestions()).extracting(MedicationEvidence::nom).containsExactly("Doliprane");
    }

    private Medicament medicament(String code, String nom, String dci) {
        return Medicament.builder()
                .id((long) code.hashCode())
                .code(code)
                .nom(nom)
                .dci(dci)
                .dosage("500 mg")
                .formePharmaceutique("Comprime")
                .statut("ENTENTE PREALABLE")
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
}
