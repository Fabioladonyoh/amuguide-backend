package com.amuguide.backend.dto;

import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotRequestDTO {

    @NotBlank(message = "La question est obligatoire")
    private String message ;
    private Long userId;
    private Long assureId;
    private String sessionId;
    private Double latitude;
    private Double longitude;

    public void setQuestion(String question) {
        if (this.message == null || this.message.isBlank()) {
            this.message = question;
        }
    }

    public void setText(String text) {
        if (this.message == null || this.message.isBlank()) {
            this.message = text;
        }
    }

}
