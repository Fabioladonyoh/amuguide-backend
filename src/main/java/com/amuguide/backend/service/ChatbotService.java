package com.amuguide.backend.service;

import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.dto.VerificationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final VerificationService verificationService;

    public ChatbotResponseDTO repondre(String message) {
        if (message == null || message.isBlank()) {
            return ChatbotResponseDTO.builder()
                    .message("Votre message est vide.")
                    .suggestions("Essayez : consultation, radiologie, dent")
                    .build();
        }

        String lower = message.toLowerCase();

        if (lower.contains("consultation")) {
            VerificationResponseDTO v = verificationService.verifierParCodeActe("CONS001");
            return mapToChatbot(v);
        }

        if (lower.contains("radiologie")) {
            VerificationResponseDTO v = verificationService.verifierParCodeActe("RAD001");
            return mapToChatbot(v);
        }

        if (lower.contains("dent")) {
            VerificationResponseDTO v = verificationService.verifierParCodeActe("DENT001");
            return mapToChatbot(v);
        }

        return ChatbotResponseDTO.builder()
                .message("Je n'ai pas compris votre demande.")
                .suggestions("Essayez : consultation, radiologie, dent")
                .build();
    }

    private ChatbotResponseDTO mapToChatbot(VerificationResponseDTO v) {
        return ChatbotResponseDTO.builder()
                .statut(v.getStatut())
                .message(v.getMessage())
                .codeActe(v.getCodeActe())
                .nomActe(v.getNomActe())
                .prisEnCharge(v.getPrisEnCharge())
                .tauxCouverture(v.getTauxCouverture())
                .conditionsPriseEnCharge(v.getConditionsPriseEnCharge())
                .documentsRequis(v.getDocumentsRequis())
                .build();
    }
}