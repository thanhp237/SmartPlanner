package com.smartplanner.smartplanner.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record ProgressMetricsResponse(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal taskCompletionRate,
        BigDecimal averageHoursPerDay,
        Map<String, Integer> tasksByPriority, // HIGH, MEDIUM, LOW
        Map<String, Integer> tasksByStatus, // OPEN, DONE, CANCELED
        Integer totalSessions,
        Integer completedSessions) {
}
