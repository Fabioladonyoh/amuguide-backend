package com.amuguide.backend.service;

import com.amuguide.backend.chat.nlp.IntentDetectionResult;
import com.amuguide.backend.chat.nlp.IntentDetector;
import com.amuguide.backend.chat.service.FaqChatbotService;
import com.amuguide.backend.chat.service.ResponseGenerator;
import com.amuguide.backend.dto.ChatbotRequestDTO;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.entity.AssureAMU;
import com.amuguide.backend.entity.ChatHistory;
import com.amuguide.backend.repository.AssureAMURepository;
import com.amuguide.backend.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private final IntentDetector intentDetector;
    private final FaqChatbotService faqChatbotService;
    private final ResponseGenerator responseGenerator;
    private final ChatHistoryRepository chatHistoryRepository;
    private final AssureAMURepository assureAMURepository;

    public ChatbotResponseDTO repondre(String message) {
        return repondre(ChatbotRequestDTO.builder().message(message).build());
    }

    public ChatbotResponseDTO repondre(ChatbotRequestDTO request) {
        String message = request == null ? null : request.getMessage();
        if (message == null || message.isBlank()) {
            return ChatbotResponseDTO.builder()
                    .intent("FALLBACK")
                    .message("Votre message est vide.")
                    .suggestions("Suggestions : Consultation, Radiologie, Hopital proche")
                    .suggestionList(List.of("Consultation", "Radiologie", "Hopital proche"))
                    .build();
        }

        IntentDetectionResult detection = intentDetector.detect(message).toBuilder()
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();
        ChatbotResponseDTO response = faqChatbotService.answer(detection)
                .orElseGet(() -> responseGenerator.generate(detection));
        enrichResponse(response, detection);
        saveHistory(request, detection, response);
        return response;
    }

    private void enrichResponse(ChatbotResponseDTO response, IntentDetectionResult detection) {
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
        if (response.getSources() == null) {
            response.setSources(List.of());
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
}
