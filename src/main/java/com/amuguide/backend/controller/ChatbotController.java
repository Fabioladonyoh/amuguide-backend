package com.amuguide.backend.controller;


import com.amuguide.backend.service.ChatbotService;
import com.amuguide.backend.chat.service.AiFallbackService;
import com.amuguide.backend.chat.service.MedicationKnowledgeService;
import com.amuguide.backend.repository.FaqChatbotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.amuguide.backend.dto.ChatbotRequestDTO;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.dto.ChatbotStatusDTO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
@Slf4j
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final AiFallbackService aiFallbackService;
    private final MedicationKnowledgeService medicationKnowledgeService;
    private final FaqChatbotRepository faqChatbotRepository;

    @PostMapping({"/api/chatbot", "/api/chat", "/api/chatbot/message"})
    public ChatbotResponseDTO discuter(@Valid @RequestBody ChatbotRequestDTO request) {
        log.info("Question recue par le chatbot : [{}]", request.getMessage());
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

}
