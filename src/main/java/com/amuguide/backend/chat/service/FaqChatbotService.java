package com.amuguide.backend.chat.service;

import com.amuguide.backend.chat.nlp.ChatIntent;
import com.amuguide.backend.chat.nlp.IntentDetectionResult;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.dto.ChatbotSourceDTO;
import com.amuguide.backend.entity.FaqChatbot;
import com.amuguide.backend.enums.FaqChatbotCategorie;
import com.amuguide.backend.repository.FaqChatbotRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaqChatbotService {

    private static final int MIN_SCORE = 10;
    private static final Set<String> STOP_WORDS = Set.of(
            "est", "ce", "que", "qui", "quoi", "dans", "avec", "pour", "une", "des", "les",
            "mon", "ma", "mes", "ton", "ta", "vos", "notre", "sont", "etre", "fait", "faut",
            "comment", "quelles", "quelle", "quel", "quels", "trouve", "moi"
    );

    private final FaqChatbotRepository faqChatbotRepository;
    private final ResponseGenerator responseGenerator;

    @PostConstruct
    void logLoadedFaqs() {
        log.info("Nombre de FAQ chatbot : {}", faqChatbotRepository.count());
        faqChatbotRepository.findAll().forEach(faq ->
                log.info("FAQ chargee : id={}, question={}, motsCles={}, actif={}",
                        faq.getId(),
                        faq.getQuestion(),
                        faq.getMotsCles(),
                        faq.getActif())
        );
    }

    public Optional<ChatbotResponseDTO> answer(IntentDetectionResult detection) {
        String normalizedQuestion = normalize(detection.getNormalizedMessage());
        log.info("Question normalisee : [{}]", normalizedQuestion);
        if (normalizedQuestion.isBlank()) {
            return Optional.empty();
        }

        List<FaqMatch> matches = faqChatbotRepository.findByActifTrue().stream()
                .map(faq -> {
                    int score = calculateScore(normalizedQuestion, detection, faq);
                    log.info("Score FAQ id={} question=[{}] score={}", faq.getId(), faq.getQuestion(), score);
                    return new FaqMatch(faq, score);
                })
                .toList();

        Optional<FaqMatch> bestMatch = matches.stream()
                .max(Comparator.comparingInt(FaqMatch::score)
                        .thenComparing(match -> valueOrZero(match.faq().getPriorite())));

        log.info("Meilleure FAQ : {}, score={}",
                bestMatch.map(match -> match.faq().getQuestion()).orElse("aucune"),
                bestMatch.map(FaqMatch::score).orElse(0));

        return bestMatch
                .filter(match -> match.score() >= MIN_SCORE)
                .map(match -> buildResponse(match.faq(), detection, match.score()));
    }

    public String normalize(String text) {
        if (text == null) {
            return "";
        }

        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        normalized = normalized.replace("â€™", "'");
        normalized = normalized.replaceAll("[^a-z0-9\\s]", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    private int calculateScore(String userQuestion, IntentDetectionResult detection, FaqChatbot faq) {
        int score = 0;
        String faqQuestion = normalize(faq.getQuestion());

        if (userQuestion.equals(faqQuestion)) {
            score += 100;
        }

        if (!faqQuestion.isBlank() && (userQuestion.contains(faqQuestion) || faqQuestion.contains(userQuestion))) {
            score += 30;
        }

        for (String keyword : keywords(faq.getMotsCles())) {
            String normalizedKeyword = normalize(keyword);
            if (normalizedKeyword.isBlank()) {
                continue;
            }
            if (userQuestion.contains(normalizedKeyword)) {
                score += 20;
            } else if (keywordTokensMatch(userQuestion, normalizedKeyword)) {
                score += 10;
            } else if (tokens(userQuestion).contains(normalizedKeyword)) {
                score += 5;
            }
        }

        Set<String> questionTokens = tokens(userQuestion);
        Set<String> faqTokens = tokens(faqQuestion + " " + normalize(faq.getMotsCles()));
        questionTokens.retainAll(faqTokens);
        score += questionTokens.size() * 5;

        if (sameCategory(detection, faq.getCategorie())) {
            score += 10;
        }

        score += valueOrZero(faq.getPriorite());
        return score;
    }

    private ChatbotResponseDTO buildResponse(FaqChatbot faq, IntentDetectionResult detection, int score) {
        ChatbotResponseDTO dynamicResponse = dynamicResponse(faq, detection);
        if (dynamicResponse != null) {
            dynamicResponse.setCategory(faq.getCategorie().name());
            dynamicResponse.setIntent(intentFor(faq.getCategorie()).name());
            dynamicResponse.setConfidence(Math.max(valueOrZero(dynamicResponse.getConfidence()), Math.min(1.0, score / 15.0)));
            dynamicResponse.setFound(dynamicResponse.getFound() == null ? true : dynamicResponse.getFound());
            addFaqSource(dynamicResponse, faq);
            return dynamicResponse;
        }

        return ChatbotResponseDTO.builder()
                .answer(faq.getReponse())
                .message(faq.getReponse())
                .category(faq.getCategorie().name())
                .intent(intentFor(faq.getCategorie()).name())
                .found(true)
                .confidence(Math.min(1.0, score / 15.0))
                .sources(List.of(faqSource(faq)))
                .suggestionList(defaultSuggestions(faq.getCategorie()))
                .suggestions("Suggestions : " + String.join(", ", defaultSuggestions(faq.getCategorie())))
                .build();
    }

    private ChatbotResponseDTO dynamicResponse(FaqChatbot faq, IntentDetectionResult detection) {
        ChatIntent intent = dynamicIntentFor(faq);
        if (faq.getCategorie() == FaqChatbotCategorie.INFORMATION_AMU) {
            return null;
        }

        IntentDetectionResult dynamicDetection = detection.toBuilder()
                .intent(intent)
                .keyword(keywordFor(faq, detection))
                .city(cityFor(faq, detection))
                .build();

        return responseGenerator.generate(dynamicDetection);
    }

    private String keywordFor(FaqChatbot faq, IntentDetectionResult detection) {
        if (detection.getKeyword() != null && !detection.getKeyword().isBlank()) {
            return detection.getKeyword();
        }

        String code = faq.getCode();
        if (code == null) {
            return null;
        }
        if (code.contains("CONSULTATION")) {
            return "consultation";
        }
        if (code.contains("HOSPITALISATION")) {
            return "hospitalisation";
        }
        if (code.contains("RADIOLOGIE")) {
            return "radiologie";
        }
        if (code.contains("MEDICAMENT")) {
            return "medicament";
        }
        return null;
    }

    private String cityFor(FaqChatbot faq, IntentDetectionResult detection) {
        if (detection.getCity() != null && !detection.getCity().isBlank()) {
            return detection.getCity();
        }
        String normalized = normalize(faq.getQuestion() + " " + faq.getMotsCles());
        if (normalized.contains("lome")) {
            return "Lome";
        }
        if (normalized.contains("kara")) {
            return "Kara";
        }
        if (normalized.contains("sokode")) {
            return "Sokode";
        }
        if (normalized.contains("atakpame")) {
            return "Atakpame";
        }
        if (normalized.contains("dapaong")) {
            return "Dapaong";
        }
        return null;
    }

    private boolean sameCategory(IntentDetectionResult detection, FaqChatbotCategorie categorie) {
        return intentFor(categorie) == detection.getIntent();
    }

    private ChatIntent intentFor(FaqChatbotCategorie categorie) {
        return switch (categorie) {
            case PRESTATION -> ChatIntent.COVERAGE;
            case STRUCTURE_SANTE -> ChatIntent.HOSPITAL_SEARCH;
            case MEDICAMENT -> ChatIntent.MEDICATION_SEARCH;
            case INFORMATION_AMU -> ChatIntent.AMU_INFO;
            case PROCEDURE -> ChatIntent.PROCEDURE;
        };
    }

    private ChatIntent dynamicIntentFor(FaqChatbot faq) {
        if (faq.getCode() != null && faq.getCode().contains("DOCUMENTS")) {
            return ChatIntent.PROCEDURE;
        }
        return intentFor(faq.getCategorie());
    }

    private List<String> keywords(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(keyword -> !keyword.isBlank())
                .toList();
    }

    private Set<String> tokens(String value) {
        return Arrays.stream(normalize(value).split("\\s+"))
                .filter(token -> token.length() >= 3)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toSet());
    }

    private boolean keywordTokensMatch(String userQuestion, String normalizedKeyword) {
        Set<String> keywordTokens = tokens(normalizedKeyword);
        return keywordTokens.size() > 1 && tokens(userQuestion).containsAll(keywordTokens);
    }

    private void addFaqSource(ChatbotResponseDTO response, FaqChatbot faq) {
        List<ChatbotSourceDTO> existing = response.getSources() == null ? List.of() : response.getSources();
        List<ChatbotSourceDTO> sources = new java.util.ArrayList<>();
        sources.add(faqSource(faq));
        sources.addAll(existing);
        response.setSources(sources);
    }

    private ChatbotSourceDTO faqSource(FaqChatbot faq) {
        return ChatbotSourceDTO.builder()
                .type("FAQ_CHATBOT")
                .id(faq.getId())
                .title(faq.getCode())
                .build();
    }

    private List<String> defaultSuggestions(FaqChatbotCategorie categorie) {
        return switch (categorie) {
            case PRESTATION -> List.of("Consultation", "Hospitalisation", "Radiologie");
            case STRUCTURE_SANTE -> List.of("Pharmacie a Lome", "Hopital a Kara", "Structures agreees");
            case MEDICAMENT -> List.of("Paracetamol", "Amoxicilline", "Ibuprofene");
            case INFORMATION_AMU -> List.of("Prestations couvertes", "Documents requis", "Carte AMU");
            case PROCEDURE -> List.of("Utiliser ma carte", "Documents requis", "Renouveler ma carte");
        };
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private double valueOrZero(Double value) {
        return value == null ? 0 : value;
    }

    private record FaqMatch(FaqChatbot faq, int score) {
    }
}
