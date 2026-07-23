package com.amuguide.backend.chat.nlp;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class IntentDetectionResult {
    ChatIntent intent;
    String normalizedMessage;
    String keyword;
    String city;
    Double latitude;
    Double longitude;
    double score;
}
