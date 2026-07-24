package com.amuguide.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDashboardStatisticsDTO {
    private long totalAssures;
    private long assuresActifs;
    private long assuresInactifs;
    private long totalAdministrateurs;
    private long administrateursActifs;
    private long totalPrestations;
    private long prestationsActives;
    private long totalMedicaments;
    private long medicamentsPrisEnCharge;
    private long totalStructures;
    private long structuresActives;
    private long totalFaq;
    private long faqActives;
    private long totalQuestionsChatbot;
    private long totalConversations;
}
