package com.amuguide.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AssureDashboardDTO {
    private AssureProfileDTO profil;
    private long totalConversations;
    private long totalMessages;
    private long totalRecherchesStructures;
    private long totalDemandesAssistance;
    private long nombreConversations;
    private long nombreRecherches;
    private LocalDateTime derniereActivite;
}
