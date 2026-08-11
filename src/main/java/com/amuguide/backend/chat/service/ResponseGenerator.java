package com.amuguide.backend.chat.service;

import com.amuguide.backend.chat.nlp.ChatIntent;
import com.amuguide.backend.chat.nlp.IntentDetectionResult;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.dto.ChatbotSourceDTO;
import com.amuguide.backend.entity.Pharmacie;
import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.chat.service.MedicamentChatbotSearchService.MedicationSearchResult;
import com.amuguide.backend.chat.service.StructureSessionContextService.StructureReference;
import com.amuguide.backend.enums.TypeStructure;
import com.amuguide.backend.repository.PharmacieRepository;
import com.amuguide.backend.repository.PrestationRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class ResponseGenerator {

    private static final List<String> DEFAULT_SUGGESTIONS = List.of("Consultation", "Radiologie", "Hopital proche");

    private final PrestationRepository prestationRepository;
    private final StructureSanteRepository structureSanteRepository;
    private final PharmacieRepository pharmacieRepository;
    private final MedicationKnowledgeService medicationKnowledgeService;
    private final MedicamentChatbotSearchService medicamentChatbotSearchService;
    private final HospitalisationTarifChatbotService hospitalisationTarifChatbotService;
    private final AiAgentService aiAgentService;

    public ChatbotResponseDTO generate(IntentDetectionResult detection) {
        return generate(detection, null);
    }

    public ChatbotResponseDTO generate(IntentDetectionResult detection, Long contextualMedicationId) {
        return generate(detection, contextualMedicationId, null);
    }

    public ChatbotResponseDTO generate(IntentDetectionResult detection, Long contextualMedicationId, Long contextualPrestationId) {
        return generate(detection, contextualMedicationId, contextualPrestationId, null);
    }

    public ChatbotResponseDTO generate(
            IntentDetectionResult detection,
            Long contextualMedicationId,
            Long contextualPrestationId,
            StructureReference contextualStructureReference
    ) {
        if (shouldResolveMedicationContext(detection, contextualMedicationId)) {
            return medicationSearch(detection.toBuilder()
                    .intent(ChatIntent.MEDICATION_SEARCH)
                    .keyword("medicament")
                    .build(), contextualMedicationId);
        }
        return switch (detection.getIntent()) {
            case GREETING -> greeting();
            case AMU_INFO -> amuInfo();
            case COVERAGE -> coverage(detection, contextualPrestationId);
            case MEDICATION_SEARCH -> medicationSearch(detection, contextualMedicationId);
            case HOSPITAL_SEARCH -> shouldResolveStructureContext(detection)
                    ? contextualStructureAnswer(detection, contextualStructureReference)
                    : hospitalSearch(detection);
            case PROCEDURE -> procedure(detection);
            case AGENT_ASSISTED -> fallback(detection);
            case FALLBACK -> fallback(detection);
        };
    }

    private boolean shouldResolveStructureContext(IntentDetectionResult detection) {
        if (detection == null || detection.getNormalizedMessage() == null) {
            return false;
        }
        if (hasExplicitStructureSearchName(detection.getNormalizedMessage())) {
            return false;
        }
        return pointsToContextualStructure(detection.getNormalizedMessage())
                && asksStructureDetail(detection.getNormalizedMessage());
    }

    private ChatbotResponseDTO greeting() {
        return base(ChatIntent.GREETING, "Bonjour. Comment puis-je vous aider sur l'AMU ?", DEFAULT_SUGGESTIONS);
    }

    private ChatbotResponseDTO amuInfo() {
        return base(ChatIntent.AMU_INFO,
                "L'AMU, ou Assurance Maladie Universelle, est un dispositif qui facilite l'acces aux soins de sante en prenant en charge une partie des frais medicaux des assures. Elle peut couvrir des prestations comme les consultations, l'hospitalisation, certains medicaments, la radiologie ou les examens, selon les taux et conditions definis.\n\nAvec AMU Guide, vous pouvez verifier si une prestation est couverte, connaitre le taux de couverture, voir les documents requis et rechercher une structure de sante agreee.",
                List.of("Consultation couverte ?", "Documents requis", "Hopital proche"))
                .toBuilder()
                .sources(List.of(backendSource("INFORMATION_AMU", "Informations generales AMU")))
                .build();
    }

    private ChatbotResponseDTO coverage(IntentDetectionResult detection, Long contextualPrestationId) {
        String medicationSearchTerm = extractMedicationSearchTerm(detection.getNormalizedMessage());
        if (looksLikePreciseMedicationQuestion(detection.getNormalizedMessage(), medicationSearchTerm)) {
            return medicationSearch(detection.toBuilder()
                    .intent(ChatIntent.MEDICATION_SEARCH)
                    .keyword("medicament")
                    .build(), null);
        }

        if (hospitalisationTarifChatbotService.isHospitalisationTarifQuestion(detection)) {
            return hospitalisationTarifChatbotService.answer(detection);
        }

        if (isGeneralPrestationsListQuestion(detection.getNormalizedMessage(), detection.getKeyword())) {
            return coveredPrestationsSummary();
        }

        List<Prestation> matches = findPrestations(detection.getKeyword(), detection.getNormalizedMessage());
        if (matches.isEmpty()) {
            if (isContextualPrestationQuestion(detection.getNormalizedMessage())) {
                return contextualPrestationAnswer(contextualPrestationId);
            }
            return aiAgentService.answer(detection);
        }
        if (matches.size() > 1) {
            return ambiguousPrestationAnswer(matches);
        }

        return prestationAnswer(matches.get(0));
    }

    private ChatbotResponseDTO contextualPrestationAnswer(Long contextualPrestationId) {
        if (contextualPrestationId == null) {
            return prestationContextMissing();
        }
        return prestationRepository.findById(contextualPrestationId)
                .map(this::prestationAnswer)
                .orElseGet(this::prestationContextMissing);
    }

    private ChatbotResponseDTO prestationAnswer(Prestation prestation) {
        if (prestation == null) {
            return prestationContextMissing();
        }

        String message = Boolean.TRUE.equals(prestation.getPrisEnCharge())
                ? String.format(Locale.FRANCE, "Oui. %s est prise en charge%s.",
                prestation.getNomActe(),
                prestation.getTauxCouverture() == null ? "" : String.format(Locale.FRANCE, " a %.0f%%", prestation.getTauxCouverture()))
                : String.format("%s n'est pas prise en charge par l'AMU.", prestation.getNomActe());

        return ChatbotResponseDTO.builder()
                .statut(Boolean.TRUE.equals(prestation.getPrisEnCharge()) ? "COUVERT" : "NON_COUVERT")
                .intent(ChatIntent.COVERAGE.name())
                .found(true)
                .message(message)
                .codeActe(prestation.getCodeActe())
                .nomActe(prestation.getNomActe())
                .prisEnCharge(prestation.getPrisEnCharge())
                .tauxCouverture(prestation.getTauxCouverture())
                .conditionsPriseEnCharge(prestation.getConditionsPriseEnCharge())
                .documentsRequis(prestation.getDocumentsRequis())
                .sources(List.of(prestationSource(prestation)))
                .suggestionList(List.of("Documents requis", "Hopital agree", "Autre prestation"))
                .suggestions("Suggestions : Documents requis, Hopital agree, Autre prestation")
                .build();
    }

    private ChatbotResponseDTO prestationContextMissing() {
        return base(ChatIntent.COVERAGE,
                "Veuillez preciser la prestation concernee.",
                List.of("Consultation", "Hospitalisation", "Medicaments"))
                .toBuilder()
                .found(false)
                .sources(List.of(backendSource("PRESTATION", "Referentiel prestations AMU PostgreSQL")))
                .build();
    }

    private ChatbotResponseDTO ambiguousPrestationAnswer(List<Prestation> matches) {
        List<Prestation> limitedMatches = matches.stream().limit(5).toList();
        String items = limitedMatches.stream()
                .map(prestation -> "\n- " + prestation.getNomActe())
                .reduce("", (left, right) -> left + right);
        return base(ChatIntent.COVERAGE,
                "J'ai trouve plusieurs prestations possibles :" + items
                        + "\n\nPouvez-vous preciser la prestation concernee ?",
                limitedMatches.stream().map(Prestation::getNomActe).toList())
                .toBuilder()
                .found(false)
                .sources(limitedMatches.stream().map(this::prestationSource).toList())
                .build();
    }

    private ChatbotResponseDTO contextualStructureAnswer(IntentDetectionResult detection, StructureReference contextualStructureReference) {
        if (contextualStructureReference == null) {
            return structureContextMissing();
        }
        if ("PHARMACIE".equals(contextualStructureReference.sourceType())) {
            return structureSanteRepository.findById(contextualStructureReference.id())
                    .filter(this::isOfficialPharmacyStructure)
                    .map(pharmacie -> contextualOfficialPharmacyAnswer(detection, pharmacie))
                    .orElseGet(this::structureContextMissing);
        }
        if ("STRUCTURE".equals(contextualStructureReference.sourceType())) {
            return structureSanteRepository.findById(contextualStructureReference.id())
                    .map(structure -> contextualHealthStructureAnswer(detection, structure))
                    .orElseGet(this::structureContextMissing);
        }
        return structureContextMissing();
    }

    private ChatbotResponseDTO contextualHealthStructureAnswer(IntentDetectionResult detection, StructureSante structure) {
        String normalizedMessage = detection.getNormalizedMessage();
        String message;
        if (asksAddress(normalizedMessage)) {
            message = "Adresse : " + addressValue(structure.getAdresse()) + ".";
        } else if (asksPhone(normalizedMessage)) {
            message = "Telephone : " + nullToUnavailable(structure.getTelephone()) + ".";
        } else if (asksRegion(normalizedMessage)) {
            message = "Region : " + nullToUnavailable(structure.getRegion()) + ".";
        } else if (asksCity(normalizedMessage)) {
            message = "Ville : " + nullToUnavailable(structure.getVille()) + ".";
        } else if (asksAgreement(normalizedMessage)) {
            message = Boolean.TRUE.equals(structure.getAgrementAMU())
                    ? "Oui, cette structure est conventionnee AMU."
                    : "Le referentiel n'indique pas cette structure comme conventionnee AMU.";
        } else if (asksDistance(normalizedMessage)) {
            message = contextualDistanceMessage(detection, structure.getLatitude(), structure.getLongitude());
        } else {
            message = formatSingleStructure(structure);
        }

        return ChatbotResponseDTO.builder()
                .intent(ChatIntent.HOSPITAL_SEARCH.name())
                .found(true)
                .message(message)
                .sources(List.of(structureSource(structure)))
                .suggestionList(List.of("Adresse", "Telephone", "Autre structure"))
                .suggestions("Suggestions : Adresse, Telephone, Autre structure")
                .build();
    }

    private ChatbotResponseDTO contextualPharmacieAnswer(IntentDetectionResult detection, Pharmacie pharmacie) {
        String normalizedMessage = detection.getNormalizedMessage();
        String message;
        if (asksAddress(normalizedMessage)) {
            message = "Adresse : " + addressValue(pharmacie.getAdresse()) + ".";
        } else if (asksPhone(normalizedMessage)) {
            message = "Telephone : " + nullToUnavailable(pharmacie.getTelephone()) + ".";
        } else if (asksRegion(normalizedMessage)) {
            message = "Region : " + nullToUnavailable(pharmacie.getRegion()) + ".";
        } else if (asksCity(normalizedMessage)) {
            message = "Ville : " + nullToUnavailable(pharmacie.getVille()) + ".";
        } else if (asksAgreement(normalizedMessage)) {
            message = Boolean.TRUE.equals(pharmacie.getAgreee())
                    ? "Oui, cette pharmacie est conventionnee AMU."
                    : "Le referentiel n'indique pas cette pharmacie comme conventionnee AMU.";
        } else if (asksDistance(normalizedMessage)) {
            message = contextualDistanceMessage(detection, pharmacie.getLatitude(), pharmacie.getLongitude());
        } else {
            message = formatSinglePharmacie(pharmacie);
        }

        return ChatbotResponseDTO.builder()
                .intent(ChatIntent.HOSPITAL_SEARCH.name())
                .found(true)
                .message(message)
                .sources(List.of(pharmacieSource(pharmacie)))
                .suggestionList(List.of("Adresse", "Telephone", "Autre pharmacie"))
                .suggestions("Suggestions : Adresse, Telephone, Autre pharmacie")
                .build();
    }

    private ChatbotResponseDTO contextualOfficialPharmacyAnswer(IntentDetectionResult detection, StructureSante pharmacie) {
        String normalizedMessage = detection.getNormalizedMessage();
        String message;
        if (asksAddress(normalizedMessage)) {
            message = "Adresse : " + addressValue(pharmacie.getAdresse()) + ".";
        } else if (asksPhone(normalizedMessage)) {
            message = "Telephone non renseigne dans le referentiel officiel.";
        } else if (asksRegion(normalizedMessage)) {
            message = "Region : " + nullToUnavailable(pharmacie.getRegion()) + ".";
        } else if (asksCity(normalizedMessage)) {
            message = "Ville : non disponible.";
        } else if (asksAgreement(normalizedMessage)) {
            message = "Oui, cette pharmacie est conventionnee AMU selon le referentiel officiel.";
        } else if (asksDistance(normalizedMessage)) {
            message = contextualDistanceMessage(detection, pharmacie.getLatitude(), pharmacie.getLongitude());
        } else {
            message = formatSingleOfficialPharmacy(pharmacie);
        }

        return ChatbotResponseDTO.builder()
                .intent(ChatIntent.HOSPITAL_SEARCH.name())
                .found(true)
                .message(message)
                .sources(List.of(officialPharmacySource(pharmacie)))
                .suggestionList(List.of("Adresse", "Telephone", "Autre pharmacie"))
                .suggestions("Suggestions : Adresse, Telephone, Autre pharmacie")
                .build();
    }

    private ChatbotResponseDTO structureContextMissing() {
        return base(ChatIntent.HOSPITAL_SEARCH,
                "Veuillez preciser la structure de sante concernee.",
                List.of("Indiquer le nom", "Pharmacie a Lome", "Hopital conventionne"))
                .toBuilder()
                .found(false)
                .sources(List.of())
                .build();
    }

    private ChatbotResponseDTO hospitalSearch(IntentDetectionResult detection) {
        String normalizedMessage = detection.getNormalizedMessage();
        boolean pharmacyRequested = normalizedMessage.contains("pharmacie");
        boolean hospitalRequested = normalizedMessage.contains("hopital") || normalizedMessage.contains("hopitaux") || normalizedMessage.contains("chu");
        boolean clinicRequested = normalizedMessage.contains("clinique");
        boolean centerRequested = normalizedMessage.contains("centre");
        boolean nearMeRequested = normalizedMessage.contains("pres de moi") || normalizedMessage.contains("proche de moi");

        if (pharmacyRequested) {
            return pharmacySearch(detection);
        }

        if (nearMeRequested && (detection.getLatitude() == null || detection.getLongitude() == null)) {
            return base(ChatIntent.HOSPITAL_SEARCH,
                    "Pour rechercher une structure pres de vous, veuillez autoriser la geolocalisation ou preciser votre ville.",
                    List.of("Clinique a Lome", "Hopital a Kara", "Pharmacie agreee"))
                    .toBuilder()
                    .found(false)
                    .build();
        }

        List<StructureSante> structures = structureSanteRepository.findByAgrementAMUTrue().stream()
                .filter(this::isActiveStructure)
                .filter(structure -> !hospitalRequested || structure.getType() != null
                        && TypeStructure.HOPITAL.equals(structure.getType()))
                .filter(structure -> !clinicRequested || structure.getType() != null
                        && TypeStructure.CLINIQUE.equals(structure.getType()))
                .filter(structure -> !centerRequested || structure.getType() != null
                        && TypeStructure.CENTRE_DE_SANTE.equals(structure.getType()))
                .sorted((left, right) -> compareByDistanceWhenAvailable(left, right, detection))
                .toList();

        LocationFilter locationFilter = findStructureLocationFilter(normalizedMessage, detection.getCity(), structures);
        if (locationFilter != null) {
            structures = structures.stream()
                    .filter(structure -> matchesStructureLocation(structure, locationFilter.normalizedValue()))
                    .toList();
        }

        List<StructureSante> relevantStructures = filterRelevantStructures(structures, normalizedMessage);
        if (!relevantStructures.isEmpty()) {
            structures = relevantStructures;
        } else if (hasSpecificStructureSearchTokens(normalizedMessage)) {
            structures = List.of();
        }

        if (structures.isEmpty()) {
            String location = locationFilter == null ? "" : " a " + locationFilter.label();
            return base(ChatIntent.HOSPITAL_SEARCH,
                    "Aucune structure agreee trouvee" + location + ". Essayez une autre ville.",
                    List.of("Lome", "Kara", "Consultation"))
                    .toBuilder()
                    .found(false)
                    .build();
        }

        String title = locationFilter == null
                ? "Structures agreees trouvees :"
                : "Structures trouvees a " + locationFilter.label() + " :";
        if (structures.size() == 1) {
            StructureSante structure = structures.get(0);
            if (asksAddress(normalizedMessage) || asksPhone(normalizedMessage)) {
                return contextualHealthStructureAnswer(detection, structure);
            }
            return ChatbotResponseDTO.builder()
                    .intent(ChatIntent.HOSPITAL_SEARCH.name())
                    .found(true)
                    .message(formatSingleStructure(structure))
                    .sources(List.of(structureSource(structure)))
                    .suggestionList(List.of("Autre pharmacie", "Pharmacie a Lome", "Structure proche"))
                    .suggestions("Suggestions : Autre pharmacie, Pharmacie a Lome, Structure proche")
                    .build();
        }

        String names = structures.stream()
                .limit(5)
                .map(this::formatStructureLine)
                .reduce("", (left, right) -> left + "\n" + right);
        String more = structures.size() > 5
                ? "\n\n" + structures.size() + " resultats trouves. Voici les 5 premiers ; precisez la ville, la region ou le type pour affiner."
                : "";

        return ChatbotResponseDTO.builder()
                .intent(ChatIntent.HOSPITAL_SEARCH.name())
                .found(true)
                .message(title + names + more + "\n\nVoulez-vous afficher la carte ?")
                .sources(structures.stream()
                        .limit(5)
                        .map(this::structureSource)
                        .toList())
                .suggestionList(List.of("Afficher la carte", "Consultation", "Autre ville"))
                .suggestions("Suggestions : Afficher la carte, Consultation, Autre ville")
                .build();
    }

    private ChatbotResponseDTO medicationSearch(IntentDetectionResult detection, Long contextualMedicationId) {
        if (medicationKnowledgeService.isListRequest(detection.getNormalizedMessage())) {
            List<MedicationEvidence> medications = medicamentChatbotSearchService.listActive(25);
            String items = medications.stream()
                    .map(this::medicationSummaryLine)
                    .reduce("", (left, right) -> left + "\n" + right);

            return base(ChatIntent.MEDICATION_SEARCH,
                    "Voici des medicaments trouves dans le referentiel AMU disponible en base PostgreSQL :"
                            + items
                            + "\n\nLa liste complete est longue. Donnez le nom, le code ou la DCI d'un medicament pour une recherche precise.",
                    List.of("Paracetamol", "Amoxicilline", "Ibuprofene"))
                    .toBuilder()
                    .sources(medications.stream().map(this::medicationSource).toList())
                    .build();
        }

        if (isDciListQuestion(detection.getNormalizedMessage())) {
            List<MedicationEvidence> matches = medicamentChatbotSearchService.searchByDci(detection.getNormalizedMessage(), 10);
            if (matches.isEmpty()) {
                return medicationNotFound(detection, List.of());
            }
            String items = matches.stream()
                    .map(this::medicationSummaryLine)
                    .reduce("", (left, right) -> left + "\n" + right);
            return base(ChatIntent.MEDICATION_SEARCH,
                    "Voici les medicaments du referentiel AMU disponible contenant cette DCI :"
                            + items
                            + "\n\nPour connaitre le taux, la part INAM ou la part beneficiaire, indiquez un medicament precis.",
                    List.of("Taux couverture", "Part INAM", "Autre medicament"))
                    .toBuilder()
                .sources(matches.stream().map(this::medicationSource).toList())
                .build();
        }

        String medicationSearchTerm = extractMedicationSearchTerm(detection.getNormalizedMessage());
        if (medicationSearchTerm.isBlank() && isContextualMedicationQuestion(detection.getNormalizedMessage())) {
            return contextualMedicationAnswer(detection, contextualMedicationId);
        }

        MedicationSearchResult result = medicamentChatbotSearchService.search(detection.getNormalizedMessage(), 10);
        if (!result.found()) {
            return medicationNotFound(detection, result.suggestions());
        }

        if (result.ambiguous()) {
            String items = result.matches().stream()
                    .limit(5)
                    .map(this::medicationSummaryLine)
                    .reduce("", (left, right) -> left + "\n" + right);
            return base(ChatIntent.MEDICATION_SEARCH,
                    "J'ai trouve plusieurs medicaments possibles dans le referentiel AMU disponible :"
                            + items
                            + "\n\nPrecisez le nom complet, le dosage ou le code pour obtenir une reponse fiable.",
                    List.of("Taux couverture", "Part INAM", "Autre medicament"))
                    .toBuilder()
                    .sources(result.matches().stream().limit(5).map(this::medicationSource).toList())
                    .build();
        }

        MedicationEvidence medication = result.matches().get(0);
        return base(ChatIntent.MEDICATION_SEARCH,
                medicationAnswer(detection.getNormalizedMessage(), medication),
                List.of("Part INAM", "Part beneficiaire", "Autre medicament"))
                .toBuilder()
                .prisEnCharge(medication.prisEnCharge())
                .tauxCouverture(medication.tauxCouverture())
                .sources(List.of(medicationSource(medication)))
                .build();
    }

    private ChatbotResponseDTO contextualMedicationAnswer(IntentDetectionResult detection, Long contextualMedicationId) {
        if (contextualMedicationId == null) {
            return medicationContextMissing(detection);
        }

        return medicamentChatbotSearchService.findActiveById(contextualMedicationId)
                .map(medication -> base(ChatIntent.MEDICATION_SEARCH,
                        medicationAnswer(detection.getNormalizedMessage(), medication),
                        List.of("Part INAM", "Part beneficiaire", "Autre medicament"))
                        .toBuilder()
                        .prisEnCharge(medication.prisEnCharge())
                        .tauxCouverture(medication.tauxCouverture())
                        .sources(List.of(medicationSource(medication)))
                        .build())
                .orElseGet(() -> medicationContextMissing(detection));
    }

    private ChatbotResponseDTO medicationContextMissing(IntentDetectionResult detection) {
        return base(ChatIntent.MEDICATION_SEARCH,
                "Veuillez preciser le medicament concerne pour que je puisse verifier cette information dans le referentiel AMU disponible.",
                List.of("Indiquer le nom complet", "Indiquer le code medicament", "Autre question"))
                .toBuilder()
                .found(false)
                .confidence(detection.getScore())
                .sources(List.of(backendSource("MEDICAMENT_POSTGRESQL", "Referentiel medicaments AMU PostgreSQL")))
                .build();
    }

    private ChatbotResponseDTO medicationNotFound(IntentDetectionResult detection, List<MedicationEvidence> suggestions) {
        String suggestionText = suggestions.isEmpty()
                ? ""
                : "\n\nVoulez-vous dire : " + suggestions.stream()
                .limit(3)
                .map(MedicationEvidence::nom)
                .collect(Collectors.joining(", "))
                + " ?";

        return base(ChatIntent.MEDICATION_SEARCH,
                "Je n'ai pas trouve ce medicament dans le referentiel AMU disponible." + suggestionText,
                List.of("Paracetamol", "Amoxicilline", "Ibuprofene"))
                .toBuilder()
                .found(false)
                .sources(List.of(backendSource("MEDICAMENT_POSTGRESQL", "Referentiel medicaments AMU PostgreSQL")))
                .confidence(detection.getScore())
                .build();
    }

    private String medicationAnswer(String normalizedMessage, MedicationEvidence medication) {
        List<String> lines = new ArrayList<>();
        boolean wantsCode = normalizedMessage.contains("code");
        boolean wantsPrice = normalizedMessage.contains("prix")
                || normalizedMessage.contains("coute")
                || normalizedMessage.contains("c'est combien")
                || normalizedMessage.contains("c est combien");
        boolean wantsPriorAgreement = wantsPriorAgreement(normalizedMessage);
        boolean wantsInamPart = wantsInamPart(normalizedMessage);
        boolean wantsBeneficiaryPart = wantsBeneficiaryPart(normalizedMessage) && !wantsPriorAgreement;
        boolean wantsRate = wantsRate(normalizedMessage);
        boolean wantsReimbursement = wantsReimbursement(normalizedMessage);
        boolean wantsStatus = normalizedMessage.contains("statut");
        boolean wantsGeneralInfo = normalizedMessage.contains("infos") || normalizedMessage.contains("informations");

        if (wantsCode) {
            lines.add("Code : " + nullToUnavailable(medication.code()) + ".");
        }
        if (wantsGeneralInfo && !wantsCode && !isBlank(medication.code())) {
            lines.add("Code : " + medication.code() + ".");
        }
        if (wantsPrice) {
            lines.add(valueSentence("Prix public", medication.prixPublic(), "information non disponible"));
        }
        if (wantsGeneralInfo && !wantsPrice && medication.prixPublic() != null) {
            lines.add(valueSentence("Prix public", medication.prixPublic(), "information non disponible"));
        }
        if (wantsStatus || wantsReimbursement) {
            lines.add(reimbursementSentence(medication));
        }
        if (wantsGeneralInfo && !wantsStatus && !wantsReimbursement && (medication.prisEnCharge() != null || !isBlank(medication.statut()))) {
            lines.add(reimbursementSentence(medication));
        }
        if (wantsRate) {
            lines.add(rateSentence(medication));
        }
        if (wantsGeneralInfo && !wantsRate && medication.tauxCouverture() != null) {
            lines.add(rateSentence(medication));
        }
        if (wantsInamPart) {
            lines.add(valueSentence("Part INAM", medication.partInam(), "information non disponible"));
        }
        if (wantsGeneralInfo && !wantsInamPart && medication.partInam() != null) {
            lines.add(valueSentence("Part INAM", medication.partInam(), "information non disponible"));
        }
        if (wantsBeneficiaryPart) {
            lines.add(valueSentence("Part beneficiaire", medication.partBeneficiaire(), "information non disponible"));
        }
        if (wantsGeneralInfo && !wantsBeneficiaryPart && medication.partBeneficiaire() != null) {
            lines.add(valueSentence("Part beneficiaire", medication.partBeneficiaire(), "information non disponible"));
        }
        if (wantsPriorAgreement) {
            lines.add(priorAgreementSentence(medication));
        }

        if (!lines.isEmpty()) {
            if (lines.size() == 1 && wantsPriorAgreement) {
                return priorAgreementAnswer(medication);
            }
            return officialMedicationIntro(medication) + String.join("\n", lines);
        }

        return officialMedicationIntro(medication)
                + "Donnees disponibles : "
                + "DCI " + nullToUnavailable(medication.dci()) + ", "
                + "dosage " + nullToUnavailable(medication.dosage()) + ", "
                + "forme " + nullToUnavailable(medication.formePharmaceutique()) + ", "
                + "statut " + nullToUnavailable(medication.statut()) + ", "
                + "taux " + (medication.tauxCouverture() == null ? "non disponible" : String.format(Locale.FRANCE, "%.0f%%", medication.tauxCouverture()))
                + ".";
    }

    private boolean wantsPriorAgreement(String normalizedMessage) {
        return normalizedMessage.contains("entente prealable")
                || normalizedMessage.contains("accord prealable")
                || normalizedMessage.contains("autorisation prealable")
                || normalizedMessage.contains("entente avant")
                || normalizedMessage.contains("accord avant");
    }

    private boolean wantsInamPart(String normalizedMessage) {
        return normalizedMessage.contains("inam") && (normalizedMessage.contains("paie")
                || normalizedMessage.contains("paye")
                || normalizedMessage.contains("prend")
                || normalizedMessage.contains("prend en charge")
                || normalizedMessage.contains("part inam")
                || normalizedMessage.contains("part de l'inam")
                || normalizedMessage.contains("part de l inam"));
    }

    private boolean wantsBeneficiaryPart(String normalizedMessage) {
        return normalizedMessage.contains("dois")
                || normalizedMessage.contains("paie") && !normalizedMessage.contains("inam")
                || normalizedMessage.contains("payer")
                || normalizedMessage.contains("ma part")
                || normalizedMessage.contains("part beneficiaire")
                || normalizedMessage.contains("part du beneficiaire")
                || normalizedMessage.contains("reste a charge")
                || normalizedMessage.contains("reste a ma charge");
    }

    private boolean wantsRate(String normalizedMessage) {
        return normalizedMessage.contains("taux")
                || normalizedMessage.contains("pourcentage")
                || normalizedMessage.contains("prise en charge de")
                || normalizedMessage.contains("a combien");
    }

    private boolean wantsReimbursement(String normalizedMessage) {
        return normalizedMessage.contains("rembourse")
                || normalizedMessage.contains("remboursable")
                || normalizedMessage.contains("rembours")
                || normalizedMessage.contains("couvre")
                || normalizedMessage.contains("couver")
                || normalizedMessage.contains("amu") && normalizedMessage.contains("avoir")
                || normalizedMessage.contains("amu") && normalizedMessage.contains("prend en charge")
                || normalizedMessage.contains("pris en charge")
                || normalizedMessage.contains("prise en charge");
    }

    private boolean shouldResolveMedicationContext(IntentDetectionResult detection, Long contextualMedicationId) {
        if (detection == null || detection.getNormalizedMessage() == null) {
            return false;
        }
        if (!isContextualMedicationQuestion(detection.getNormalizedMessage())) {
            return false;
        }
        if (!extractMedicationSearchTerm(detection.getNormalizedMessage()).isBlank()) {
            return false;
        }
        return contextualMedicationId != null;
    }

    private String extractMedicationSearchTerm(String normalizedMessage) {
        String searchTerm = medicamentChatbotSearchService.extractSearchTerm(normalizedMessage);
        return searchTerm == null ? "" : searchTerm;
    }

    private boolean isContextualMedicationQuestion(String normalizedMessage) {
        boolean asksMedicationDetail = wantsPrice(normalizedMessage)
                || wantsRate(normalizedMessage)
                || wantsStatus(normalizedMessage)
                || wantsInamPart(normalizedMessage)
                || wantsBeneficiaryPart(normalizedMessage)
                || wantsPriorAgreement(normalizedMessage);
        boolean startsAsFollowUp = normalizedMessage.startsWith("et ") && asksMedicationDetail;
        boolean pointsToCurrentMedication = normalizedMessage.contains("ce medicament")
                && (wantsReimbursement(normalizedMessage)
                || wantsStatus(normalizedMessage)
                || wantsPriorAgreement(normalizedMessage));
        return startsAsFollowUp || pointsToCurrentMedication;
    }

    private boolean wantsPrice(String normalizedMessage) {
        return normalizedMessage.contains("prix")
                || normalizedMessage.contains("coute")
                || normalizedMessage.contains("c'est combien")
                || normalizedMessage.contains("c est combien");
    }

    private boolean wantsStatus(String normalizedMessage) {
        return normalizedMessage.contains("statut");
    }

    private String officialMedicationIntro(MedicationEvidence medication) {
        return "D'apres le referentiel AMU disponible en base PostgreSQL, "
                + medication.nom()
                + (isBlank(medication.dosage()) ? "" : " " + medication.dosage())
                + (isBlank(medication.formePharmaceutique()) ? "" : " (" + medication.formePharmaceutique() + ")")
                + " est enregistre avec le code "
                + nullToUnavailable(medication.code())
                + ". ";
    }

    private String reimbursementSentence(MedicationEvidence medication) {
        String coverage = medication.prisEnCharge() == null
                ? "Prise en charge : information non disponible."
                : Boolean.TRUE.equals(medication.prisEnCharge())
                ? "Prise en charge : oui."
                : "Prise en charge : non.";
        if (isBlank(medication.statut())) {
            return coverage + " Statut officiel : non disponible.";
        }
        return coverage + " Statut officiel : " + medication.statut() + ".";
    }

    private String rateSentence(MedicationEvidence medication) {
        if (medication.tauxCouverture() == null) {
            return "Taux de couverture : information non disponible.";
        }
        return String.format(Locale.FRANCE, "Taux de couverture : %.0f%%.", medication.tauxCouverture());
    }

    private String priorAgreementSentence(MedicationEvidence medication) {
        if (isBlank(medication.statut())) {
            return "Entente prealable : information non disponible.";
        }

        if ("ENTENTE PREALABLE".equalsIgnoreCase(medication.statut().trim())) {
            return "Entente prealable : oui.";
        }

        if ("REMBOURSABLE".equalsIgnoreCase(medication.statut().trim())) {
            return "Entente prealable : non. Statut officiel : " + medication.statut() + ".";
        }

        return "Entente prealable : non, le referentiel ne l'indique pas comme ENTENTE PREALABLE. Statut officiel : "
                + medication.statut()
                + ".";
    }

    private String priorAgreementAnswer(MedicationEvidence medication) {
        if (isBlank(medication.statut())) {
            return "Le statut relatif a l'entente prealable n'est pas disponible dans le referentiel AMU."
                    + medicationReferenceSuffix(medication);
        }

        if ("ENTENTE PREALABLE".equalsIgnoreCase(medication.statut().trim())) {
            return "Oui. D'apres le referentiel AMU disponible, ce medicament est soumis a une entente prealable."
                    + medicationReferenceSuffix(medication);
        }

        if ("REMBOURSABLE".equalsIgnoreCase(medication.statut().trim())) {
            return "Non. D'apres le referentiel AMU disponible, ce medicament n'est pas soumis a une entente prealable. Son statut officiel est "
                    + medication.statut()
                    + "."
                    + medicationReferenceSuffix(medication);
        }

        return "Non, le referentiel ne l'indique pas comme ENTENTE PREALABLE. Son statut officiel est "
                + medication.statut()
                + "."
                + medicationReferenceSuffix(medication);
    }

    private String medicationReferenceSuffix(MedicationEvidence medication) {
        return " Medicament : " + medication.nom()
                + ", code " + nullToUnavailable(medication.code())
                + ".";
    }

    private String valueSentence(String label, BigDecimal value, String unavailableMessage) {
        if (value == null) {
            return label + " : " + unavailableMessage + ".";
        }
        return label + " : " + String.format(Locale.FRANCE, "%,.0f", value) + " FCFA.";
    }

    private String medicationSummaryLine(MedicationEvidence medication) {
        return "- " + medication.nom()
                + (isBlank(medication.dosage()) ? "" : " - " + medication.dosage())
                + (isBlank(medication.formePharmaceutique()) ? "" : " - " + medication.formePharmaceutique())
                + (isBlank(medication.dci()) ? "" : " - DCI : " + medication.dci())
                + (isBlank(medication.code()) ? "" : " - code : " + medication.code());
    }

    private boolean isDciListQuestion(String normalizedMessage) {
        return normalizedMessage.contains("contient")
                || normalizedMessage.contains("contiennent")
                || normalizedMessage.contains("a base de")
                || normalizedMessage.contains("principe actif")
                || normalizedMessage.contains("dci");
    }

    private boolean looksLikePreciseMedicationQuestion(String normalizedMessage, String medicationSearchTerm) {
        if (isBlank(medicationSearchTerm)) {
            return false;
        }
        return normalizedMessage.contains("medicament")
                || normalizedMessage.contains("doliprane")
                || normalizedMessage.contains("panadol")
                || normalizedMessage.contains("efferalgan")
                || normalizedMessage.contains("dafalgan")
                || normalizedMessage.contains("paracetamol")
                || normalizedMessage.contains("amoxicilline")
                || normalizedMessage.contains("ibuprofene")
                || normalizedMessage.contains("ceftriaxone")
                || normalizedMessage.contains("metronidazole")
                || normalizedMessage.contains("quinine")
                || normalizedMessage.contains("diclofenac")
                || normalizedMessage.contains("tramadol")
                || normalizedMessage.contains("morphine")
                || normalizedMessage.contains("gentamicine")
                || normalizedMessage.contains("salbutamol")
                || normalizedMessage.contains("furosemide")
                || normalizedMessage.contains("insuline");
    }

    private ChatbotResponseDTO procedure(IntentDetectionResult detection) {
        if (isGeneralProcedureQuestion(detection.getNormalizedMessage())) {
            return generalProcedure(detection);
        }

        Prestation prestation = findPrestation(detection.getKeyword(), detection.getNormalizedMessage());
        if (prestation == null) {
            return generalProcedure(detection);
        }

        return ChatbotResponseDTO.builder()
                .intent(ChatIntent.PROCEDURE.name())
                .message("Documents requis pour " + prestation.getNomActe() + " : " + prestation.getDocumentsRequis()
                        + "\nConditions : " + prestation.getConditionsPriseEnCharge())
                .codeActe(prestation.getCodeActe())
                .nomActe(prestation.getNomActe())
                .prisEnCharge(prestation.getPrisEnCharge())
                .tauxCouverture(prestation.getTauxCouverture())
                .conditionsPriseEnCharge(prestation.getConditionsPriseEnCharge())
                .documentsRequis(prestation.getDocumentsRequis())
                .sources(List.of(prestationSource(prestation)))
                .suggestionList(List.of("Taux couverture", "Hopital agree", "Autre prestation"))
                .suggestions("Suggestions : Taux couverture, Hopital agree, Autre prestation")
                .build();
    }

    private ChatbotResponseDTO fallback(IntentDetectionResult detection) {
        return aiAgentService.answer(detection)
                .toBuilder()
                .found(false)
                .build();
    }

    private ChatbotResponseDTO pharmacySearch(IntentDetectionResult detection) {
        String normalizedMessage = detection.getNormalizedMessage();
        boolean nearMeRequested = normalizedMessage.contains("pres de moi") || normalizedMessage.contains("proche de moi");

        if (nearMeRequested && (detection.getLatitude() == null || detection.getLongitude() == null)) {
            return base(ChatIntent.HOSPITAL_SEARCH,
                    "Pour rechercher une pharmacie pres de vous, veuillez autoriser la geolocalisation ou preciser votre ville.",
                    List.of("Pharmacie a Lome", "Autre ville", "Documents requis"))
                    .toBuilder()
                    .found(false)
                    .build();
        }

        List<StructureSante> pharmacies = structureSanteRepository.findOfficialPharmacies(Sort.by("nom")).stream()
                .sorted((left, right) -> compareByDistanceWhenAvailable(left, right, detection))
                .toList();

        LocationFilter locationFilter = findStructureLocationFilter(normalizedMessage, detection.getCity(), pharmacies);
        if (locationFilter != null) {
            pharmacies = pharmacies.stream()
                    .filter(pharmacie -> matchesStructureLocation(pharmacie, locationFilter.normalizedValue()))
                    .toList();
        }

        List<StructureSante> relevantPharmacies = filterRelevantStructures(pharmacies, normalizedMessage);
        if (!relevantPharmacies.isEmpty()) {
            pharmacies = relevantPharmacies;
        } else if (hasSpecificStructureSearchTokens(normalizedMessage)) {
            pharmacies = List.of();
        }

        if (pharmacies.isEmpty()) {
            String location = locationFilter == null ? "" : " a " + locationFilter.label();
            return base(ChatIntent.HOSPITAL_SEARCH,
                    "Aucune pharmacie agreee trouvee" + location + ". Essayez une autre ville.",
                    List.of("Lome", "Autre pharmacie", "Structure agreee"))
                    .toBuilder()
                    .sources(List.of())
                    .found(false)
                    .build();
        }

        if (pharmacies.size() == 1) {
            StructureSante pharmacie = pharmacies.get(0);
            if (asksAddress(normalizedMessage) || asksPhone(normalizedMessage)) {
                return contextualOfficialPharmacyAnswer(detection, pharmacie);
            }
            return ChatbotResponseDTO.builder()
                    .intent(ChatIntent.HOSPITAL_SEARCH.name())
                    .found(true)
                    .message(formatSingleOfficialPharmacy(pharmacie))
                    .sources(List.of(officialPharmacySource(pharmacie)))
                    .suggestionList(List.of("Autre pharmacie", "Pharmacie a Lome", "Structure proche"))
                    .suggestions("Suggestions : Autre pharmacie, Pharmacie a Lome, Structure proche")
                    .build();
        }

        String title = locationFilter == null
                ? "Pharmacies agreees trouvees :"
                : "Pharmacies trouvees a " + locationFilter.label() + " :";
        String names = pharmacies.stream()
                .limit(5)
                .map(this::formatOfficialPharmacyLine)
                .reduce("", (left, right) -> left + "\n" + right);
        String more = pharmacies.size() > 5
                ? "\n\n" + pharmacies.size() + " resultats trouves. Voici les 5 premiers ; precisez la ville, la region ou le quartier pour affiner."
                : "";

        return ChatbotResponseDTO.builder()
                .intent(ChatIntent.HOSPITAL_SEARCH.name())
                .found(true)
                .message(title + names + more + "\n\nVoulez-vous afficher la carte ?")
                .sources(pharmacies.stream()
                        .limit(5)
                        .map(this::officialPharmacySource)
                        .toList())
                .suggestionList(List.of("Afficher la carte", "Consultation", "Autre ville"))
                .suggestions("Suggestions : Afficher la carte, Consultation, Autre ville")
                .build();
    }

    private boolean isGeneralProcedureQuestion(String normalizedMessage) {
        return normalizedMessage.contains("renouvel")
                || normalizedMessage.contains("utiliser")
                || normalizedMessage.contains("beneficier")
                || normalizedMessage.contains("que faut il");
    }

    private ChatbotResponseDTO generalProcedure(IntentDetectionResult detection) {
        String normalizedMessage = detection.getNormalizedMessage();

        if (normalizedMessage.contains("renouvel")
                || normalizedMessage.contains("refaire")
                || normalizedMessage.contains("perte")
                || normalizedMessage.contains("expire")) {
            return procedureResponse(
                    "Renouvellement de la carte AMU",
                    "Pour renouveler votre carte AMU :\n1. Presentez votre ancienne carte AMU.\n2. Fournissez une piece d'identite valide.\n3. Deposez la demande aupres du service competent.\n4. Suivez les instructions donnees pour le retrait de la nouvelle carte.",
                    List.of("Utiliser ma carte", "Documents requis", "Beneficier d'une prestation"));
        }

        if (normalizedMessage.contains("utiliser") && normalizedMessage.contains("carte")) {
            return procedureResponse(
                    "Utilisation de la carte AMU",
                    "Pour utiliser votre carte AMU :\n1. Presentez votre carte AMU valide dans une structure agreee.\n2. Indiquez la prestation souhaitee.\n3. Fournissez les documents medicaux demandes si la prestation l'exige.\n4. La structure applique la prise en charge selon le taux enregistre.",
                    List.of("Structure agreee", "Consultation", "Documents requis"));
        }

        if (normalizedMessage.contains("beneficier") || normalizedMessage.contains("que faut il")) {
            return procedureResponse(
                    "Beneficier d'une prestation AMU",
                    "Pour beneficier d'une prestation AMU :\n1. Avoir un compte assure AMU actif.\n2. Presenter une carte AMU valide.\n3. Consulter une structure de sante agreee.\n4. Respecter les conditions et documents requis pour la prestation demandee.",
                    List.of("Prestations couvertes", "Structures agreees", "Documents requis"));
        }

        return procedureResponse(
                "Documents AMU",
                "Les documents a fournir dependent de la prestation. De maniere generale, gardez votre carte AMU valide, une piece d'identite et les documents medicaux disponibles. Pour une reponse precise, indiquez la prestation, par exemple consultation, radiologie ou hospitalisation.",
                List.of("Consultation", "Radiologie", "Hospitalisation"));
    }

    private ChatbotResponseDTO procedureResponse(String title, String message, List<String> suggestions) {
        return base(ChatIntent.PROCEDURE, message, suggestions)
                .toBuilder()
                .sources(List.of(backendSource("PROCEDURE", title)))
                .build();
    }

    private List<Prestation> findPrestations(String keyword, String normalizedMessage) {
        if (keyword != null) {
            List<Prestation> matches = prestationRepository.findByNomActeContainingIgnoreCase(keyword);
            if (!matches.isEmpty()) {
                List<Prestation> narrowedMatches = narrowPrestationMatches(matches, normalizedMessage);
                return narrowedMatches.isEmpty() ? matches : narrowedMatches;
            }
        }

        String normalizedKeyword = normalizeText(keyword);
        return prestationRepository.findAll().stream()
                .filter(prestation -> containsPrestationText(prestation, normalizedMessage, normalizedKeyword))
                .toList();
    }

    private List<Prestation> narrowPrestationMatches(List<Prestation> matches, String normalizedMessage) {
        Set<String> messageTokens = prestationTokens(normalizedMessage);
        if (messageTokens.isEmpty()) {
            return List.of();
        }
        return matches.stream()
                .filter(prestation -> messageTokens.containsAll(prestationTokens(prestation.getNomActe())))
                .toList();
    }

    private Set<String> prestationTokens(String value) {
        String normalized = normalizeText(value);
        if (normalized.isBlank()) {
            return Set.of();
        }
        return List.of(normalized.split("\\s+")).stream()
                .map(this::singularToken)
                .filter(token -> token.length() >= 3)
                .filter(token -> !Set.of("les", "des", "une", "pour", "avec", "prise", "charge", "taux").contains(token))
                .collect(Collectors.toSet());
    }

    private String singularToken(String token) {
        if (token != null && token.length() > 3 && token.endsWith("s")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private Prestation findPrestation(String keyword, String normalizedMessage) {
        List<Prestation> matches = findPrestations(keyword, normalizedMessage);
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private boolean containsPrestationText(Prestation prestation, String normalizedMessage, String normalizedKeyword) {
        List<String> values = List.of(
                normalizeText(prestation.getNomActe()),
                normalizeText(prestation.getCodeActe()),
                normalizeText(prestation.getCategorie() == null ? null : prestation.getCategorie().name()),
                normalizeText(prestation.getDescription()),
                normalizeText(prestation.getConditionsPriseEnCharge()),
                normalizeText(prestation.getDocumentsRequis())
        );

        return values.stream().anyMatch(value -> !value.isBlank()
                && (normalizedMessage.contains(value)
                || value.contains(normalizedMessage)
                || !normalizedKeyword.isBlank() && value.contains(normalizedKeyword)));
    }

    private boolean isGeneralCoverageQuestion(String normalizedMessage) {
        return normalizedMessage.contains("prestation")
                || normalizedMessage.contains("prestations")
                || normalizedMessage.contains("couvert")
                || normalizedMessage.contains("couvre")
                || normalizedMessage.contains("couverture")
                || normalizedMessage.contains("prise en charge");
    }

    private boolean isContextualPrestationQuestion(String normalizedMessage) {
        boolean asksRate = normalizedMessage.contains("taux")
                || normalizedMessage.contains("a quel taux")
                || normalizedMessage.contains("a combien")
                || normalizedMessage.contains("combien");
        boolean pointsToPrestation = normalizedMessage.contains("cette prestation")
                || normalizedMessage.contains("la prestation")
                || normalizedMessage.startsWith("elle ")
                || normalizedMessage.contains("son ")
                || normalizedMessage.contains("leur ")
                || normalizedMessage.startsWith("et ");
        return pointsToPrestation && (asksRate
                || normalizedMessage.contains("prise en charge")
                || normalizedMessage.contains("pris en charge")
                || normalizedMessage.contains("couverte")
                || normalizedMessage.contains("couvert")
                || normalizedMessage.contains("prestation"));
    }

    private boolean isGeneralPrestationsListQuestion(String normalizedMessage, String keyword) {
        String normalizedKeyword = normalizeText(keyword);
        boolean hasSpecificKeyword = !normalizedKeyword.isBlank()
                && !Set.of("prestation", "prestations", "soin", "soins", "amu").contains(normalizedKeyword);
        if (hasSpecificKeyword) {
            return false;
        }

        return normalizedMessage.contains("quelles prestations")
                || normalizedMessage.contains("quels soins")
                || normalizedMessage.contains("prestations couvertes")
                || normalizedMessage.contains("prestations prises en charge")
                || normalizedMessage.contains("liste des prestations")
                || normalizedMessage.contains("liste prestations")
                || normalizedMessage.contains("donne moi les prestations")
                || normalizedMessage.contains("connaitre les prestations")
                || normalizedMessage.contains("couvre l'amu")
                || normalizedMessage.contains("couvre l amu")
                || normalizedMessage.contains("amu couvre")
                || normalizedMessage.contains("amu prend en charge")
                || normalizedMessage.contains("qu est ce que l amu prend en charge");
    }

    private ChatbotResponseDTO coveredPrestationsSummary() {
        List<Prestation> prestations = prestationRepository.findByPrisEnChargeTrue().stream()
                .sorted(Comparator.comparing(Prestation::getNomActe))
                .toList();

        if (prestations.isEmpty()) {
            return base(ChatIntent.COVERAGE,
                    "Je n'ai pas trouve de prestation couverte dans la base pour le moment.",
                    List.of("Indiquer une prestation precise", "Documents requis", "Autre question"))
                    .toBuilder()
                    .found(false)
                    .sources(List.of(backendSource("PRESTATION", "Referentiel prestations AMU PostgreSQL")))
                    .build();
        }

        String items = prestations.stream()
                .map(prestation -> "\n- " + prestation.getNomActe())
                .reduce("", (left, right) -> left + "\n" + right);

        return ChatbotResponseDTO.builder()
                .statut("COUVERT")
                .intent(ChatIntent.COVERAGE.name())
                .found(true)
                .message("Les prestations couvertes disponibles dans le referentiel AMU sont :"
                        + items
                        + "\n\nVous pouvez me demander le taux de prise en charge d'une prestation precise.")
                .sources(prestations.stream()
                        .map(this::prestationSource)
                        .toList())
                .suggestionList(prestations.stream().map(Prestation::getNomActe).limit(3).toList())
                .suggestions("Suggestions : " + String.join(", ", prestations.stream().map(Prestation::getNomActe).limit(3).toList()))
                .build();
    }

    private boolean sameNormalizedCity(String structureCity, String normalizedCity) {
        String normalizedStructureCity = normalizeText(structureCity);
        if (normalizedStructureCity.isBlank() || normalizedCity == null || normalizedCity.isBlank()) {
            return false;
        }
        return normalizedStructureCity.equals(normalizedCity)
                || normalizedStructureCity.contains(normalizedCity)
                || normalizedCity.contains(normalizedStructureCity);
    }

    private LocationFilter findStructureLocationFilter(String normalizedMessage, String detectedCity, List<StructureSante> structures) {
        if (!isBlank(detectedCity)) {
            return new LocationFilter(normalizeText(detectedCity), detectedCity);
        }
        return structures.stream()
                .flatMap(structure -> Stream.of(structure.getVille(), structure.getRegion()))
                .filter(value -> !isBlank(value))
                .map(value -> new LocationFilter(normalizeText(value), value))
                .filter(filter -> filter.normalizedValue().length() >= 3)
                .filter(filter -> normalizedMessage.contains(filter.normalizedValue()))
                .max(Comparator.comparingInt(filter -> filter.normalizedValue().length()))
                .orElse(null);
    }

    private LocationFilter findPharmacyLocationFilter(String normalizedMessage, String detectedCity, List<Pharmacie> pharmacies) {
        if (!isBlank(detectedCity)) {
            return new LocationFilter(normalizeText(detectedCity), detectedCity);
        }
        return pharmacies.stream()
                .flatMap(pharmacie -> Stream.of(pharmacie.getVille(), pharmacie.getRegion(), pharmacie.getQuartier()))
                .filter(value -> !isBlank(value))
                .map(value -> new LocationFilter(normalizeText(value), value))
                .filter(filter -> filter.normalizedValue().length() >= 3)
                .filter(filter -> normalizedMessage.contains(filter.normalizedValue()))
                .max(Comparator.comparingInt(filter -> filter.normalizedValue().length()))
                .orElse(null);
    }

    private boolean matchesStructureLocation(StructureSante structure, String normalizedLocation) {
        return sameNormalizedCity(structure.getVille(), normalizedLocation)
                || sameNormalizedCity(structure.getRegion(), normalizedLocation);
    }

    private boolean matchesPharmacyLocation(Pharmacie pharmacie, String normalizedLocation) {
        return sameNormalizedCity(pharmacie.getVille(), normalizedLocation)
                || sameNormalizedCity(pharmacie.getRegion(), normalizedLocation)
                || sameNormalizedCity(pharmacie.getQuartier(), normalizedLocation);
    }

    private boolean isActiveStructure(StructureSante structure) {
        return structure.getType() != null
                && !"PHARMACIE".equals(structure.getType().name())
                && (structure.getActif() == null || Boolean.TRUE.equals(structure.getActif()));
    }

    private List<Pharmacie> filterRelevantPharmacies(List<Pharmacie> pharmacies, String normalizedMessage) {
        Set<String> queryTokens = structureSearchTokens(normalizedMessage);

        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<ScoredPharmacie> scored = pharmacies.stream()
                .map(pharmacie -> new ScoredPharmacie(pharmacie, scorePharmacieText(pharmacie, queryTokens, normalizedMessage)))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(ScoredPharmacie::score).reversed())
                .toList();

        int bestScore = scored.isEmpty() ? 0 : scored.get(0).score();
        if (bestScore < 2) {
            return List.of();
        }
        return scored.stream()
                .filter(match -> match.score() == bestScore || match.score() >= 4)
                .map(ScoredPharmacie::pharmacie)
                .limit(5)
                .toList();
    }

    private int scorePharmacieText(Pharmacie pharmacie, Set<String> queryTokens, String normalizedMessage) {
        String candidate = normalizeText(String.join(" ",
                nullToEmpty(pharmacie.getCode()),
                nullToEmpty(pharmacie.getNom()),
                nullToEmpty(pharmacie.getAdresse()),
                nullToEmpty(pharmacie.getQuartier()),
                nullToEmpty(pharmacie.getVille()),
                nullToEmpty(pharmacie.getRegion()),
                nullToEmpty(pharmacie.getTelephone())));
        int score = 0;
        if (candidate.contains(normalizedMessage)) {
            score += 6;
        }
        for (String token : queryTokens) {
            if (candidate.contains(token)) {
                score += 2;
            }
        }
        return score;
    }

    private List<StructureSante> filterRelevantStructures(List<StructureSante> structures, String normalizedMessage) {
        Set<String> queryTokens = structureSearchTokens(normalizedMessage);

        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<ScoredStructure> scored = structures.stream()
                .map(structure -> new ScoredStructure(structure, scoreStructureText(structure, queryTokens, normalizedMessage)))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(ScoredStructure::score).reversed())
                .toList();

        int bestScore = scored.isEmpty() ? 0 : scored.get(0).score();
        if (bestScore < 2) {
            return List.of();
        }
        return scored.stream()
                .filter(match -> match.score() == bestScore || match.score() >= 4)
                .map(ScoredStructure::structure)
                .limit(5)
                .toList();
    }

    private boolean hasSpecificStructureSearchTokens(String normalizedMessage) {
        return !structureSearchTokens(normalizedMessage).isEmpty();
    }

    private boolean hasExplicitStructureSearchName(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        if (!List.of("pharmacie", "pharmacies", "hopital", "hopitaux", "clinique", "centre", "centres",
                "structure", "structures").stream().anyMatch(normalizedMessage::contains)) {
            return false;
        }
        return hasSpecificStructureSearchTokens(normalizedMessage);
    }

    private Set<String> structureSearchTokens(String normalizedMessage) {
        return List.of(normalizedMessage.split("\\s+")).stream()
                .filter(token -> token.length() >= 3)
                .filter(token -> !List.of("une", "des", "les", "dans", "pour", "avec", "pharmacie",
                        "pharmacies", "partenaire", "partenaires", "agreee", "agreees", "trouve",
                        "existe", "numero", "telephone", "situe", "trouver", "quel", "quelle",
                        "quels", "quelles", "sont", "disponible", "disponibles", "conventionne",
                        "conventionnes", "conventionnee", "conventionnees", "hopital", "hopitaux",
                        "clinique", "centre", "centres", "structure", "structures", "cherche", "veux",
                        "montre", "puis", "trouver", "pres", "proche", "proches", "plus", "dans", "region",
                        "est", "moi").contains(token))
                .collect(Collectors.toSet());
    }

    private int scoreStructureText(StructureSante structure, Set<String> queryTokens, String normalizedMessage) {
        String candidate = normalizeText(String.join(" ",
                nullToEmpty(structure.getNom()),
                nullToEmpty(structure.getAdresse()),
                nullToEmpty(structure.getVille()),
                nullToEmpty(structure.getRegion()),
                nullToEmpty(structure.getTelephone())));
        int score = 0;
        if (candidate.contains(normalizedMessage)) {
            score += 6;
        }
        for (String token : queryTokens) {
            if (candidate.contains(token)) {
                score += 2;
            }
        }
        return score;
    }

    private String formatSingleStructure(StructureSante structure) {
        return structure.getNom()
                + " (" + nullToUnavailable(structure.getType() == null ? null : structure.getType().name()) + ") est une structure agreee. "
                + "Region : " + nullToUnavailable(structure.getRegion())
                + ". Ville : " + nullToUnavailable(structure.getVille())
                + ". Adresse : " + addressValue(structure.getAdresse())
                + ". Telephone : " + nullToUnavailable(structure.getTelephone()) + ".";
    }

    private String formatStructureLine(StructureSante structure) {
        return "- " + structure.getNom()
                + " (" + nullToUnavailable(structure.getType() == null ? null : structure.getType().name()) + ")"
                + " - Region : " + nullToUnavailable(structure.getRegion())
                + " - Ville : " + nullToUnavailable(structure.getVille())
                + " - Adresse : " + addressValue(structure.getAdresse())
                + " - Tel : " + nullToUnavailable(structure.getTelephone());
    }

    private String formatSinglePharmacie(Pharmacie pharmacie) {
        return pharmacie.getNom()
                + " est une pharmacie agreee. Region : " + nullToUnavailable(pharmacie.getRegion())
                + ". Ville : " + nullToUnavailable(pharmacie.getVille())
                + ". Quartier : " + nullToUnavailable(pharmacie.getQuartier())
                + ". Adresse : " + addressValue(pharmacie.getAdresse())
                + ". Telephone : " + nullToUnavailable(pharmacie.getTelephone()) + ".";
    }

    private String formatPharmacieLine(Pharmacie pharmacie) {
        return "- " + pharmacie.getNom()
                + " - Region : " + nullToUnavailable(pharmacie.getRegion())
                + " - Ville : " + nullToUnavailable(pharmacie.getVille())
                + " - Quartier : " + nullToUnavailable(pharmacie.getQuartier())
                + " - Adresse : " + addressValue(pharmacie.getAdresse())
                + " - Tel : " + nullToUnavailable(pharmacie.getTelephone());
    }

    private String formatSingleOfficialPharmacy(StructureSante pharmacie) {
        return pharmacie.getNom()
                + " est une pharmacie conventionnee AMU selon le referentiel officiel. "
                + "Type officiel : " + nullToUnavailable(pharmacie.getTypeOfficiel())
                + ". Region : " + nullToUnavailable(pharmacie.getRegion())
                + ". Adresse : " + addressValue(pharmacie.getAdresse())
                + ". Telephone : non renseigne dans le referentiel officiel.";
    }

    private String formatOfficialPharmacyLine(StructureSante pharmacie) {
        return "- " + pharmacie.getNom()
                + " - Type officiel : " + nullToUnavailable(pharmacie.getTypeOfficiel())
                + " - Region : " + nullToUnavailable(pharmacie.getRegion())
                + " - Adresse : " + addressValue(pharmacie.getAdresse())
                + " - Tel : non renseigne dans le referentiel officiel";
    }

    private String addressValue(String address) {
        return isBlank(address) ? "adresse non renseignee" : address;
    }

    private boolean asksStructureDetail(String normalizedMessage) {
        return asksAddress(normalizedMessage)
                || asksPhone(normalizedMessage)
                || asksRegion(normalizedMessage)
                || asksCity(normalizedMessage)
                || asksAgreement(normalizedMessage)
                || asksDistance(normalizedMessage);
    }

    private boolean pointsToContextualStructure(String normalizedMessage) {
        return normalizedMessage.contains(" son ")
                || normalizedMessage.startsWith("son ")
                || normalizedMessage.contains(" sa ")
                || normalizedMessage.startsWith("sa ")
                || normalizedMessage.contains(" ses ")
                || normalizedMessage.startsWith("ses ")
                || normalizedMessage.contains(" elle")
                || normalizedMessage.startsWith("elle ")
                || normalizedMessage.contains(" il ")
                || normalizedMessage.startsWith("il ")
                || normalizedMessage.contains("cette structure")
                || normalizedMessage.contains("la structure")
                || normalizedMessage.contains("cette pharmacie")
                || normalizedMessage.contains("la pharmacie")
                || normalizedMessage.contains("cet hopital")
                || normalizedMessage.contains("l hopital")
                || normalizedMessage.startsWith("dans quelle ville")
                || normalizedMessage.startsWith("dans quelle region");
    }

    private boolean asksAddress(String normalizedMessage) {
        return normalizedMessage.contains("adresse")
                || normalizedMessage.contains("situee")
                || normalizedMessage.contains("situe")
                || normalizedMessage.contains("ou se trouve");
    }

    private boolean asksPhone(String normalizedMessage) {
        return normalizedMessage.contains("telephone")
                || normalizedMessage.contains("tel ")
                || normalizedMessage.endsWith("tel")
                || normalizedMessage.contains("numero");
    }

    private boolean asksRegion(String normalizedMessage) {
        return normalizedMessage.contains("region");
    }

    private boolean asksCity(String normalizedMessage) {
        return normalizedMessage.contains("ville");
    }

    private boolean asksAgreement(String normalizedMessage) {
        return normalizedMessage.contains("conventionne")
                || normalizedMessage.contains("conventionnee")
                || normalizedMessage.contains("agree")
                || normalizedMessage.contains("agreee")
                || normalizedMessage.contains("partenaire");
    }

    private boolean asksDistance(String normalizedMessage) {
        return normalizedMessage.contains("distance")
                || normalizedMessage.contains("combien de km")
                || normalizedMessage.contains("kilometre");
    }

    private String contextualDistanceMessage(IntentDetectionResult detection, Double structureLatitude, Double structureLongitude) {
        if (detection.getLatitude() == null || detection.getLongitude() == null) {
            return "Je ne peux pas calculer la distance sans votre position. Autorisez la geolocalisation ou precisez votre ville.";
        }
        if (structureLatitude == null || structureLongitude == null) {
            return "Les coordonnees de cette structure ne sont pas renseignees dans le referentiel.";
        }
        return "La recherche peut utiliser les coordonnees pour classer les resultats proches, mais la distance exacte n'est pas disponible dans cette reponse.";
    }

    private int compareByDistanceWhenAvailable(StructureSante left, StructureSante right, IntentDetectionResult detection) {
        if (detection.getLatitude() == null || detection.getLongitude() == null) {
            return left.getNom().compareToIgnoreCase(right.getNom());
        }

        return Double.compare(
                distance(left, detection.getLatitude(), detection.getLongitude()),
                distance(right, detection.getLatitude(), detection.getLongitude())
        );
    }

    private double distance(StructureSante structure, double latitude, double longitude) {
        if (structure.getLatitude() == null || structure.getLongitude() == null) {
            return Double.MAX_VALUE;
        }

        double latitudeDelta = structure.getLatitude() - latitude;
        double longitudeDelta = structure.getLongitude() - longitude;
        return Math.sqrt(latitudeDelta * latitudeDelta + longitudeDelta * longitudeDelta);
    }

    private int comparePharmaciesByDistanceWhenAvailable(Pharmacie left, Pharmacie right, IntentDetectionResult detection) {
        if (detection.getLatitude() == null || detection.getLongitude() == null) {
            return left.getNom().compareToIgnoreCase(right.getNom());
        }
        return Double.compare(
                pharmacyDistance(left, detection.getLatitude(), detection.getLongitude()),
                pharmacyDistance(right, detection.getLatitude(), detection.getLongitude())
        );
    }

    private double pharmacyDistance(Pharmacie pharmacie, double latitude, double longitude) {
        if (pharmacie.getLatitude() == null || pharmacie.getLongitude() == null) {
            return Double.MAX_VALUE;
        }
        double latitudeDelta = pharmacie.getLatitude() - latitude;
        double longitudeDelta = pharmacie.getLongitude() - longitude;
        return Math.sqrt(latitudeDelta * latitudeDelta + longitudeDelta * longitudeDelta);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replaceAll("[^a-z0-9'\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String nullToUnavailable(String value) {
        return isBlank(value) ? "non disponible" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ChatbotResponseDTO base(ChatIntent intent, String message, List<String> suggestions) {
        return ChatbotResponseDTO.builder()
                .intent(intent.name())
                .found(true)
                .message(message)
                .suggestionList(suggestions)
                .suggestions("Suggestions : " + String.join(", ", suggestions))
                .build();
    }

    private ChatbotSourceDTO prestationSource(Prestation prestation) {
        return ChatbotSourceDTO.builder()
                .type("PRESTATION")
                .id(prestation.getIdPrestation())
                .title(prestation.getNomActe())
                .build();
    }

    private ChatbotSourceDTO structureSource(StructureSante structure) {
        return ChatbotSourceDTO.builder()
                .type("STRUCTURE")
                .id(structure.getIdStructure())
                .title(structure.getNom())
                .build();
    }

    private ChatbotSourceDTO pharmacieSource(Pharmacie pharmacie) {
        return ChatbotSourceDTO.builder()
                .type("PHARMACIE")
                .id(pharmacie.getId())
                .title(pharmacie.getNom())
                .build();
    }

    private ChatbotSourceDTO officialPharmacySource(StructureSante pharmacie) {
        return ChatbotSourceDTO.builder()
                .type("PHARMACIE")
                .id(pharmacie.getIdStructure())
                .title(pharmacie.getNom())
                .build();
    }

    private ChatbotSourceDTO medicationSource(MedicationEvidence medication) {
        return ChatbotSourceDTO.builder()
                .type("MEDICAMENT_POSTGRESQL")
                .id(medication.id())
                .title(medication.nom())
                .build();
    }

    private ChatbotSourceDTO referentielMedicationSource() {
        return ChatbotSourceDTO.builder()
                .type("REFERENTIEL_MEDICAMENT")
                .title("Référentiel médicaments School AMU 2025")
                .build();
    }

    private ChatbotSourceDTO backendSource(String type, String title) {
        return ChatbotSourceDTO.builder()
                .type(type)
                .title(title)
                .build();
    }

    private record ScoredStructure(StructureSante structure, int score) {
    }

    private record ScoredPharmacie(Pharmacie pharmacie, int score) {
    }

    private record LocationFilter(String normalizedValue, String label) {
    }

    private boolean isOfficialPharmacyStructure(StructureSante structure) {
        if (structure == null || isBlank(structure.getCode())) {
            return false;
        }
        return Boolean.TRUE.equals(structure.getAgrementAMU())
                && List.of("PHARMACIE", "DEPOT PHARMACIE", "DEPOT PHARMACEUTIQUE").contains(structure.getTypeOfficiel());
    }
}
