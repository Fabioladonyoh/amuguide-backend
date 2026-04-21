package com.amuguide.backend.controller;


import com.amuguide.backend.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.amuguide.backend.dto.ChatbotRequestDTO;
import com.amuguide.backend.dto.ChatbotResponseDTO;

import java.util.Map;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping
    public ChatbotResponseDTO discuter(@RequestBody ChatbotRequestDTO request) {
        return chatbotService.repondre(request.getMessage());
    }

}
