package com.smartplanner.smartplanner.dto.dashboard;

import java.time.LocalDateTime;

public record SessionReflectionResponse(
        Integer sessionId,
        String courseName,
        String taskTitle,
        String difficulty,
        String note,
        String aiStatus,
        Integer aiQualityScore,
        String aiSummary,
        String aiNextAction,
        String aiRevisionSuggestion,
        LocalDateTime aiAnalyzedAt,
        String aiError) {
}

