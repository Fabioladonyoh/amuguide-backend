package com.amuguide.backend.dto;

import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotRequestDTO {

    @NotBlank(message = "La question est obligatoire")
    @Size(max = 1000, message = "La question ne doit pas depasser 1000 caracteres")
    private String message ;
    private Long userId;
    private Long assureId;
    @Size(max = 120, message = "L'identifiant de session ne doit pas depasser 120 caracteres")
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
