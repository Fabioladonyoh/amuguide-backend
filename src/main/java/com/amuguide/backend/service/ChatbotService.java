package com.amuguide.backend.service;

import com.amuguide.backend.chat.nlp.IntentDetectionResult;
import com.amuguide.backend.chat.nlp.IntentDetector;
import com.amuguide.backend.chat.nlp.ChatIntent;
import com.amuguide.backend.chat.service.FaqChatbotService;
import com.amuguide.backend.chat.service.MedicamentChatbotSearchService;
import com.amuguide.backend.chat.service.MedicationEvidence;
import com.amuguide.backend.chat.service.MedicationSessionContextService;
import com.amuguide.backend.chat.service.MedicationSessionContextService.PendingMedicationCandidate;
import com.amuguide.backend.chat.service.MedicationSessionContextService.PendingMedicationSuggestionContext;
import com.amuguide.backend.chat.service.PrestationSessionContextService;
import com.amuguide.backend.chat.service.ResponseGenerator;
import com.amuguide.backend.chat.service.StructureSessionContextService;
import com.amuguide.backend.chat.service.StructureSessionContextService.StructureReference;
import com.amuguide.backend.dto.ChatbotRequestDTO;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.dto.ChatbotSourceDTO;
import com.amuguide.backend.entity.AssureAMU;
import com.amuguide.backend.entity.ChatHistory;
import com.amuguide.backend.repository.AssureAMURepository;
import com.amuguide.backend.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private final IntentDetector intentDetector;
    private final FaqChatbotService faqChatbotService;
    private final ResponseGenerator responseGenerator;
    private final ChatHistoryRepository chatHistoryRepository;
    private final AssureAMURepository assureAMURepository;
    private final MedicationSessionContextService medicationSessionContextService;
    private final MedicamentChatbotSearchService medicamentChatbotSearchService;
    private final PrestationSessionContextService prestationSessionContextService;
    private final StructureSessionContextService structureSessionContextService;

    public ChatbotResponseDTO repondre(String message) {
        return repondre(ChatbotRequestDTO.builder().message(message).build());
    }

    public ChatbotResponseDTO repondre(ChatbotRequestDTO request) {
        String message = request == null ? null : request.getMessage();
        if (message == null || message.isBlank()) {
            return ChatbotResponseDTO.builder()
                    .intent("FALLBACK")
                    .message("Votre message est vide.")
                    .sessionId(request == null ? null : request.getSessionId())
                    .suggestions("Suggestions : Consultation, Radiologie, Hopital proche")
                    .suggestionList(List.of("Consultation", "Radiologie", "Hopital proche"))
                    .build();
        }
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            request.setSessionId("session-" + UUID.randomUUID());
        }

        IntentDetectionResult detected = intentDetector.detect(message).toBuilder()
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();
        Optional<ChatbotResponseDTO> pendingSuggestionResponse = resolvePendingMedicationSuggestion(request, detected);
        if (pendingSuggestionResponse.isPresent()) {
            ChatbotResponseDTO response = pendingSuggestionResponse.get();
            enrichResponse(response, detected, request.getSessionId());
            saveHistory(request, detected, response);
            return response;
        }

        IntentDetectionResult detection = medicationDetectionWhenExplicitTermExists(detected);
        Long contextualMedicationId = medicationSessionContextService.findLastMedicationId(request.getSessionId())
                .orElse(null);
        Long contextualPrestationId = prestationSessionContextService.findLastPrestationId(request.getSessionId())
                .orElse(null);
        StructureReference contextualStructureReference = structureSessionContextService.findLastStructureReference(request.getSessionId())
                .orElse(null);
        boolean contextualStructureFollowUp = shouldRouteAsStructureFollowUp(detection)
                && (contextualStructureReference != null || asksMissingStructureContext(detection.getNormalizedMessage()));
        boolean contextualPrestationFollowUp = shouldRouteAsPrestationFollowUp(detection)
                && (contextualPrestationId != null || contextualMedicationId == null && asksMissingPrestationContext(detection.getNormalizedMessage()));
        boolean contextualMedicationFollowUp = !contextualPrestationFollowUp
                && !contextualStructureFollowUp
                && (contextualMedicationId != null || contextualPrestationId == null)
                && shouldRouteAsMedicationFollowUp(detection);
        if (contextualStructureFollowUp) {
            detection = detection.toBuilder()
                    .intent(ChatIntent.HOSPITAL_SEARCH)
                    .keyword(null)
                    .build();
        }
        if (contextualPrestationFollowUp) {
            detection = detection.toBuilder()
                    .intent(ChatIntent.COVERAGE)
                    .keyword(null)
                    .build();
        }
        if (contextualMedicationFollowUp) {
            detection = detection.toBuilder()
                    .intent(ChatIntent.MEDICATION_SEARCH)
                    .keyword("medicament")
                    .build();
        }

        IntentDetectionResult finalDetection = detection;
        ChatbotResponseDTO response = contextualMedicationFollowUp || contextualPrestationFollowUp || contextualStructureFollowUp
                ? responseGenerator.generate(
                finalDetection,
                contextualPrestationFollowUp ? null : contextualMedicationId,
                contextualPrestationId,
                contextualStructureReference
        )
                : faqChatbotService.answer(finalDetection)
                .orElseGet(() -> responseGenerator.generate(finalDetection, contextualMedicationId, contextualPrestationId, contextualStructureReference));
        enrichResponse(response, detection, request.getSessionId());
        rememberConfirmedMedication(request.getSessionId(), detection, response);
        rememberConfirmedPrestation(request.getSessionId(), detection, response);
        rememberConfirmedStructure(request.getSessionId(), detection, response);
        rememberPendingMedicationSuggestions(request.getSessionId(), detection, response);
        saveHistory(request, detection, response);
        return response;
    }

    private Optional<ChatbotResponseDTO> resolvePendingMedicationSuggestion(ChatbotRequestDTO request, IntentDetectionResult detection) {
        Optional<PendingMedicationSuggestionContext> pendingContext = medicationSessionContextService.findPendingSuggestions(request.getSessionId());
        if (pendingContext.isEmpty()) {
            return Optional.empty();
        }

        String normalizedMessage = detection.getNormalizedMessage();
        PendingMedicationSuggestionContext context = pendingContext.get();
        if (isMedicationSuggestionRefusal(normalizedMessage)) {
            medicationSessionContextService.clearPendingSuggestions(request.getSessionId());
            return Optional.of(ChatbotResponseDTO.builder()
                    .intent(ChatIntent.MEDICATION_SEARCH.name())
                    .found(false)
                    .message("D'accord. Pouvez-vous preciser le nom complet, le dosage ou le code du medicament ?")
                    .suggestionList(List.of("Indiquer le nom complet", "Indiquer le code medicament", "Autre medicament"))
                    .suggestions("Suggestions : Indiquer le nom complet, Indiquer le code medicament, Autre medicament")
                    .sources(List.of())
                    .build());
        }

        if (isMedicationSuggestionConfirmation(normalizedMessage)) {
            if (context.candidates().size() == 1) {
                return Optional.of(confirmPendingMedication(request.getSessionId(), context.candidates().get(0), context.originalNormalizedMessage()));
            }
            return Optional.of(askUserToDisambiguatePendingSuggestions(context));
        }

        return findCandidateFromMessage(normalizedMessage, context)
                .map(candidate -> confirmPendingMedication(request.getSessionId(), candidate, context.originalNormalizedMessage()));
    }

    private ChatbotResponseDTO confirmPendingMedication(String sessionId, PendingMedicationCandidate candidate, String originalNormalizedMessage) {
        Optional<MedicationEvidence> medication = medicamentChatbotSearchService.findActiveById(candidate.medicationId());
        if (medication.isEmpty()) {
            medicationSessionContextService.clearPendingSuggestions(sessionId);
            return ChatbotResponseDTO.builder()
                    .intent(ChatIntent.MEDICATION_SEARCH.name())
                    .found(false)
                    .message("Cette suggestion n'est plus disponible dans le referentiel AMU. Pouvez-vous preciser le medicament ?")
                    .suggestionList(List.of("Indiquer le nom complet", "Indiquer le code medicament", "Autre medicament"))
                    .suggestions("Suggestions : Indiquer le nom complet, Indiquer le code medicament, Autre medicament")
                    .sources(List.of())
                    .build();
        }

        medicationSessionContextService.rememberMedication(sessionId, candidate.medicationId());
        IntentDetectionResult confirmationDetection = IntentDetectionResult.builder()
                .intent(ChatIntent.MEDICATION_SEARCH)
                .normalizedMessage(contextualQuestionForInitialIntent(originalNormalizedMessage))
                .keyword("medicament")
                .score(1.0)
                .build();
        return responseGenerator.generate(confirmationDetection, candidate.medicationId());
    }

    private ChatbotResponseDTO askUserToDisambiguatePendingSuggestions(PendingMedicationSuggestionContext context) {
        StringBuilder message = new StringBuilder("J'ai trouve plusieurs medicaments possibles.\nPouvez-vous preciser lequel ?\n");
        for (int index = 0; index < context.candidates().size(); index++) {
            message.append("\n").append(index + 1).append(". ").append(context.candidates().get(index).name());
        }
        List<String> suggestions = context.candidates().stream()
                .map(PendingMedicationCandidate::name)
                .toList();
        return ChatbotResponseDTO.builder()
                .intent(ChatIntent.MEDICATION_SEARCH.name())
                .found(false)
                .message(message.toString())
                .suggestionList(suggestions)
                .suggestions("Suggestions : " + String.join(", ", suggestions))
                .sources(List.of())
                .build();
    }

    private void enrichResponse(ChatbotResponseDTO response, IntentDetectionResult detection, String sessionId) {
        if (response.getAnswer() == null) {
            response.setAnswer(response.getMessage());
        }
        if (response.getMessage() == null) {
            response.setMessage(response.getAnswer());
        }
        if (response.getCategory() == null) {
            response.setCategory(response.getIntent() == null ? detection.getIntent().name() : response.getIntent());
        }
        if (response.getIntent() == null) {
            response.setIntent(response.getCategory());
        }
        if (response.getConfidence() == null) {
            response.setConfidence(detection.getScore());
        }
        if (response.getTimestamp() == null) {
            response.setTimestamp(LocalDateTime.now());
        }
        if (response.getSessionId() == null) {
            response.setSessionId(sessionId);
        }
        if (response.getSources() == null) {
            response.setSources(List.of());
        }
        if (response.getSource() == null) {
            response.setSource(response.getSources().isEmpty() ? "LOCAL_RULES" : "DATABASE");
        }
        if (response.getFound() == null) {
            boolean knownCategory = !"FALLBACK".equals(response.getCategory())
                    && !"AGENT_ASSISTED".equals(response.getCategory())
                    && !"ERREUR".equals(response.getCategory());
            response.setFound(knownCategory || !response.getSources().isEmpty());
        }
    }

    private void saveHistory(ChatbotRequestDTO request, IntentDetectionResult detection, ChatbotResponseDTO response) {
        try {
            Long assureId = request.getAssureId() != null ? request.getAssureId() : request.getUserId();

            AssureAMU user = assureId == null
                    ? null
                    : assureAMURepository.findById(assureId).orElse(null);

            chatHistoryRepository.save(ChatHistory.builder()
                    .user(user)
                    .message(request.getMessage())
                    .reponse(response.getAnswer())
                    .sessionId(request.getSessionId())
                    .intention(detection.getIntent())
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Impossible d'enregistrer l'historique chatbot pour l'intention {}", detection.getIntent(), ex);
        }
    }

    private Optional<PendingMedicationCandidate> findCandidateFromMessage(String normalizedMessage, PendingMedicationSuggestionContext context) {
        String normalized = normalizeText(normalizedMessage);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        Optional<Integer> requestedIndex = requestedCandidateIndex(normalized);
        if (requestedIndex.isPresent() && requestedIndex.get() < context.candidates().size()) {
            return Optional.of(context.candidates().get(requestedIndex.get()));
        }

        return context.candidates().stream()
                .filter(candidate -> normalizeText(candidate.name()).equals(normalized)
                        || normalized.contains(normalizeText(candidate.name())))
                .findFirst();
    }

    private Optional<Integer> requestedCandidateIndex(String normalizedMessage) {
        if (Set.of("1", "premier", "le premier", "premiere", "la premiere").contains(normalizedMessage)) {
            return Optional.of(0);
        }
        if (Set.of("2", "deuxieme", "le deuxieme", "la deuxieme").contains(normalizedMessage)) {
            return Optional.of(1);
        }
        if (Set.of("3", "troisieme", "le troisieme", "la troisieme").contains(normalizedMessage)) {
            return Optional.of(2);
        }
        return Optional.empty();
    }

    private boolean isMedicationSuggestionConfirmation(String normalizedMessage) {
        return Set.of(
                "oui",
                "oui c est ca",
                "oui c est ce que je voulais dire",
                "c est ca",
                "exactement"
        ).contains(normalizeText(normalizedMessage));
    }

    private boolean isMedicationSuggestionRefusal(String normalizedMessage) {
        return Set.of(
                "non",
                "non ce n est pas ca"
        ).contains(normalizeText(normalizedMessage));
    }

    private String contextualQuestionForInitialIntent(String originalNormalizedMessage) {
        String original = originalNormalizedMessage == null ? "" : originalNormalizedMessage;
        if (original.contains("prix") || original.contains("coute") || original.contains("c est combien")) {
            return "et son prix ?";
        }
        if (original.contains("taux") || original.contains("pourcentage") || original.contains("prise en charge de")) {
            return "et son taux ?";
        }
        if (original.contains("inam")) {
            return "et la part inam ?";
        }
        if (original.contains("ma part")
                || original.contains("part beneficiaire")
                || original.contains("reste a charge")
                || original.contains("reste a ma charge")
                || original.contains("paie")
                || original.contains("payer")) {
            return "et ma part ?";
        }
        if (original.contains("entente prealable")
                || original.contains("accord prealable")
                || original.contains("autorisation prealable")) {
            return "et l entente prealable ?";
        }
        if (original.contains("code")) {
            return "et son code ?";
        }
        if (original.contains("rembourse") || original.contains("remboursable") || original.contains("prise en charge")) {
            return "ce medicament est il remboursable ?";
        }
        return "ce medicament";
    }

    private void rememberPendingMedicationSuggestions(String sessionId, IntentDetectionResult detection, ChatbotResponseDTO response) {
        if (detection == null || detection.getIntent() != ChatIntent.MEDICATION_SEARCH || response == null) {
            return;
        }

        List<PendingMedicationCandidate> candidates = pendingCandidatesFromAmbiguousResponse(response);
        if (candidates.isEmpty() && !Boolean.TRUE.equals(response.getFound())) {
            candidates = medicamentChatbotSearchService.search(detection.getNormalizedMessage(), 10)
                    .suggestions()
                    .stream()
                    .limit(3)
                    .map(suggestion -> new PendingMedicationCandidate(suggestion.id(), suggestion.nom()))
                    .toList();
        }

        if (candidates.isEmpty()) {
            return;
        }
        medicationSessionContextService.rememberPendingSuggestions(sessionId, candidates, detection.getNormalizedMessage());
    }

    private List<PendingMedicationCandidate> pendingCandidatesFromAmbiguousResponse(ChatbotResponseDTO response) {
        if (response.getSources() == null) {
            return List.of();
        }
        List<PendingMedicationCandidate> candidates = response.getSources().stream()
                .filter(source -> "MEDICAMENT_POSTGRESQL".equals(source.getType()))
                .filter(source -> source.getId() != null && source.getTitle() != null && !source.getTitle().isBlank())
                .map(source -> new PendingMedicationCandidate(source.getId(), source.getTitle()))
                .toList();
        return candidates.size() > 1 ? candidates : List.of();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void rememberConfirmedMedication(String sessionId, IntentDetectionResult detection, ChatbotResponseDTO response) {
        if (response == null || !Boolean.TRUE.equals(response.getFound())) {
            return;
        }
        String searchTerm = medicamentChatbotSearchService.extractSearchTerm(detection.getNormalizedMessage());
        if (searchTerm.isBlank()) {
            return;
        }
        List<ChatbotSourceDTO> medicationSources = response.getSources() == null
                ? List.of()
                : response.getSources().stream()
                .filter(source -> "MEDICAMENT_POSTGRESQL".equals(source.getType()))
                .filter(source -> source.getId() != null)
                .toList();
        if (medicationSources.size() == 1) {
            medicationSessionContextService.rememberMedication(sessionId, medicationSources.get(0).getId());
        }
    }

    private void rememberConfirmedPrestation(String sessionId, IntentDetectionResult detection, ChatbotResponseDTO response) {
        if (response == null
                || detection == null
                || detection.getIntent() != ChatIntent.COVERAGE
                || !Boolean.TRUE.equals(response.getFound())
                || isGeneralPrestationsListQuestion(detection.getNormalizedMessage())) {
            return;
        }
        List<ChatbotSourceDTO> prestationSources = response.getSources() == null
                ? List.of()
                : response.getSources().stream()
                .filter(source -> "PRESTATION".equals(source.getType()))
                .filter(source -> source.getId() != null)
                .toList();
        if (prestationSources.size() == 1) {
            prestationSessionContextService.rememberPrestation(sessionId, prestationSources.get(0).getId());
        }
    }

    private void rememberConfirmedStructure(String sessionId, IntentDetectionResult detection, ChatbotResponseDTO response) {
        if (response == null
                || detection == null
                || detection.getIntent() != ChatIntent.HOSPITAL_SEARCH
                || !Boolean.TRUE.equals(response.getFound())) {
            return;
        }
        List<ChatbotSourceDTO> structureSources = response.getSources() == null
                ? List.of()
                : response.getSources().stream()
                .filter(source -> ("STRUCTURE".equals(source.getType()) || "PHARMACIE".equals(source.getType()))
                        && source.getId() != null)
                .toList();
        if (structureSources.size() == 1) {
            ChatbotSourceDTO source = structureSources.get(0);
            structureSessionContextService.rememberStructure(sessionId, new StructureReference(source.getType(), source.getId()));
        }
    }

    private IntentDetectionResult medicationDetectionWhenExplicitTermExists(IntentDetectionResult detection) {
        if (detection.getIntent() != ChatIntent.FALLBACK) {
            return detection;
        }
        if (asksStructureDetail(detection.getNormalizedMessage())) {
            return detection;
        }
        String searchTerm = medicamentChatbotSearchService.extractSearchTerm(detection.getNormalizedMessage());
        if (searchTerm.isBlank()) {
            return detection;
        }
        return detection.toBuilder()
                .intent(ChatIntent.MEDICATION_SEARCH)
                .keyword("medicament")
                .build();
    }

    private boolean shouldRouteAsMedicationFollowUp(IntentDetectionResult detection) {
        if (detection == null || detection.getNormalizedMessage() == null) {
            return false;
        }
        String normalizedMessage = detection.getNormalizedMessage();
        if (!medicamentChatbotSearchService.extractSearchTerm(normalizedMessage).isBlank()) {
            return false;
        }
        return normalizedMessage.startsWith("et ") && asksMedicationDetail(normalizedMessage)
                || normalizedMessage.contains("ce medicament") && asksMedicationDetail(normalizedMessage);
    }

    private boolean shouldRouteAsStructureFollowUp(IntentDetectionResult detection) {
        if (detection == null || detection.getNormalizedMessage() == null) {
            return false;
        }
        if (hasExplicitStructureSearchName(detection.getNormalizedMessage())) {
            return false;
        }
        return pointsToContextualStructure(detection.getNormalizedMessage())
                && asksStructureDetail(detection.getNormalizedMessage());
    }

    private boolean hasExplicitStructureSearchName(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return false;
        }
        if (!List.of("pharmacie", "pharmacies", "hopital", "hopitaux", "clinique", "centre", "centres",
                "structure", "structures").stream().anyMatch(normalizedMessage::contains)) {
            return false;
        }
        Set<String> searchTokens = List.of(normalizedMessage.split("\\s+")).stream()
                .filter(token -> token.length() >= 3)
                .filter(token -> !Set.of("une", "des", "les", "dans", "pour", "avec", "pharmacie",
                        "pharmacies", "partenaire", "partenaires", "agreee", "agreees", "trouve",
                        "existe", "numero", "telephone", "tel", "situe", "situee", "trouver", "quel", "quelle",
                        "quels", "quelles", "sont", "disponible", "disponibles", "conventionne",
                        "conventionnes", "conventionnee", "conventionnees", "hopital", "hopitaux",
                        "clinique", "centre", "centres", "sante", "structure", "structures", "cherche", "veux",
                        "montre", "puis", "pres", "proche", "proches", "plus", "region",
                        "adresse", "est", "moi").contains(token))
                .collect(java.util.stream.Collectors.toSet());
        return !searchTokens.isEmpty();
    }

    private boolean asksStructureDetail(String normalizedMessage) {
        return normalizedMessage.contains("adresse")
                || normalizedMessage.contains("telephone")
                || normalizedMessage.contains("numero")
                || normalizedMessage.contains("region")
                || normalizedMessage.contains("ville")
                || normalizedMessage.contains("conventionne")
                || normalizedMessage.contains("conventionnee")
                || normalizedMessage.contains("agree")
                || normalizedMessage.contains("agreee")
                || normalizedMessage.contains("partenaire")
                || normalizedMessage.contains("distance")
                || normalizedMessage.contains("combien de km")
                || normalizedMessage.contains("kilometre")
                || normalizedMessage.contains("ou se trouve");
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

    private boolean asksMedicationDetail(String normalizedMessage) {
        return normalizedMessage.contains("prix")
                || normalizedMessage.contains("coute")
                || normalizedMessage.contains("taux")
                || normalizedMessage.contains("pourcentage")
                || normalizedMessage.contains("statut")
                || normalizedMessage.contains("rembourse")
                || normalizedMessage.contains("remboursable")
                || normalizedMessage.contains("ma part")
                || normalizedMessage.contains("part beneficiaire")
                || normalizedMessage.contains("part du beneficiaire")
                || normalizedMessage.contains("reste a charge")
                || normalizedMessage.contains("reste a ma charge")
                || normalizedMessage.contains("inam")
                || normalizedMessage.contains("entente prealable")
                || normalizedMessage.contains("accord prealable")
                || normalizedMessage.contains("autorisation prealable")
                || normalizedMessage.contains("entente avant")
                || normalizedMessage.contains("accord avant");
    }

    private boolean shouldRouteAsPrestationFollowUp(IntentDetectionResult detection) {
        if (detection == null || detection.getNormalizedMessage() == null) {
            return false;
        }
        String normalizedMessage = detection.getNormalizedMessage();
        if (isGeneralPrestationsListQuestion(normalizedMessage)) {
            return false;
        }
        boolean pointsToPrestation = normalizedMessage.startsWith("et ")
                || normalizedMessage.contains("cette prestation")
                || normalizedMessage.contains("la prestation")
                || normalizedMessage.startsWith("elle ")
                || normalizedMessage.contains("son ")
                || normalizedMessage.contains("leur ");
        return pointsToPrestation && asksPrestationDetail(normalizedMessage);
    }

    private boolean asksPrestationDetail(String normalizedMessage) {
        return normalizedMessage.contains("taux")
                || normalizedMessage.contains("a quel taux")
                || normalizedMessage.contains("a combien")
                || normalizedMessage.contains("combien")
                || normalizedMessage.contains("prise en charge")
                || normalizedMessage.contains("pris en charge")
                || normalizedMessage.contains("couverte")
                || normalizedMessage.contains("couvert")
                || normalizedMessage.contains("prestation");
    }

    private boolean asksMissingPrestationContext(String normalizedMessage) {
        return normalizedMessage != null
                && (normalizedMessage.contains("a quel taux")
                || normalizedMessage.contains("cette prestation")
                || normalizedMessage.contains("la prestation")
                || normalizedMessage.startsWith("elle ")
                || normalizedMessage.contains("leur "));
    }

    private boolean asksMissingStructureContext(String normalizedMessage) {
        return normalizedMessage != null && asksStructureDetail(normalizedMessage);
    }

    private boolean isGeneralPrestationsListQuestion(String normalizedMessage) {
        if (normalizedMessage == null) {
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
}
