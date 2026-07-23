package com.amuguide.backend.chat.service;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MedicationKnowledgeService {

    private static final Map<String, String> BRAND_TO_DCI = Map.of(
            "panadol", "paracetamol",
            "doliprane", "paracetamol",
            "efferalgan", "paracetamol",
            "dafalgan", "paracetamol"
    );

    private final List<MedicationEntry> entries = new ArrayList<>();

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource("chat/amu-medicaments-2025.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.startsWith("#"))
                    .forEach(line -> entries.add(new MedicationEntry(line, normalize(line))));
        } catch (Exception ex) {
            entries.clear();
        }
    }

    public List<String> search(String question, int limit) {
        Set<String> queryTokens = tokens(expandBrands(normalize(question)));
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        return entries.stream()
                .map(entry -> new MedicationMatch(entry.label(), score(entry.normalized(), queryTokens)))
                .filter(match -> match.score() >= 4)
                .sorted(Comparator.comparingInt(MedicationMatch::score).reversed())
                .limit(limit)
                .map(MedicationMatch::label)
                .toList();
    }

    public List<String> list(int limit) {
        return entries.stream()
                .map(MedicationEntry::label)
                .limit(limit)
                .toList();
    }

    public boolean isListRequest(String question) {
        String normalized = normalize(question);
        return normalized.contains("liste")
                || normalized.contains("lister")
                || normalized.contains("tous les medicaments")
                || normalized.contains("quels medicaments")
                || normalized.contains("donne moi les medicaments")
                || normalized.contains("donne moi la liste");
    }

    public String resolveBrand(String question) {
        String normalized = normalize(question);
        return BRAND_TO_DCI.entrySet().stream()
                .filter(entry -> normalized.contains(entry.getKey()))
                .map(entry -> entry.getKey().toUpperCase(Locale.ROOT) + " correspond generalement a " + entry.getValue().toUpperCase(Locale.ROOT))
                .findFirst()
                .orElse(null);
    }

    public int count() {
        return entries.size();
    }

    private int score(String candidate, Set<String> queryTokens) {
        Set<String> candidateTokens = tokens(candidate);
        int score = 0;
        for (String token : queryTokens) {
            if (token.length() < 3) {
                continue;
            }
            if (candidateTokens.contains(token)) {
                score += 4;
            } else if (candidate.contains(token)) {
                score += 2;
            } else if (candidateTokens.stream().anyMatch(candidateToken -> similarity(candidateToken, token) >= 0.82)) {
                score += 1;
            }
        }
        return score;
    }

    private Set<String> tokens(String value) {
        return List.of(value.split("\\s+")).stream()
                .map(String::trim)
                .filter(token -> token.length() >= 3)
                .filter(token -> !List.of("est", "dans", "avec", "pour", "une", "des", "les", "par", "pris", "prise", "inam", "medicament", "medicaments", "couvert", "couverte", "rembourse", "remboursee", "charge", "amu").contains(token))
                .collect(Collectors.toSet());
    }

    private String expandBrands(String normalized) {
        String expanded = normalized;
        for (Map.Entry<String, String> entry : BRAND_TO_DCI.entrySet()) {
            if (expanded.contains(entry.getKey())) {
                expanded = expanded + " " + entry.getValue();
            }
        }
        return expanded;
    }

    private String normalize(String value) {
        String withoutAccents = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double similarity(String left, String right) {
        int distance = levenshtein(left, right);
        int maxLength = Math.max(left.length(), right.length());
        return maxLength == 0 ? 0 : 1.0 - ((double) distance / maxLength);
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

    private record MedicationEntry(String label, String normalized) {
    }

    private record MedicationMatch(String label, int score) {
    }
}
