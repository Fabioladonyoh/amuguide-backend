package com.amuguide.backend.controller;


import com.amuguide.backend.service.ChatbotService;
import com.amuguide.backend.chat.service.AiFallbackService;
import com.amuguide.backend.chat.service.MedicationKnowledgeService;
import com.amuguide.backend.repository.FaqChatbotRepository;
import com.amuguide.backend.repository.AssureAMURepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.amuguide.backend.dto.ChatbotRequestDTO;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.dto.ChatbotStatusDTO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final AiFallbackService aiFallbackService;
    private final MedicationKnowledgeService medicationKnowledgeService;
    private final FaqChatbotRepository faqChatbotRepository;
    private final AssureAMURepository assureAMURepository;

    @PostMapping({"/api/chatbot", "/api/chat", "/api/chatbot/message"})
    public ChatbotResponseDTO discuter(@Valid @RequestBody ChatbotRequestDTO request, Authentication authentication) {
        log.info("Question recue par le chatbot : [{}]", request.getMessage());
        attachAuthenticatedAssure(request, authentication);
        return chatbotService.repondre(request);
    }

    @GetMapping("/api/chat/status")
    public ChatbotStatusDTO status() {
        return ChatbotStatusDTO.builder()
                .provider(aiFallbackService.activeProvider())
                .openAiModel(aiFallbackService.openAiModel())
                .openAiConfigured(aiFallbackService.isOpenAiConfigured())
                .geminiConfigured(aiFallbackService.isGeminiConfigured())
                .ollamaEnabled(aiFallbackService.isOllamaEnabled())
                .medicationEntries(medicationKnowledgeService.count())
                .faqEntries(faqChatbotRepository.count())
                .build();
    }

    private void attachAuthenticatedAssure(ChatbotRequestDTO request, Authentication authentication) {
        if (authentication == null || authentication.getName() == null || !authentication.getName().startsWith("ASSURE:")) {
            return;
        }
        assureAMURepository.findByNumeroAMU(authentication.getName().substring(7))
                .ifPresent(assure -> {
                    request.setAssureId(assure.getIdAssure());
                    request.setUserId(assure.getIdAssure());
                });
    }

}
