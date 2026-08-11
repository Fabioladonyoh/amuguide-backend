package com.amuguide.backend.chat.service;

import com.amuguide.backend.chat.nlp.ChatIntent;
import com.amuguide.backend.chat.nlp.IntentDetectionResult;
import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.repository.PrestationRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentContextService {

    private static final List<String> STOP_WORDS = List.of(
            "est", "une", "des", "les", "avec", "pour", "dans", "quoi", "comment", "quel", "quelle", "inam",
            "quels", "quelles", "pris", "prise", "charge", "couvert", "couverte", "rembourse", "remboursee", "prendre",
            "amu", "sante", "maladie", "assurance", "universelle", "document", "documents");

    private final PrestationRepository prestationRepository;
    private final StructureSanteRepository structureSanteRepository;
    private final MedicationKnowledgeService medicationKnowledgeService;
    private final MedicamentChatbotSearchService medicamentChatbotSearchService;

    public AgentContext build(IntentDetectionResult detection) {
        ChatIntent intent = detection.getIntent();
        String message = detection.getNormalizedMessage();

        boolean includePrestations = intent == ChatIntent.COVERAGE
                || intent == ChatIntent.PROCEDURE
                || intent == ChatIntent.FALLBACK
                || intent == ChatIntent.AGENT_ASSISTED;
        boolean includeStructures = intent == ChatIntent.HOSPITAL_SEARCH
                || intent == ChatIntent.FALLBACK
                || intent == ChatIntent.AGENT_ASSISTED;
        boolean includeMedications = intent == ChatIntent.MEDICATION_SEARCH
                || intent == ChatIntent.FALLBACK
                || intent == ChatIntent.AGENT_ASSISTED;

        return build(message, includePrestations, includeStructures, includeMedications);
    }

    public AgentContext build(String message) {
        return build(message, true, true, true);
    }

    private AgentContext build(String message, boolean includePrestations, boolean includeStructures, boolean includeMedications) {
        String normalized = normalize(message);
        Set<String> tokens = tokens(normalized);

        List<Prestation> prestations = includePrestations ? prestationRepository.findAll().stream()
                .map(prestation -> new ScoredPrestation(prestation, scorePrestation(prestation, tokens, normalized)))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(ScoredPrestation::score).reversed())
                .limit(3)
                .map(ScoredPrestation::prestation)
                .toList() : List.of();

        List<StructureSante> structures = includeStructures ? structureSanteRepository.findByAgrementAMUTrue().stream()
                .map(structure -> new ScoredStructure(structure, scoreStructure(structure, tokens, normalized)))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(ScoredStructure::score).reversed())
                .limit(5)
                .map(ScoredStructure::structure)
                .toList() : List.of();

        List<MedicationEvidence> medications = includeMedications
                ? medicamentChatbotSearchService.search(message, 5).matches()
                : List.of();
        boolean amuInfoRelevant = normalized.contains("amu")
                || normalized.contains("assurance maladie")
                || normalized.contains("prise en charge")
                || normalized.contains("rembourse");

        return new AgentContext(prestations, structures, medications, amuInfoRelevant);
    }

    public String toPromptContext(AgentContext context) {
        StringBuilder builder = new StringBuilder();
        if (context.amuInfoRelevant()) {
            builder.append("Information generale AMU: L'AMU est l'Assurance Maladie Universelle. Elle facilite l'acces aux soins en prenant en charge une partie des frais selon des prestations, taux, conditions, documents requis et structures agreees.\n");
        }

        if (!context.prestations().isEmpty()) {
            builder.append("Prestations trouvees:\n");
            for (Prestation prestation : context.prestations()) {
                builder.append("- ")
                        .append(prestation.getNomActe())
                        .append(" | code: ").append(prestation.getCodeActe())
                        .append(" | pris en charge: ").append(Boolean.TRUE.equals(prestation.getPrisEnCharge()) ? "oui" : "non")
                        .append(" | taux: ").append(prestation.getTauxCouverture()).append("%")
                        .append(" | documents: ").append(nullToEmpty(prestation.getDocumentsRequis()))
                        .append(" | conditions: ").append(nullToEmpty(prestation.getConditionsPriseEnCharge()))
                        .append("\n");
            }
        }

        if (!context.structures().isEmpty()) {
            builder.append("Structures agreees trouvees:\n");
            for (StructureSante structure : context.structures()) {
                builder.append("- ")
                        .append(structure.getNom())
                        .append(" | ville: ").append(structure.getVille())
                        .append(" | type: ").append(structure.getType())
                        .append(" | telephone: ").append(structure.getTelephone())
                        .append(" | adresse: ").append(structure.getAdresse())
                        .append("\n");
            }
        }

        if (!context.medications().isEmpty()) {
            builder.append("DONNEES OFFICIELLES AMU - MEDICAMENT\n");
            for (MedicationEvidence medication : context.medications()) {
                builder.append("- code: ").append(nullToEmpty(medication.code()))
                        .append(" | nom: ").append(nullToEmpty(medication.nom()))
                        .append(" | dci: ").append(nullToEmpty(medication.dci()))
                        .append(" | dosage: ").append(nullToEmpty(medication.dosage()))
                        .append(" | forme pharmaceutique: ").append(nullToEmpty(medication.formePharmaceutique()))
                        .append(" | statut: ").append(nullToEmpty(medication.statut()))
                        .append(" | type medicament: ").append(nullToEmpty(medication.typeMedicament()))
                        .append(" | groupe therapeutique: ").append(nullToEmpty(medication.groupeTherapeutique()))
                        .append(" | prix public: ").append(medication.prixPublic())
                        .append(" | base remboursement: ").append(medication.baseRemboursement())
                        .append(" | taux couverture: ").append(medication.tauxCouverture())
                        .append(" | part INAM: ").append(medication.partInam())
                        .append(" | part beneficiaire: ").append(medication.partBeneficiaire())
                        .append(" | pris en charge: ").append(medication.prisEnCharge())
                        .append("\n");
            }
        }

        return builder.toString().trim();
    }

    private int scorePrestation(Prestation prestation, Set<String> tokens, String normalized) {
        String candidate = normalize(String.join(" ",
                nullToEmpty(prestation.getCodeActe()),
                nullToEmpty(prestation.getNomActe()),
                prestation.getCategorie() == null ? "" : prestation.getCategorie().name(),
                nullToEmpty(prestation.getDescription())));

        int score = scoreText(candidate, tokens);
        if (normalized.contains("docteur") || normalized.contains("medecin")) {
            score += candidate.contains("consultation") ? 5 : 0;
        }
        if (normalized.contains("radio") || normalized.contains("scanner")) {
            score += candidate.contains("radiologie") ? 5 : 0;
        }
        return score;
    }

    private int scoreStructure(StructureSante structure, Set<String> tokens, String normalized) {
        String candidate = normalize(String.join(" ",
                nullToEmpty(structure.getNom()),
                nullToEmpty(structure.getVille()),
                structure.getType() == null ? "" : structure.getType().name(),
                nullToEmpty(structure.getSpecialites())));

        int score = scoreText(candidate, tokens);
        if (normalized.contains("hopital") || normalized.contains("clinique") || normalized.contains("structure")) {
            score += 1;
        }
        return score;
    }

    private int scoreText(String candidate, Set<String> tokens) {
        Set<String> candidateTokens = tokens(candidate);
        int score = 0;
        for (String token : tokens) {
            if (candidateTokens.contains(token)) {
                score += 4;
            } else if (candidate.contains(token)) {
                score += 2;
            } else if (candidateTokens.stream().anyMatch(candidateToken -> similarity(candidateToken, token) >= 0.84)) {
                score += 1;
            }
        }
        return score;
    }

    private Set<String> tokens(String value) {
        return List.of(value.split("\\s+")).stream()
                .map(String::trim)
                .filter(token -> token.length() >= 3)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        String withoutAccents = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replace('-', ' ')
                .replaceAll("[^a-z0-9'\\s]", " ")
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ScoredPrestation(Prestation prestation, int score) {
    }

    private record ScoredStructure(StructureSante structure, int score) {
    }
}
