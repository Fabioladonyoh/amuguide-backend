package com.amuguide.backend.chat.nlp;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IntentDetector {

    private static final Pattern HOSPITAL_PATTERN = Pattern.compile(
            "\\b(hopital|hopitaux|chu|clinique|centre|structure|structures|pharmacie|agree|agre[e]?e?)\\b");
    private static final Pattern CITY_PATTERN = Pattern.compile("\\b(?:a|au|dans|de|sur)\\s+([a-zA-Z\\p{L}'-]{2,})\\b");
    private static final Pattern AMU_INFO_PATTERN = Pattern.compile(
            "\\b(amu|assurance maladie universelle|assurance maladie|regime amu)\\b");
    private static final Pattern DEFINITION_PATTERN = Pattern.compile(
            "\\b(c'est quoi|c est quoi|qu'est ce que|qu est ce que|ce que c'est|ce que c est|definition|definir|explique|signifie|ca veut dire|a quoi sert|role|objectif|fonctionne|fonctionnement)\\b");
    private static final Pattern COVERAGE_PATTERN = Pattern.compile(
            "\\b(couvert|couverts|couverte|couvertes|couverture|prestations?|prise en charge|pris en charge|rembourse|remboursement|taux)\\b");
    private static final Pattern MEDICATION_PATTERN = Pattern.compile(
            "\\b(medicament|medicaments|pharmaceutique|referentiel|panadol|doliprane|efferalgan|dafalgan|paracetamol|amoxicilline|ibuprofene|ceftriaxone|metronidazole|quinine|diclofenac|tramadol|morphine|gentamicine|salbutamol|furosemide|insuline|gants?|aiguille|bande|sonde|catheter)\\b");
    private static final Pattern PROCEDURE_PATTERN = Pattern.compile(
            "\\b(document|documents|piece|pieces|condition|conditions|procedure|demarche|ordonnance|carte|renouveler|renouvellement|utiliser|beneficier|faire)\\b");

    private static final List<String> GREETINGS = List.of(
            "salut", "bonjour", "bonsoir", "hello", "coucou", "bjr", "slt");

    private static final Map<String, List<String>> COVERAGE_SYNONYMS = Map.of(
            "consultation", List.of("consultation", "consult", "docteur", "medecin", "generaliste"),
            "radiologie", List.of("radiologie", "radio", "scanner", "imagerie", "radiographie"),
            "dent", List.of("dent", "dentaire", "dentiste", "soin dentaire"),
            "hospitalisation", List.of("hospitalisation", "hospitalise", "sejour hopital"),
            "medicament", List.of("medicament", "medicaments", "pharmacie", "ordonnance")
    );

    public IntentDetectionResult detect(String message) {
        String normalized = normalize(message);
        String coverageKeyword = bestKeyword(normalized);

        if (GREETINGS.stream().anyMatch(greeting -> similarity(normalized, greeting) >= 0.82 || normalized.contains(greeting))) {
            return result(ChatIntent.GREETING, normalized, null, null, 1);
        }

        if (isAmuInfoQuestion(normalized)) {
            return result(ChatIntent.AMU_INFO, normalized, "amu", null, 0.95);
        }

        if (MEDICATION_PATTERN.matcher(normalized).find() && normalized.contains("rembours")) {
            return result(ChatIntent.COVERAGE, normalized, "medicament", null, 0.95);
        }

        Matcher hospitalMatcher = HOSPITAL_PATTERN.matcher(normalized);
        if (hospitalMatcher.find()) {
            return result(ChatIntent.HOSPITAL_SEARCH, normalized, hospitalMatcher.group(), extractCity(normalized), 0.95);
        }

        if (PROCEDURE_PATTERN.matcher(normalized).find() || normalized.contains("que faut il")) {
            return result(ChatIntent.PROCEDURE, normalized, coverageKeyword, null, 0.8);
        }

        if (MEDICATION_PATTERN.matcher(normalized).find()) {
            return result(ChatIntent.MEDICATION_SEARCH, normalized, "medicament", null, 0.9);
        }

        if (coverageKeyword != null || COVERAGE_PATTERN.matcher(normalized).find()) {
            return result(ChatIntent.COVERAGE, normalized, coverageKeyword, null, coverageKeyword == null ? 0.72 : 0.95);
        }

        return result(ChatIntent.FALLBACK, normalized, null, null, 0);
    }

    private boolean isAmuInfoQuestion(String normalized) {
        if (!AMU_INFO_PATTERN.matcher(normalized).find()) {
            return false;
        }
        return DEFINITION_PATTERN.matcher(normalized).find()
                || normalized.matches("\\b(amu|l'amu|assurance maladie universelle)\\b");
    }

    private String bestKeyword(String normalized) {
        String best = null;
        double bestScore = 0;

        for (Map.Entry<String, List<String>> entry : COVERAGE_SYNONYMS.entrySet()) {
            for (String synonym : entry.getValue()) {
                double score = normalized.contains(synonym) ? 1 : similarity(normalized, synonym);
                if (score > bestScore) {
                    bestScore = score;
                    best = entry.getKey();
                }
            }
        }

        return bestScore >= 0.72 ? best : null;
    }

    private String extractCity(String normalized) {
        Matcher matcher = CITY_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!List.of("charge", "couverture", "amu", "sante", "moi", "vous").contains(candidate)) {
                return capitalize(candidate);
            }
        }
        return null;
    }

    private IntentDetectionResult result(ChatIntent intent, String normalized, String keyword, String city, double score) {
        return IntentDetectionResult.builder()
                .intent(intent)
                .normalizedMessage(normalized)
                .keyword(keyword)
                .city(city)
                .score(score)
                .build();
    }

    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replace("â€™", "'")
                .replace('-', ' ')
                .replaceAll("[^a-z0-9'\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double similarity(String left, String right) {
        if (left.isBlank() || right.isBlank()) {
            return 0;
        }
        int distance = levenshtein(left, right);
        int maxLength = Math.max(left.length(), right.length());
        return 1.0 - ((double) distance / maxLength);
    }

    private int levenshtein(String left, String right) {
        int[] costs = new int[right.length() + 1];
        for (int j = 0; j < costs.length; j++) {
            costs[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            costs[0] = i;
            int previous = i - 1;
            for (int j = 1; j <= right.length(); j++) {
                int current = costs[j];
                costs[j] = Math.min(
                        Math.min(costs[j] + 1, costs[j - 1] + 1),
                        previous + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1)
                );
                previous = current;
            }
        }
        return costs[right.length()];
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }
}
