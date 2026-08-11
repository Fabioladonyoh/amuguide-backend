package com.amuguide.backend.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AiFallbackService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    @Value("${chatbot.ai.provider:none}")
    private String provider;

    @Value("${chatbot.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${chatbot.ai.openai.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${chatbot.ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${chatbot.ai.gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    @Value("${chatbot.ai.ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${chatbot.ai.ollama.model:llama3.2}")
    private String ollamaModel;

    public Optional<String> answer(String message) {
        return answer(message, "");
    }

    public String activeProvider() {
        return provider == null || provider.isBlank() ? "none" : provider.toLowerCase();
    }

    public boolean isOpenAiConfigured() {
        return openAiApiKey != null && !openAiApiKey.isBlank();
    }

    public boolean isGeminiConfigured() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    public boolean isOllamaEnabled() {
        String activeProvider = activeProvider();
        return "auto".equals(activeProvider) || "ollama".equals(activeProvider);
    }

    public String openAiModel() {
        return openAiModel;
    }

    public Optional<String> answer(String message, String toolContext) {
        try {
            return switch (activeProvider()) {
                case "auto" -> askAuto(message, toolContext);
                case "openai" -> askOpenAi(message, toolContext);
                case "gemini" -> askGemini(message, toolContext);
                case "ollama" -> askOllama(message, toolContext);
                default -> Optional.empty();
            };
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<String> askAuto(String message, String toolContext) {
        Optional<String> openAiAnswer = askOpenAi(message, toolContext);
        if (openAiAnswer.isPresent()) {
            return openAiAnswer;
        }

        Optional<String> geminiAnswer = askGemini(message, toolContext);
        if (geminiAnswer.isPresent()) {
            return geminiAnswer;
        }

        return askOllama(message, toolContext);
    }

    private Optional<String> askOpenAi(String message, String toolContext) {
        if (openAiApiKey.isBlank()) {
            return Optional.empty();
        }

        Map<String, Object> body = Map.of(
                "model", openAiModel,
                "instructions", systemPrompt(),
                "input", userPrompt(message, toolContext)
        );

        String response = restClient.post()
                .uri("https://api.openai.com/v1/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + openAiApiKey)
                .body(body)
                .retrieve()
                .body(String.class);

        Optional<String> outputText = readText(response, "/output_text");
        if (outputText.isPresent()) {
            return outputText;
        }
        return readText(response, "/output/0/content/0/text");
    }

    private Optional<String> askGemini(String message, String toolContext) {
        if (geminiApiKey.isBlank()) {
            return Optional.empty();
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", systemPrompt() + "\n\n" + userPrompt(message, toolContext)))
                ))
        );

        String response = restClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key=" + geminiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return readText(response, "/candidates/0/content/parts/0/text");
    }

    private Optional<String> askOllama(String message, String toolContext) {
        Map<String, Object> body = Map.of(
                "model", ollamaModel,
                "prompt", systemPrompt() + "\n\n" + userPrompt(message, toolContext),
                "stream", false
        );

        String response = restClient.post()
                .uri(ollamaUrl + "/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return readText(response, "/response");
    }

    private Optional<String> readText(String json, String pointer) {
        try {
            JsonNode node = objectMapper.readTree(json).at(pointer);
            if (node.isMissingNode() || node.asText().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(node.asText().trim());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String systemPrompt() {
        return "Tu es l'assistant AMU Guide. Raisonne comme un conseiller AMU: comprends l'intention de l'utilisateur, utilise les donnees verifiees du backend, puis reponds en francais de facon naturelle, claire et utile. Tu aides uniquement sur l'assurance maladie, les prestations, les medicaments/dispositifs du referentiel AMU, les documents et les structures de sante. Utilise d'abord le contexte fourni par les outils. Pour les medicaments et remboursements, les donnees PostgreSQL fournies sont la seule source de verite. Ne jamais inventer un taux, un prix, un statut, une part INAM, une part beneficiaire ou une prise en charge. Ne jamais affirmer qu'un medicament est pris en charge s'il n'est pas present dans les donnees recuperees. Si aucune donnee officielle n'est fournie pour un medicament, dis clairement que l'information n'a pas ete trouvee dans le referentiel AMU disponible. Tu peux reformuler les donnees pour les rendre comprehensibles, mais tu ne dois pas les modifier.";
    }

    private String userPrompt(String message, String toolContext) {
        if (toolContext == null || toolContext.isBlank()) {
            return "Question utilisateur : " + message;
        }
        return "Contexte verifie par les outils backend :\n"
                + toolContext
                + "\n\nQuestion utilisateur : "
                + message
                + "\n\nRedige une reponse utile pour l'utilisateur. Mentionne clairement quand une information vient du referentiel ou de la base AMU Guide.";
    }
}
