package com.amuguide.backend.chat.service;

import com.amuguide.backend.entity.Medicament;
import com.amuguide.backend.repository.MedicamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MedicamentChatbotSearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final Pattern CODE_PATTERN = Pattern.compile("\\b[A-Z0-9][A-Z0-9._/-]{2,}\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP_WORDS = Set.of(
            "est", "c'est", "ce", "ca", "que", "quoi", "quel", "quelle", "quels", "quelles", "dans", "avec", "pour", "par", "sur", "a", "un", "une", "des", "les",
            "il", "ils", "elle", "elles", "je", "tu", "nous", "vous", "moi", "mon", "ma", "mes", "ton", "ta", "son", "sa", "ses", "vos", "notre", "sont", "etre", "fait", "faut", "comment", "combien",
            "dois", "veux", "savoir", "peux", "donne", "avoir", "acheter", "payer", "paie", "coute", "inam", "amu", "medicament", "medicaments", "contient", "contiennent", "prend", "pris", "prise",
            "charge", "couvert", "couverte", "couvre", "couvrir", "rembourse", "remboursee", "remboursable", "rembourses", "remboursement",
            "taux", "pourcentage", "montant", "part", "reste", "prix", "code", "cherche", "infos", "informations",
            "public", "base", "statut", "beneficiaire", "entente", "prealable", "accord", "autorisation", "necessaire",
            "necessite", "necessitent", "soumis",
            "soumise", "soumettre", "avant", "faire", "et", "en", "du", "de", "la", "le", "l"
    );

    private final MedicamentRepository medicamentRepository;

    public MedicationSearchResult search(String question) {
        return search(question, DEFAULT_LIMIT);
    }

    public MedicationSearchResult search(String question, int limit) {
        String searchTerm = extractSearchTerm(question);
        if (searchTerm.isBlank()) {
            return MedicationSearchResult.notFound(searchTerm, List.of());
        }

        Optional<MedicationEvidence> byCode = findByCode(question, searchTerm);
        if (byCode.isPresent()) {
            return MedicationSearchResult.single(searchTerm, byCode.get());
        }

        Optional<MedicationEvidence> byExactName = medicamentRepository.findByNomIgnoreCaseAndActifTrue(searchTerm)
                .filter(this::isOfficial)
                .map(MedicationEvidence::from);
        if (byExactName.isPresent()) {
            return MedicationSearchResult.single(searchTerm, byExactName.get());
        }

        List<MedicationEvidence> matches = uniqueByCode(searchByText(searchTerm, limit));
        if (matches.size() == 1) {
            return MedicationSearchResult.single(searchTerm, matches.get(0));
        }
        if (!matches.isEmpty()) {
            return MedicationSearchResult.multiple(searchTerm, matches);
        }

        List<MedicationEvidence> suggestions = typoSuggestions(searchTerm, limit);
        return MedicationSearchResult.notFound(searchTerm, suggestions);
    }

    public List<MedicationEvidence> searchByDci(String question, int limit) {
        String searchTerm = extractSearchTerm(question);
        if (searchTerm.isBlank()) {
            return List.of();
        }
        return uniqueByCode(medicamentRepository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc(searchTerm).stream()
                .filter(this::isOfficial)
                .limit(limit)
                .map(MedicationEvidence::from)
                .toList());
    }

    public List<MedicationEvidence> listActive(int limit) {
        return medicamentRepository.searchForChatbot("", PageRequest.of(0, limit)).stream()
                .map(MedicationEvidence::from)
                .toList();
    }

    public Optional<MedicationEvidence> findActiveById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return medicamentRepository.findById(id)
                .filter(this::isActive)
                .filter(this::isOfficial)
                .map(MedicationEvidence::from);
    }

    public String extractSearchTerm(String question) {
        String normalized = normalize(question);
        if (normalized.isBlank()) {
            return "";
        }

        String cleaned = normalized
                .replaceAll("\\b([ld])'(?=[a-z0-9])", "$1 ")
                .replaceAll("\\b(est ce que|quel est|quelle est|quels sont|quelles sont|combien|dois je|doit on|l inam|inam|paie|payer|pour|a combien|taux de|taux de remboursement|prise en charge|pris en charge|remboursement|rembourse|remboursee|remboursable|medicament|medicaments|contient|contiennent|entente prealable|accord prealable|autorisation prealable|statut|prix public|base remboursement|part beneficiaire|part du beneficiaire|part de l'inam|part de l inam|part inam)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();

        List<String> tokens = List.of(cleaned.split("\\s+")).stream()
                .map(String::trim)
                .map(token -> token.replaceAll("^[._/-]+|[._/-]+$", ""))
                .filter(token -> token.length() >= 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .toList();

        if (tokens.isEmpty()) {
            return "";
        }
        return canonicalizeSearchTerm(String.join(" ", tokens));
    }

    private Optional<MedicationEvidence> findByCode(String question, String searchTerm) {
        List<String> candidates = new ArrayList<>();
        candidates.add(searchTerm);

        var matcher = CODE_PATTERN.matcher(question == null ? "" : question);
        while (matcher.find()) {
            candidates.add(matcher.group());
        }

        return candidates.stream()
                .map(String::trim)
                .filter(candidate -> candidate.length() >= 3)
                .distinct()
                .map(medicamentRepository::findByCodeIgnoreCase)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::isActive)
                .filter(this::isOfficial)
                .map(MedicationEvidence::from)
                .findFirst();
    }

    private List<MedicationEvidence> searchByText(String searchTerm, int limit) {
        List<Medicament> results = new ArrayList<>();
        results.addAll(medicamentRepository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc(searchTerm));
        results.addAll(medicamentRepository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc(searchTerm));
        results.addAll(medicamentRepository.searchForChatbot(searchTerm, PageRequest.of(0, limit)));
        results.addAll(searchByExactTokens(searchTerm, limit));

        return results.stream()
                .filter(this::isActive)
                .filter(this::isOfficial)
                .sorted(Comparator.comparingInt(medicament -> score(medicament, searchTerm)))
                .limit(limit)
                .map(MedicationEvidence::from)
                .toList();
    }

    private List<Medicament> searchByExactTokens(String searchTerm, int limit) {
        List<String> queryTokens = significantMedicationTokens(searchTerm);
        if (queryTokens.size() < 2) {
            return List.of();
        }

        String firstToken = queryTokens.get(0);
        if (firstToken.length() < 4) {
            return List.of();
        }

        return medicamentRepository.searchForChatbot(firstToken, PageRequest.of(0, limit)).stream()
                .filter(this::isActive)
                .filter(this::isOfficial)
                .filter(medicament -> candidateContainsAllTokens(medicament, queryTokens))
                .toList();
    }

    private List<MedicationEvidence> typoSuggestions(String searchTerm, int limit) {
        if (searchTerm.length() < 5) {
            return List.of();
        }

        String firstToken = searchTerm.split("\\s+")[0];
        return medicamentRepository.searchForChatbot(firstToken.substring(0, Math.min(4, firstToken.length())), PageRequest.of(0, limit)).stream()
                .filter(this::isActive)
                .filter(this::isOfficial)
                .map(MedicationEvidence::from)
                .filter(evidence -> similarity(normalize(evidence.nom()), searchTerm) >= 0.82
                        || similarity(normalize(evidence.dci()), searchTerm) >= 0.82)
                .limit(3)
                .toList();
    }

    private List<MedicationEvidence> uniqueByCode(List<MedicationEvidence> evidences) {
        Map<String, MedicationEvidence> unique = new LinkedHashMap<>();
        for (MedicationEvidence evidence : evidences) {
            String key = evidence.code() == null ? evidence.nom() : evidence.code();
            unique.putIfAbsent(key, evidence);
        }
        return List.copyOf(unique.values());
    }

    private int score(Medicament medicament, String searchTerm) {
        String normalizedNom = canonicalizeSearchTerm(normalize(medicament.getNom()));
        String normalizedDci = canonicalizeSearchTerm(normalize(medicament.getDci()));
        if (normalizedNom.equals(searchTerm)) {
            return 0;
        }
        if (normalizedDci.equals(searchTerm)) {
            return 1;
        }
        if (normalizedNom.startsWith(searchTerm)) {
            return 2;
        }
        if (normalizedDci.startsWith(searchTerm)) {
            return 3;
        }
        if (normalizedNom.contains(searchTerm)) {
            return 4;
        }
        if (normalizedDci.contains(searchTerm)) {
            return 5;
        }
        return 6;
    }

    private boolean candidateContainsAllTokens(Medicament medicament, List<String> queryTokens) {
        Set<String> candidateTokens = Set.copyOf(significantMedicationTokens(String.join(" ",
                normalize(medicament.getCode()),
                normalize(medicament.getNom()),
                normalize(medicament.getDci()),
                normalize(medicament.getDosage())
        )));
        return candidateTokens.containsAll(queryTokens);
    }

    private List<String> significantMedicationTokens(String value) {
        String canonical = canonicalizeSearchTerm(normalize(value));
        if (canonical.isBlank()) {
            return List.of();
        }
        return List.of(canonical.split("\\s+")).stream()
                .filter(token -> token.length() >= 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .toList();
    }

    private String canonicalizeSearchTerm(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace('.', ' ')
                .replaceAll("\\b(comprime|comprimes)\\b", "comp")
                .replaceAll("\\b(effervescent|effervescente)\\b", "eff")
                .replaceAll("\\b(milligramme|milligrammes)\\b", "mg")
                .replaceAll("\\b(gramme|grammes)\\b", "g")
                .replaceAll("(?<=[a-z])(?=\\d)", " ")
                .replaceAll("\\b(\\d+)\\s+(mg|ml|mcg|g)\\b", "$1$2")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isActive(Medicament medicament) {
        return medicament.getActif() == null || Boolean.TRUE.equals(medicament.getActif());
    }

    private boolean isOfficial(Medicament medicament) {
        return medicament.getStatut() != null
                && medicament.getTypeMedicament() != null
                && medicament.getBaseRemboursement() != null;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replace('-', ' ')
                .replaceAll("[^a-z0-9'\\s._/-]", " ")
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

    public record MedicationSearchResult(
            String query,
            List<MedicationEvidence> matches,
            List<MedicationEvidence> suggestions,
            boolean found,
            boolean ambiguous
    ) {
        static MedicationSearchResult single(String query, MedicationEvidence evidence) {
            return new MedicationSearchResult(query, List.of(evidence), List.of(), true, false);
        }

        static MedicationSearchResult multiple(String query, List<MedicationEvidence> matches) {
            return new MedicationSearchResult(query, matches, List.of(), true, true);
        }

        static MedicationSearchResult notFound(String query, List<MedicationEvidence> suggestions) {
            return new MedicationSearchResult(query, List.of(), suggestions, false, false);
        }
    }
}
