package com.amuguide.backend.controller;

import com.amuguide.backend.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping
    public Map<String, Object> discuter(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        return chatbotService.repondre(message);
    }
}
