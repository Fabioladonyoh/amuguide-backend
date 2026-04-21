package com.amuguide.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final VerificationService verificationService;

    public Map<String, Object> repondre(String message) {

        Map<String, Object> response = new HashMap<>();

        message = message.toLowerCase();

        if (message.contains("consultation")) {
            return verificationService.verifierParCodeActe("CONS001");
        }

        if (message.contains("radiologie")) {
            return verificationService.verifierParCodeActe("RAD001");
        }

        if (message.contains("dent")) {
            return verificationService.verifierParCodeActe("DENT001");
        }

        response.put("message", "Je n'ai pas compris votre demande.");
        response.put("suggestions", "Essayez : consultation, radiologie, dent");

        return response;
    }
}
