package com.amuguide.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatbotStatusDTO {
    String provider;
    String openAiModel;
    boolean openAiConfigured;
    boolean geminiConfigured;
    boolean ollamaEnabled;
    int medicationEntries;
    long faqEntries;
}
