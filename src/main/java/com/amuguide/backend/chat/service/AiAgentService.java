package com.amuguide.backend.chat.service;

import com.amuguide.backend.chat.nlp.ChatIntent;
import com.amuguide.backend.chat.nlp.IntentDetectionResult;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.entity.StructureSante;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiAgentService {

    private final AgentContextService agentContextService;
    private final AiFallbackService aiFallbackService;

    public ChatbotResponseDTO answer(IntentDetectionResult detection) {
        AgentContext context = agentContextService.build(detection);
        String contextPrompt = agentContextService.toPromptContext(context);

        if (context.hasEvidence()) {
            var aiAnswer = aiFallbackService.answer(detection.getNormalizedMessage(), contextPrompt);
            if (aiAnswer.isPresent()) {
                return base(aiAnswer.get(), suggestionsFor(context));
            }
            return base(localAnswer(context), suggestionsFor(context));
        }

        var aiAnswer = aiFallbackService.answer(detection.getNormalizedMessage(), "");
        return aiAnswer
                .map(answer -> base(answer, List.of("Consultation", "Hopital proche", "Documents requis")))
                .orElseGet(() -> base(
                        "Je peux vous aider sur l'AMU, les prestations couvertes, les medicaments du referentiel, les documents requis et les structures agreees. Reformulez votre question avec le soin, le medicament ou la ville concernee.",
                        List.of("C'est quoi l'AMU ?", "Paracetamol", "Hopital a Kara")));
    }

    private String localAnswer(AgentContext context) {
        StringBuilder builder = new StringBuilder();

        if (!context.prestations().isEmpty()) {
            Prestation prestation = context.prestations().get(0);
            builder.append("D'apres les donnees AMU disponibles, ")
                    .append(prestation.getNomActe())
                    .append(Boolean.TRUE.equals(prestation.getPrisEnCharge())
                            ? " est prise en charge"
                            : " n'est pas prise en charge")
                    .append(" avec un taux de ")
                    .append(prestation.getTauxCouverture())
                    .append("%.");
            if (prestation.getDocumentsRequis() != null && !prestation.getDocumentsRequis().isBlank()) {
                builder.append("\nDocuments requis : ").append(prestation.getDocumentsRequis()).append(".");
            }
            if (prestation.getConditionsPriseEnCharge() != null && !prestation.getConditionsPriseEnCharge().isBlank()) {
                builder.append("\nConditions : ").append(prestation.getConditionsPriseEnCharge()).append(".");
            }
        }

        if (!context.medications().isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("Dans les donnees officielles AMU disponibles en base PostgreSQL, j'ai trouve :");
            context.medications().stream()
                    .limit(5)
                    .forEach(medication -> builder.append("\n- ")
                            .append(medication.nom())
                            .append(" | code: ").append(medication.code())
                            .append(" | DCI: ").append(medication.dci())
                            .append(" | dosage: ").append(medication.dosage())
                            .append(" | taux: ").append(medication.tauxCouverture())
                            .append(" | part INAM: ").append(medication.partInam())
                            .append(" | part beneficiaire: ").append(medication.partBeneficiaire())
                            .append(" | statut: ").append(medication.statut()));
        }

        if (!context.structures().isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("Structures agreees pertinentes :");
            for (StructureSante structure : context.structures()) {
                builder.append("\n- ")
                        .append(structure.getNom())
                        .append(" (").append(structure.getVille()).append(", ")
                        .append(structure.getType()).append(")");
            }
        }

        if (builder.isEmpty() && context.amuInfoRelevant()) {
            builder.append("L'AMU est l'Assurance Maladie Universelle. Elle facilite l'acces aux soins en prenant en charge une partie des frais medicaux selon les prestations, les taux, les conditions et les structures agreees.");
        }

        if (!builder.isEmpty()) {
            builder.append("\n\nJe peux aussi verifier une prestation precise, rechercher une structure par ville ou chercher un medicament dans le referentiel.");
        }

        return builder.toString();
    }

    private List<String> suggestionsFor(AgentContext context) {
        if (!context.medications().isEmpty()) {
            return List.of("Autre medicament", "Documents requis", "Pharmacie agreee");
        }
        if (!context.structures().isEmpty()) {
            return List.of("Afficher la carte", "Consultation", "Autre ville");
        }
        if (!context.prestations().isEmpty()) {
            return List.of("Documents requis", "Hopital agree", "Autre prestation");
        }
        return List.of("Consultation", "Radiologie", "Hopital proche");
    }

    private ChatbotResponseDTO base(String message, List<String> suggestions) {
        return ChatbotResponseDTO.builder()
                .intent(ChatIntent.AGENT_ASSISTED.name())
                .message(message)
                .suggestionList(suggestions)
                .suggestions("Suggestions : " + String.join(", ", suggestions))
                .build();
    }
}
