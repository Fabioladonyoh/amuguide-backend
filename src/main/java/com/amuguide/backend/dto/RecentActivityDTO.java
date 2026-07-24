package com.amuguide.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RecentActivityDTO {
    private String type;
    private String label;
    private Long entityId;
    private LocalDateTime date;
}
